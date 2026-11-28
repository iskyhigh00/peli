package com.peli.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peli.app.data.SeriesEntry
import com.peli.app.data.SeriesFilter
import com.peli.app.data.stateLabel
import com.peli.app.ui.components.PosterImage
import com.peli.app.ui.features.SeriesFeature

/**
 * Apartado de series (en desarrollo): buscar en TMDB o ver populares, y marcar «quiero verla», «la vi» y «me gustó».
 * Con el filtro distinto de «Todas» muestra solo mis series marcadas.
 */
@Composable
fun SeriesTab(vm: MainViewModel) {
    val feature = vm.series
    val query by feature.query.collectAsState()
    val results by feature.results.collectAsState()
    val mine by feature.mine.collectAsState()
    val loading by feature.loading.collectAsState()
    val error by feature.error.collectAsState()
    var filter by rememberSaveable { mutableStateOf(SeriesFilter.All) }
    var text by remember { mutableStateOf(query) }
    LaunchedEffect(Unit) { feature.load() }

    val byId = remember(mine) { mine.associateBy { it.seriesId } }
    val shown = if (filter == SeriesFilter.All) results.map { byId[it.seriesId] ?: it } else mine.filter { filter.accepts(it) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; feature.search(it) },
            label = { Text("Buscar series") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SeriesFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (shown.isEmpty() && !loading) {
            Text(
                if (filter == SeriesFilter.All) "Sin resultados." else "Aún no marcas series en esta lista.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 16.dp),
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(shown, key = { it.seriesId }) { s -> SeriesRow(s, feature) }
        }
    }
}

@Composable
private fun SeriesRow(s: SeriesEntry, feature: SeriesFeature) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PosterImage(s.posterPath, s.title, Modifier.width(80.dp).height(120.dp))
        Column(Modifier.weight(1f)) {
            Text(s.displayTitle, style = MaterialTheme.typography.titleMedium)
            val meta = listOfNotNull(s.year.ifBlank { null }, s.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) }, s.stateLabel())
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { feature.toggleWatch(s) }) { Text(if (s.watchlist) "Quitar" else "Quiero verla") }
                TextButton(onClick = { feature.toggleSeen(s) }) { Text(if (s.seen) "No la vi" else "La vi") }
                TextButton(onClick = { feature.toggleLiked(s) }) { Text(if (s.liked) "Ya no" else "Me gustó") }
            }
        }
    }
}
