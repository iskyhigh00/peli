package com.peli.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/** Escala a 0.95 al presionar, con spring rápido. */
@Composable
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.95f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "pressScale",
    )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Claves que ya hicieron su animación de entrada (para no repetirla al volver o al hacer scroll). */
private val animatedEntrances = HashSet<Any>()

/**
 * Entrada: fadeIn + slide vertical (1/3 de su alto) con spring "bouncy", escalonada por índice.
 * Con [key], cada elemento anima solo la primera vez que aparece.
 */
@Composable
fun Modifier.entrance(index: Int = 0, key: Any? = null): Modifier {
    val already = remember { key != null && key in animatedEntrances }
    val progress = remember { Animatable(if (already) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (already) return@LaunchedEffect
        delay(index.coerceIn(0, 8) * 45L)
        progress.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        )
        if (key != null) animatedEntrances.add(key)
    }
    return this.graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * size.height / 3f
    }
}

/** Barrido de brillo para skeletons; se dibuja en la fase de draw, sin recomponer. */
@Composable
fun Modifier.shimmer(): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "shimmerPhase",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f).compositeOver(base)
    return this.drawBehind {
        val w = size.width.coerceAtLeast(1f)
        val center = -0.5f * w + phase.value * 2f * w
        drawRect(
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(center - 0.5f * w, 0f),
                end = Offset(center + 0.5f * w, size.height * 0.3f),
            ),
        )
    }
}
