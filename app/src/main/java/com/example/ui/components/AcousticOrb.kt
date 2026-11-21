package com.example.ui.components

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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CoralBargeIn
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.LavenderMemory
import com.example.ui.theme.RoseNeon
import com.example.ui.theme.RoseSoftGlow
import com.example.ui.viewmodel.AssistantUiState
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AcousticOrb(
    uiState: AssistantUiState,
    rmsLevel: Float,
    isOffline: Boolean,
    accessibilityLarge: Boolean,
    onTapOrb: () -> Unit,
    modifier: Modifier = Modifier,
    stageStyle: Boolean = false,
    size: Dp = if (accessibilityLarge) 96.dp else 82.dp
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")

    val breath by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath"
    )

    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_progress"
    )

    val smoothRms by animateFloatAsState(
        targetValue = rmsLevel.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 380f, dampingRatio = 0.7f),
        label = "rms"
    )

    val isActiveStop = uiState is AssistantUiState.Listening ||
        uiState is AssistantUiState.Thinking ||
        uiState is AssistantUiState.Streaming ||
        uiState is AssistantUiState.Speaking

    val primaryGlow by animateColorAsState(
        targetValue = when {
            uiState is AssistantUiState.Listening -> if (stageStyle) RoseNeon else CoralBargeIn
            uiState is AssistantUiState.Speaking -> if (stageStyle) RoseSoftGlow else CyanPulse
            uiState is AssistantUiState.Thinking || uiState is AssistantUiState.Streaming -> LavenderMemory
            isOffline -> MaterialTheme.colorScheme.outline
            stageStyle -> RoseNeon
            else -> CyanPulse
        },
        animationSpec = tween(260),
        label = "primaryGlow"
    )

    val secondaryGlow by animateColorAsState(
        targetValue = when (uiState) {
            is AssistantUiState.Listening -> if (stageStyle) RoseSoftGlow else CyanPulse
            is AssistantUiState.Speaking -> if (stageStyle) RoseNeon else LavenderMemory
            else -> if (stageStyle) RoseSoftGlow else CoralBargeIn
        },
        animationSpec = tween(260),
        label = "secondaryGlow"
    )

    val cdText = if (isActiveStop) {
        stringResource(R.string.orb_cd_stop)
    } else {
        stringResource(R.string.orb_cd_listen)
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size + 30.dp)
            .minimumInteractiveComponentSize()
            .testTag("voice_orb_button")
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = false, color = primaryGlow),
                onClickLabel = cdText
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onTapOrb()
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val baseRadius = (this.size.minDimension / 2f) * 0.64f

            val dynamicExpand = when (uiState) {
                is AssistantUiState.Listening -> 1f + smoothRms * 0.36f
                is AssistantUiState.Speaking -> breath * 1.12f
                is AssistantUiState.Thinking, is AssistantUiState.Streaming -> breath * 1.05f
                else -> breath
            }

            // Outer ambient halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryGlow.copy(alpha = 0.36f),
                        secondaryGlow.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = baseRadius * 1.55f * dynamicExpand
                ),
                center = center,
                radius = baseRadius * 1.55f * dynamicExpand
            )

            // Dual staggered expanding pulse waves while listening or speaking
            if (uiState is AssistantUiState.Listening || uiState is AssistantUiState.Speaking) {
                listOf(pulseProgress, (pulseProgress + 0.5f) % 1f).forEach { wave ->
                    val waveRadius = baseRadius * (1.02f + wave * (0.48f + smoothRms * 0.16f))
                    val waveAlpha = ((1f - wave) * 0.55f).coerceIn(0f, 0.55f)
                    drawCircle(
                        color = primaryGlow.copy(alpha = waveAlpha),
                        radius = waveRadius,
                        center = center,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Reactive harmonic ring & orbiting particle
            if (uiState !is AssistantUiState.Idle) {
                val ringRadius = baseRadius * (1.16f + smoothRms * 0.24f)
                drawCircle(
                    color = primaryGlow.copy(alpha = 0.55f),
                    radius = ringRadius,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )

                val orbitX = center.x + cos(phase) * ringRadius
                val orbitY = center.y + sin(phase) * ringRadius
                drawCircle(
                    color = secondaryGlow,
                    radius = 4.dp.toPx(),
                    center = Offset(orbitX, orbitY)
                )
            }
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            primaryGlow,
                            secondaryGlow.copy(alpha = 0.85f)
                        )
                    )
                )
        ) {
            Icon(
                imageVector = when {
                    uiState is AssistantUiState.Speaking ||
                        uiState is AssistantUiState.Thinking ||
                        uiState is AssistantUiState.Streaming -> Icons.Rounded.Stop
                    uiState is AssistantUiState.Listening -> Icons.Rounded.GraphicEq
                    else -> Icons.Rounded.Mic
                },
                contentDescription = cdText,
                tint = Color(0xFF06101D),
                modifier = Modifier.size(if (accessibilityLarge) 38.dp else 32.dp)
            )
        }
    }
}
