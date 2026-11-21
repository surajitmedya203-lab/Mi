package com.example.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.local.ChatMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.local.SpeakerProfileEntity
import com.example.data.preferences.AppLanguageMode
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAssistantService"

enum class AssistantActionType {
    NONE,
    OPEN_WHATSAPP,
    SEND_WHATSAPP,
    OPEN_APP,
    OPEN_MAPS,
    SEND_EMAIL,
    SET_ALARM,
    SET_TIMER,
    ADD_CALENDAR_EVENT,
    SEND_SMS,
    WEB_SEARCH,
    OPEN_SETTINGS,
    DIAL_NUMBER,
    TOGGLE_FLASHLIGHT,
    ADJUST_VOLUME,
    DEVICE_STATUS
}

data class TurnAnalysisResult(
    val emotion: String = "Neutral",
    val detectedLanguage: String = "Auto",
    val actionType: AssistantActionType = AssistantActionType.NONE,
    val actionTitle: String = "",
    val actionPayload: String = "",
    val suggestedMemory: String = ""
)

class GeminiAssistantService {

    private val analyzerSchema = Schema.obj(
        properties = mapOf(
            "emotion" to Schema.enumeration(
                values = listOf("Neutral", "Happy", "Curious", "Urgent", "Frustrated", "Empathetic"),
                description = "Detected emotional tone of the user's message"
            ),
            "detectedLanguage" to Schema.enumeration(
                values = listOf("Bengali", "English", "Banglish", "Multilingual"),
                description = "Primary language or script mix used by the user"
            ),
            "actionType" to Schema.enumeration(
                values = listOf(
                    "NONE",
                    "OPEN_WHATSAPP",
                    "SEND_WHATSAPP",
                    "OPEN_APP",
                    "OPEN_MAPS",
                    "SEND_EMAIL",
                    "SET_ALARM",
                    "SET_TIMER",
                    "ADD_CALENDAR_EVENT",
                    "SEND_SMS",
                    "WEB_SEARCH",
                    "OPEN_SETTINGS",
                    "DIAL_NUMBER",
                    "TOGGLE_FLASHLIGHT",
                    "ADJUST_VOLUME",
                    "DEVICE_STATUS"
                ),
                description = "Android device action requested by the user, or NONE if purely conversational"
            ),
            "actionTitle" to Schema.string(
                description = "Short button label for the device action, e.g. 'Open WhatsApp', 'Turn On Flashlight', or 'Set 7:00 AM Alarm'",
                nullable = true
            ),
            "actionPayload" to Schema.string(
                description = "Semicolon key=value pairs for the action. " +
                    "For OPEN_WHATSAPP: empty string. " +
                    "For SEND_WHATSAPP: contact=Rahim;body=I am coming soon. " +
                    "For OPEN_APP: app=camera|youtube|maps|gmail|music|browser. " +
                    "For OPEN_MAPS: query=Dhaka University. " +
                    "For SEND_EMAIL: to=;subject=Hello;body=Message text. " +
                    "For SET_ALARM: hour=7;minute=30;label=Wake up. " +
                    "For SET_TIMER: seconds=300;label=Tea timer. " +
                    "For ADD_CALENDAR_EVENT: title=Doctor;description=Checkup;minutesFromNow=60. " +
                    "For SEND_SMS: to=Rahim;body=I am on my way. " +
                    "For WEB_SEARCH: query=latest news Dhaka. " +
                    "For OPEN_SETTINGS: target=wifi|bluetooth|accessibility|general. " +
                    "For DIAL_NUMBER: number=Rahim or 017... " +
                    "For TOGGLE_FLASHLIGHT: state=on|off|toggle. " +
                    "For ADJUST_VOLUME: direction=up|down|mute. " +
                    "For DEVICE_STATUS: empty string.",
                nullable = true
            ),
            "suggestedMemory" to Schema.string(
                description = "A concise third-person personal fact to remember long-term if the user stated a preference, name, location, or routine; empty string otherwise.",
                nullable = true
            )
        ),
        optionalProperties = listOf("actionTitle", "actionPayload", "suggestedMemory")
    )

