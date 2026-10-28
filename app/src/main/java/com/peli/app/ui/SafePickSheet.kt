package com.peli.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.peli.app.data.Entry
import com.peli.app.ui.components.ActionButton
import com.peli.app.ui.components.CompactSpacing
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.PosterImage
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.StatusChip
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.theme.SheetShape

/**
 * «Película segura»: la que más probablemente me guste según mis notas, con los motivos. Se puede marcar como
 * ya vista (y se pide la nota), quererla, descartarla o pedir otra. Es una estimación, no una garantía.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafePickSheet(
    state: MainViewModel.SafeState,
    onAnswer: (Entry, MainViewModel.SafeAnswer) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
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
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Text("Película segura", style = MaterialTheme.typography.headlineSmall)
            when (state) {
                MainViewModel.SafeState.Idle, MainViewModel.SafeState.Loading -> {
                    Text(
                        "Buscando la que más se parece a lo que calificaste alto…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CircularProgressIndicator(Modifier.padding(vertical = 24.dp).align(androidx.compose.ui.Alignment.CenterHorizontally))
                }
                is MainViewModel.SafeState.NeedMore -> Text(
                    "Necesito conocer mejor tus gustos: califica o marca «La vi y me gustó» en al menos ${state.need} películas " +
                        "(tienes ${state.have}) y vuelve a intentarlo.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                MainViewModel.SafeState.Empty -> {
                    Text(
                        "No encontré más películas que cumplan, por ahora. Califica más películas o inténtalo más tarde.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TonalButton("Buscar de nuevo", onRetry)
                }
                is MainViewModel.SafeState.Failed -> {
                    Text("No se pudo calcular: ${state.message}", style = MaterialTheme.typography.bodyMedium)
                    TonalButton("Reintentar", onRetry)
                }
                is MainViewModel.SafeState.Ready -> {
                    val e = state.pick.entry
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PosterImage(
                            e.posterPath, "Carátula de ${e.title}",
                            Modifier.width(120.dp).height(180.dp),
                            contentScale = ContentScale.Crop,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                            Text(e.title, style = MaterialTheme.typography.titleLarge)
                            if (e.dateLabel.isNotBlank()) StatusChip(e.dateLabel)
                            Text(
                                e.overview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 6,
                            )
                        }
                    }
                    Text("Por qué creemos que te gustará", style = MaterialTheme.typography.titleMedium)
                    state.pick.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        "Es una estimación según tus notas; no podemos garantizarlo.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                        ActionButton("Ya la vi", Icons.Default.Check, { onAnswer(e, MainViewModel.SafeAnswer.Seen) }, Modifier.weight(1f))
                        ActionButton("No me interesa", Icons.Default.ThumbDown, { onAnswer(e, MainViewModel.SafeAnswer.NotInterested) }, Modifier.weight(1f))
                        ActionButton("Quiero verla", Icons.Default.Bookmark, { onAnswer(e, MainViewModel.SafeAnswer.Want) }, Modifier.weight(1f), gradient = true)
                    }
                    TonalButton("Otra opción", { onAnswer(e, MainViewModel.SafeAnswer.Another) }, Modifier.fillMaxWidth())
                }
            }
        }
    }
}
