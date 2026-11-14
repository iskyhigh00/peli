package com.peli.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.peli.app.data.WelcomeMessage
import kotlinx.coroutines.delay

/**
 * Mensaje de bienvenida sobre la app mientras carga: las palabras aparecen una tras otra, se queda un momento y se
 * desvanece solo. Un toque lo cierra antes.
 */
@Composable
fun WelcomeSplash(text: String, onDone: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val words = remember(text) { WelcomeMessage.words(text) }
    val progress = remember(text) { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val sticker = remember { Stickers.all.first() }
    LaunchedEffect(text) {
        progress.animateTo(words.size.toFloat(), tween(WelcomeMessage.revealMs(words.size), easing = LinearEasing))
        delay(WelcomeMessage.HOLD_MS)
        fade.animateTo(0f, tween(450))
        onDone()
    }
    Box(
        Modifier
            .fillMaxSize()
            .alpha(fade.value)
            .background(scheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(listOf(scheme.primary.copy(alpha = 0.30f), scheme.tertiary.copy(alpha = 0.10f), Color.Transparent)),
            ),
        )
        Column(
            Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            val p = if (words.isEmpty()) 1f else progress.value / words.size
            Image(
                painter = painterResource(sticker),
                contentDescription = null,
                modifier = Modifier.size(120.dp).graphicsLayer {
                    alpha = (p * 3f).coerceIn(0f, 1f)
                    val s = 0.85f + 0.15f * p.coerceIn(0f, 1f)
                    scaleX = s; scaleY = s
                },
            )
            val brush = Brush.horizontalGradient(listOf(scheme.primary, scheme.tertiary))
            Text(
                buildAnnotatedString {
                    words.forEachIndexed { i, w ->
                        withStyle(SpanStyle(brush = brush, alpha = WelcomeMessage.alpha(i, progress.value))) { append(w) }
                        if (i < words.lastIndex) append(" ")
                    }
                },
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val s = 0.96f + 0.04f * p.coerceIn(0f, 1f)
                    scaleX = s; scaleY = s
                },
            )
        }
    }
}
