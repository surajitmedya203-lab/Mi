package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CoralBargeIn
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.LavenderMemory
import kotlin.math.abs
import kotlin.math.sin

/**
 * Live Canvas acoustic waveform visualizer that activates when SpeechRecognizer is listening
 * (driven by real-time RMS microphone amplitude) or while TextToSpeech is outputting speech.
 */
@Composable
fun VoiceWaveformVisualizer(
    isListening: Boolean,
    isSpeaking: Boolean,
    isThinking: Boolean,
    rmsLevel: Float,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "voice_waveform")
    val wavePhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isListening) 950 else 1350, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    val smoothRms by animateFloatAsState(
        targetValue = if (isListening) rmsLevel.coerceIn(0.08f, 1f) else if (isSpeaking) 0.65f else 0.25f,
        animationSpec = spring(stiffness = 320f, dampingRatio = 0.72f),
        label = "smooth_rms"
    )

    val startColor by animateColorAsState(
        targetValue = when {
            isListening -> CoralBargeIn
            isSpeaking -> CyanPulse
            else -> LavenderMemory
        },
        animationSpec = tween(250),
        label = "wave_start_color"
    )

    val endColor by animateColorAsState(
        targetValue = when {
            isListening -> CyanPulse
            isSpeaking -> LavenderMemory
            else -> CyanPulse
        },
        animationSpec = tween(250),
        label = "wave_end_color"
    )

    val semanticLabel = when {
        isListening -> "Microphone listening waveform active"
        isSpeaking -> "Text-to-speech voice output waveform active"
        isThinking -> "Assistant thinking indicator active"
        else -> "Voice waveform idle"
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(34.dp)
            .testTag("voice_waveform_visualizer")
            .semantics { contentDescription = semanticLabel }
    ) {
        val barCount = 28
        val totalWidth = size.width
        val maxBarHeight = size.height * 0.9f
        val minBarHeight = size.height * 0.16f
        val step = totalWidth / barCount
        val barWidth = (step * 0.52f).coerceAtLeast(3.dp.toPx())
        val centerIndex = (barCount - 1) / 2f
        val centerY = size.height / 2f

        val brush = Brush.horizontalGradient(colors = listOf(startColor, endColor))

        for (i in 0 until barCount) {
            // Bell-curve envelope so center bars peak taller than edge bars
            val distFromCenter = abs(i - centerIndex) / centerIndex
            val envelope = (1f - distFromCenter * 0.68f).coerceIn(0.25f, 1f)

            val harmonic = when {
                isListening -> {
                    val waveA = 0.5f + 0.5f * sin(wavePhase * 2f + i * 0.55f)
                    val waveB = 0.5f + 0.5f * sin(-wavePhase + i * 0.9f)
                    ((waveA * 0.6f + waveB * 0.4f) * smoothRms).coerceIn(0.08f, 1f)
                }
                isSpeaking -> {
                    val cadence = 0.5f + 0.5f * sin(wavePhase * 1.5f + i * 0.42f)
                    val overtone = 0.5f + 0.5f * sin(wavePhase * 2.5f - i * 0.72f)
                    (0.22f + 0.78f * (cadence * 0.65f + overtone * 0.35f)).coerceIn(0.15f, 1f)
                }
                isThinking -> {
                    val shimmer = 0.5f + 0.5f * sin(wavePhase + i * 0.35f)
                    0.18f + shimmer * 0.32f
                }
                else -> 0.1f
            }

            val barHeight = (minBarHeight + (maxBarHeight - minBarHeight) * harmonic * envelope)
                .coerceIn(minBarHeight, maxBarHeight)
            val x = i * step + (step - barWidth) / 2f
            val y = centerY - barHeight / 2f

            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
