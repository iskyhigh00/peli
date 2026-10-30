package com.peli.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.peli.app.APP_VERSION
import com.peli.app.data.RatingSource
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.SectionSpacing
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.components.UpdateBanner
import com.peli.app.ui.theme.SheetShape

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // El Row completo es el control (toggleable); el Switch solo refleja el estado.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Ajustes: de qué fuentes mostrar valoraciones y reseñas, versión y búsqueda de actualización. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    sources: Set<RatingSource>,
    omdbAvailable: Boolean,
    onToggle: (RatingSource) -> Unit,
    remindersOn: Boolean,
    onReminders: (Boolean) -> Unit,
    update: MainViewModel.ManualUpdate,
    progress: Int,
    onInstall: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(bottom = SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
            Text("Valoraciones y reseñas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = ItemSpacing))
            Text(
                "Elige de qué lugares quieres ver notas y reseñas en el detalle de cada película.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            RatingSource.entries.forEach { source ->
                val available = !source.needsOmdb || omdbAvailable
                SettingRow(
                    title = source.label,
                    subtitle = if (available) source.hint else "Necesita la clave gratuita de OMDb (OMDB_API_KEY)",
                    checked = available && source in sources,
                    enabled = available,
                    onChange = { onToggle(source) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            SettingRow(
                title = "Avisos de estreno",
                subtitle = "Una notificación cuando se acerca el estreno de una película de \"Quiero verla\": a una semana, el día anterior y el mismo día.",
                checked = remindersOn,
                enabled = true,
                onChange = onReminders,
            )
            HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            Text("Versión $APP_VERSION", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (update) {
                is MainViewModel.ManualUpdate.Available -> UpdateBanner(
                    version = update.version,
                    progress = progress,
                    onUpdate = { onInstall(update.version) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> {
                    val status = when (update) {
                        MainViewModel.ManualUpdate.Checking -> "Buscando…"
                        MainViewModel.ManualUpdate.UpToDate -> "Estás al día: tienes la última versión."
                        is MainViewModel.ManualUpdate.Failed -> "No se pudo consultar: ${update.error ?: "sin conexión"}"
                        else -> null
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    TonalButton("Buscar actualización", onCheckUpdate, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            LogSection(onMessage)
        }
    }
}
