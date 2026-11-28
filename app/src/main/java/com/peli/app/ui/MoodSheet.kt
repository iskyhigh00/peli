package com.peli.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.peli.app.data.Entry
import com.peli.app.data.Match
import com.peli.app.data.Mood
import com.peli.app.data.moodCounts
import com.peli.app.data.moodPicks
import com.peli.app.ui.components.GradientButton
import com.peli.app.ui.components.PosterImage
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.theme.SheetShape

/**
 * «Ver una peli»: pregunta el mood y propone, de las candidatas de Juntos, las que encajan. Por defecto solo las
 * disponibles (ya las tienes); se puede ampliar a todas. También puede elegir una al azar.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoodSheet(
    matches: List<Match>,
    available: Set<Int>,
    onOpen: (Entry) -> Unit,
    onDismiss: () -> Unit,
) {
    var mood by rememberSaveable { mutableStateOf<Mood?>(null) }
    var onlyAvailable by rememberSaveable { mutableStateOf(true) }
    var luckyId by rememberSaveable { mutableStateOf<Int?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(bottom = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Solo las que ya tengo", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (onlyAvailable) "Disponibles en tu servidor o marcadas como disponibles" else "Todas las candidatas de Juntos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = onlyAvailable, onCheckedChange = { onlyAvailable = it; luckyId = null })
            }
            val current = mood
            if (current == null) {
                Text("¿Qué mood tienen?", style = MaterialTheme.typography.titleLarge)
                val counts = remember(matches, available, onlyAvailable) { moodCounts(matches, available, onlyAvailable) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mood.entries.forEach { m ->
                        FilterChip(
                            selected = false,
                            enabled = (counts[m] ?: 0) > 0,
                            onClick = { mood = m; luckyId = null },
                            label = { Text("${m.label} (${counts[m] ?: 0})") },
                        )
                    }
                }
                if (counts[Mood.Cualquiera] == 0) {
                    Text(
                        if (onlyAvailable) "Ninguna de sus candidatas está disponible todavía. Apaga «Solo las que ya tengo» para ver todas."
                        else "Aún no hay candidatas: marquen «Quiero verla» en Juntos.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val picks = remember(matches, available, current, onlyAvailable) { moodPicks(matches, available, current, onlyAvailable) }
                Text("Mood: ${current.label}", style = MaterialTheme.typography.titleLarge)
                if (picks.isEmpty()) {
                    Text("Con ese mood no hay opciones.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    val lucky = picks.firstOrNull { it.entry.movieId == luckyId }
                    val shown = if (lucky != null) listOf(lucky) else picks
                    shown.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(m.entry); onDismiss() },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PosterImage(m.entry.posterPath, m.entry.title, Modifier.width(64.dp).height(96.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.entry.displayTitle, style = MaterialTheme.typography.titleMedium)
                                val meta = listOfNotNull(
                                    m.entry.year.ifBlank { null },
                                    m.entry.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                                    if (m.both) "Les interesa a los dos" else if (m.iWant) "La quieres tú" else "La quiere ${PartnerName.cap}",
                                    if (m.entry.movieId in available) "Disponible" else null,
                                )
                                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (picks.size > 1) {
                        GradientButton(
                            if (lucky == null) "Elegir una al azar" else "Otra al azar",
                            { luckyId = picks.filter { it.entry.movieId != luckyId }.random().entry.movieId },
                            Modifier.fillMaxWidth(),
                        )
                    }
                    if (lucky != null) TonalButton("Ver todas las opciones", { luckyId = null }, Modifier.fillMaxWidth())
                }
                TonalButton("Cambiar mood", { mood = null; luckyId = null }, Modifier.fillMaxWidth())
            }
        }
    }
}
