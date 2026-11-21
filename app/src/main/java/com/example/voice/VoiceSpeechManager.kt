package com.example.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.data.preferences.AppLanguageMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "VoiceSpeechManager"

class VoiceSpeechManager(context: Context) {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _rmsLevel = MutableStateFlow(0f)
    val rmsLevel: StateFlow<Float> = _rmsLevel.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _speechStatusNote = MutableStateFlow<String?>(null)
    val speechStatusNote: StateFlow<String?> = _speechStatusNote.asStateFlow()

    private val _isRecognizerAvailable = MutableStateFlow(
        checkAnyRecognizerAvailable()
    )
    val isRecognizerAvailable: StateFlow<Boolean> = _isRecognizerAvailable.asStateFlow()

    private val _isTtsReady = MutableStateFlow(false)
    val isTtsReady: StateFlow<Boolean> = _isTtsReady.asStateFlow()

    private data class QueuedSpeech(
        val text: String,
        val speed: Float,
        val pitch: Float,
        val emotion: String,
        val queueMode: Int
    )

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    @Volatile
    private var ttsReady = false
    private val pendingUtteranceCount = AtomicInteger(0)
    private val preInitSpeechQueue = mutableListOf<QueuedSpeech>()

    private var onFinalTranscriptCallback: ((String) -> Unit)? = null
    private var onListeningEndedCallback: ((Boolean) -> Unit)? = null
    private var onSpeechCompletedCallback: (() -> Unit)? = null

    init {
        initTts()
    }

    private fun checkAnyRecognizerAvailable(): Boolean {
        val standard = SpeechRecognizer.isRecognitionAvailable(appContext)
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
        return standard || onDevice
    }

    private fun initTts() {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                    }

                    override fun onDone(utteranceId: String?) {
                        if (pendingUtteranceCount.decrementAndGet().coerceAtLeast(0) == 0) {
                            pendingUtteranceCount.set(0)
                            _isSpeaking.value = false
                            mainHandler.post { onSpeechCompletedCallback?.invoke() }
                        }
                    }

