package com.peli.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peli.app.data.DirectorStat
import com.peli.app.data.Directors
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.theme.SheetShape
import kotlinx.coroutines.launch

/**
 * Directores con al menos [Directors.MIN_FILMS] películas conocidas por la app, primero los que tienen más películas y
 * más premios. Al elegir uno, la lista principal muestra sus películas; el filtro se guarda hasta quitarlo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectorsSheet(
    directors: List<DirectorStat>?,
    selectedId: Int,
    onPick: (DirectorStat) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
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
            Modifier.padding(horizontal = ScreenHorizontalPadding).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Text("Directores", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Con ${Directors.MIN_FILMS} o más películas conocidas por Peli (las que has marcado y explorado), " +
                    "de más a menos películas y premios. La lista crece a medida que usas la app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (selectedId > 0) TonalButton("Quitar director", { close(onClear) }, Modifier.fillMaxWidth())
            when {
                directors == null -> CircularProgressIndicator(Modifier.padding(24.dp).align(Alignment.CenterHorizontally))
                directors.isEmpty() -> Text(
                    "Aún no hay directores con ${Directors.MIN_FILMS} o más películas conocidas. " +
                        "Marca y explora más películas y vuelve a mirar.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> LazyColumn(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(directors, key = { it.id }) { d ->
                        Row(
                            Modifier.fillMaxWidth().clickable { close { onPick(d) } }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${d.films} películas" + if (d.wins > 0) " · ${d.wins} premios" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (d.id == selectedId) Icon(Icons.Default.Check, contentDescription = "Seleccionado")
                        }
                    }
                }
            }
        }
    }
}
