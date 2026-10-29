package com.peli.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.peli.app.data.Entry
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.theme.SheetShape

/** Al marcar «La vi y me gustó»: pide una nota de 1 a 10 (un toque la guarda). Se puede omitir. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatePromptSheet(entry: Entry, onRate: (Int) -> Unit, onSkip: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onSkip,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.padding(horizontal = ScreenHorizontalPadding).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("¿Qué nota le pones?", style = MaterialTheme.typography.headlineSmall)
            Text(
                "«${entry.displayTitle}» — con tu nota Peli aprende mejor qué te gusta y te recomienda cosas más parecidas.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            listOf(1..5, 6..10).forEach { range ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    range.forEach { n -> ScoreButton(n, { onRate(n) }, Modifier.weight(1f)) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("1 = floja · 10 = obra maestra", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onSkip) { Text("Omitir") }
            }
        }
    }
}

/** Botón numérico sin margen lateral: "10" cabe aunque haya cinco por fila. */
@Composable
private fun ScoreButton(n: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Nota $n" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$n",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}
