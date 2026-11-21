package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ai.AssistantActionType
import com.example.data.local.ChatMessageEntity
import com.example.ui.theme.EmeraldAction
import com.example.ui.theme.NotoBengaliFontFamily
import com.example.ui.theme.PlusJakartaFontFamily

fun fontForScript(text: String): FontFamily {
    return if (text.any { it in '\u0980'..'\u09FF' }) {
        NotoBengaliFontFamily
    } else {
        PlusJakartaFontFamily
    }
}

@Composable
fun ChatMessageBubble(
    message: ChatMessageEntity,
    onReplayVoice: (String, String) -> Unit,
    onSaveMemory: (String) -> Unit,
    onExecuteAction: (ChatMessageEntity) -> Unit,
    modifier: Modifier = Modifier,
    onCopyText: (String) -> Unit = {}
) {
    val isUser = message.isUser
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp,
        topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 20.dp
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(if (isUser) 0.84f else 0.92f)
                .clip(bubbleShape)
                .background(
                    if (isUser) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    },
                    shape = bubbleShape
                )
                .padding(14.dp)
        ) {
            if (isUser && message.hasAttachment) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Image,
                        contentDescription = "Attached screenshot or image",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Screen / Image Attached",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (!isUser && (message.emotionTag.isNotBlank() || message.isOfflineReply)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    if (message.isOfflineReply) {
                        MetadataBadge(
                            text = "Offline On-Device",
                            icon = Icons.Rounded.CloudOff,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    } else if (message.emotionTag.isNotBlank() && message.emotionTag != "Neutral") {
                        MetadataBadge(
                            text = "${message.emotionTag} • ${message.languageTag.ifBlank { "Auto" }}",
                            icon = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    } else if (message.languageTag.isNotBlank() && message.languageTag != "Auto") {
                        MetadataBadge(
                            text = message.languageTag,
                            icon = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = fontForScript(message.content)
                ),
                color = if (isUser) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            // Real Android Intent Action Card
            val actionType = runCatching {
                AssistantActionType.valueOf(message.actionType)
            }.getOrDefault(AssistantActionType.NONE)

            if (!isUser && actionType != AssistantActionType.NONE) {
                Spacer(Modifier.height(10.dp))
                DeviceActionCard(
                    actionType = actionType,
                    title = message.actionTitle.ifBlank { actionType.name.replace('_', ' ') },
                    executed = message.actionExecuted,
                    onRun = { onExecuteAction(message) }
                )
            }

            if (!isUser) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onCopyText(message.content) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy response text",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    IconButton(
                        onClick = { onReplayVoice(message.content, message.emotionTag) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "Speak this reply aloud",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onSaveMemory(message.content) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BookmarkAdd,
                            contentDescription = "Save this reply to long-term memory",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataBadge(
    text: String,
    icon: ImageVector?,
    tint: Color
) {
    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}

@Composable
fun DeviceActionCard(
    actionType: AssistantActionType,
    title: String,
    executed: Boolean,
    onRun: () -> Unit
) {
    val icon = when (actionType) {
        AssistantActionType.OPEN_WHATSAPP, AssistantActionType.SEND_WHATSAPP -> Icons.AutoMirrored.Rounded.Chat
        AssistantActionType.OPEN_APP -> Icons.Rounded.Apps
        AssistantActionType.OPEN_MAPS -> Icons.Rounded.Map
        AssistantActionType.SEND_EMAIL -> Icons.Rounded.Email
        AssistantActionType.SET_ALARM -> Icons.Rounded.Alarm
        AssistantActionType.SET_TIMER -> Icons.Rounded.Timer
        AssistantActionType.ADD_CALENDAR_EVENT -> Icons.Rounded.CalendarMonth
        AssistantActionType.SEND_SMS -> Icons.Rounded.Sms
        AssistantActionType.WEB_SEARCH -> Icons.Rounded.Search
        AssistantActionType.OPEN_SETTINGS -> Icons.Rounded.Settings
        AssistantActionType.DIAL_NUMBER -> Icons.Rounded.Phone
        AssistantActionType.TOGGLE_FLASHLIGHT -> Icons.Rounded.FlashlightOn
        AssistantActionType.ADJUST_VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
        AssistantActionType.DEVICE_STATUS -> Icons.Rounded.BatteryChargingFull
        AssistantActionType.NONE -> Icons.AutoMirrored.Rounded.OpenInNew
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = EmeraldAction.copy(alpha = 0.45f),
                shape = RoundedCornerShape(14.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = EmeraldAction,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (executed) "Action opened" else "Ready to run on device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onRun,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (executed) MaterialTheme.colorScheme.surfaceVariant else EmeraldAction,
                    contentColor = if (executed) MaterialTheme.colorScheme.onSurface else Color(0xFF032116)
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("execute_action_button")
            ) {
                Icon(
                    imageVector = if (executed) Icons.Rounded.CheckCircle else Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = "Launch device action",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (executed) "Again" else "Run")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceWelcomeHero(
    activeProfileName: String,
    onSelectPrompt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val starterPrompts = listOf(
        "হোয়াটসঅ্যাপ খোলো",
        "হোয়াটসঅ্যাপে করিমকে মেসেজ করো আমি আসছি",
        "সকাল ৭টায় অ্যালার্ম সেট করো",
        "Set a 5 minute tea timer"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_voice_backdrop),
                contentDescription = "Kotha AI acoustic wave backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF090E1A).copy(alpha = 0.25f),
                                Color(0xFF090E1A).copy(alpha = 0.88f)
                            )
                        )
                    )
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_kotha_character),
                    contentDescription = "Kotha companion avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .border(1.5.dp, com.example.ui.theme.RoseBorder.copy(alpha = 0.85f), androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "কথা AI • Kotha",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Speaking as $activeProfileName • বাংলা, English & Banglish voice companion",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = NotoBengaliFontFamily
                        ),
                        color = Color.White.copy(alpha = 0.86f)
                    )
                }
            }
        }

        Text(
            text = "Tap the orb to speak, or try a quick command:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            starterPrompts.forEach { prompt ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            RoundedCornerShape(14.dp)
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectPrompt(prompt) }
                ) {
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = fontForScript(prompt)
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
