package com.peli.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.peli.app.data.MovieFilters
import com.peli.app.data.OscarFilter
import com.peli.app.ui.components.CompactSpacing
import com.peli.app.ui.components.GradientButton
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.PeliFilterChip
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.SectionSpacing
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.theme.SheetShape
import kotlinx.coroutines.launch

/** Chip que abre los filtros de valoraciones y premios; marcado, con la cantidad de filtros activos. */
@Composable
fun MovieFiltersChip(filters: MovieFilters, onClick: () -> Unit) {
    PeliFilterChip(
        label = if (filters.isActive) "Filtros (${filters.count()})" else "Filtros",
        selected = filters.isActive,
        onClick = onClick,
    )
}

@Composable
private fun <T> OptionRow(title: String, options: List<T>, selected: T, label: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CompactSpacing)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CompactSpacing),
        ) {
            options.forEach { o -> PeliFilterChip(label(o), o == selected, { if (enabled) onSelect(o) }) }
        }
    }
}

/**
 * Hoja de filtros por puntajes en plataformas y premios. Lo elegido se guarda en el teléfono y sigue activo
 * hasta que se quite. Los puntajes de IMDb, Rotten Tomatoes y Metacritic y los premios salen de OMDb
 * ([omdbAvailable]); sin clave esos filtros no se pueden usar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieFiltersSheet(
    current: MovieFilters,
    omdbAvailable: Boolean,
    onApply: (MovieFilters) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var f by remember { mutableStateOf(current) }

    fun close(action: () -> Unit) {
        action()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

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
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Text("Filtros", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Se guardan aunque cierres la app, hasta que los quites.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OptionRow("Nota TMDB mínima", listOf(0.0, 6.0, 6.5, 7.0, 7.5, 8.0), f.minTmdb, { if (it == 0.0) "Cualquiera" else "≥ ${it}" }) {
                f = f.copy(minTmdb = it)
            }
            Text(
                if (omdbAvailable) "Lo siguiente se consulta a OMDb (IMDb, Rotten Tomatoes, Metacritic y premios); las películas sin ese dato no aparecen."
                else "Lo siguiente necesita la clave de OMDb, que esta versión no tiene.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OptionRow("IMDb mínima", listOf(0.0, 6.0, 7.0, 7.5, 8.0, 8.5), f.minImdb, { if (it == 0.0) "Cualquiera" else "≥ ${it}" }, omdbAvailable) {
                f = f.copy(minImdb = it)
            }
            OptionRow("Rotten Tomatoes mínima", listOf(0, 60, 75, 85, 90), f.minRottenTomatoes, { if (it == 0) "Cualquiera" else "≥ $it%" }, omdbAvailable) {
                f = f.copy(minRottenTomatoes = it)
            }
            OptionRow("Metacritic mínima", listOf(0, 50, 60, 70, 80), f.minMetacritic, { if (it == 0) "Cualquiera" else "≥ $it" }, omdbAvailable) {
                f = f.copy(minMetacritic = it)
            }
            OptionRow("Premios Oscar", OscarFilter.entries, f.oscar, { it.label }, omdbAvailable) { f = f.copy(oscar = it) }
            OptionRow("Premios ganados (en total)", listOf(0, 1, 5, 10, 25, 50), f.minWins, { if (it == 0) "Cualquiera" else "$it+" }, omdbAvailable) {
                f = f.copy(minWins = it)
            }
            OptionRow("Nominaciones (en total)", listOf(0, 5, 10, 25, 50, 100), f.minNominations, { if (it == 0) "Cualquiera" else "$it+" }, omdbAvailable) {
                f = f.copy(minNominations = it)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                GradientButton("Aplicar", { close { onApply(f) } }, Modifier.weight(1f))
                TonalButton("Quitar filtros", { close(onClearAll) })
            }
        }
    }
}
