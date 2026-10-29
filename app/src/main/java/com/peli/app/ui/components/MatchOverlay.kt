package com.peli.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.peli.app.data.Entry
import com.peli.app.ui.theme.PeliTheme

/**
 * "¡Es un match!": aparece cuando a los dos nos interesa la misma película.
 * Entra con un resorte, vibra una vez y el corazón late mientras está abierta.
 * @param more cuántos matches más esperan detrás de este.
 */
@Composable
fun MatchOverlay(
    entry: Entry,
    more: Int,
    onKeep: () -> Unit,
    onSeeList: () -> Unit,
) {
    // Atrás cierra el match (antes de que Atrás actúe sobre lo que hay debajo).
    BackHandler(onBack = onKeep)
    val scheme = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(entry.movieId) {
        shown = false
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        shown = true
    }
    val enter = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "matchEnter",
    )
    val beat = rememberInfiniteTransition(label = "matchBeat").animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeat(),
        label = "matchBeatScale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(scheme.scrim.copy(alpha = 0.72f))
            // Absorbe los toques para que no lleguen a lo que hay debajo.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            Modifier
                .padding(24.dp)
                .graphicsLayer {
                    scaleX = 0.6f + 0.4f * enter.value
                    scaleY = 0.6f + 0.4f * enter.value
                    alpha = enter.value.coerceIn(0f, 1f)
                },
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = scheme.tertiary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(40.dp)
                    .graphicsLayer { scaleX = beat.value; scaleY = beat.value },
            )
            Text(
                "¡Es un match!",
                style = MaterialTheme.typography.displaySmall.copy(
                    brush = Brush.horizontalGradient(listOf(scheme.primary, scheme.tertiary)),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
            )
            Text(
                "A los dos les interesa verla",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(16.dp))
            PosterImage(
                entry.posterPath,
                "Carátula de ${entry.title}",
                Modifier.align(Alignment.CenterHorizontally).size(width = 160.dp, height = 240.dp),
            )
            Text(
                entry.displayTitle,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            if (more > 0) {
                Text(
                    if (more == 1) "y 1 match más" else "y $more matches más",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(16.dp))
            GradientButton("Seguir buscando", onKeep, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            TonalButton("Ver nuestros matches", onSeeList, Modifier.fillMaxWidth())
        }
    }
}

private fun infiniteRepeat() = infiniteRepeatable<Float>(tween(650), RepeatMode.Reverse)

@ThemePreviews
@Composable
private fun MatchOverlayPreview() {
    PeliTheme {
        BrandBackground { MatchOverlay(sampleEntry, more = 2, onKeep = {}, onSeeList = {}) }
    }
}
