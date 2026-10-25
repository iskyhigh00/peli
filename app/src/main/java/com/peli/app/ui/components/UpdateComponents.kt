package com.peli.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peli.app.APP_VERSION
import com.peli.app.ui.theme.PeliTheme
import com.peli.app.ui.theme.SheetShape
import kotlinx.coroutines.launch

/**
 * Franja "Hay una versión nueva". progress: -2 inactivo (muestra el botón),
 * -1 descargando sin tamaño conocido, 0..100 con barra determinada.
 */
@Composable
fun UpdateBanner(version: String, progress: Int, onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier, shape = MaterialTheme.shapes.large, contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = scheme.secondary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Hay una versión nueva", style = MaterialTheme.typography.titleSmall)
                Text(
                    "v$version (tienes v$APP_VERSION)",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (progress == -2) {
            GradientButton("Actualizar", onUpdate, Modifier.fillMaxWidth(), icon = Icons.Default.SystemUpdate)
        } else {
            if (progress >= 0) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = scheme.secondary,
                    trackColor = scheme.surfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text("Descargando… $progress%", style = MaterialTheme.typography.labelMedium)
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = scheme.secondary, trackColor = scheme.surfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("Descargando…", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Hoja con lo nuevo desde la última versión vista (más reciente primero). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewSheet(items: List<Pair<String, List<String>>>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Novedades", style = MaterialTheme.typography.headlineSmall)
            items.forEach { (version, changes) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusChip("v$version")
                    changes.forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
                }
            }
            GradientButton("Entendido", { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }, Modifier.fillMaxWidth())
        }
    }
}

@ThemePreviews
@Composable
private fun UpdateBannerPreview() {
    PeliTheme {
        BrandBackground {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                UpdateBanner("1.0.1", -2, {})
                UpdateBanner("1.0.1", 42, {})
            }
        }
    }
}
