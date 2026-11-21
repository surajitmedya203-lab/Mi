package com.example.ai

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.actions.DeviceActionExecutor
import com.example.data.local.MemoryFactEntity
import com.example.data.local.SpeakerProfileEntity
import com.example.data.preferences.AppLanguageMode
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.FunctionResponsePart
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveAudioConversationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

private const val TAG = "GeminiLiveVoiceManager"
private const val LIVE_MODEL_NAME = "gemini-3.1-flash-live-preview"

sealed interface LiveTalkState {
    data object Idle : LiveTalkState
    data object Connecting : LiveTalkState
    data class Active(
        val userTranscript: String = "",
        val modelTranscript: String = "",
        val elapsedSeconds: Int = 0
    ) : LiveTalkState
    data class Error(val message: String) : LiveTalkState
}

@OptIn(PublicPreviewAPI::class)
class GeminiLiveVoiceManager {

    private val _state = MutableStateFlow<LiveTalkState>(LiveTalkState.Idle)
    val state: StateFlow<LiveTalkState> = _state.asStateFlow()

    private var activeSession: LiveSession? = null
    private var timerJob: Job? = null
    private var sessionJob: Job? = null

    private val userTranscriptBuffer = StringBuilder()
    private val modelTranscriptBuffer = StringBuilder()
    private var onSessionTranscriptFlush: ((userText: String, modelText: String) -> Unit)? = null

    private val liveTools: Tool by lazy {
        Tool.functionDeclarations(
            listOf(
                FunctionDeclaration(
                    name = "open_whatsapp",
                    description = "Opens WhatsApp on the user's Android device.",
                    parameters = emptyMap()
                ),
                FunctionDeclaration(
                    name = "send_whatsapp_message",
                    description = "Opens WhatsApp with a contact name or phone number and pre-filled message text.",
                    parameters = mapOf(
                        "contact" to Schema.string("Contact name or phone number"),
                        "message" to Schema.string("Formatted message body to send")
                    )
                ),
                FunctionDeclaration(
                    name = "toggle_flashlight",
                    description = "Turns the device flashlight/torch ON or OFF.",
                    parameters = mapOf(
                        "state" to Schema.string("Either 'on', 'off', or 'toggle'")
                    )
                ),
                FunctionDeclaration(
                    name = "adjust_volume",
                    description = "Raises, lowers, or mutes media volume on the phone.",
                    parameters = mapOf(
                        "direction" to Schema.string("Either 'up', 'down', or 'mute'")
                    )
                ),
                FunctionDeclaration(
                    name = "get_device_status",
                    description = "Reads the device's live battery percentage, charging status, and media volume.",
                    parameters = emptyMap()
                ),
                FunctionDeclaration(
                    name = "set_alarm",
                    description = "Sets an alarm on the user's device.",
                    parameters = mapOf(
                        "hour" to Schema.integer("Hour in 24-hour format (0-23)"),
                        "minute" to Schema.integer("Minute (0-59)"),
                        "label" to Schema.string("Short alarm title")
                    )
                ),
                FunctionDeclaration(
                    name = "web_search",
                    description = "Opens a live Google web search for real-time information.",
                    parameters = mapOf(
                        "query" to Schema.string("Search query")
                    )
                )
            )
        )
    }

    @SuppressLint("MissingPermission")
    fun startLiveTalk(
        context: Context,
        profile: SpeakerProfileEntity?,
        memories: List<MemoryFactEntity>,
        languageMode: AppLanguageMode,
        scope: CoroutineScope,
        onActionExecuted: (String) -> Unit,
        onTranscriptSaved: (userText: String, modelText: String) -> Unit
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            _state.value = LiveTalkState.Error("Microphone permission is required for Live Gemini Talk.")
            return
        }

        stopLiveTalk()
        onSessionTranscriptFlush = onTranscriptSaved
        userTranscriptBuffer.clear()
        modelTranscriptBuffer.clear()
        _state.value = LiveTalkState.Connecting

