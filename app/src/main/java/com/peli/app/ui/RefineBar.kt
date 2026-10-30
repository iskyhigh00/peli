package com.peli.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peli.app.data.MovieFilters
import com.peli.app.data.YearRange
import com.peli.app.ui.components.CompactSpacing

/** Un filtro de la barra: siempre con su ícono; marcado (y mostrando su valor) cuando está activo. */
@Composable
fun RefineChip(label: String, active: Boolean, icon: ImageVector, onClick: () -> Unit) {
    FilterChip(
        selected = active,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = MaterialTheme.shapes.medium,
    )
}

/** «Película segura»: es una acción, no un filtro, y se ve distinta. */
@Composable
fun SafePickChip(onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text("Película segura", style = MaterialTheme.typography.labelLarge) },
        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = MaterialTheme.shapes.medium,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = null,
    )
}

/** Lo que está activo, en una línea: «2000–2026 · IMDb ≥ 7.5 · Director: X». Vacío si no hay nada. */
fun activeSummary(years: YearRange?, filters: MovieFilters): String =
    listOfNotNull(years?.label(), filters.summary().ifEmpty { null }).joinToString(" · ")

/**
 * Barra de filtros, siempre en el mismo orden: Filtros (puntajes y premios), Años y Director; «Limpiar» al final solo
 * si hay algo activo; debajo, el resumen de lo activo. [leading] va antes (p. ej. «Película segura» en Descubrir).
 */
@Composable
fun RefineBar(
    years: YearRange?,
    filters: MovieFilters,
    onFilters: () -> Unit,
    onYears: () -> Unit,
    onDirectors: () -> Unit,
    onClear: () -> Unit,
    leading: @Composable RowScope.() -> Unit = {},
) {
    val scoreFilters = filters.copy(directorId = 0, directorName = "").count()
    Column(verticalArrangement = Arrangement.spacedBy(CompactSpacing)) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(CompactSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            RefineChip(if (scoreFilters > 0) "Filtros ($scoreFilters)" else "Filtros", scoreFilters > 0, Icons.Default.Tune, onFilters)
            RefineChip(years?.label() ?: "Años", years != null, Icons.Default.CalendarMonth, onYears)
            RefineChip(
                if (filters.directorId > 0) filters.directorName.ifBlank { "Director" } else "Director",
                filters.directorId > 0,
                Icons.Default.Person,
                onDirectors,
            )
            if (years != null || filters.isActive) {
                TextButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Limpiar")
                }
            }
        }
        val summary = activeSummary(years, filters)
        if (summary.isNotEmpty()) {
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
