package com.peli.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peli.app.data.ReleaseInfo
import com.peli.app.data.YearRange
import com.peli.app.ui.components.CompactSpacing
import com.peli.app.ui.components.GradientButton
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.PeliFilterChip
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.SectionSpacing
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.theme.SheetShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Chip que abre el selector de años; marcado (con el rango en el texto) cuando hay un filtro activo. */
@Composable
fun YearChip(years: YearRange?, onClick: () -> Unit) {
    PeliFilterChip(
        label = years?.let { "Años: ${it.label()}" } ?: "Años",
        selected = years != null,
        onClick = onClick,
    )
}

/**
 * Hoja para elegir el rango de años: deslizador de rango + atajos (décadas, "Clásicas", próximos estrenos).
 * Abarcar todo el rango equivale a no filtrar. Atrás o arrastrar hacia abajo cierra sin aplicar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearFilterSheet(current: YearRange?, onApply: (YearRange?) -> Unit, onDismiss: () -> Unit) {
    val thisYear = remember { ReleaseInfo.currentYear() }
    val minYear = YearRange.MIN_YEAR
    val maxYear = YearRange.maxYear(thisYear)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var from by rememberSaveable { mutableStateOf((current?.from ?: minYear).coerceIn(minYear, maxYear)) }
    var to by rememberSaveable { mutableStateOf((current?.to ?: maxYear).coerceIn(minYear, maxYear)) }
    val isAll = from == minYear && to == maxYear

    fun apply(range: YearRange?) {
        onApply(range)
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
            Modifier.padding(horizontal = ScreenHorizontalPadding).padding(bottom = SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Text("Filtrar por años", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (isAll) "Todos los años" else YearRange(from, to).label(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            RangeSlider(
                value = from.toFloat()..to.toFloat(),
                onValueChange = { range ->
                    from = range.start.roundToInt().coerceIn(minYear, maxYear)
                    to = range.endInclusive.roundToInt().coerceIn(from, maxYear)
                },
                valueRange = minYear.toFloat()..maxYear.toFloat(),
                steps = 0,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CompactSpacing),
            ) {
                YearRange.presets(thisYear).forEach { (label, preset) ->
                    val start = preset.from.coerceIn(minYear, maxYear)
                    val end = preset.to.coerceIn(start, maxYear)
                    PeliFilterChip(
                        label = label,
                        selected = from == start && to == end,
                        onClick = { from = start; to = end },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                GradientButton(
                    "Aplicar",
                    { apply(if (isAll) null else YearRange(from, to)) },
                    Modifier.weight(1f),
                )
                TonalButton("Quitar filtro", { apply(null) }, enabled = current != null)
            }
        }
    }
}
