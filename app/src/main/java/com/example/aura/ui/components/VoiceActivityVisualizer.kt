package com.example.aura.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.aura.ui.theme.AuraBorder
import com.example.aura.ui.theme.AuraCyanPrimary
import com.example.aura.ui.theme.AuraVioletSecondary
import kotlin.math.sin

/**
 * Animated dynamic waveform and voice activity visualizer.
 * Reacts in real time to microphone amplitude and speech playback status.
 */
@Composable
fun VoiceActivityVisualizer(
    amplitude: Float,
    isSpeaking: Boolean,
    isListening: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 16,
    maxHeight: Dp = 36.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val activeColor = when {
        isSpeaking -> AuraVioletSecondary
        isListening -> AuraCyanPrimary
        else -> AuraBorder
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(maxHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until barCount) {
            val normalizedIndex = i.toFloat() / barCount
            val waveMod = ((sin(phase + normalizedIndex * 4.0) + 1.0) / 2.0).toFloat()

            val targetFraction = when {
                isSpeaking -> (0.25f + waveMod * 0.75f).coerceIn(0.1f, 1.0f)
                isListening -> {
                    val energyScale = (amplitude * 3.5f).coerceIn(0.1f, 1.0f)
                    (0.15f + waveMod * energyScale).coerceIn(0.1f, 1.0f)
                }
                else -> 0.12f
            }

            val animHeight = remember { Animatable(targetFraction) }
            LaunchedEffect(targetFraction) {
                animHeight.animateTo(
                    targetValue = targetFraction,
                    animationSpec = tween(durationMillis = 80, easing = LinearEasing)
                )
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(maxHeight * animHeight.value)
                    .background(
                        color = if (isSpeaking || isListening) activeColor else activeColor.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
