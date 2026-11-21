package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.preferences.AppLanguageMode
import com.example.ui.theme.CoralBargeIn
import com.example.ui.viewmodel.AssistantViewModel
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
            ) {
                Text(
                    text = "Voice & Privacy Settings",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Configure bilingual speech, hands-free wake mode, offline fallback & accessibility",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Language & Code-Switching
        item {
            SettingsCard(
                title = "Language & Script",
                icon = Icons.Rounded.Language
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppLanguageMode.entries.forEach { mode ->
                        FilterChip(
                            selected = prefs.languageMode == mode,
                            onClick = { viewModel.setLanguageMode(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        // Speech & Prosody
        item {
            SettingsCard(
                title = "Voice Output & Speed",
                icon = Icons.AutoMirrored.Rounded.VolumeUp
            ) {
                ToggleRow(
                    title = "Auto-Speak Replies",
                    subtitle = "Speak Gemini responses aloud with low-latency sentence streaming",
                    checked = prefs.autoSpeakReplies,
                    onCheckedChange = viewModel::setAutoSpeak
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Speech Speed: ${String.format(Locale.US, "%.2fx", prefs.ttsSpeed)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = prefs.ttsSpeed,
                    onValueChange = viewModel::setTtsSpeed,
                    valueRange = 0.6f..1.6f
                )
                Text(
                    text = "Voice Pitch: ${String.format(Locale.US, "%.2fx", prefs.ttsPitch)}",
                    style = MaterialTheme.typography.labelMedium
                )
                Slider(
                    value = prefs.ttsPitch,
                    onValueChange = viewModel::setTtsPitch,
                    valueRange = 0.7f..1.4f
                )
                OutlinedButton(
                    onClick = {
                        val sample = if (prefs.languageMode == AppLanguageMode.ENGLISH) {
                            "Hello! I am Kotha, your multilingual voice assistant."
                        } else {
                            "নমস্কার এবং হ্যালো! আমি কথা, আপনার বাংলা ও ইংরেজি ভয়েস অ্যাসিস্ট্যান্ট।"
                        }
                        viewModel.replayMessageVoice(sample, "Happy")
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("test_voice_button")
                ) {
                    Text("Test Voice Output")
                }
            }
        }

        // Hands-free, Wake Word & Device Actions
        item {
            SettingsCard(
                title = "Hands-Free & Device Control",
                icon = Icons.Rounded.GraphicEq
            ) {
                ToggleRow(
                    title = "Auto-Launch Voice Actions",
                    subtitle = "Immediately open WhatsApp, apps, alarms, or maps when requested by voice",
                    checked = prefs.autoExecuteActions,
                    onCheckedChange = viewModel::setAutoExecuteActions
                )
                ToggleRow(
                    title = "Continuous Auto-Listen",
                    subtitle = "Automatically resume listening after Kotha finishes speaking",
                    checked = prefs.handsFreeContinuous,
                    onCheckedChange = viewModel::setHandsFreeContinuous
                )
                ToggleRow(
                    title = "Wake Phrase Filter",
                    subtitle = "Require \"Hey Kotha\" or \"হ্যালো কথা\" at the start of voice commands",
                    checked = prefs.wakePhraseMode,
                    onCheckedChange = viewModel::setWakePhraseMode
                )
            }
        }

        // Display Edge Glow
        item {
            SettingsCard(
                title = "Display Edge Glow",
                icon = Icons.Rounded.AutoAwesome
            ) {
                ToggleRow(
                    title = "Edge Glow on Display",
                    subtitle = "Illuminate the entire screen border dynamically during Voice Listening, Speaking, Thinking & Live Gemini Talk",
                    checked = prefs.edgeGlowEnabled,
                    onCheckedChange = viewModel::setEdgeGlowEnabled
                )
                ToggleRow(
                    title = "Always-On Ambient Glow",
                    subtitle = "Keep a gentle neon aurora glowing along the display edges even when idle",
                    checked = prefs.edgeGlowAlwaysOn,
                    onCheckedChange = viewModel::setEdgeGlowAlwaysOn
                )
            }
        }

        // Accessibility
        item {
            SettingsCard(
                title = "Accessibility (সহজ ব্যবহার)",
                icon = Icons.Rounded.AccessibilityNew
            ) {
                ToggleRow(
                    title = "Large Text & High-Clarity Mode",
                    subtitle = "Enlarges text by 22%, increases orb size, and simplifies spoken responses",
                    checked = prefs.accessibilityLargeMode,
                    onCheckedChange = viewModel::setAccessibilityLargeMode
                )
            }
        }

        // Privacy & Offline Mode
        item {
            SettingsCard(
                title = "Privacy & Offline Mode",
                icon = Icons.Rounded.Lock
            ) {
                ToggleRow(
                    title = "Offline On-Device Mode",
                    subtitle = "Keep all processing on-device (supports alarms, timers, settings, time & local memories)",
                    checked = prefs.offlineModeOnly,
                    onCheckedChange = viewModel::setOfflineModeOnly,
                    icon = Icons.Rounded.CloudOff
                )
                ToggleRow(
                    title = "Private / Incognito Session",
                    subtitle = "Do not save conversation transcripts or auto-learn new memories",
                    checked = prefs.incognitoMode,
                    onCheckedChange = viewModel::setIncognitoMode
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showClearConfirm = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = CoralBargeIn
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("clear_data_button")
                ) {
                    Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Clear All Local History & Memories")
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Local Data?") },
            text = { Text("This deletes all saved conversation threads and long-term memories from this device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllHistoryAndMemories()
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralBargeIn)
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 760.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.minimumInteractiveComponentSize()
        )
    }
}
