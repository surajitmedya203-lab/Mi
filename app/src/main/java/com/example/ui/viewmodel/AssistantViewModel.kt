package com.example.ui.viewmodel

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.actions.DeviceActionExecutor
import com.example.ai.AssistantActionType
import com.example.ai.GeminiAssistantService
import com.example.ai.GeminiLiveVoiceManager
import com.example.ai.LiveTalkState
import com.example.ai.TurnAnalysisResult
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationThreadEntity
import com.example.data.local.KothaDatabase
import com.example.data.local.MemoryFactEntity
import com.example.data.local.SpeakerProfileEntity
import com.example.data.preferences.AppLanguageMode
import com.example.data.preferences.UserPreferences
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.AssistantRepository
import com.example.voice.VoiceSpeechManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

private const val TAG = "AssistantViewModel"

sealed interface AssistantUiState {
    data object Idle : AssistantUiState
    data object Listening : AssistantUiState
    data object Thinking : AssistantUiState
    data class Streaming(val partialReply: String) : AssistantUiState
    data object Speaking : AssistantUiState
    data class Error(val message: String) : AssistantUiState
}

data class PendingAttachment(
    val uri: Uri,
    val bitmap: Bitmap
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val database = KothaDatabase.getInstance(application)
    private val geminiService = GeminiAssistantService()
    private val repository = AssistantRepository(application, database, geminiService)
    private val prefsRepo = UserPreferencesRepository(application)
    val voiceManager = VoiceSpeechManager(application)
    val liveVoiceManager = GeminiLiveVoiceManager()
    val liveTalkState: StateFlow<LiveTalkState> = liveVoiceManager.state

    val preferences: StateFlow<UserPreferences> = prefsRepo.preferencesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferences()
    )

    val profiles: StateFlow<List<SpeakerProfileEntity>> = repository.profilesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val threads: StateFlow<List<ConversationThreadEntity>> = repository.threadsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _currentThreadId = MutableStateFlow(0L)
    val currentThreadId: StateFlow<Long> = _currentThreadId.asStateFlow()

    private val _incognitoMessages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessageEntity>> = combine(
        _currentThreadId,
        preferences,
        _incognitoMessages
    ) { threadId, prefs, incognitoList ->
        Triple(threadId, prefs.incognitoMode, incognitoList)
    }.flatMapLatest { (threadId, incognito, incognitoList) ->
        if (incognito) {
            MutableStateFlow(incognitoList)
        } else if (threadId > 0L) {
            repository.observeMessages(threadId)
        } else {
            MutableStateFlow(emptyList())
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val memories: StateFlow<List<MemoryFactEntity>> = preferences
        .flatMapLatest { prefs -> repository.observeMemories(prefs.activeProfileId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow<AssistantUiState>(AssistantUiState.Idle)
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private val _isNetworkOnline = MutableStateFlow(repository.isNetworkAvailable())
    val isNetworkOnline: StateFlow<Boolean> = _isNetworkOnline.asStateFlow()

    private val _pendingAttachment = MutableStateFlow<PendingAttachment?>(null)
    val pendingAttachment: StateFlow<PendingAttachment?> = _pendingAttachment.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _needsContactsPermission = MutableStateFlow(false)
    val needsContactsPermission: StateFlow<Boolean> = _needsContactsPermission.asStateFlow()

    private var pendingContactActionMessage: ChatMessageEntity? = null

    private val _lastEmotion = MutableStateFlow("Neutral")
    val lastEmotion: StateFlow<String> = _lastEmotion.asStateFlow()

    private val _lastLanguage = MutableStateFlow("Auto")
    val lastLanguage: StateFlow<String> = _lastLanguage.asStateFlow()

    private val _isFlashlightOn = MutableStateFlow(DeviceActionExecutor.currentTorchEnabled)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    private val _isTranslationMode = MutableStateFlow(false)
    val isTranslationMode: StateFlow<Boolean> = _isTranslationMode.asStateFlow()

    private var activeGenerationJob: Job? = null
    private var lastPromptProcessedAt = 0L

    init {
        viewModelScope.launch {
            val defaultProfileId = repository.ensureDefaultProfile()
            val existingThreads = repository.threadsFlow.first()
            if (_currentThreadId.value == 0L) {
                _currentThreadId.value = if (existingThreads.isNotEmpty()) {
                    existingThreads.first().id
                } else {
                    repository.createNewThread(defaultProfileId)
                }
            }
        }

        // Observe listening and speaking states to keep AssistantUiState synchronized
        viewModelScope.launch {
            voiceManager.isListening.collect { listening ->
                if (listening) {
                    _uiState.value = AssistantUiState.Listening
                } else if (_uiState.value is AssistantUiState.Listening) {
                    _uiState.value = AssistantUiState.Idle
                }
            }
        }

        viewModelScope.launch {
            voiceManager.isSpeaking.collect { speaking ->
                if (speaking && activeGenerationJob?.isActive != true) {
                    _uiState.value = AssistantUiState.Speaking
                } else if (!speaking && _uiState.value is AssistantUiState.Speaking) {
                    _uiState.value = AssistantUiState.Idle
                }
            }
        }

        voiceManager.setCallbacks(
            onFinalTranscript = { transcript ->
                handleVoiceTranscript(transcript)
            },
            onListeningEnded = { _ -> },
            onSpeechCompleted = {
                val prefs = preferences.value
                if (prefs.handsFreeContinuous && activeGenerationJob?.isActive != true) {
                    viewModelScope.launch {
                        delay(380L)
                        if (_uiState.value is AssistantUiState.Idle) {
                            startVoiceListening()
                        }
                    }
                }
            }
        )
    }

    fun refreshNetworkState() {
        _isNetworkOnline.value = repository.isNetworkAvailable()
    }

    fun startVoiceListening() {
        refreshNetworkState()
        liveVoiceManager.stopLiveTalk()
        activeGenerationJob?.cancel()
        voiceManager.stopSpeaking()
        voiceManager.clearStatusNote()
        val preferOnDevice = preferences.value.offlineModeOnly || !_isNetworkOnline.value
        voiceManager.startListening(
            languageMode = preferences.value.languageMode,
            preferOnDevice = preferOnDevice
        )
    }

    fun startLiveGeminiTalk() {
        refreshNetworkState()
        val prefs = preferences.value
        if (prefs.offlineModeOnly || !_isNetworkOnline.value) {
            _statusBanner.value = "Live Gemini Talk requires online mode. Connect to the internet or disable Offline Mode."
            return
        }

        activeGenerationJob?.cancel()
        activeGenerationJob = null
        voiceManager.cancelListening()
        voiceManager.stopSpeaking()
        voiceManager.clearStatusNote()
        _statusBanner.value = null
        _uiState.value = AssistantUiState.Idle

        viewModelScope.launch {
            val profile = repository.getProfileById(prefs.activeProfileId)
            val memoriesList = if (prefs.incognitoMode) {
                emptyList()
            } else {
                repository.getMemoriesOnce(prefs.activeProfileId)
            }
            liveVoiceManager.startLiveTalk(
                context = getApplication(),
                profile = profile,
                memories = memoriesList,
                languageMode = prefs.languageMode,
                scope = viewModelScope,
                onActionExecuted = { message ->
                    viewModelScope.launch {
                        _isFlashlightOn.value = DeviceActionExecutor.currentTorchEnabled
                        _statusBanner.value = message
                    }
                },
                onTranscriptSaved = { userText, modelText ->
                    viewModelScope.launch {
                        persistLiveTranscriptTurn(userText, modelText)
                    }
                }
            )
        }
    }

    fun stopLiveGeminiTalk() {
        liveVoiceManager.stopLiveTalk()
    }

    fun clearLiveTalkError() {
        liveVoiceManager.clearError()
    }

    private suspend fun persistLiveTranscriptTurn(userText: String, modelText: String) {
        if (userText.isBlank() && modelText.isBlank()) return
        val prefs = preferences.value
        val threadId = ensureActiveThread(prefs)

        if (userText.isNotBlank()) {
            val uMsg = ChatMessageEntity(
                threadId = threadId,
                isUser = true,
                content = userText,
                languageTag = "Live"
            )
            if (prefs.incognitoMode) {
                _incognitoMessages.value = _incognitoMessages.value + uMsg.copy(id = System.currentTimeMillis())
            } else {
                val hadMessages = messages.value.isNotEmpty()
                repository.saveMessage(uMsg)
                if (!hadMessages) {
                    repository.updateThreadTitle(threadId, userText.take(40))
                }
            }
        }

        if (modelText.isNotBlank()) {
            val mMsg = ChatMessageEntity(
                threadId = threadId,
                isUser = false,
                content = modelText,
                emotionTag = "Live",
                languageTag = "Gemini Live"
            )
            saveAssistantMessage(prefs.incognitoMode, mMsg)
        }
    }

    fun finishVoiceListening() {
        voiceManager.finishListening()
    }

    fun onSystemSpeechTranscript(transcript: String) {
        if (transcript.isNotBlank()) {
            handleVoiceTranscript(transcript)
        }
    }

    fun interruptAndStop() {
        liveVoiceManager.stopLiveTalk()
        activeGenerationJob?.cancel()
        activeGenerationJob = null
        voiceManager.cancelListening()
        voiceManager.stopSpeaking()
        _uiState.value = AssistantUiState.Idle
    }

    fun interruptAndListen() {
        interruptAndStop()
        startVoiceListening()
    }

    private fun handleVoiceTranscript(rawTranscript: String) {
        val prefs = preferences.value
        val cleaned = rawTranscript.trim()
        if (cleaned.isEmpty()) return

        if (prefs.wakePhraseMode) {
            val wakePrefixes = listOf(
                "hey kotha", "ok kotha", "hello kotha", "kotha",
                "হ্যালো কথা", "হে কথা", "ওকে কথা", "কথা"
            )
            val lower = cleaned.lowercase()
            val matchedPrefix = wakePrefixes.firstOrNull { lower.startsWith(it) }
            if (matchedPrefix == null) {
                _statusBanner.value = "Wake word required: start with \"Hey Kotha\" or \"হ্যালো কথা\"."
                if (prefs.handsFreeContinuous) {
                    viewModelScope.launch {
                        delay(500L)
                        startVoiceListening()
                    }
                }
                return
            } else {
                val stripped = cleaned.drop(matchedPrefix.length).trim(' ', ',', ':', '।', '!')
                val finalCmd = stripped.ifBlank { "হ্যালো! কেমন আছো?" }
                sendMessage(finalCmd)
                return
            }
        }

        sendMessage(cleaned)
    }

    fun sendMessage(rawPrompt: String) {
        val prompt = rawPrompt.trim()
        val attachment = _pendingAttachment.value
        if (prompt.isEmpty() && attachment == null) return

        val effectivePrompt = if (prompt.isEmpty()) {
            "Please examine this image/screenshot and explain what is on the screen."
        } else {
            prompt
        }

        // Prevent accidental double submit
        val now = System.currentTimeMillis()
        if (now - lastPromptProcessedAt < 250L) return
        lastPromptProcessedAt = now

        refreshNetworkState()
        liveVoiceManager.stopLiveTalk()
        activeGenerationJob?.cancel()
        voiceManager.stopSpeaking()
        voiceManager.cancelListening()
        _pendingAttachment.value = null
        _statusBanner.value = null

        activeGenerationJob = viewModelScope.launch {
            val prefs = preferences.value
            val activeProfile = repository.getProfileById(prefs.activeProfileId)
            val effectivePitch = (prefs.ttsPitch * (activeProfile?.pitchMultiplier ?: 1.0f))
                .coerceIn(0.7f, 1.4f)

            val threadId = ensureActiveThread(prefs)

            val userMsg = ChatMessageEntity(
                threadId = threadId,
                isUser = true,
                content = effectivePrompt,
                hasAttachment = attachment != null
            )

            if (prefs.incognitoMode) {
                _incognitoMessages.value = _incognitoMessages.value + userMsg
            } else {
                repository.saveMessage(userMsg)
                if (messages.value.isEmpty()) {
                    repository.updateThreadTitle(threadId, effectivePrompt.take(40))
                }
            }

            _uiState.value = AssistantUiState.Thinking

            val shouldUseOffline = prefs.offlineModeOnly || !_isNetworkOnline.value
            if (shouldUseOffline) {
                try {
                    val (offlineReply, offlineMeta) = repository.processOfflineCommand(
                        userPrompt = effectivePrompt,
                        profileId = prefs.activeProfileId,
                        languageMode = prefs.languageMode
                    )
                    _lastEmotion.value = offlineMeta.emotion
                    _lastLanguage.value = offlineMeta.detectedLanguage

                    val aiMsg = ChatMessageEntity(
                        threadId = threadId,
                        isUser = false,
                        content = offlineReply,
                        emotionTag = offlineMeta.emotion,
                        languageTag = offlineMeta.detectedLanguage,
                        actionType = offlineMeta.actionType.name,
                        actionTitle = offlineMeta.actionTitle,
                        actionPayload = offlineMeta.actionPayload,
                        isOfflineReply = true
                    )
                    val savedId = saveAssistantMessage(prefs.incognitoMode, aiMsg)
                    if (prefs.autoExecuteActions && offlineMeta.actionType != AssistantActionType.NONE) {
                        executeActionCard(aiMsg.copy(id = savedId))
                    }

                    if (prefs.autoSpeakReplies) {
                        _uiState.value = AssistantUiState.Speaking
                        voiceManager.speakText(
                            text = offlineReply,
                            speed = prefs.ttsSpeed,
                            pitch = effectivePitch,
                            emotion = offlineMeta.emotion,
                            queueMode = TextToSpeech.QUEUE_FLUSH
                        )
                    } else {
                        _uiState.value = AssistantUiState.Idle
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Offline processing failed", e)
                    _uiState.value = AssistantUiState.Error(e.localizedMessage ?: "Offline error.")
                }
                return@launch
            }

            // Online Firebase AI Logic path: parallel structured metadata analysis + streaming response
            try {
                val memoriesList = if (prefs.incognitoMode) {
                    emptyList()
                } else {
                    repository.getMemoriesOnce(prefs.activeProfileId)
                }
                val history = if (prefs.incognitoMode) {
                    _incognitoMessages.value.dropLast(1)
                } else {
                    repository.getMessagesOnce(threadId).dropLast(1)
                }

                val analysisDeferred = async {
                    repository.analyzeTurnMetadata(effectivePrompt)
                }

                val fullReply = StringBuilder()
                val speechBuffer = StringBuilder()
                var firstSpokenChunk = true

                repository.streamCloudReply(
                    userPrompt = effectivePrompt,
                    attachedBitmap = attachment?.bitmap,
                    recentMessages = history,
                    profile = activeProfile,
                    memories = memoriesList,
                    languageMode = prefs.languageMode,
                    accessibilityMode = prefs.accessibilityLargeMode,
                    translationMode = _isTranslationMode.value
                ).collect { chunk ->
                    fullReply.append(chunk)
                    val currentText = fullReply.toString()
                    _uiState.value = AssistantUiState.Streaming(currentText)

                    if (prefs.autoSpeakReplies) {
                        speechBuffer.append(chunk)
                        val buffered = speechBuffer.toString()
                        val sentenceEndIdx = findSentenceBoundary(buffered)
                        if (sentenceEndIdx >= 0) {
                            val sentenceToSpeak = buffered.substring(0, sentenceEndIdx + 1).trim()
                            val remainder = buffered.substring(sentenceEndIdx + 1)
                            speechBuffer.clear()
                            speechBuffer.append(remainder)
                            if (sentenceToSpeak.isNotEmpty()) {
                                voiceManager.speakText(
                                    text = sentenceToSpeak,
                                    speed = prefs.ttsSpeed,
                                    pitch = effectivePitch,
                                    queueMode = if (firstSpokenChunk) {
                                        firstSpokenChunk = false
                                        TextToSpeech.QUEUE_FLUSH
                                    } else {
                                        TextToSpeech.QUEUE_ADD
                                    }
                                )
                            }
                        }
                    }
                }

                // Speak any trailing buffered text
                if (prefs.autoSpeakReplies && speechBuffer.isNotBlank()) {
                    voiceManager.speakText(
                        text = speechBuffer.toString().trim(),
                        speed = prefs.ttsSpeed,
                        pitch = effectivePitch,
                        queueMode = if (firstSpokenChunk) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                    )
                }

                val finalReplyText = fullReply.toString().trim()
                if (finalReplyText.isEmpty()) {
                    _uiState.value = AssistantUiState.Error("Gemini returned an empty response.")
                    return@launch
                }

                val meta = analysisDeferred.await()
                _lastEmotion.value = meta.emotion
                _lastLanguage.value = meta.detectedLanguage

                if (!prefs.incognitoMode && meta.suggestedMemory.isNotBlank()) {
                    repository.addMemory(
                        profileId = prefs.activeProfileId,
                        fact = meta.suggestedMemory,
                        category = "Learned"
                    )
                    _statusBanner.value = "Remembered: ${meta.suggestedMemory}"
                }

                val aiMsg = ChatMessageEntity(
                    threadId = threadId,
                    isUser = false,
                    content = finalReplyText,
                    emotionTag = meta.emotion,
                    languageTag = meta.detectedLanguage,
                    actionType = meta.actionType.name,
                    actionTitle = meta.actionTitle,
                    actionPayload = meta.actionPayload
                )
                val savedId = saveAssistantMessage(prefs.incognitoMode, aiMsg)
                if (prefs.autoExecuteActions && meta.actionType != AssistantActionType.NONE) {
                    executeActionCard(aiMsg.copy(id = savedId))
                }

                _uiState.value = if (voiceManager.isSpeaking.value) {
                    AssistantUiState.Speaking
                } else {
                    AssistantUiState.Idle
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini request timed out", e)
                _uiState.value = AssistantUiState.Error("Response timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini streaming failed", e)
                _uiState.value = AssistantUiState.Error(
                    e.localizedMessage ?: "AI request failed. Check network or App Check status."
                )
            }
        }
    }

    private suspend fun ensureActiveThread(prefs: UserPreferences): Long {
        if (prefs.incognitoMode) return -1L
        val existing = _currentThreadId.value
        if (existing > 0L) return existing
        val created = repository.createNewThread(prefs.activeProfileId)
        _currentThreadId.value = created
        return created
    }

    private suspend fun saveAssistantMessage(incognito: Boolean, msg: ChatMessageEntity): Long {
        return if (incognito) {
            val syntheticId = System.currentTimeMillis()
            _incognitoMessages.value = _incognitoMessages.value + msg.copy(id = syntheticId)
            syntheticId
        } else {
            repository.saveMessage(msg)
        }
    }

    private fun findSentenceBoundary(text: String): Int {
        for (i in text.indices) {
            val c = text[i]
            if (c == '।' || c == '.' || c == '?' || c == '!' || c == '\n') {
                if (i >= 10) return i
            }
        }
        return -1
    }

    fun attachImageFromUri(uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                decodeScaledBitmap(uri, maxDim = 1024)
            }
            if (bitmap != null) {
                _pendingAttachment.value = PendingAttachment(uri, bitmap)
            } else {
                _statusBanner.value = "Could not load selected image."
            }
        }
    }

    fun clearPendingAttachment() {
        _pendingAttachment.value = null
    }

    private fun decodeScaledBitmap(uri: Uri, maxDim: Int): Bitmap? {
        return try {
            val resolver = getApplication<Application>().contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream: InputStream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return null

            var sampleSize = 1
            while (origW / sampleSize > maxDim || origH / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            resolver.openInputStream(uri)?.use { stream: InputStream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode image", e)
            null
        }
    }

    fun executeActionCard(message: ChatMessageEntity) {
        val actionType = runCatching {
            AssistantActionType.valueOf(message.actionType)
        }.getOrDefault(AssistantActionType.NONE)

        val result = DeviceActionExecutor.execute(
            context = getApplication(),
            actionType = actionType,
            actionPayload = message.actionPayload
        )
        _isFlashlightOn.value = DeviceActionExecutor.currentTorchEnabled
        _statusBanner.value = result.message
        _needsContactsPermission.value = result.needsContactsPermission
        if (result.needsContactsPermission) {
            pendingContactActionMessage = message
        } else {
            pendingContactActionMessage = null
        }
        if (result.success && !preferences.value.incognitoMode && message.id > 0L) {
            viewModelScope.launch {
                repository.markActionExecuted(message.id)
            }
        }
    }

    fun onContactsPermissionGranted() {
        _needsContactsPermission.value = false
        val pending = pendingContactActionMessage
        pendingContactActionMessage = null
        if (pending != null) {
            executeActionCard(pending)
        }
    }

    fun sendWhatsAppToPickedContactUri(contactPhoneUri: Uri) {
        val pending = pendingContactActionMessage
        val resolved = com.example.actions.WhatsAppMessagingUtil.resolveSelectedContactFromUri(
            context = getApplication(),
            contactPhoneUri = contactPhoneUri
        )
        if (resolved == null) {
            _statusBanner.value = "Selected contact has no phone number."
            return
        }

        val body = pending?.actionPayload
            ?.split(";", "|")
            ?.firstOrNull { it.trim().lowercase().startsWith("body=") }
            ?.substringAfter("=")
            ?.trim()
            .orEmpty()

        val result = com.example.actions.WhatsAppMessagingUtil.sendFormattedMessage(
            context = getApplication(),
            phoneNumber = resolved.phoneNumber,
            formattedMessage = body,
            displayName = resolved.displayName
        )
        _statusBanner.value = result.message
        _needsContactsPermission.value = false
        pendingContactActionMessage = null
        if (result.success && pending != null && !preferences.value.incognitoMode && pending.id > 0L) {
            viewModelScope.launch {
                repository.markActionExecuted(pending.id)
            }
        }
    }

    fun replayMessageVoice(text: String, emotion: String = "Neutral") {
        val prefs = preferences.value
        val activeProfile = profiles.value.firstOrNull { it.id == prefs.activeProfileId }
        val effectivePitch = (prefs.ttsPitch * (activeProfile?.pitchMultiplier ?: 1.0f))
            .coerceIn(0.7f, 1.4f)
        voiceManager.speakText(
            text = text,
            speed = prefs.ttsSpeed,
            pitch = effectivePitch,
            emotion = emotion,
            queueMode = TextToSpeech.QUEUE_FLUSH
        )
    }

    fun openMusicQuickAction() {
        val result = DeviceActionExecutor.openMusicPlayer(getApplication())
        _statusBanner.value = result.message
    }

    fun speakCurrentTime(formattedTime: String) {
        val prefs = preferences.value
        val spoken = when (prefs.languageMode) {
            AppLanguageMode.ENGLISH -> "The current time is $formattedTime."
            AppLanguageMode.BENGALI, AppLanguageMode.AUTO -> "এখন সময় $formattedTime।"
        }
        replayMessageVoice(spoken, emotion = "Warm")
        _statusBanner.value = spoken
    }

    fun toggleFlashlightFromUi() {
        val target = !_isFlashlightOn.value
        val result = DeviceActionExecutor.setFlashlight(getApplication(), target)
        _isFlashlightOn.value = DeviceActionExecutor.currentTorchEnabled
        _statusBanner.value = result.message
    }

    fun toggleTranslationMode() {
        val next = !_isTranslationMode.value
        _isTranslationMode.value = next
        _statusBanner.value = if (next) {
            "Live Translator active: speak or type in বাংলা or English."
        } else {
            "Returned to normal Assistant conversation mode."
        }
    }

    fun triggerDailyBriefing() {
        val app = getApplication<Application>()
        val telemetry = DeviceActionExecutor.readDeviceTelemetry(app)
        val dateClock = SimpleDateFormat("EEEE, MMM d • hh:mm a", Locale.getDefault()).format(Date())
        val chargingState = if (telemetry.isCharging) "charging" else "on battery"
        val prompt = if (preferences.value.languageMode == AppLanguageMode.ENGLISH) {
            "Give me a warm, concise daily voice briefing. Current time & date: $dateClock. " +
                "Device battery: ${telemetry.batteryPercent}% ($chargingState). " +
                "Use my saved personal memories if relevant, and keep it natural for speech."
        } else {
            "আমার জন্য একটি সংক্ষিপ্ত ও আন্তরিক ডেইলি ব্রিফিং দাও। এখন সময় ও তারিখ: $dateClock। " +
                "ফোনের ব্যাটারি: ${telemetry.batteryPercent}% ($chargingState)। " +
                "আমার সংরক্ষিত মেমরি প্রাসঙ্গিক হলে উল্লেখ করো এবং কথা বলার উপযোগী সহজ ভাষায় বলো।"
        }
        sendMessage(prompt)
    }

    fun copyTextToClipboard(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Kotha AI", clean))
        _statusBanner.value = "Copied to clipboard."
    }

    fun saveMessageAsMemory(text: String) {
        viewModelScope.launch {
            repository.addMemory(
                profileId = preferences.value.activeProfileId,
                fact = text.take(180),
                category = "Pinned"
            )
            _statusBanner.value = "Saved to long-term memory."
        }
    }

    fun startNewSession() {
        interruptAndStop()
        viewModelScope.launch {
            if (preferences.value.incognitoMode) {
                _incognitoMessages.value = emptyList()
            } else {
                val id = repository.createNewThread(preferences.value.activeProfileId)
                _currentThreadId.value = id
            }
        }
    }

    fun selectThread(threadId: Long) {
        interruptAndStop()
        _currentThreadId.value = threadId
    }

    fun deleteThread(threadId: Long) {
        viewModelScope.launch {
            repository.deleteThread(threadId)
            if (_currentThreadId.value == threadId) {
                val id = repository.createNewThread(preferences.value.activeProfileId)
                _currentThreadId.value = id
            }
        }
    }

    fun addManualMemory(fact: String, category: String) {
        viewModelScope.launch {
            repository.addMemory(preferences.value.activeProfileId, fact, category)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch { repository.deleteMemory(id) }
    }

    fun selectProfile(profileId: Long) {
        viewModelScope.launch {
            prefsRepo.setActiveProfileId(profileId)
        }
    }

    fun addSpeakerProfile(
        name: String,
        preferredLanguage: AppLanguageMode,
        personaNote: String,
        pitchMultiplier: Float
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.addSpeakerProfile(name, preferredLanguage, personaNote, pitchMultiplier)
            prefsRepo.setActiveProfileId(newId)
        }
    }

    fun deleteSpeakerProfile(profileId: Long) {
        viewModelScope.launch {
            repository.deleteSpeakerProfile(profileId)
            if (preferences.value.activeProfileId == profileId) {
                prefsRepo.setActiveProfileId(1L)
            }
        }
    }

    fun setLanguageMode(mode: AppLanguageMode) = viewModelScope.launch { prefsRepo.setLanguageMode(mode) }
    fun setTtsSpeed(speed: Float) = viewModelScope.launch { prefsRepo.setTtsSpeed(speed) }
    fun setTtsPitch(pitch: Float) = viewModelScope.launch { prefsRepo.setTtsPitch(pitch) }
    fun setAutoSpeak(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAutoSpeak(enabled) }
    fun setAutoExecuteActions(enabled: Boolean) = viewModelScope.launch { prefsRepo.setAutoExecuteActions(enabled) }
    fun setHandsFreeContinuous(enabled: Boolean) = viewModelScope.launch { prefsRepo.setHandsFreeContinuous(enabled) }
    fun setWakePhraseMode(enabled: Boolean) = viewModelScope.launch { prefsRepo.setWakePhraseMode(enabled) }
    fun setOfflineModeOnly(enabled: Boolean) = viewModelScope.launch { prefsRepo.setOfflineModeOnly(enabled) }
    fun setIncognitoMode(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setIncognitoMode(enabled)
        if (enabled) _incognitoMessages.value = emptyList()
    }
    fun setAccessibilityLargeMode(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setAccessibilityLargeMode(enabled)
    }
    fun setEdgeGlowEnabled(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setEdgeGlowEnabled(enabled)
    }
    fun setEdgeGlowAlwaysOn(enabled: Boolean) = viewModelScope.launch {
        prefsRepo.setEdgeGlowAlwaysOn(enabled)
    }

    fun clearAllHistoryAndMemories() {
        viewModelScope.launch {
            repository.clearAllUserData()
            _incognitoMessages.value = emptyList()
            val newId = repository.createNewThread(preferences.value.activeProfileId)
            _currentThreadId.value = newId
            _statusBanner.value = "All local conversation history and memories cleared."
        }
    }

    fun dismissBanner() {
        _statusBanner.value = null
        _needsContactsPermission.value = false
        pendingContactActionMessage = null
        voiceManager.clearStatusNote()
    }

    fun clearErrorState() {
        if (_uiState.value is AssistantUiState.Error) {
            _uiState.value = AssistantUiState.Idle
        }
    }

    override fun onCleared() {
        super.onCleared()
        liveVoiceManager.release()
        voiceManager.release()
    }
}