                    override fun onStop(utteranceId: String?, interrupted: Boolean) {
                        pendingUtteranceCount.set(0)
                        _isSpeaking.value = false
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (pendingUtteranceCount.decrementAndGet().coerceAtLeast(0) == 0) {
                            pendingUtteranceCount.set(0)
                            _isSpeaking.value = false
                        }
                    }
                })
                ttsReady = true
                _isTtsReady.value = true
                Log.d(TAG, "TextToSpeech initialized successfully.")

                mainHandler.post {
                    if (preInitSpeechQueue.isNotEmpty()) {
                        val buffered = preInitSpeechQueue.toList()
                        preInitSpeechQueue.clear()
                        buffered.forEach { req ->
                            speakText(req.text, req.speed, req.pitch, req.emotion, req.queueMode)
                        }
                    }
                }
            } else {
                ttsReady = false
                _isTtsReady.value = false
                Log.e(TAG, "TextToSpeech initialization failed with status $status")
            }
        }
    }

    fun setCallbacks(
        onFinalTranscript: (String) -> Unit,
        onListeningEnded: (hadResult: Boolean) -> Unit,
        onSpeechCompleted: () -> Unit
    ) {
        onFinalTranscriptCallback = onFinalTranscript
        onListeningEndedCallback = onListeningEnded
        onSpeechCompletedCallback = onSpeechCompleted
    }

    fun createRecognizerIntent(languageMode: AppLanguageMode): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageMode.localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageMode.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
        }
    }

    fun startListening(
        languageMode: AppLanguageMode,
        preferOnDevice: Boolean = false
    ) {
        mainHandler.post {
            stopSpeakingInternal()

            val canUseOnDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            val canUseStandard = SpeechRecognizer.isRecognitionAvailable(appContext)

            if (!canUseStandard && !canUseOnDevice) {
                _isRecognizerAvailable.value = false
                _speechStatusNote.value = "Built-in SpeechRecognizer unavailable on this device. Try the keyboard or System Voice input."
                _isListening.value = false
                return@post
            }
            _isRecognizerAvailable.value = true

            try {
                speechRecognizer?.destroy()
                speechRecognizer = if (preferOnDevice && canUseOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                } else if (canUseStandard) {
                    SpeechRecognizer.createSpeechRecognizer(appContext)
                } else {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                }

                speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                    private var emittedFinal = false

                    override fun onReadyForSpeech(params: Bundle?) {
                        _isListening.value = true
                        _speechStatusNote.value = null
                        _partialTranscript.value = ""
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                        _rmsLevel.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _rmsLevel.value = 0f
                    }

                    override fun onError(error: Int) {
                        _isListening.value = false
                        _rmsLevel.value = 0f

                        // Rescue partial transcript if recognizer timed out after hearing words
                        val rescuedPartial = _partialTranscript.value.trim()
                        if (!emittedFinal && rescuedPartial.isNotEmpty() &&
                            (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                                error == SpeechRecognizer.ERROR_NO_MATCH)
                        ) {
                            emittedFinal = true
                            _partialTranscript.value = ""
                            _speechStatusNote.value = null
                            onFinalTranscriptCallback?.invoke(rescuedPartial)
                            onListeningEndedCallback?.invoke(true)
                            return
                        }

                        _partialTranscript.value = ""
                        val message = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — tap the orb or speak again."
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
                            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
                            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed."
                            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer was busy — tap orb to retry."
                            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                                "Speech recognition network unavailable. Try Offline On-Device or typing."
                            else -> "Speech recognizer stopped (code $error)."
                        }
                        if (!emittedFinal) {
                            _speechStatusNote.value = message
                            onListeningEndedCallback?.invoke(false)
                        }
                    }

                    override fun onResults(results: Bundle?) {
                        _isListening.value = false
                        _rmsLevel.value = 0f
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val best = matches?.firstOrNull()?.trim().orEmpty()
                        _partialTranscript.value = ""
                        if (best.isNotEmpty()) {
                            emittedFinal = true
                            _speechStatusNote.value = null
                            onFinalTranscriptCallback?.invoke(best)
                            onListeningEndedCallback?.invoke(true)
                        } else {
                            onListeningEndedCallback?.invoke(false)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            .orEmpty()
                        if (partial.isNotBlank()) {
                            _partialTranscript.value = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = createRecognizerIntent(languageMode)
                speechRecognizer?.startListening(intent)
                _isListening.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start SpeechRecognizer", e)
                _isListening.value = false
                _speechStatusNote.value = "Could not start microphone: ${e.localizedMessage}"
            }
        }
    }

    /**
     * Signals SpeechRecognizer that the user finished speaking so buffered audio is processed
     * and delivered via onResults() to the Gemini AI pipeline.
     */
    fun finishListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Error finishing speech recognition", e)
            }
            _rmsLevel.value = 0f
        }
    }

    /**
     * Immediately aborts recognition without waiting for final results.
     */
    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Error canceling speech recognition", e)
            }
            _isListening.value = false
            _rmsLevel.value = 0f
            _partialTranscript.value = ""
        }
    }

    fun speakText(
        text: String,
        speed: Float,
        pitch: Float,
        emotion: String = "Neutral",
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ) {
        val cleaned = cleanTextForTts(text)
        if (cleaned.isBlank()) return

        mainHandler.post {
            if (!ttsReady) {
                if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                    preInitSpeechQueue.clear()
                }
                preInitSpeechQueue.add(QueuedSpeech(cleaned, speed, pitch, emotion, queueMode))
                return@post
            }

            val engine = tts ?: return@post
            val hasBengaliScript = cleaned.any { it in '\u0980'..'\u09FF' }
            val targetLocale = if (hasBengaliScript) {
                Locale("bn", "BD")
            } else {
                Locale.US
            }

            val langResult = engine.setLanguage(targetLocale)
            if (langResult == TextToSpeech.LANG_MISSING_DATA ||
                langResult == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                if (hasBengaliScript) {
                    val inLocale = Locale("bn", "IN")
                    val fallbackBn = engine.setLanguage(inLocale)
                    if (fallbackBn == TextToSpeech.LANG_MISSING_DATA ||
                        fallbackBn == TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        engine.setLanguage(Locale.US)
                    }
                } else {
                    engine.setLanguage(Locale.US)
                }
            }

            val (rateDelta, pitchDelta) = when (emotion.lowercase()) {
                "urgent" -> 0.08f to 0.04f
                "happy" -> 0.04f to 0.08f
                "empathetic", "frustrated" -> -0.07f to -0.04f
                else -> 0f to 0f
            }

            engine.setSpeechRate((speed + rateDelta).coerceIn(0.6f, 1.7f))
            engine.setPitch((pitch + pitchDelta).coerceIn(0.65f, 1.45f))

            if (queueMode == TextToSpeech.QUEUE_FLUSH) {
                pendingUtteranceCount.set(0)
            }

            val maxLen = TextToSpeech.getMaxSpeechInputLength().coerceAtLeast(500)
            val chunks = if (cleaned.length <= maxLen) {
                listOf(cleaned)
            } else {
                cleaned.chunked(maxLen)
            }

            chunks.forEachIndexed { index, segment ->
                val mode = if (index == 0) queueMode else TextToSpeech.QUEUE_ADD
                pendingUtteranceCount.incrementAndGet()
                _isSpeaking.value = true
                val utteranceId = UUID.randomUUID().toString()
                engine.speak(segment, mode, null, utteranceId)
            }
        }
    }

    fun stopSpeaking() {
        mainHandler.post {
            preInitSpeechQueue.clear()
            stopSpeakingInternal()
        }
    }

    private fun stopSpeakingInternal() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        }
        pendingUtteranceCount.set(0)
        _isSpeaking.value = false
    }

    fun clearStatusNote() {
        _speechStatusNote.value = null
    }

    private fun cleanTextForTts(raw: String): String {
        return raw
            .replace("**", "")
            .replace("__", "")
            .replace("`", "")
            .replace("#", "")
            .replace("•", ",")
            .trim()
    }

    fun release() {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
                tts?.stop()
                tts?.shutdown()
                tts = null
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing speech resources", e)
            }
        }
    }
}
