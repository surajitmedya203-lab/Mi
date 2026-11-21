package com.example.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.ai.AssistantActionType
import java.util.Calendar

data class ActionExecutionResult(
    val success: Boolean,
    val message: String,
    val needsContactsPermission: Boolean = false
)

data class DeviceTelemetry(
    val batteryPercent: Int,
    val isCharging: Boolean,
    val mediaVolumePercent: Int
)

object DeviceActionExecutor {

    @Volatile
    var currentTorchEnabled: Boolean = false
        private set

    private fun parsePayload(payload: String): Map<String, String> {
        if (payload.isBlank()) return emptyMap()
        return payload.split(";", "|")
            .mapNotNull { part ->
                val idx = part.indexOf('=')
                if (idx > 0) {
                    val key = part.substring(0, idx).trim().lowercase()
                    val value = part.substring(idx + 1).trim()
                    key to value
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun findInstalledWhatsAppPackage(context: Context): String? {
        val candidates = listOf("com.whatsapp", "com.whatsapp.w4b")
        return candidates.firstOrNull { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg) != null
        }
    }

    fun execute(
        context: Context,
        actionType: AssistantActionType,
        actionPayload: String
    ): ActionExecutionResult {
        val params = parsePayload(actionPayload)
        return try {
            when (actionType) {
                AssistantActionType.OPEN_WHATSAPP -> {
                    val waPkg = findInstalledWhatsAppPackage(context)
                    if (waPkg != null) {
                        val launch = context.packageManager.getLaunchIntentForPackage(waPkg)
                        if (launch != null) {
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(launch)
                            return ActionExecutionResult(true, "Opened WhatsApp.")
                        }
                    }
                    // Fallback to Play Store listing if WhatsApp isn't installed
                    val storeIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                    context.startActivity(storeIntent)
                    ActionExecutionResult(
                        success = false,
                        message = "WhatsApp is not installed on this device; opened Play Store."
                    )
                }

                AssistantActionType.SEND_WHATSAPP -> {
                    val contactQuery = params["contact"].orEmpty().ifBlank { params["to"].orEmpty() }
                    val messageBody = params["body"].orEmpty()
                    WhatsAppMessagingUtil.sendFormattedMessageToContact(
                        context = context,
                        contactQuery = contactQuery,
                        formattedMessage = messageBody
                    )
                }

                AssistantActionType.OPEN_APP -> {
                    val target = params["app"]?.lowercase().orEmpty()
                    val intent: Intent? = when {
                        target.contains("camera") || target.contains("ক্যামেরা") ->
                            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                        target.contains("youtube") || target.contains("ইউটিউব") ->
                            context.packageManager.getLaunchIntentForPackage("com.google.android.youtube")
                                ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                        target.contains("map") || target.contains("ম্যাপ") ->
                            context.packageManager.getLaunchIntentForPackage("com.google.android.apps.maps")
                                ?: Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
                        target.contains("gmail") || target.contains("mail") || target.contains("ইমেইল") ->
                            context.packageManager.getLaunchIntentForPackage("com.google.android.gm")
                                ?: Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                        target.contains("whatsapp") || target.contains("হোয়াটসঅ্যাপ") ->
                            findInstalledWhatsAppPackage(context)?.let {
                                context.packageManager.getLaunchIntentForPackage(it)
                            }
                        target.contains("music") || target.contains("spotify") || target.contains("গান") || target.contains("মিউজিক") ->
                            createMusicPlayerIntent(context)
                        target.contains("browser") || target.contains("chrome") || target.contains("ব্রাউজার") ->
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                        else -> null
                    }
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        ActionExecutionResult(true, "Opened ${params["app"]?.ifBlank { "app" } ?: "app"}.")
                    } else {
                        ActionExecutionResult(false, "Could not find a handler for \"${params["app"]}\".")
                    }
                }

                AssistantActionType.OPEN_MAPS -> {
                    val query = params["query"].orEmpty()
                    val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionExecutionResult(true, "Opened Maps for \"$query\".")
                }

                AssistantActionType.SEND_EMAIL -> {
                    val to = params["to"].orEmpty()
                    val subject = params["subject"].orEmpty()
                    val body = params["body"].orEmpty()
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:${Uri.encode(to)}")
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionExecutionResult(true, "Opened Email composer.")
                }

                AssistantActionType.SET_ALARM -> {
                    val now = Calendar.getInstance()
                    val hour = params["hour"]?.toIntOrNull()?.coerceIn(0, 23)
                        ?: ((now.get(Calendar.HOUR_OF_DAY) + 1) % 24)
                    val minute = params["minute"]?.toIntOrNull()?.coerceIn(0, 59) ?: 0
                    val label = params["label"]?.takeIf { it.isNotBlank() } ?: "Kotha AI Alarm"
                    val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                        putExtra(AlarmClock.EXTRA_HOUR, hour)
                        putExtra(AlarmClock.EXTRA_MINUTES, minute)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionExecutionResult(true, "Opened Alarm clock for $hour:${minute.toString().padStart(2, '0')}.")
                }

                AssistantActionType.SET_TIMER -> {
                    val seconds = params["seconds"]?.toIntOrNull()?.coerceAtLeast(5) ?: 60
                    val label = params["label"]?.takeIf { it.isNotBlank() } ?: "Kotha AI Timer"
                    val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
                        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                        putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionExecutionResult(true, "Started timer setup.")
                }

                AssistantActionType.ADD_CALENDAR_EVENT -> {
                    val title = params["title"]?.takeIf { it.isNotBlank() } ?: "Kotha Reminder"
                    val desc = params["description"].orEmpty()
                    val minsFromNow = params["minutesfromnow"]?.toLongOrNull() ?: 60L
                    val beginMillis = System.currentTimeMillis() + minsFromNow * 60_000L
                    val intent = Intent(Intent.ACTION_INSERT).apply {
                        data = CalendarContract.Events.CONTENT_URI
                        putExtra(CalendarContract.Events.TITLE, title)
                        putExtra(CalendarContract.Events.DESCRIPTION, desc)
                        putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
                        putExtra(CalendarContract.EXTRA_EVENT_END_TIME, beginMillis + 30 * 60_000L)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionExecutionResult(true, "Opened Calendar event creator.")
                }

                AssistantActionType.SEND_SMS -> {
                    val contactQuery = params["to"].orEmpty().ifBlank { params["contact"].orEmpty() }
                    val body = params["body"].orEmpty()
                    val explicitDigits = contactQuery.filter { it.isDigit() }
                    val resolved = if (explicitDigits.length >= 3) {
                        explicitDigits
                    } else if (contactQuery.isNotBlank()) {
                        ContactLookupHelper.findPhoneByName(context, contactQuery)?.phoneNumber.orEmpty()
                    } else ""

                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:${Uri.encode(resolved)}")
                        putExtra("sms_body", body)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionExecutionResult(true, "Opened SMS app with message ready.")
                }

                AssistantActionType.WEB_SEARCH -> {
                    val query = params["query"]?.takeIf { it.isNotBlank() } ?: "latest news"
                    val url = "https://www.google.com/search?q=${Uri.encode(query)}"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionExecutionResult(true, "Opened Google search for \"$query\".")
                }

                AssistantActionType.OPEN_SETTINGS -> {
                    val settingsAction = when (params["target"]?.lowercase()) {
                        "wifi" -> Settings.ACTION_WIFI_SETTINGS
                        "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                        "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
                        else -> Settings.ACTION_SETTINGS
                    }
                    val intent = Intent(settingsAction).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionExecutionResult(true, "Opened system settings.")
                }

                AssistantActionType.DIAL_NUMBER -> {
                    val contactQuery = params["number"].orEmpty().ifBlank { params["contact"].orEmpty() }
                    val explicitDigits = contactQuery.filter { it.isDigit() || it == '+' }
                    val number = if (explicitDigits.isNotBlank()) {
                        explicitDigits
                    } else if (contactQuery.isNotBlank()) {
                        ContactLookupHelper.findPhoneByName(context, contactQuery)?.phoneNumber.orEmpty()
                    } else ""
                    val intent = Intent(Intent.ACTION_DIAL).apply {
                        data = Uri.parse("tel:${Uri.encode(number)}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    ActionExecutionResult(true, "Opened Phone dialer.")
                }

                AssistantActionType.TOGGLE_FLASHLIGHT -> {
                    val requested = when (params["state"]?.lowercase()) {
                        "on", "true", "enable" -> true
                        "off", "false", "disable" -> false
                        else -> !currentTorchEnabled
                    }
                    setFlashlight(context, requested)
                }

                AssistantActionType.ADJUST_VOLUME -> {
                    adjustMediaVolume(context, params["direction"]?.lowercase().orEmpty())
                }

                AssistantActionType.DEVICE_STATUS -> {
                    val t = readDeviceTelemetry(context)
                    val chargeLabel = if (t.isCharging) "charging" else "on battery"
                    ActionExecutionResult(
                        true,
                        "Battery: ${t.batteryPercent}% ($chargeLabel) • Media volume: ${t.mediaVolumePercent}%"
                    )
                }

                AssistantActionType.NONE -> {
                    ActionExecutionResult(false, "No device action specified.")
                }
            }
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                message = "No compatible app found on this device for ${actionType.name.lowercase().replace('_', ' ')}."
            )
        }
    }

    private fun createMusicPlayerIntent(context: Context): Intent {
        val candidates = listOf(
            "com.google.android.apps.youtube.music",
            "com.spotify.music",
            "com.apple.android.music"
        )
        val installedLauncher = candidates.firstNotNullOfOrNull { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)
        }
        if (installedLauncher != null) {
            return installedLauncher
        }

        val musicCategoryIntent = Intent.makeMainSelectorActivity(
            Intent.ACTION_MAIN,
            Intent.CATEGORY_APP_MUSIC
        )
        if (musicCategoryIntent.resolveActivity(context.packageManager) != null) {
            return musicCategoryIntent
        }

        val mediaStoreMusic = Intent(MediaStore.INTENT_ACTION_MUSIC_PLAYER)
        if (mediaStoreMusic.resolveActivity(context.packageManager) != null) {
            return mediaStoreMusic
        }

        return Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com"))
    }

    fun openMusicPlayer(context: Context): ActionExecutionResult {
        return try {
            val intent = createMusicPlayerIntent(context).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ActionExecutionResult(true, "Opened Music player.")
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                message = "Could not open a music app on this device."
            )
        }
    }

    fun setFlashlight(context: Context, enabled: Boolean): ActionExecutionResult {
        return try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return ActionExecutionResult(false, "Camera service unavailable on this device.")
            val flashCameraId = camManager.cameraIdList.firstOrNull { id ->
                camManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (flashCameraId == null) {
                return ActionExecutionResult(false, "No flashlight hardware found on this device.")
            }
            camManager.setTorchMode(flashCameraId, enabled)
            currentTorchEnabled = enabled
            ActionExecutionResult(
                success = true,
                message = if (enabled) "Flashlight turned ON." else "Flashlight turned OFF."
            )
        } catch (e: Exception) {
            ActionExecutionResult(
                success = false,
                message = "Could not toggle flashlight: ${e.localizedMessage ?: "hardware busy"}"
            )
        }
    }

    fun adjustMediaVolume(context: Context, direction: String): ActionExecutionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return ActionExecutionResult(false, "Audio service unavailable.")
            when {
                direction.contains("mute") || direction.contains("silent") -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_TOGGLE_MUTE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    ActionExecutionResult(true, "Toggled media mute.")
                }
                direction.contains("down") || direction.contains("lower") || direction.contains("কমাও") -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_LOWER,
                        AudioManager.FLAG_SHOW_UI
                    )
                    ActionExecutionResult(true, "Lowered media volume.")
                }
                else -> {
                    audioManager.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC,
                        AudioManager.ADJUST_RAISE,
                        AudioManager.FLAG_SHOW_UI
                    )
                    ActionExecutionResult(true, "Increased media volume.")
                }
            }
        } catch (e: Exception) {
            ActionExecutionResult(false, "Could not adjust volume on this device.")
        }
    }

    fun readDeviceTelemetry(context: Context): DeviceTelemetry {
        val batteryIntent = runCatching {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val rawPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }
            ?: run {
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
                if (level >= 0 && scale > 0) (level * 100) / scale else 85
            }

        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val mediaPct = if (audioManager != null) {
            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            (cur * 100) / max
        } else 50

        return DeviceTelemetry(
            batteryPercent = rawPct.coerceIn(0, 100),
            isCharging = isCharging,
            mediaVolumePercent = mediaPct.coerceIn(0, 100)
        )
    }
}
