package com.peli.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.peli.app.ui.theme.PeliTheme

/** Fondo de marca: gradiente vertical sutil + dos "orbes" de color que derivan lento detrás del vidrio. */
@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val drift = rememberInfiniteTransition(label = "drift")
    val shift = drift.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "driftShift",
    )
    Box(modifier.fillMaxSize().background(scheme.background)) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(scheme.primary.copy(alpha = 0.05f), Color.Transparent, scheme.secondary.copy(alpha = 0.03f)),
                ),
            ),
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (-120).dp, y = (-80).dp)
                .size(360.dp)
                .graphicsLayer { translationX = shift.value * 60.dp.toPx(); translationY = shift.value * 40.dp.toPx() }
                .background(Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.28f), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 120.dp, y = 100.dp)
                .size(380.dp)
                .graphicsLayer { translationX = -shift.value * 50.dp.toPx(); translationY = -shift.value * 60.dp.toPx() }
                .background(Brush.radialGradient(listOf(scheme.tertiary.copy(alpha = 0.20f), Color.Transparent))),
        )
        content()
    }
}

@ThemePreviews
@Composable
private fun BrandBackgroundPreview() {
    PeliTheme {
        BrandBackground { Text("Peli", Modifier.align(Alignment.Center), style = MaterialTheme.typography.displaySmall) }
    }
}