        sessionJob = scope.launch(Dispatchers.IO) {
            try {
                val appContext = context.applicationContext
                val memorySummary = if (memories.isEmpty()) {
                    "No saved personal facts yet."
                } else {
                    memories.take(10).joinToString("; ") { it.fact }
                }
                val langHint = when (languageMode) {
                    AppLanguageMode.AUTO -> "Speak naturally in Bengali (বাংলা), English, or Banglish matching whatever the user speaks."
                    AppLanguageMode.BENGALI -> "Always respond warmly in natural Bengali (বাংলা)."
                    AppLanguageMode.ENGLISH -> "Always respond clearly in natural English."
                }
                val speaker = profile?.name ?: "Primary Speaker"
                val sysInstruction = content {
                    text(
                        """
                        You are Kotha (কথা), a warm, ultra-responsive live AI voice companion having a real-time phone-call-style conversation with $speaker.
                        $langHint
                        Keep spoken replies concise, lively, and natural for real-time audio conversation.
                        You can call device tools when asked to open WhatsApp, send WhatsApp messages, toggle the flashlight, adjust volume, check battery status, set alarms, or search the web.
                        Known user memories: $memorySummary
                        """.trimIndent()
                    )
                }

                val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                    modelName = LIVE_MODEL_NAME,
                    generationConfig = liveGenerationConfig {
                        responseModality = ResponseModality.AUDIO
                        speechConfig = SpeechConfig(voice = Voice("Aoede"))
                        inputAudioTranscription = AudioTranscriptionConfig()
                        outputAudioTranscription = AudioTranscriptionConfig()
                    },
                    systemInstruction = sysInstruction,
                    tools = listOf(liveTools)
                )

                val session = withTimeout(25_000L) {
                    liveModel.connect()
                }
                activeSession = session
                _state.value = LiveTalkState.Active()

                timerJob?.cancel()
                timerJob = scope.launch {
                    var secs = 0
                    while (isActive) {
                        delay(1000L)
                        secs += 1
                        val current = _state.value
                        if (current is LiveTalkState.Active) {
                            _state.value = current.copy(elapsedSeconds = secs)
                        } else {
                            break
                        }
                    }
                }

                val convConfig = liveAudioConversationConfig {
                    functionCallHandler = { call ->
                        handleLiveFunctionCall(appContext, call, onActionExecuted)
                    }
                    transcriptHandler = { inputTranscript, outputTranscript ->
                        val inText = inputTranscript?.text?.trim().orEmpty()
                        val outText = outputTranscript?.text?.trim().orEmpty()
                        if (inText.isNotEmpty()) {
                            if (userTranscriptBuffer.isNotEmpty()) userTranscriptBuffer.append(" ")
                            userTranscriptBuffer.append(inText)
                        }
                        if (outText.isNotEmpty()) {
                            if (modelTranscriptBuffer.isNotEmpty()) modelTranscriptBuffer.append(" ")
                            modelTranscriptBuffer.append(outText)
                        }
                        val cur = _state.value
                        if (cur is LiveTalkState.Active) {
                            _state.value = cur.copy(
                                userTranscript = userTranscriptBuffer.toString().takeLast(220),
                                modelTranscript = modelTranscriptBuffer.toString().takeLast(240)
                            )
                        }
                    }
                    enableInterruptions = true
                }

                session.startAudioConversation(convConfig)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini Live connection timed out", e)
                cleanupSessionOnly()
                _state.value = LiveTalkState.Error("Gemini Live connection timed out. Check internet or App Check.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Live session failed", e)
                cleanupSessionOnly()
                _state.value = LiveTalkState.Error(
                    e.localizedMessage ?: "Live Gemini Talk could not connect."
                )
            }
        }
    }

    private fun handleLiveFunctionCall(
        context: Context,
        call: FunctionCallPart,
        onActionExecuted: (String) -> Unit
    ): FunctionResponsePart {
        fun arg(key: String): String =
            runCatching { call.args[key]?.jsonPrimitive?.content.orEmpty() }.getOrDefault("")

        val result = when (call.name) {
            "open_whatsapp" -> DeviceActionExecutor.execute(context, AssistantActionType.OPEN_WHATSAPP, "")
            "send_whatsapp_message" -> {
                val payload = "contact=${arg("contact")};body=${arg("message")}"
                DeviceActionExecutor.execute(context, AssistantActionType.SEND_WHATSAPP, payload)
            }
            "toggle_flashlight" -> {
                val stateArg = arg("state").ifBlank { "toggle" }
                DeviceActionExecutor.execute(context, AssistantActionType.TOGGLE_FLASHLIGHT, "state=$stateArg")
            }
            "adjust_volume" -> {
                val dir = arg("direction").ifBlank { "up" }
                DeviceActionExecutor.execute(context, AssistantActionType.ADJUST_VOLUME, "direction=$dir")
            }
            "get_device_status" -> DeviceActionExecutor.execute(context, AssistantActionType.DEVICE_STATUS, "")
            "set_alarm" -> {
                val payload = "hour=${arg("hour")};minute=${arg("minute")};label=${arg("label")}"
                DeviceActionExecutor.execute(context, AssistantActionType.SET_ALARM, payload)
            }
            "web_search" -> {
                DeviceActionExecutor.execute(context, AssistantActionType.WEB_SEARCH, "query=${arg("query")}")
            }
            else -> com.example.actions.ActionExecutionResult(false, "Unknown tool: ${call.name}")
        }

        onActionExecuted(result.message)
        return FunctionResponsePart(
            name = call.name,
            response = JsonObject(
                mapOf(
                    "success" to JsonPrimitive(result.success),
                    "message" to JsonPrimitive(result.message)
                )
            ),
            id = call.id
        )
    }

    fun stopLiveTalk() {
        val uText = userTranscriptBuffer.toString().trim()
        val mText = modelTranscriptBuffer.toString().trim()
        userTranscriptBuffer.clear()
        modelTranscriptBuffer.clear()

        if (uText.isNotEmpty() || mText.isNotEmpty()) {
            onSessionTranscriptFlush?.invoke(uText, mText)
        }
        onSessionTranscriptFlush = null

        cleanupSessionOnly()
        _state.value = LiveTalkState.Idle
    }

    private fun cleanupSessionOnly() {
        timerJob?.cancel()
        timerJob = null
        sessionJob?.cancel()
        sessionJob = null
        val s = activeSession
        activeSession = null
        if (s != null) {
            runCatching { s.stopAudioConversation() }
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { s.close() }
            }
        }
    }

    fun clearError() {
        if (_state.value is LiveTalkState.Error) {
            _state.value = LiveTalkState.Idle
        }
    }

    fun release() {
        cleanupSessionOnly()
        _state.value = LiveTalkState.Idle
    }
}
