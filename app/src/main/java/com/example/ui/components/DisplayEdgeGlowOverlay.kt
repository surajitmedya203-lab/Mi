package com.example.ui.components

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ai.LiveTalkState
import com.example.ui.theme.CoralBargeIn
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.LavenderMemory
import com.example.ui.theme.RoseNeon
import com.example.ui.theme.RoseSoftGlow
import com.example.ui.viewmodel.AssistantUiState

private val GeminiBlueGlow = Color(0xFF4F8CFF)

/**
 * Full-display perimeter edge glow overlay rendered across the entire screen edge-to-edge.
 * Non-interactive (pure Canvas) so all taps pass cleanly through to underlying UI.
 */
@Composable
fun DisplayEdgeGlowOverlay(
    enabled: Boolean,
    alwaysOn: Boolean,
    uiState: AssistantUiState,
    liveTalkState: LiveTalkState,
    isListening: Boolean,
    isSpeaking: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val isLiveActive = liveTalkState is LiveTalkState.Active || liveTalkState is LiveTalkState.Connecting
    val isThinking = uiState is AssistantUiState.Thinking || uiState is AssistantUiState.Streaming
    val isListeningNow = isListening || uiState is AssistantUiState.Listening
    val isSpeakingNow = isSpeaking || uiState is AssistantUiState.Speaking
    val isVoiceActive = isLiveActive || isListeningNow || isSpeakingNow || isThinking

    val targetIntensity = when {
        !enabled -> 0f
        isLiveActive -> 1f
        isListeningNow -> 0.78f + rmsLevel.coerceIn(0f, 1f) * 0.22f
        isSpeakingNow -> 0.85f
        isThinking -> 0.75f
        alwaysOn -> 0.36f
        else -> 0f
    }

    val animatedIntensity by animateFloatAsState(
        targetValue = targetIntensity,
        animationSpec = spring(stiffness = 180f, dampingRatio = 0.8f),
        label = "edge_glow_intensity"
    )

    if (animatedIntensity <= 0.01f) return

    val infiniteTransition = rememberInfiniteTransition(label = "display_edge_glow")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when {
                    isLiveActive || isListeningNow -> 2600
                    isSpeakingNow || isThinking -> 3400
                    else -> 6400
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "edge_sweep_angle"
    )

    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "edge_breath"
    )

    val smoothRms by animateFloatAsState(
        targetValue = rmsLevel.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 340f, dampingRatio = 0.72f),
        label = "edge_rms"
    )

    val sweepColors = when {
        isLiveActive -> listOf(
            CyanPulse,
            GeminiBlueGlow,
            LavenderMemory,
            RoseNeon,
            CyanPulse
        )
        isListeningNow -> listOf(
            RoseNeon,
            CoralBargeIn,
            CyanPulse,
            RoseSoftGlow,
            RoseNeon
        )
        isThinking -> listOf(
            LavenderMemory,
            CyanPulse,
            GeminiBlueGlow,
            RoseSoftGlow,
            LavenderMemory
        )
        isSpeakingNow -> listOf(
            RoseSoftGlow,
            RoseNeon,
            LavenderMemory,
            CyanPulse,
            RoseSoftGlow
        )
        else -> listOf(
            RoseNeon.copy(alpha = 0.85f),
            LavenderMemory.copy(alpha = 0.75f),
            CyanPulse.copy(alpha = 0.7f),
            RoseSoftGlow.copy(alpha = 0.85f),
            RoseNeon.copy(alpha = 0.85f)
        )
    }

    val semanticNote = if (isVoiceActive) {
        "Display perimeter edge glow active"
    } else {
        "Display perimeter ambient edge glow"
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("display_edge_glow_overlay")
            .semantics { contentDescription = semanticNote }
    ) {
        val w = size.width
        val h = size.height
        val center = Offset(w / 2f, h / 2f)
        val cornerRadius = CornerRadius(28.dp.toPx(), 28.dp.toPx())
        val dynamicBoost = if (isListeningNow) 1f + smoothRms * 0.48f else breathScale

        val outerStroke = (24.dp.toPx() * dynamicBoost * animatedIntensity).coerceAtLeast(1f)
        val midStroke = (10.dp.toPx() * dynamicBoost * animatedIntensity).coerceAtLeast(1f)
        val coreStroke = (3.dp.toPx() * (0.85f + 0.25f * animatedIntensity)).coerceAtLeast(1f)

        withTransform({
            rotate(degrees = sweepAngle, pivot = center)
        }) {
            val sweepBrush = Brush.sweepGradient(
                colors = sweepColors,
                center = center
            )

            // Wide soft outer perimeter bloom
            drawRoundRect(
                brush = sweepBrush,
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = cornerRadius,
                alpha = (0.20f * animatedIntensity).coerceIn(0f, 0.45f),
                style = Stroke(width = outerStroke)
            )

            // Medium vibrant rim halo
            drawRoundRect(
                brush = sweepBrush,
                topLeft = Offset(1.dp.toPx(), 1.dp.toPx()),
                size = Size(w - 2.dp.toPx(), h - 2.dp.toPx()),
                cornerRadius = cornerRadius,
                alpha = (0.44f * animatedIntensity).coerceIn(0f, 0.7f),
                style = Stroke(width = midStroke)
            )

            // Crisp neon inner hairline
            drawRoundRect(
                brush = sweepBrush,
                topLeft = Offset(1.5.dp.toPx(), 1.5.dp.toPx()),
                size = Size(w - 3.dp.toPx(), h - 3.dp.toPx()),
                cornerRadius = cornerRadius,
                alpha = (0.88f * animatedIntensity).coerceIn(0f, 0.98f),
                style = Stroke(width = coreStroke)
            )
        }
    }
}
