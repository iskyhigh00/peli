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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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

/** Colores cálidos de la foto (luz de lámpara) para los matches y la pestaña Juntos. */
val WarmAmber = androidx.compose.ui.graphics.Color(0xFFF2B15A)
val WarmCoral = androidx.compose.ui.graphics.Color(0xFFE8785F)
val WarmScrim = androidx.compose.ui.graphics.Color(0xFF1B1210)

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
            // Absorbe los toques para que no lleguen a lo que hay debajo.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
    ) {
        // La foto es lo principal: ocupa toda la pantalla.
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(com.peli.app.R.drawable.foto_sofa),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        // Velo solo abajo (para leer la película) y un toque arriba (para leer el título); el centro queda limpio.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to WarmScrim.copy(alpha = 0.55f),
                    0.28f to androidx.compose.ui.graphics.Color.Transparent,
                    0.55f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to WarmScrim.copy(alpha = 0.94f),
                ),
            ),
        )
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp).graphicsLayer { alpha = enter.value.coerceIn(0f, 1f) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = WarmAmber,
                modifier = Modifier.size(44.dp).graphicsLayer { scaleX = beat.value; scaleY = beat.value },
            )
            Text(
                "¡Es un match!",
                style = MaterialTheme.typography.displayMedium.copy(
                    color = androidx.compose.ui.graphics.Color.White,
                    shadow = androidx.compose.ui.graphics.Shadow(WarmScrim, androidx.compose.ui.geometry.Offset(0f, 4f), 16f),
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .graphicsLayer {
                    translationY = (1f - enter.value) * 120f
                    alpha = enter.value.coerceIn(0f, 1f)
                },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PosterImage(
                    entry.posterPath,
                    "Carátula de ${entry.title}",
                    Modifier.size(width = 84.dp, height = 126.dp),
                )
                Column(Modifier.padding(start = 16.dp)) {
                    Text(
                        "A los dos les interesa verla",
                        style = MaterialTheme.typography.labelLarge,
                        color = WarmAmber,
                    )
                    Text(
                        entry.displayTitle,
                        style = MaterialTheme.typography.titleLarge,
                        color = androidx.compose.ui.graphics.Color.White,
                        maxLines = 3,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (more > 0) {
                        Text(
                            if (more == 1) "y 1 match más" else "y $more matches más",
                            style = MaterialTheme.typography.labelMedium,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
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
