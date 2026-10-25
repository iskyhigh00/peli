package com.peli.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.peli.app.ui.theme.PeliTheme
import com.peli.app.ui.theme.isDark

/**
 * Tarjeta de vidrio: capa difuminada (blur 20dp, efectivo en Android 12+) con surface al 80%,
 * velo blanco al 8%, gradiente primary→transparente, borde degradado y sombra teñida de primary.
 * El blur se aplica solo a la capa de fondo para no desenfocar el contenido.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val elevation by animateDpAsState(if (pressed) 2.dp else 8.dp, spring(), label = "glassElevation")
    val veil = if (scheme.isDark) Color.White else scheme.primary
    val clickable = if (onClick != null) {
        Modifier.clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
    } else Modifier

    Box(
        modifier
            .pressScale(source)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = scheme.primary.copy(alpha = 0.1f),
                spotColor = scheme.primary.copy(alpha = 0.15f),
            )
            .clip(shape)
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(listOf(scheme.primary.copy(alpha = 0.5f), veil.copy(alpha = 0.12f))),
                ),
                shape,
            )
            .then(clickable),
    ) {
        Box(Modifier.matchParentSize().blur(20.dp).background(scheme.surface.copy(alpha = 0.8f)))
        Box(Modifier.matchParentSize().background(veil.copy(alpha = 0.08f)))
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(scheme.primary.copy(alpha = 0.14f), Color.Transparent)),
            ),
        )
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@ThemePreviews
@Composable
private fun GlassCardPreview() {
    PeliTheme {
        BrandBackground {
            GlassCard(Modifier.padding(20.dp), onClick = {}) {
                Text("Noche de películas", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Tarjeta de vidrio con borde degradado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