    private val analyzerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = analyzerSchema
                temperature = 0.2f
            }
        )
    }

    private fun buildSystemInstruction(
        profile: SpeakerProfileEntity?,
        memories: List<MemoryFactEntity>,
        languageMode: AppLanguageMode,
        accessibilityMode: Boolean,
        translationMode: Boolean
    ): Content {
        val memoryBlock = if (memories.isEmpty()) {
            "No long-term user facts stored yet."
        } else {
            memories.take(15).joinToString("\n") { "- [${it.category}] ${it.fact}" }
        }

        val languageRule = when (languageMode) {
            AppLanguageMode.AUTO ->
                "Detect whether the user speaks/writes Bengali (বাংলা), English, or Banglish (mixed Bengali-English). Reply naturally in the same language—use clean Bengali script when the user uses Bengali or asks in Banglish unless they prefer English."
            AppLanguageMode.BENGALI ->
                "Always reply in natural, warm, conversational Bengali (বাংলা script), keeping technical terms clear."
            AppLanguageMode.ENGLISH ->
                "Always reply in clear, natural conversational English."
        }

        val speakerInfo = profile?.let {
            "Active Speaker Profile: ${it.name}. Persona note: ${it.personaNote.ifBlank { "Standard helpful mode" }}."
        } ?: "Active Speaker Profile: Default User."

        val styleRule = when {
            translationMode ->
                "LIVE INTERPRETER MODE ACTIVE: Automatically detect if the user's input is Bengali/Banglish or English. If it is Bengali or Banglish, translate it directly into natural, fluent English. If it is English, translate it directly into natural, warm Bengali (বাংলা script). Output ONLY the translated text so Text-to-Speech speaks the translation cleanly without extra preamble."
            accessibilityMode ->
                "Keep sentences short, crystal clear, and structured for comfortable spoken listening by elderly or visually impaired users."
            else ->
                "Be concise, natural, and conversational for spoken voice output (typically 2-4 sentences unless detailed help or screen analysis is requested). Avoid heavy markdown symbols like asterisks or hash headings so text-to-speech reads smoothly."
        }

        val instruction = """
            You are Kotha AI (কথা), an advanced, empathetic multilingual voice assistant on Android.
            $speakerInfo
            
            Language rule:
            $languageRule
            
            Speaking & style rule:
            $styleRule
            
            Device, WhatsApp & Screen capabilities:
            - You can open WhatsApp, compose WhatsApp messages to a named contact or phone number, open apps (Camera, YouTube, Music, Maps, Gmail, Browser), dial numbers, send SMS/Email, set alarms/timers, create calendar events, search the web, toggle the phone's flashlight/torch, adjust media volume, check live battery/device status, and open device settings via Android intents and hardware APIs.
            - For WhatsApp messages and SMS: honestly note that you are opening WhatsApp/Messages with the recipient and text pre-filled so the user can review and tap Send (Android security does not allow third-party apps to silently press Send inside WhatsApp without user confirmation).
            - Never claim full unrestricted background control over private third-party apps; instead explain clearly what Android action is being launched.
            - If an image or screenshot is attached, inspect every visible detail and explain it clearly and helpfully.
            
            Long-term memory about this user:
            $memoryBlock
        """.trimIndent()

        return content { text(instruction) }
    }

    fun streamReply(
        userPrompt: String,
        attachedBitmap: Bitmap?,
        recentMessages: List<ChatMessageEntity>,
        profile: SpeakerProfileEntity?,
        memories: List<MemoryFactEntity>,
        languageMode: AppLanguageMode,
        accessibilityMode: Boolean,
        translationMode: Boolean = false
    ): Flow<String> = flow {
        val chatModel = Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = buildSystemInstruction(
                profile = profile,
                memories = memories,
                languageMode = languageMode,
                accessibilityMode = accessibilityMode,
                translationMode = translationMode
            ),
            generationConfig = generationConfig {
                temperature = if (translationMode) 0.35f else 0.7f
            }
        )

        val history = recentMessages
            .takeLast(12)
            .filter { it.content.isNotBlank() }
            .map { msg ->
                content(role = if (msg.isUser) "user" else "model") {
                    text(msg.content)
                }
            }

        val chat = chatModel.startChat(history = history)
        val outgoingContent = content {
            if (attachedBitmap != null) {
                image(attachedBitmap)
            }
            text(userPrompt)
        }

        withTimeout(60_000L) {
            chat.sendMessageStream(outgoingContent).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    emit(chunkText)
                }
            }
        }
    }

    suspend fun analyzeTurnMetadata(userPrompt: String): TurnAnalysisResult {
        return try {
            val prompt = """
                Analyze this voice assistant user utterance for emotion, language, device intent, and long-term memory worth saving.
                User utterance: "$userPrompt"
            """.trimIndent()

            val response = withTimeout(30_000L) {
                analyzerModel.generateContent(prompt)
            }
            val jsonString = response.text ?: return TurnAnalysisResult()
            val json = JSONObject(jsonString)

            val actionTypeStr = json.optString("actionType", "NONE")
            val parsedAction = runCatching {
                AssistantActionType.valueOf(actionTypeStr)
            }.getOrDefault(AssistantActionType.NONE)

            TurnAnalysisResult(
                emotion = json.optString("emotion", "Neutral"),
                detectedLanguage = json.optString("detectedLanguage", "Auto"),
                actionType = parsedAction,
                actionTitle = json.optString("actionTitle", ""),
                actionPayload = json.optString("actionPayload", ""),
                suggestedMemory = json.optString("suggestedMemory", "")
            )
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Metadata analysis timed out", e)
            TurnAnalysisResult()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Metadata analysis failed", e)
            TurnAnalysisResult()
        }
    }
}
