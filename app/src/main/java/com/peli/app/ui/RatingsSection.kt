package com.peli.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peli.app.data.InsightsState
import com.peli.app.data.MovieInsights
import com.peli.app.data.RatingSource
import com.peli.app.data.Ratings
import com.peli.app.data.Review
import com.peli.app.ui.components.CompactSpacing
import com.peli.app.ui.components.GlassCard
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.ShimmerBox
import com.peli.app.ui.components.StatusChip
import com.peli.app.ui.components.ThemePreviews
import com.peli.app.ui.theme.PeliTheme
import kotlin.math.roundToInt

private data class RatingLine(val label: String, val value: String, val detail: String?, val accent: Color)

@Composable
private fun RatingRow(line: RatingLine) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.width(132.dp), shape = MaterialTheme.shapes.small, color = line.accent.copy(alpha = 0.2f)) {
            Text(
                line.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Text(line.value, style = MaterialTheme.typography.titleLarge)
        if (line.detail != null) {
            Text(line.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Las filas de las fuentes elegidas que sí tienen dato. */
@Composable
private fun linesFor(data: MovieInsights, sources: Set<RatingSource>): List<RatingLine> {
    val scheme = MaterialTheme.colorScheme
    return buildList {
        if (RatingSource.Tmdb in sources && data.tmdbRating != null) {
            add(RatingLine("TMDB", "${Ratings.formatScore(data.tmdbRating)}/10", "· ${Ratings.formatVotes(data.tmdbVotes)} votos", scheme.secondary))
        }
        if (RatingSource.Imdb in sources && data.imdbRating != null) {
            val votes = data.imdbVotes?.let { "· ${Ratings.formatVotes(it)}" }
            add(RatingLine("IMDb", "${Ratings.formatScore(data.imdbRating)}/10", votes, scheme.primary))
        }
        if (RatingSource.RottenTomatoes in sources && data.rottenTomatoes != null) {
            add(RatingLine("Rotten Tomatoes", "${data.rottenTomatoes}%", "· crítica", scheme.tertiary))
        }
        if (RatingSource.Metacritic in sources && data.metacritic != null) {
            add(RatingLine("Metacritic", "${data.metacritic}/100", null, scheme.onSurfaceVariant))
        }
    }
}

/**
 * Valoraciones de la película por fuente (las que el usuario eligió en Ajustes), mi nota y la de mi pareja.
 * Mientras carga muestra la nota de TMDB que ya venía en la lista, para no dejar el cuadro vacío.
 */
@Composable
fun RatingsSection(
    state: InsightsState?,
    sources: Set<RatingSource>,
    tmdbFallback: Double?,
    myRating: Int,
    partnerRating: Int,
    omdbAvailable: Boolean,
    onRate: (Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    GlassCard(modifier) {
        Text("Valoraciones", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(ItemSpacing))
        Column(verticalArrangement = Arrangement.spacedBy(CompactSpacing)) {
            when (state) {
                is InsightsState.Ready -> {
                    val lines = linesFor(state.data, sources)
                    lines.forEach { RatingRow(it) }
                    if (lines.isEmpty()) {
                        val needsKey = !omdbAvailable && sources.any { it.needsOmdb }
                        Text(
                            if (needsKey) "IMDb, Rotten Tomatoes y Metacritic necesitan la clave gratuita de OMDb (OMDB_API_KEY)."
                            else "No hay valoraciones de las fuentes elegidas. Puedes cambiarlas en Ajustes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                is InsightsState.Failed -> {
                    if (RatingSource.Tmdb in sources && tmdbFallback != null) {
                        RatingRow(RatingLine("TMDB", "${Ratings.formatScore(tmdbFallback)}/10", null, scheme.secondary))
                    }
                    Text("No se pudieron cargar todas las valoraciones.", style = MaterialTheme.typography.bodySmall, color = scheme.error)
                    TextButton(onClick = onRetry) { Text("Reintentar") }
                }
                else -> {
                    if (RatingSource.Tmdb in sources && tmdbFallback != null) {
                        RatingRow(RatingLine("TMDB", "${Ratings.formatScore(tmdbFallback)}/10", null, scheme.secondary))
                    }
                    ShimmerBox(Modifier.fillMaxWidth(0.6f).height(28.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.45f).height(28.dp))
                }
            }

            if (RatingSource.Partner in sources && partnerRating > 0) {
                RatingRow(RatingLine("Mi pareja", "$partnerRating/10", null, scheme.tertiary))
            }
        }
        Spacer(Modifier.height(ItemSpacing))
        MyRating(myRating, onRate)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyRating(rating: Int, onRate: (Int) -> Unit) {
    var draft by remember(rating) { mutableFloatStateOf(rating.toFloat()) }
    val shown = draft.roundToInt()
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mi nota", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                if (shown == 0) "Sin calificar" else "$shown/10",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onRate(draft.roundToInt()) },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            modifier = Modifier.semantics { contentDescription = "Mi nota de 0 a 10" },
        )
        if (rating > 0) TextButton(onClick = { onRate(0) }) { Text("Quitar mi nota") }
    }
}

/** Reseñas escritas por usuarios de TMDB (si esa fuente está activa y hay alguna). */
@Composable
fun ReviewsSection(state: InsightsState?, sources: Set<RatingSource>, modifier: Modifier = Modifier) {
    if (RatingSource.Reviews !in sources) return
    val reviews = (state as? InsightsState.Ready)?.data?.reviews.orEmpty()
    if (reviews.isEmpty()) return
    GlassCard(modifier) {
        Text("Reseñas de usuarios", style = MaterialTheme.typography.titleMedium)
        Text(
            "TMDB · suelen estar en inglés",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(ItemSpacing))
        Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
            reviews.forEach { ReviewItem(it) }
        }
    }
}

@Composable
private fun ReviewItem(review: Review) {
    var expanded by rememberSaveable(review.author, review.date) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                review.author,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            review.rating?.let { StatusChip("$it/10") }
            if (review.date.isNotBlank()) {
                Text(review.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            review.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (review.text.length > 220) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ver menos" else "Ver más") }
        }
    }
}

@ThemePreviews
@Composable
private fun RatingsPreview() {
    PeliTheme {
        com.peli.app.ui.components.BrandBackground {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                val data = MovieInsights(7.4, 12_345, 7.2, 2_300, 91, 73, listOf(Review("ana", 9, "Una película que te deja pensando durante días. ".repeat(6), "2026-01-02")))
                RatingsSection(InsightsState.Ready(data), RatingSource.entries.toSet(), 7.4, 8, 9, true, {}, {})
                ReviewsSection(InsightsState.Ready(data), RatingSource.entries.toSet())
            }
        }
    }
}
