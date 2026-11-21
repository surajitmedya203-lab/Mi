package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.AssistantActionType
import com.example.ai.LiveTalkState
import com.example.data.local.ChatMessageEntity
import com.example.ui.theme.CoralBargeIn
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldAction
import com.example.ui.theme.LavenderMemory
import com.example.ui.theme.RoseBorder
import com.example.ui.theme.RoseNeon
import com.example.ui.theme.RoseSoftGlow
import com.example.ui.theme.SpaceGroteskFontFamily
import com.example.ui.theme.StageObsidian
import com.example.ui.theme.StageSurface
import com.example.ui.viewmodel.AssistantUiState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private data class StageSparkle(
    val xFraction: Float,
    val yFraction: Float,
    val sizeDp: Float,
    val isCross: Boolean,
    val phaseOffset: Float,
    val driftSpeed: Float
)

@Composable
fun CompanionCharacterStage(
    uiState: AssistantUiState,
    isListening: Boolean,
    isSpeaking: Boolean,
    rmsLevel: Float,
    partialTranscript: String,
    latestAssistantMessage: ChatMessageEntity?,
    currentEmotion: String,
    currentLanguage: String,
    isFlashlightOn: Boolean,
    isTranslationMode: Boolean,
    liveTalkState: LiveTalkState = LiveTalkState.Idle,
    onToggleLiveTalk: () -> Unit = {},
    onDismissLiveError: () -> Unit = {},
    onQuickWeather: () -> Unit,
    onQuickMusic: () -> Unit,
    onQuickTime: (String) -> Unit,
    onDailyBriefing: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleFlashlight: () -> Unit,
    onReplayLatest: (ChatMessageEntity) -> Unit,
    onCopyLatest: (String) -> Unit,
    onExecuteLatestAction: (ChatMessageEntity) -> Unit,
    onOpenTranscript: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTimeText by remember { mutableStateOf(formatCurrentClock()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTimeText = formatCurrentClock()
            delay(15_000L)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "companion_stage_anim")
    val driftPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sparkle_drift"
    )

    val haloBreath by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_breath"
    )

    val pulseRing by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voice_halo_ring"
    )

    val smoothRms by animateFloatAsState(
        targetValue = rmsLevel.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 340f, dampingRatio = 0.72f),
        label = "stage_rms"
    )

    val isLiveConnecting = liveTalkState is LiveTalkState.Connecting
    val isLiveActive = liveTalkState is LiveTalkState.Active
    val isLiveSession = isLiveConnecting || isLiveActive
    val isThinking = uiState is AssistantUiState.Thinking || uiState is AssistantUiState.Streaming
    val isVoiceActive = isLiveSession || isListening || isSpeaking || isThinking ||
        uiState is AssistantUiState.Listening || uiState is AssistantUiState.Speaking

    val emotionAura = when (currentEmotion.lowercase()) {
        "happy", "warm" -> Color(0xFFFF82BA)
        "curious" -> CyanPulse
        "urgent", "frustrated" -> CoralBargeIn
        "empathetic" -> LavenderMemory
        else -> RoseNeon
    }

    val auraColor by animateColorAsState(
        targetValue = when {
            isLiveSession -> CyanPulse
            isTranslationMode && isVoiceActive -> CyanPulse
            isListening || uiState is AssistantUiState.Listening -> RoseNeon
            isSpeaking || uiState is AssistantUiState.Speaking -> emotionAura
            isThinking -> CyanPulse
            else -> emotionAura
        },
        animationSpec = tween(320),
        label = "aura_color"
    )

    val characterScale by animateFloatAsState(
        targetValue = when {
            isListening || uiState is AssistantUiState.Listening -> 1.015f + smoothRms * 0.03f
            isSpeaking || uiState is AssistantUiState.Speaking -> 1.022f
            else -> 1f
        },
        animationSpec = spring(stiffness = 240f, dampingRatio = 0.76f),
        label = "character_scale"
    )

    val sparkles = remember {
        listOf(
            StageSparkle(0.14f, 0.24f, 6f, isCross = true, phaseOffset = 0.2f, driftSpeed = 1f),
            StageSparkle(0.84f, 0.16f, 5f, isCross = true, phaseOffset = 1.1f, driftSpeed = 1.2f),
            StageSparkle(0.35f, 0.08f, 6.5f, isCross = true, phaseOffset = 2.0f, driftSpeed = 0.9f),
            StageSparkle(0.76f, 0.45f, 4.5f, isCross = false, phaseOffset = 2.8f, driftSpeed = 1.1f),
            StageSparkle(0.12f, 0.48f, 6f, isCross = true, phaseOffset = 3.5f, driftSpeed = 1.0f),
            StageSparkle(0.90f, 0.56f, 6f, isCross = true, phaseOffset = 4.1f, driftSpeed = 1.3f),
            StageSparkle(0.24f, 0.67f, 6.5f, isCross = true, phaseOffset = 0.7f, driftSpeed = 1.15f),
            StageSparkle(0.70f, 0.63f, 5f, isCross = true, phaseOffset = 1.7f, driftSpeed = 0.85f),
            StageSparkle(0.37f, 0.63f, 4f, isCross = false, phaseOffset = 4.9f, driftSpeed = 1.25f),
            StageSparkle(0.81f, 0.27f, 5f, isCross = false, phaseOffset = 5.4f, driftSpeed = 0.95f)
        )
    }

    val stageCd = stringResource(R.string.character_avatar_cd)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(StageObsidian)
            .testTag("companion_character_stage")
    ) {
        val stageHeight = maxHeight
        val isCompactHeight = stageHeight < 480.dp

        // Animated radial pink glow, floating sparkles, and reactive voice halo behind character
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val focalCenter = Offset(w * 0.5f, h * 0.37f)
            val baseHaloRadius = (size.minDimension * 0.52f) *
                (if (isVoiceActive) 1.08f + smoothRms * 0.24f else haloBreath)

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        auraColor.copy(alpha = if (isVoiceActive) 0.42f else 0.26f),
                        RoseSoftGlow.copy(alpha = if (isVoiceActive) 0.18f else 0.10f),
                        Color.Transparent
                    ),
                    center = focalCenter,
                    radius = baseHaloRadius
                ),
                center = focalCenter,
                radius = baseHaloRadius
            )

            // Reactive pulsing voice ring when listening, speaking, or in Live Gemini Talk
            if (isLiveSession || isListening || isSpeaking || uiState is AssistantUiState.Listening || uiState is AssistantUiState.Speaking) {
                listOf(pulseRing, (pulseRing + 0.5f) % 1f).forEach { wave ->
                    val r = size.minDimension * (0.24f + wave * (0.26f + smoothRms * 0.1f))
                    val alpha = ((1f - wave) * 0.45f).coerceIn(0f, 0.45f)
                    drawCircle(
                        color = auraColor.copy(alpha = alpha),
                        radius = r,
                        center = focalCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Floating neon sparkles and plus crosses
            sparkles.forEach { s ->
                val phase = driftPhase * s.driftSpeed + s.phaseOffset
                val cx = w * s.xFraction + cos(phase) * 5.dp.toPx()
                val cy = h * s.yFraction + sin(phase) * 7.dp.toPx()
                val r = s.sizeDp.dp.toPx()
                val alpha = 0.42f + 0.35f * (0.5f + 0.5f * sin(phase * 1.4f))

                if (s.isCross) {
                    val strokeW = 1.5.dp.toPx()
                    drawLine(
                        color = RoseSoftGlow.copy(alpha = alpha),
                        start = Offset(cx - r, cy),
                        end = Offset(cx + r, cy),
                        strokeWidth = strokeW
                    )
                    drawLine(
                        color = RoseSoftGlow.copy(alpha = alpha),
                        start = Offset(cx, cy - r),
                        end = Offset(cx, cy + r),
                        strokeWidth = strokeW
                    )
                } else {
                    rotate(degrees = (phase * 24f), pivot = Offset(cx, cy)) {
                        drawOval(
                            color = RoseNeon.copy(alpha = alpha * 0.9f),
                            topLeft = Offset(cx - r * 0.55f, cy - r * 0.95f),
                            size = androidx.compose.ui.geometry.Size(r * 1.1f, r * 1.9f)
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .align(Alignment.Center)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Neon glowing companion header
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "KOTHA",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = SpaceGroteskFontFamily,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 6.sp,
                        shadow = Shadow(
                            color = RoseNeon.copy(alpha = 0.85f),
                            offset = Offset.Zero,
                            blurRadius = 26f
                        )
                    ),
                    color = RoseSoftGlow
                )
                if (!isCompactHeight || isLiveSession) {
                    val badgeText = when {
                        liveTalkState is LiveTalkState.Connecting -> "CONNECTING GEMINI LIVE…"
                        liveTalkState is LiveTalkState.Active -> "LIVE GEMINI TALK • ${formatCallDuration(liveTalkState.elapsedSeconds)}"
                        isTranslationMode -> "GEMINI INTERPRETER • বাংলা ↔ EN"
                        currentEmotion.isNotBlank() && currentEmotion != "Neutral" ->
                            "${currentEmotion.uppercase()} • ${currentLanguage.ifBlank { "MULTILINGUAL" }.uppercase()}"
                        else -> "কথা AI COMPANION"
                    }
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 1.8.sp
                        ),
                        color = when {
                            isLiveSession -> CyanPulse
                            isTranslationMode -> CyanPulse
                            else -> RoseSoftGlow.copy(alpha = 0.78f)
                        }
                    )
                }
            }

            // Centered 3D companion character blended smoothly into obsidian backdrop
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_kotha_character),
                    contentDescription = stageCd,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = characterScale
                            scaleY = characterScale
                        }
                        .semantics { contentDescription = stageCd }
                )

                // Soft perimeter vignette blending the image cleanly into StageObsidian
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    StageObsidian.copy(alpha = 0.35f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    StageObsidian.copy(alpha = 0.88f)
                                )
                            )
                        )
                )

                // Live caption / latest spoken reply overlay at lower edge of character
                val liveCaption = when {
                    liveTalkState is LiveTalkState.Connecting ->
                        "Connecting to Gemini Live bidirectional audio…"
                    liveTalkState is LiveTalkState.Active -> {
                        val u = liveTalkState.userTranscript.trim()
                        val m = liveTalkState.modelTranscript.trim()
                        when {
                            m.isNotBlank() && u.isNotBlank() -> "You: $u\nKotha: $m"
                            m.isNotBlank() -> "Kotha: $m"
                            u.isNotBlank() -> "You: $u"
                            else -> "Live Gemini Talk active — speak naturally anytime"
                        }
                    }
                    (isListening || uiState is AssistantUiState.Listening) && partialTranscript.isNotBlank() ->
                        "“$partialTranscript”"
                    uiState is AssistantUiState.Streaming -> uiState.partialReply
                    uiState is AssistantUiState.Thinking -> "Thinking…"
                    latestAssistantMessage != null -> latestAssistantMessage.content
                    else -> null
                }

                androidx.compose.animation.AnimatedVisibility(
                    visible = !liveCaption.isNullOrBlank() || liveTalkState is LiveTalkState.Error,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (liveTalkState is LiveTalkState.Error) {
                            Surface(
                                color = StageSurface.copy(alpha = 0.92f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .widthIn(max = 520.dp)
                                    .border(1.dp, CoralBargeIn.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onDismissLiveError() }
                                    .testTag("stage_live_error_pill")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ErrorOutline,
                                        contentDescription = "Live Gemini Talk error",
                                        tint = CoralBargeIn,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = liveTalkState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }
                        }

                        if (!liveCaption.isNullOrBlank()) {
                            Surface(
                                color = StageSurface.copy(alpha = 0.84f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .widthIn(max = 520.dp)
                                    .border(
                                        width = 1.dp,
                                        color = if (isLiveSession) CyanPulse.copy(alpha = 0.72f) else RoseBorder.copy(alpha = 0.55f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { onOpenTranscript() }
                                    .testTag("stage_caption_pill")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = liveCaption,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = fontForScript(liveCaption)
                                        ),
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (!isLiveSession &&
                                        latestAssistantMessage != null &&
                                        uiState !is AssistantUiState.Streaming &&
                                        uiState !is AssistantUiState.Thinking &&
                                        !isListening
                                    ) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy response text",
                                        tint = RoseSoftGlow.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onCopyLatest(latestAssistantMessage.content) }
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "Replay spoken response",
                                        tint = RoseSoftGlow,
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clickable { onReplayLatest(latestAssistantMessage) }
                                    )
                                    }
                                }
                            }
                        }

                        // If latest reply includes a pending device action (WhatsApp, Alarm, etc.), let user execute right here
                        val latestActionType = latestAssistantMessage?.let { msg ->
                            runCatching { AssistantActionType.valueOf(msg.actionType) }
                                .getOrDefault(AssistantActionType.NONE)
                        } ?: AssistantActionType.NONE

                        if (latestAssistantMessage != null && latestActionType != AssistantActionType.NONE) {
                            Surface(
                                color = EmeraldAction.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        EmeraldAction.copy(alpha = 0.7f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onExecuteLatestAction(latestAssistantMessage) }
                                    .testTag("stage_execute_action_chip")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                        contentDescription = "Run device action",
                                        tint = EmeraldAction,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = latestAssistantMessage.actionTitle.ifBlank {
                                            "Run ${latestActionType.name.replace('_', ' ')}"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmeraldAction,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Smart Gemini Live, Briefing, Translate & Hardware Flashlight Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                StageUtilityChip(
                    icon = if (isLiveSession) Icons.Rounded.StopCircle else Icons.Rounded.GraphicEq,
                    label = when {
                        isLiveConnecting -> "Linking"
                        isLiveActive -> stringResource(R.string.end_live_talk)
                        else -> stringResource(R.string.live_gemini_talk)
                    },
                    active = isLiveSession,
                    activeColor = if (isLiveSession) CoralBargeIn else CyanPulse,
                    contentDesc = stringResource(R.string.live_gemini_talk_cd),
                    testTag = "live_gemini_talk_button",
                    onClick = onToggleLiveTalk,
                    modifier = Modifier.weight(1f)
                )
                StageUtilityChip(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Briefing",
                    active = false,
                    activeColor = RoseNeon,
                    contentDesc = stringResource(R.string.quick_briefing_cd),
                    testTag = "quick_chip_briefing",
                    onClick = onDailyBriefing,
                    modifier = Modifier.weight(1f)
                )
                StageUtilityChip(
                    icon = Icons.Rounded.Translate,
                    label = if (isTranslationMode) "On" else "Translate",
                    active = isTranslationMode,
                    activeColor = CyanPulse,
                    contentDesc = stringResource(R.string.quick_translate_cd),
                    testTag = "quick_chip_translate",
                    onClick = onToggleTranslation,
                    modifier = Modifier.weight(1f)
                )
                StageUtilityChip(
                    icon = if (isFlashlightOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                    label = if (isFlashlightOn) "Torch ON" else "Torch",
                    active = isFlashlightOn,
                    activeColor = Color(0xFFFFCA58),
                    contentDesc = stringResource(R.string.quick_torch_cd),
                    testTag = "quick_chip_torch",
                    onClick = onToggleFlashlight,
                    modifier = Modifier.weight(1f)
                )
            }

            // Three Neon-Bordered Quick-Action Cards (Weather, Music, Time)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StageQuickActionCard(
                    icon = Icons.Rounded.WbCloudy,
                    label = "Weather",
                    value = "Live",
                    contentDesc = stringResource(R.string.quick_weather_cd),
                    testTag = "quick_card_weather",
                    onClick = onQuickWeather,
                    modifier = Modifier.weight(1f)
                )
                StageQuickActionCard(
                    icon = Icons.Rounded.MusicNote,
                    label = "Music",
                    value = "Play",
                    contentDesc = stringResource(R.string.quick_music_cd),
                    testTag = "quick_card_music",
                    onClick = onQuickMusic,
                    modifier = Modifier.weight(1f)
                )
                StageQuickActionCard(
                    icon = Icons.Rounded.Schedule,
                    label = "Time",
                    value = currentTimeText,
                    contentDesc = stringResource(R.string.quick_time_cd),
                    testTag = "quick_card_time",
                    onClick = { onQuickTime(currentTimeText) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StageQuickActionCard(
    icon: ImageVector,
    label: String,
    value: String,
    contentDesc: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Surface(
        color = StageSurface.copy(alpha = 0.82f),
        shape = shape,
        modifier = modifier
            .heightIn(min = 82.dp)
            .minimumInteractiveComponentSize()
            .border(
                width = 1.25.dp,
                color = RoseBorder.copy(alpha = 0.78f),
                shape = shape
            )
            .clip(shape)
            .clickable(onClickLabel = contentDesc, onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = RoseSoftGlow,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = RoseSoftGlow.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StageUtilityChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    activeColor: Color,
    contentDesc: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    val bgColor = if (active) activeColor.copy(alpha = 0.22f) else StageSurface.copy(alpha = 0.72f)
    val borderColor = if (active) activeColor.copy(alpha = 0.9f) else RoseBorder.copy(alpha = 0.45f)
    val contentColor = if (active) activeColor else RoseSoftGlow

    Surface(
        color = bgColor,
        shape = shape,
        modifier = modifier
            .heightIn(min = 48.dp)
            .minimumInteractiveComponentSize()
            .border(width = 1.dp, color = borderColor, shape = shape)
            .clip(shape)
            .clickable(onClickLabel = contentDesc, onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDesc,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatCurrentClock(): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
}

private fun formatCallDuration(totalSeconds: Int): String {
    val mins = totalSeconds / 60
    val secs = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}
