package com.peli.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.peli.app.ui.theme.PeliTheme

/** Estado vacío / error: ilustración con halo pulsante, título, mensaje y acción opcional. */
@Composable
fun StateView(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.secondary,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    val pulse = rememberInfiniteTransition(label = "statePulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "statePulseScale",
    )
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(160.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(160.dp)
                    .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                    .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.35f), Color.Transparent))),
            )
            Box(
                Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.10f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(40.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(8.dp))
            GradientButton(actionLabel, onAction, icon = actionIcon)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Movie,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) = StateView(icon, title, message, modifier, MaterialTheme.colorScheme.secondary, actionLabel, actionIcon, onAction)

@Composable
fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) = StateView(
    icon = Icons.Default.CloudOff,
    title = "Algo salió mal",
    message = message,
    modifier = modifier,
    accent = MaterialTheme.colorScheme.tertiary,
    actionLabel = "Reintentar",
    actionIcon = Icons.Default.Refresh,
    onAction = onRetry,
)

@ThemePreviews
@Composable
private fun StatesPreview() {
    PeliTheme {
        BrandBackground {
            Column {
                EmptyState("Sin resultados", "Prueba con otro título.")
                ErrorState("Error de red: timeout", onRetry = {})
            }
        }
    }
}
