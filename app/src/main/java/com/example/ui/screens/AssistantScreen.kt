package com.example.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AddComment
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.LiveTalkState
import com.example.data.local.ChatMessageEntity
import com.example.ui.components.AcousticOrb
import com.example.ui.components.ChatMessageBubble
import com.example.ui.components.CompanionCharacterStage
import com.example.ui.components.VoiceWaveformVisualizer
import com.example.ui.components.VoiceWelcomeHero
import com.example.ui.components.fontForScript
import com.example.ui.theme.CoralBargeIn
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldAction
import com.example.ui.theme.RoseBorder
import com.example.ui.theme.RoseNeon
import com.example.ui.theme.RoseSoftGlow
import com.example.ui.theme.StageObsidian
import com.example.ui.theme.StageSurface
import com.example.ui.viewmodel.AssistantUiState
import com.example.ui.viewmodel.AssistantViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isOnline by viewModel.isNetworkOnline.collectAsStateWithLifecycle()
    val pendingAttachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBanner.collectAsStateWithLifecycle()
    val needsContactsPermission by viewModel.needsContactsPermission.collectAsStateWithLifecycle()
    val lastEmotion by viewModel.lastEmotion.collectAsStateWithLifecycle()
    val lastLanguage by viewModel.lastLanguage.collectAsStateWithLifecycle()
    val isFlashlightOn by viewModel.isFlashlightOn.collectAsStateWithLifecycle()
    val isTranslationMode by viewModel.isTranslationMode.collectAsStateWithLifecycle()
    val liveTalkState by viewModel.liveTalkState.collectAsStateWithLifecycle()

    val rmsLevel by viewModel.voiceManager.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.voiceManager.partialTranscript.collectAsStateWithLifecycle()
    val speechStatusNote by viewModel.voiceManager.speechStatusNote.collectAsStateWithLifecycle()
    val isRecognizerAvailable by viewModel.voiceManager.isRecognizerAvailable.collectAsStateWithLifecycle()
    val isListeningActive by viewModel.voiceManager.isListening.collectAsStateWithLifecycle()
    val isSpeakingActive by viewModel.voiceManager.isSpeaking.collectAsStateWithLifecycle()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var showCharacterStage by remember { mutableStateOf(true) }
    var showKeyboardInput by remember { mutableStateOf(false) }
    var textDraft by remember { mutableStateOf("") }
    var showProfileSheet by remember { mutableStateOf(false) }
    var pendingLiveTalkAfterMicPermission by remember { mutableStateOf(false) }

    val systemSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (spoken.isNotEmpty()) {
                viewModel.onSystemSpeechTranscript(spoken)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            if (pendingLiveTalkAfterMicPermission) {
                pendingLiveTalkAfterMicPermission = false
                viewModel.startLiveGeminiTalk()
            } else if (isRecognizerAvailable) {
                viewModel.startVoiceListening()
            } else {
                runCatching {
                    systemSpeechLauncher.launch(
                        viewModel.voiceManager.createRecognizerIntent(prefs.languageMode)
                    )
                }
            }
        } else {
            pendingLiveTalkAfterMicPermission = false
        }
    }

    fun toggleLiveGeminiTalk() {
        if (liveTalkState is LiveTalkState.Active || liveTalkState is LiveTalkState.Connecting) {
            viewModel.stopLiveGeminiTalk()
        } else if (hasMicPermission) {
            viewModel.startLiveGeminiTalk()
        } else {
            pendingLiveTalkAfterMicPermission = true
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onContactsPermissionGranted()
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                viewModel.sendWhatsAppToPickedContactUri(uri)
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.attachImageFromUri(it) }
    }

    val activeProfile = profiles.firstOrNull { it.id == prefs.activeProfileId }
        ?: profiles.firstOrNull()

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, uiState) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val isEffectiveOffline = prefs.offlineModeOnly || !isOnline
    val screenBg = if (showCharacterStage) StageObsidian else MaterialTheme.colorScheme.background

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(screenBg),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Status & Control Strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 760.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Active Speaker Pill
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showProfileSheet = true }
                    .minimumInteractiveComponentSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = "Switch speaker profile",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = activeProfile?.name ?: "Speaker",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Connection & Privacy Pill
                Surface(
                    color = when {
                        prefs.incognitoMode -> MaterialTheme.colorScheme.tertiaryContainer
                        isEffectiveOffline -> MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                prefs.incognitoMode -> Icons.Rounded.Lock
                                isEffectiveOffline -> Icons.Rounded.CloudOff
                                else -> Icons.Rounded.CloudDone
                            },
                            contentDescription = null,
                            tint = when {
                                prefs.incognitoMode -> MaterialTheme.colorScheme.tertiary
                                isEffectiveOffline -> MaterialTheme.colorScheme.secondary
                                else -> EmeraldAction
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = when {
                                prefs.incognitoMode -> "Private"
                                isEffectiveOffline -> "Offline"
                                else -> "Cloud AI"
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                IconButton(
                    onClick = { showCharacterStage = !showCharacterStage },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_character_stage")
                ) {
                    Icon(
                        imageVector = if (showCharacterStage) {
                            Icons.AutoMirrored.Rounded.Chat
                        } else {
                            Icons.Rounded.AutoAwesome
                        },
                        contentDescription = stringResource(R.string.toggle_stage_cd),
                        tint = if (showCharacterStage) {
                            RoseSoftGlow
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                }

                IconButton(
                    onClick = { viewModel.setAccessibilityLargeMode(!prefs.accessibilityLargeMode) },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessibilityNew,
                        contentDescription = "Toggle high-clarity accessibility mode",
                        tint = when {
                            prefs.accessibilityLargeMode -> MaterialTheme.colorScheme.primary
                            showCharacterStage -> RoseSoftGlow.copy(alpha = 0.8f)
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                IconButton(
                    onClick = { viewModel.startNewSession() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddComment,
                        contentDescription = stringResource(R.string.new_chat_cd),
                        tint = if (showCharacterStage) RoseSoftGlow else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Info or Speech Note Banner
        val combinedBanner = statusBanner ?: speechStatusNote
        AnimatedVisibility(visible = combinedBanner != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = combinedBanner.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (needsContactsPermission) {
                        TextButton(
                            onClick = {
                                val pickIntent = Intent(
                                    Intent.ACTION_PICK,
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                                )
                                contactPickerLauncher.launch(pickIntent)
                            }
                        ) {
                            Text("Pick Contact")
                        }
                        TextButton(
                            onClick = {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        ) {
                            Text("Allow")
                        }
                    }
                    IconButton(
                        onClick = { viewModel.dismissBanner() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Dismiss notification",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Main Stage: Companion Character Stage OR Full Conversation Transcript
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Crossfade(
                targetState = showCharacterStage,
                label = "character_transcript_crossfade",
                modifier = Modifier.fillMaxSize()
            ) { stageActive ->
                if (stageActive) {
                    CompanionCharacterStage(
                        uiState = uiState,
                        isListening = isListeningActive,
                        isSpeaking = isSpeakingActive,
                        rmsLevel = rmsLevel,
                        partialTranscript = partialTranscript,
                        latestAssistantMessage = messages.lastOrNull { !it.isUser },
                        currentEmotion = lastEmotion,
                        currentLanguage = lastLanguage,
                        isFlashlightOn = isFlashlightOn,
                        isTranslationMode = isTranslationMode,
                        liveTalkState = liveTalkState,
                        onToggleLiveTalk = { toggleLiveGeminiTalk() },
                        onDismissLiveError = { viewModel.clearLiveTalkError() },
                        onQuickWeather = { viewModel.sendMessage("আজকের আবহাওয়া সার্চ করো") },
                        onQuickMusic = { viewModel.openMusicQuickAction() },
                        onQuickTime = { currentClock -> viewModel.speakCurrentTime(currentClock) },
                        onDailyBriefing = { viewModel.triggerDailyBriefing() },
                        onToggleTranslation = { viewModel.toggleTranslationMode() },
                        onToggleFlashlight = { viewModel.toggleFlashlightFromUi() },
                        onReplayLatest = { msg -> viewModel.replayMessageVoice(msg.content, msg.emotionTag) },
                        onCopyLatest = { text -> viewModel.copyTextToClipboard(text) },
                        onExecuteLatestAction = { msg -> viewModel.executeActionCard(msg) },
                        onOpenTranscript = { showCharacterStage = false }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 760.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        item(key = "live_talk_transcript_strip") {
                            val isLiveOn = liveTalkState is LiveTalkState.Active || liveTalkState is LiveTalkState.Connecting
                            Surface(
                                color = if (isLiveOn) CyanPulse.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (isLiveOn) CyanPulse.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = when (val live = liveTalkState) {
                                                is LiveTalkState.Active -> "Live Gemini Talk • ${String.format(Locale.US, "%02d:%02d", live.elapsedSeconds / 60, live.elapsedSeconds % 60)}"
                                                is LiveTalkState.Connecting -> "Connecting Gemini Live…"
                                                is LiveTalkState.Error -> "Live Gemini Talk Error"
                                                else -> "Live Gemini Talk (Real-Time Audio)"
                                            },
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = when (val live = liveTalkState) {
                                                is LiveTalkState.Error -> live.message
                                                is LiveTalkState.Active -> live.modelTranscript.ifBlank { live.userTranscript.ifBlank { "Bidirectional audio & barge-in active" } }
                                                else -> "Talk continuously with Gemini Live & interrupt naturally anytime"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (liveTalkState is LiveTalkState.Error) CoralBargeIn else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Button(
                                        onClick = { toggleLiveGeminiTalk() },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("transcript_live_talk_button")
                                    ) {
                                        Text(if (isLiveOn) "End" else "Start Live")
                                    }
                                }
                            }
                        }

                        if (messages.isEmpty() && uiState !is AssistantUiState.Streaming) {
                            item {
                                VoiceWelcomeHero(
                                    activeProfileName = activeProfile?.name ?: "Guest",
                                    onSelectPrompt = { prompt -> viewModel.sendMessage(prompt) }
                                )
                            }
                        }

                        items(
                            items = messages,
                            key = { "${it.id}_${it.timestamp}" }
                        ) { msg ->
                            ChatMessageBubble(
                                message = msg,
                                onReplayVoice = { text, emotion ->
                                    viewModel.replayMessageVoice(text, emotion)
                                },
                                onSaveMemory = { text ->
                                    viewModel.saveMessageAsMemory(text)
                                },
                                onExecuteAction = { message ->
                                    viewModel.executeActionCard(message)
                                },
                                onCopyText = { text ->
                                    viewModel.copyTextToClipboard(text)
                                }
                            )
                        }

                        if (uiState is AssistantUiState.Streaming) {
                            val streamingText = (uiState as AssistantUiState.Streaming).partialReply
                            item(key = "streaming_live") {
                                ChatMessageBubble(
                                    message = ChatMessageEntity(
                                        threadId = 0,
                                        isUser = false,
                                        content = streamingText
                                    ),
                                    onReplayVoice = { _, _ -> },
                                    onSaveMemory = {},
                                    onExecuteAction = {}
                                )
                            }
                        }

                        if (uiState is AssistantUiState.Error) {
                            val errorMsg = (uiState as AssistantUiState.Error).message
                            item(key = "error_card") {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ErrorOutline,
                                            contentDescription = "Error",
                                            tint = CoralBargeIn
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            text = errorMsg,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { viewModel.clearErrorState() }) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Live Acoustic Waveform + Partial Voice Caption / TTS Indicator
        val isLiveSessionActive = liveTalkState is LiveTalkState.Active || liveTalkState is LiveTalkState.Connecting
        val showVoiceActivityBar = isLiveSessionActive ||
            isListeningActive ||
            isSpeakingActive ||
            uiState is AssistantUiState.Listening ||
            uiState is AssistantUiState.Thinking ||
            uiState is AssistantUiState.Streaming ||
            uiState is AssistantUiState.Speaking

        AnimatedVisibility(visible = showVoiceActivityBar) {
            Surface(
                color = if (showCharacterStage) {
                    StageSurface.copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 680.dp)
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .then(
                        if (showCharacterStage) {
                            Modifier.border(
                                1.dp,
                                if (isLiveSessionActive) CyanPulse.copy(alpha = 0.65f) else RoseBorder.copy(alpha = 0.45f),
                                RoundedCornerShape(18.dp)
                            )
                        } else {
                            Modifier
                        }
                    )
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    VoiceWaveformVisualizer(
                        isListening = liveTalkState is LiveTalkState.Active || isListeningActive || uiState is AssistantUiState.Listening,
                        isSpeaking = isSpeakingActive || uiState is AssistantUiState.Speaking,
                        isThinking = liveTalkState is LiveTalkState.Connecting || uiState is AssistantUiState.Thinking || uiState is AssistantUiState.Streaming,
                        rmsLevel = if (liveTalkState is LiveTalkState.Active) 0.65f else rmsLevel
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = when {
                            liveTalkState is LiveTalkState.Connecting ->
                                "Connecting to Gemini Live bidirectional audio…"
                            liveTalkState is LiveTalkState.Active -> {
                                val secs = (liveTalkState as LiveTalkState.Active).elapsedSeconds
                                "Live Gemini Talk • ${String.format(Locale.US, "%02d:%02d", secs / 60, secs % 60)} • Tap Stop to end"
                            }
                            (isListeningActive || uiState is AssistantUiState.Listening) && partialTranscript.isNotBlank() ->
                                "“$partialTranscript”"
                            isListeningActive || uiState is AssistantUiState.Listening ->
                                if (prefs.wakePhraseMode) {
                                    "Listening for “Hey Kotha” / “হ্যালো কথা”…"
                                } else {
                                    "Listening… Speak in বাংলা, English, or Banglish"
                                }
                            isSpeakingActive || uiState is AssistantUiState.Speaking ->
                                "Speaking… Tap orb anytime to interrupt"
                            uiState is AssistantUiState.Thinking || uiState is AssistantUiState.Streaming ->
                                "Thinking…"
                            else -> ""
                        },
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = fontForScript(partialTranscript)
                        ),
                        color = when {
                            isLiveSessionActive -> CyanPulse
                            showCharacterStage -> RoseSoftGlow
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        }

        // Attached Screenshot / Image Preview Strip
        AnimatedVisibility(visible = pendingAttachment != null) {
            pendingAttachment?.let { attachment ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .widthIn(max = 680.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Image(
                            bitmap = attachment.bitmap.asImageBitmap(),
                            contentDescription = "Attached screen or photo preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Screen / photo ready — ask Kotha to explain or help",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearPendingAttachment() }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove attached image")
                        }
                    }
                }
            }
        }

        // Optional Keyboard Input Bar
        AnimatedVisibility(visible = showKeyboardInput) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    placeholder = { Text(stringResource(R.string.input_hint)) },
                    singleLine = true,
                    colors = if (showCharacterStage) {
                        OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedPlaceholderColor = RoseSoftGlow.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = RoseSoftGlow.copy(alpha = 0.6f),
                            focusedBorderColor = RoseNeon,
                            unfocusedBorderColor = RoseBorder.copy(alpha = 0.55f),
                            focusedContainerColor = StageSurface,
                            unfocusedContainerColor = StageSurface,
                            cursorColor = RoseNeon
                        )
                    } else {
                        OutlinedTextFieldDefaults.colors()
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textDraft.isNotBlank() || pendingAttachment != null) {
                                viewModel.sendMessage(textDraft)
                                textDraft = ""
                            }
                        }
                    ),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_text_input")
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (textDraft.isNotBlank() || pendingAttachment != null) {
                            viewModel.sendMessage(textDraft)
                            textDraft = ""
                        }
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(CircleShape)
                        .background(if (showCharacterStage) RoseNeon else MaterialTheme.colorScheme.primary)
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.send_cd),
                        tint = if (showCharacterStage) Color(0xFF1A0614) else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Bottom Acoustic Voice Dock
        val dockBg = if (showCharacterStage) StageSurface.copy(alpha = 0.94f) else MaterialTheme.colorScheme.surface
        val dockBorder = if (showCharacterStage) RoseBorder.copy(alpha = 0.42f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        val dockIconDefault = if (showCharacterStage) RoseSoftGlow.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

        Surface(
            color = dockBg,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = dockBorder,
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 760.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Screen / Photo Help (Zero-Permission Photo Picker)
                IconButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("attach_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ImageSearch,
                        contentDescription = stringResource(R.string.attach_screen_cd),
                        tint = if (pendingAttachment != null) {
                            if (showCharacterStage) RoseNeon else MaterialTheme.colorScheme.primary
                        } else {
                            dockIconDefault
                        }
                    )
                }

                // Hands-Free Auto-Listen Toggle
                IconButton(
                    onClick = { viewModel.setHandsFreeContinuous(!prefs.handsFreeContinuous) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("hands_free_toggle")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AllInclusive,
                        contentDescription = "Toggle hands-free continuous conversation",
                        tint = if (prefs.handsFreeContinuous) {
                            EmeraldAction
                        } else {
                            dockIconDefault
                        }
                    )
                }

                // Primary Acoustic Orb (Listen, Finish Speaking, Live Stop, or Barge-in Stop)
                val effectiveOrbState = when {
                    liveTalkState is LiveTalkState.Active -> AssistantUiState.Speaking
                    liveTalkState is LiveTalkState.Connecting -> AssistantUiState.Thinking
                    else -> uiState
                }
                AcousticOrb(
                    uiState = effectiveOrbState,
                    rmsLevel = if (liveTalkState is LiveTalkState.Active) 0.55f else rmsLevel,
                    isOffline = isEffectiveOffline,
                    accessibilityLarge = prefs.accessibilityLargeMode,
                    stageStyle = showCharacterStage,
                    onTapOrb = {
                        when {
                            liveTalkState is LiveTalkState.Active ||
                                liveTalkState is LiveTalkState.Connecting -> {
                                viewModel.stopLiveGeminiTalk()
                            }
                            uiState is AssistantUiState.Listening -> {
                                viewModel.finishVoiceListening()
                            }
                            uiState is AssistantUiState.Thinking ||
                                uiState is AssistantUiState.Streaming ||
                                uiState is AssistantUiState.Speaking -> {
                                viewModel.interruptAndStop()
                            }
                            hasMicPermission -> {
                                if (isRecognizerAvailable) {
                                    viewModel.startVoiceListening()
                                } else {
                                    runCatching {
                                        systemSpeechLauncher.launch(
                                            viewModel.voiceManager.createRecognizerIntent(prefs.languageMode)
                                        )
                                    }.onFailure {
                                        showKeyboardInput = true
                                    }
                                }
                            }
                            else -> {
                                pendingLiveTalkAfterMicPermission = false
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                )

                // Keyboard / Text Toggle
                IconButton(
                    onClick = { showKeyboardInput = !showKeyboardInput },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_keyboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Keyboard,
                        contentDescription = "Toggle text keyboard input",
                        tint = if (showKeyboardInput) {
                            if (showCharacterStage) RoseNeon else MaterialTheme.colorScheme.primary
                        } else {
                            dockIconDefault
                        }
                    )
                }

                // Dedicated Barge-In / Live Talk Stop Button
                if (liveTalkState is LiveTalkState.Active ||
                    liveTalkState is LiveTalkState.Connecting ||
                    uiState is AssistantUiState.Speaking ||
                    uiState is AssistantUiState.Thinking ||
                    uiState is AssistantUiState.Streaming ||
                    uiState is AssistantUiState.Listening
                ) {
                    IconButton(
                        onClick = { viewModel.interruptAndStop() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("barge_in_stop_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.StopCircle,
                            contentDescription = "Stop listening, speaking, or Live Talk immediately",
                            tint = CoralBargeIn
                        )
                    }
                } else {
                    Box(modifier = Modifier.size(48.dp))
                }
            }
        }
    }

    if (showProfileSheet) {
        ModalBottomSheet(onDismissRequest = { showProfileSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Active Speaker Profile",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Kotha adapts memory, tone, and pitch to the active speaker.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                profiles.forEach { profile ->
                    val selected = profile.id == prefs.activeProfileId
                    Surface(
                        color = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.selectProfile(profile.id)
                                showProfileSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (profile.personaNote.isNotBlank()) {
                                    Text(
                                        text = profile.personaNote,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (selected) {
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
