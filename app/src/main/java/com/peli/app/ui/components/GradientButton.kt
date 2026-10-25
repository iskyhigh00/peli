package com.peli.app.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.peli.app.ui.theme.PeliTheme

/** Botón primario: gradiente horizontal primary→tertiary, 56dp, esquinas 16dp, escala al presionar. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = 56.dp)
            .pressScale(source)
            .alpha(if (enabled) 1f else 0.4f)
            .shadow(
                if (enabled) 6.dp else 0.dp,
                shape,
                ambientColor = scheme.primary.copy(alpha = 0.1f),
                spotColor = scheme.primary.copy(alpha = 0.15f),
            )
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(scheme.primary, scheme.tertiary)))
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = scheme.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = scheme.onPrimary, maxLines = 1)
    }
}

/** Botón secundario tonal: secondary al 30% de opacidad. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    Row(
        modifier
            .heightIn(min = 56.dp)
            .pressScale(source)
            .alpha(if (enabled) 1f else 0.4f)
            .clip(MaterialTheme.shapes.medium)
            .background(scheme.secondary.copy(alpha = 0.3f))
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = scheme.onSurface, maxLines = 1)
    }
}

/** Botón vertical (icono sobre texto de hasta 2 líneas) para acciones con etiqueta larga, p. ej. en "Descubrir". */
@Composable
fun ActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val source = remember { MutableInteractionSource() }
    val background = if (gradient) {
        Modifier.background(Brush.horizontalGradient(listOf(scheme.primary, scheme.tertiary)))
    } else {
        Modifier.background(scheme.secondary.copy(alpha = 0.3f))
    }
    val content = if (gradient) scheme.onPrimary else scheme.onSurface
    Column(
        modifier
            .heightIn(min = 72.dp)
            .pressScale(source)
            .clip(MaterialTheme.shapes.medium)
            .then(background)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@ThemePreviews
@Composable
private fun ButtonsPreview() {
    PeliTheme {
        BrandBackground {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                GradientButton("Quiero verla", onClick = {}, icon = Icons.Default.Bookmark)
                TonalButton("No me interesa", onClick = {}, icon = Icons.Default.ThumbDown)
                GradientButton("Deshabilitado", onClick = {}, enabled = false)
            }
        }
    }
}
