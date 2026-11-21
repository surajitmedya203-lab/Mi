package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.ai.AssistantActionType
import com.example.ai.GeminiAssistantService
import com.example.ai.TurnAnalysisResult
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ConversationThreadEntity
import com.example.data.local.KothaDatabase
import com.example.data.local.MemoryFactEntity
import com.example.data.local.SpeakerProfileEntity
import com.example.data.preferences.AppLanguageMode
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AssistantRepository(
    context: Context,
    private val database: KothaDatabase,
    private val geminiService: GeminiAssistantService
) {
    private val appContext = context.applicationContext
    private val threadDao = database.threadDao()
    private val messageDao = database.chatMessageDao()
    private val memoryDao = database.memoryDao()
    private val profileDao = database.speakerProfileDao()

    val threadsFlow: Flow<List<ConversationThreadEntity>> = threadDao.observeThreads()
    val profilesFlow: Flow<List<SpeakerProfileEntity>> = profileDao.observeProfiles()

    fun observeMessages(threadId: Long): Flow<List<ChatMessageEntity>> =
        messageDao.observeMessagesForThread(threadId)

    fun observeMemories(profileId: Long): Flow<List<MemoryFactEntity>> =
        memoryDao.observeMemories(profileId)

    suspend fun ensureDefaultProfile(): Long {
        if (profileDao.getProfileCount() == 0) {
            return profileDao.insertProfile(
                SpeakerProfileEntity(
                    name = "Primary Speaker",
                    preferredLanguage = AppLanguageMode.AUTO.name,
                    personaNote = "Friendly, clear, bilingual Bengali & English assistant",
                    pitchMultiplier = 1.0f,
                    isDefault = true
                )
            )
        }
        return 1L
    }

    suspend fun createNewThread(profileId: Long, title: String = "New Voice Session"): Long {
        val now = System.currentTimeMillis()
        return threadDao.insertThread(
            ConversationThreadEntity(
                title = title,
                profileId = profileId,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    suspend fun updateThreadTitle(threadId: Long, title: String) {
        threadDao.updateThreadTitle(threadId, title.take(42), System.currentTimeMillis())
    }

    suspend fun deleteThread(threadId: Long) {
        messageDao.deleteMessagesForThread(threadId)
        threadDao.deleteThread(threadId)
    }

    suspend fun saveMessage(message: ChatMessageEntity): Long {
        val insertedId = messageDao.insertMessage(message)
        threadDao.touchThreadUpdatedAt(message.threadId, message.timestamp)
        return insertedId
    }

    suspend fun getMessagesOnce(threadId: Long): List<ChatMessageEntity> =
        messageDao.getMessagesForThreadOnce(threadId)

    suspend fun markActionExecuted(messageId: Long) {
        messageDao.markActionExecuted(messageId)
    }

    suspend fun addMemory(profileId: Long, fact: String, category: String = "Personal") {
        val clean = fact.trim()
        if (clean.isNotEmpty()) {
            memoryDao.insertMemory(
                MemoryFactEntity(
                    profileId = profileId,
                    fact = clean,
                    category = category
                )
            )
        }
    }

    suspend fun deleteMemory(id: Long) {
        memoryDao.deleteMemory(id)
    }

    suspend fun getMemoriesOnce(profileId: Long): List<MemoryFactEntity> =
        memoryDao.getMemoriesOnce(profileId)

    suspend fun getProfileById(profileId: Long): SpeakerProfileEntity? =
        profileDao.getProfileById(profileId)

    suspend fun addSpeakerProfile(
        name: String,
        preferredLanguage: AppLanguageMode,
        personaNote: String,
        pitchMultiplier: Float
    ): Long {
        return profileDao.insertProfile(
            SpeakerProfileEntity(
                name = name.trim(),
                preferredLanguage = preferredLanguage.name,
                personaNote = personaNote.trim(),
                pitchMultiplier = pitchMultiplier.coerceIn(0.8f, 1.3f),
                isDefault = false
            )
        )
    }

    suspend fun deleteSpeakerProfile(profileId: Long) {
        profileDao.deleteProfile(profileId)
    }

    suspend fun clearAllUserData() {
        messageDao.clearAllMessages()
        threadDao.clearAllThreads()
        memoryDao.clearAllMemories()
    }

    fun isNetworkAvailable(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun streamCloudReply(
        userPrompt: String,
        attachedBitmap: Bitmap?,
        recentMessages: List<ChatMessageEntity>,
        profile: SpeakerProfileEntity?,
        memories: List<MemoryFactEntity>,
        languageMode: AppLanguageMode,
        accessibilityMode: Boolean,
        translationMode: Boolean = false
    ) = geminiService.streamReply(
        userPrompt = userPrompt,
        attachedBitmap = attachedBitmap,
        recentMessages = recentMessages,
        profile = profile,
        memories = memories,
        languageMode = languageMode,
        accessibilityMode = accessibilityMode,
        translationMode = translationMode
    )

    suspend fun analyzeTurnMetadata(userPrompt: String): TurnAnalysisResult =
        geminiService.analyzeTurnMetadata(userPrompt)

    /**
     * Honest on-device offline command engine for time, alarms, timers, settings,
     * and locally stored memories when offline mode is enabled or connectivity is absent.
     */
    suspend fun processOfflineCommand(
        userPrompt: String,
        profileId: Long,
        languageMode: AppLanguageMode
    ): Pair<String, TurnAnalysisResult> {
        val lower = userPrompt.lowercase(Locale.ROOT)
        val hasBn = userPrompt.any { it in '\u0980'..'\u09FF' }
        val useBn = languageMode == AppLanguageMode.BENGALI || (languageMode == AppLanguageMode.AUTO && hasBn)

        // Check WhatsApp intent offline
        if (lower.contains("whatsapp") || userPrompt.contains("হোয়াটসঅ্যাপ")) {
            val isMsg = lower.contains("message") || lower.contains("msg") || lower.contains("send") ||
                userPrompt.contains("মেসেজ") || userPrompt.contains("পাঠাও")
            return if (isMsg) {
                val reply = if (useBn) {
                    "হোয়াটসঅ্যাপ খোলা হচ্ছে। প্রাপক বেছে বা মেসেজ দেখে সেন্ড বাটনে ট্যাপ করুন।"
                } else {
                    "Opening WhatsApp composer so you can review and tap Send."
                }
                reply to TurnAnalysisResult(
                    emotion = "Neutral",
                    detectedLanguage = if (useBn) "Bengali" else "English",
                    actionType = AssistantActionType.SEND_WHATSAPP,
                    actionTitle = "Message on WhatsApp",
                    actionPayload = "body=${userPrompt.trim()}"
                )
            } else {
                val reply = if (useBn) {
                    "হোয়াটসঅ্যাপ খোলা হচ্ছে।"
                } else {
                    "Opening WhatsApp."
                }
                reply to TurnAnalysisResult(
                    emotion = "Neutral",
                    detectedLanguage = if (useBn) "Bengali" else "English",
                    actionType = AssistantActionType.OPEN_WHATSAPP,
                    actionTitle = "Open WhatsApp",
                    actionPayload = ""
                )
            }
        }

        // Check Camera intent offline
        if (lower.contains("camera") || userPrompt.contains("ক্যামেরা")) {
            val reply = if (useBn) "ক্যামেরা খোলা হচ্ছে।" else "Opening Camera."
            return reply to TurnAnalysisResult(
                emotion = "Neutral",
                detectedLanguage = if (useBn) "Bengali" else "English",
                actionType = AssistantActionType.OPEN_APP,
                actionTitle = "Open Camera",
                actionPayload = "app=camera"
            )
        }

        // Check alarm intent
        if (lower.contains("alarm") || userPrompt.contains("অ্যালার্ম")) {
            val hourMatch = Regex("""(\d{1,2})(?::(\d{2}))?""").find(lower)
            val hour = hourMatch?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 23) ?: 7
            val minute = hourMatch?.groupValues?.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
            val reply = if (useBn) {
                "অফলাইন মোডে অ্যালার্ম সেট করার কার্ড তৈরি করেছি। ট্যাপ করে নিশ্চিত করুন।"
            } else {
                "I prepared an offline alarm action card for $hour:${minute.toString().padStart(2, '0')}."
            }
            return reply to TurnAnalysisResult(
                emotion = "Neutral",
                detectedLanguage = if (useBn) "Bengali" else "English",
                actionType = AssistantActionType.SET_ALARM,
                actionTitle = "Set Alarm $hour:${minute.toString().padStart(2, '0')}",
                actionPayload = "hour=$hour;minute=$minute;label=Kotha Offline Alarm"
            )
        }

        // Check timer intent
        if (lower.contains("timer") || userPrompt.contains("টাইমার")) {
            val mins = Regex("""(\d{1,3})""").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 5
            val seconds = (mins * 60).coerceAtLeast(10)
            val reply = if (useBn) {
                "অফলাইন মোডে $mins মিনিটের টাইমার প্রস্তুত করা হয়েছে।"
            } else {
                "Prepared a $mins minute timer action on your device."
            }
            return reply to TurnAnalysisResult(
                emotion = "Neutral",
                detectedLanguage = if (useBn) "Bengali" else "English",
                actionType = AssistantActionType.SET_TIMER,
                actionTitle = "Start $mins min Timer",
                actionPayload = "seconds=$seconds;label=Kotha Timer"
            )
        }

        // Check settings intent
        if (lower.contains("wifi") || lower.contains("wi-fi") || lower.contains("bluetooth") ||
            lower.contains("setting") || userPrompt.contains("সেটিংস")
        ) {
            val target = when {
                lower.contains("wifi") || lower.contains("wi-fi") -> "wifi"
                lower.contains("bluetooth") -> "bluetooth"
                lower.contains("accessibility") -> "accessibility"
                else -> "general"
            }
            val reply = if (useBn) {
                "ডিভাইস সেটিংস খোলার বাটন নিচে দেওয়া হয়েছে।"
            } else {
                "Here is a direct shortcut to open $target settings on your device."
            }
            return reply to TurnAnalysisResult(
                emotion = "Neutral",
                detectedLanguage = if (useBn) "Bengali" else "English",
                actionType = AssistantActionType.OPEN_SETTINGS,
                actionTitle = "Open ${target.replaceFirstChar { it.uppercase() }} Settings",
                actionPayload = "target=$target"
            )
        }

        // Check flashlight/torch intent offline
        if (lower.contains("flashlight") || lower.contains("torch") ||
            userPrompt.contains("টর্চ") || userPrompt.contains("ফ্ল্যাশ")
        ) {
            val turnOff = lower.contains("off") || userPrompt.contains("বন্ধ")
            val turnOn = lower.contains("on") || userPrompt.contains("চালু") || userPrompt.contains("জ্বালাও")
            val stateParam = when {
                turnOff -> "off"
                turnOn -> "on"
                else -> "toggle"
            }
            val reply = when {
                turnOff && useBn -> "ফ্ল্যাশলাইট বন্ধ করা হচ্ছে।"
                turnOff -> "Turning off the flashlight."
                turnOn && useBn -> "ফ্ল্যাশলাইট চালু করা হচ্ছে।"
                turnOn -> "Turning on the flashlight."
                useBn -> "ফ্ল্যাশলাইট টগল করা হচ্ছে।"
                else -> "Toggling the flashlight."
            }
            return reply to TurnAnalysisResult(
                emotion = "Neutral",
                detectedLanguage = if (useBn) "Bengali" else "English",
                actionType = AssistantActionType.TOGGLE_FLASHLIGHT,
                actionTitle = if (turnOff) "Turn Off Flashlight" else "Toggle Flashlight",
                actionPayload = "state=$stateParam"
            )
        }

        // Check volume intent offline
        if (lower.contains("volume") || userPrompt.contains("ভলিউম") || userPrompt.contains("আওয়াজ")) {
            val direction = when {
                lower.contains("mute") || userPrompt.contains("মিউট") -> "mute"
                lower.contains("down") || lower.contains("lower") || userPrompt.contains("কমা") -> "down"
                else -> "up"
            }
            val reply = if (useBn) {
                "মিডিয়া ভলিউম পরিবর্তন করা হচ্ছে।"
            } else {
                "Adjusting media volume $direction."
            }
            return reply to TurnAnalysisResult(
                emotion = "Neutral",
                detectedLanguage = if (useBn) "Bengali" else "English",
                actionType = AssistantActionType.ADJUST_VOLUME,
                actionTitle = "Volume ${direction.replaceFirstChar { it.uppercase() }}",
                actionPayload = "direction=$direction"
            )
        }

        // Check battery / device status offline
        if (lower.contains("battery") || lower.contains("device status") ||
            userPrompt.contains("ব্যাটারি") || userPrompt.contains("চার্জ")
        ) {
            val t = com.example.actions.DeviceActionExecutor.readDeviceTelemetry(appContext)
            val reply = if (useBn) {
                val ch = if (t.isCharging) "চার্জিং হচ্ছে" else "ব্যাটারিতে চলছে"
                "আপনার ফোনের ব্যাটারি এখন ${t.batteryPercent}% ($ch) এবং মিডিয়া ভলিউম ${t.mediaVolumePercent}%।"
            } else {
                val ch = if (t.isCharging) "charging" else "on battery"
                "Your device battery is at ${t.batteryPercent}% ($ch), and media volume is ${t.mediaVolumePercent}%."
            }
            return reply to TurnAnalysisResult(
                emotion = "Neutral",
                detectedLanguage = if (useBn) "Bengali" else "English",
                actionType = AssistantActionType.DEVICE_STATUS,
                actionTitle = "Battery ${t.batteryPercent}%",
                actionPayload = ""
            )
        }

        // Check time/date query
        if (lower.contains("time") || lower.contains("date") ||
            userPrompt.contains("সময়") || userPrompt.contains("কয়টা") || userPrompt.contains("তারিখ")
        ) {
            val formatted = SimpleDateFormat("hh:mm a, EEEE, MMM d", Locale.getDefault()).format(Date())
            val reply = if (useBn) {
                "এখন স্থানীয় সময় ও তারিখ: $formatted (অফলাইন মোড)।"
            } else {
                "The current local device time is $formatted."
            }
            return reply to TurnAnalysisResult(
                emotion = "Neutral",
                detectedLanguage = if (useBn) "Bengali" else "English"
            )
        }

        // Check daily briefing or memory recall offline
        if (lower.contains("briefing") || lower.contains("remember") || lower.contains("memory") ||
            userPrompt.contains("ব্রিফিং") || userPrompt.contains("মনে") || userPrompt.contains("মেমরি")
        ) {
            val memories = getMemoriesOnce(profileId)
            val t = com.example.actions.DeviceActionExecutor.readDeviceTelemetry(appContext)
            val nowTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
            val reply = if (lower.contains("briefing") || userPrompt.contains("ব্রিফিং")) {
                val memPart = if (memories.isEmpty()) "" else " | Saved note: ${memories.first().fact}"
                if (useBn) {
                    "অফলাইন ব্রিফিং: সময় $nowTime, ব্যাটারি ${t.batteryPercent}%$memPart।"
                } else {
                    "Offline briefing: It is $nowTime, battery is ${t.batteryPercent}%$memPart."
                }
            } else if (memories.isEmpty()) {
                if (useBn) "এই প্রোফাইলে এখনও কোনো সংরক্ষিত মেমরি নেই।" else "No saved memories found on this device yet."
            } else {
                val summary = memories.take(5).joinToString("; ") { it.fact }
                if (useBn) "আপনার সংরক্ষিত মেমরিগুলো হলো: $summary" else "Here are your saved local memories: $summary"
            }
            return reply to TurnAnalysisResult(
                emotion = "Warm",
                detectedLanguage = if (useBn) "Bengali" else "English"
            )
        }

        val fallback = if (useBn) {
            "আমি এখন অফলাইন অন-ডিভাইস মোডে আছি। টর্চ, ভলিউম, ব্যাটারি স্ট্যাটাস, অ্যালার্ম, টাইমার, সময় জানা, সেটিংস খোলা বা সংরক্ষিত মেমরি দেখার কাজ অফলাইনে করতে পারি। বিস্তারিত এআই উত্তরের জন্য ইন্টারনেট চালু করে অনলাইন মোডে ফিরুন।"
        } else {
            "I am currently in Offline On-Device Mode. I can toggle the flashlight, adjust volume, check battery status, set alarms or timers, open settings, and read saved memories. Turn off Offline Mode with internet access for full Gemini AI reasoning."
        }
        return fallback to TurnAnalysisResult(
            emotion = "Neutral",
            detectedLanguage = if (useBn) "Bengali" else "English"
        )
    }
}
