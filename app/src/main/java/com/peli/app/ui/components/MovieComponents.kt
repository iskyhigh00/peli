package com.peli.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.peli.app.data.Entry
import com.peli.app.data.OWNER_ME
import com.peli.app.data.TmdbApi
import com.peli.app.data.stateLabel
import com.peli.app.ui.theme.PeliTheme

/** Carátula: shimmer mientras carga y crossfade de 500ms; icono si no hay imagen. `Fit` muestra la imagen completa. */
@Composable
fun PosterImage(
    path: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    contentScale: ContentScale = ContentScale.Crop,
    onClick: (() -> Unit)? = null,
) {
    var failed by remember(path) { mutableStateOf(false) }
    var loaded by remember(path) { mutableStateOf(false) }
    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Ampliar carátula", onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (path == null || failed) {
            Icon(
                Icons.Default.Movie,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp),
            )
        } else {
            if (!loaded) ShimmerBox(Modifier.fillMaxSize(), RectangleShape)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(TmdbApi.POSTER_BASE + path)
                    .crossfade(500)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Botón de alternancia con rebote de resorte al activarse. Área táctil de 48dp (IconButton). */
@Composable
fun ToggleIconButton(
    checked: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    checkedIcon: ImageVector,
    description: String,
    checkedTint: Color,
    modifier: Modifier = Modifier,
) {
    val bounce = remember { Animatable(1f) }
    var previous by remember { mutableStateOf(checked) }
    LaunchedEffect(checked) {
        if (checked && !previous) {
            bounce.snapTo(0.6f)
            bounce.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow))
        }
        previous = checked
    }
    val tint by animateColorAsState(
        if (checked) checkedTint else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "toggleTint",
    )
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (checked) checkedIcon else icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.graphicsLayer { scaleX = bounce.value; scaleY = bounce.value },
        )
    }
}

/** Etiqueta informativa (p. ej. "Les interesa a las dos"). No es interactiva: no se anuncia como botón. */
@Composable
fun StatusChip(text: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.secondary) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = accent.copy(alpha = 0.2f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** "Próximo estreno" para películas con fecha futura. */
@Composable
fun UpcomingChip(modifier: Modifier = Modifier) {
    StatusChip("Próximo estreno", modifier, accent = MaterialTheme.colorScheme.tertiary)
}

/** Filter chip con fondo y check animados al seleccionar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeliFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        leadingIcon = {
            AnimatedVisibility(
                visible = selected,
                enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = scheme.surface.copy(alpha = 0.5f),
            selectedContainerColor = scheme.primary.copy(alpha = 0.30f),
            labelColor = scheme.onSurfaceVariant,
            selectedLabelColor = scheme.onSurface,
            selectedLeadingIconColor = scheme.onSurface,
        ),
    )
}

/** Color del chip de estado: quiero verla (primary), me gustó (mint), no me gusta (rojo), vista (neutro). */
@Composable
private fun stateAccent(e: Entry): Color {
    val scheme = MaterialTheme.colorScheme
    return when {
        e.watchlist -> scheme.primary
        e.liked -> scheme.secondary
        e.disliked -> scheme.error
        else -> scheme.onSurfaceVariant
    }
}

/**
 * Tarjeta de lista con tres acciones:
 *  - marcador: "Quiero verla" (lo que se comparte con la pareja),
 *  - pulgar arriba: "La vi y me gustó" (la app aprende de esto),
 *  - pulgar abajo: "No me gusta / no me interesa".
 */
@Composable
fun MovieCard(
    entry: Entry,
    mine: Entry?,
    onOpen: () -> Unit,
    onWant: () -> Unit,
    onLiked: () -> Unit,
    onDislike: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val want = mine?.watchlist == true
    val liked = mine?.liked == true
    val disliked = mine?.disliked == true
    val state = if (badge == null) mine?.stateLabel() else null
    GlassCard(
        modifier = modifier.alpha(if (disliked) 0.6f else 1f),
        shape = MaterialTheme.shapes.large,
        contentPadding = PaddingValues(12.dp),
        onClick = onOpen,
        onClickLabel = "Ver detalle de ${entry.title}",
    ) {
        Row {
            PosterImage(
                path = entry.posterPath,
                contentDescription = "Carátula de ${entry.title}",
                modifier = Modifier.width(96.dp).aspectRatio(2f / 3f).sharedPoster(entry.movieId),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.sharedTitle(entry.movieId),
                )
                if (entry.dateLabel.isNotBlank()) {
                    Text(entry.dateLabel, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                }
                if (entry.isUpcoming || badge != null || state != null) {
                    Spacer(Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (entry.isUpcoming) UpcomingChip()
                        if (badge != null) StatusChip(badge)
                        if (state != null && mine != null) StatusChip(state, accent = stateAccent(mine))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    entry.overview.ifBlank { "Sin descripción" },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ToggleIconButton(
                        checked = want,
                        onClick = onWant,
                        icon = Icons.Outlined.BookmarkBorder,
                        checkedIcon = Icons.Filled.Bookmark,
                        description = if (want) "Quitar ${entry.title} de las que quiero ver" else "Quiero ver ${entry.title}",
                        checkedTint = scheme.primary,
                    )
                    ToggleIconButton(
                        checked = liked,
                        onClick = onLiked,
                        icon = Icons.Outlined.ThumbUp,
                        checkedIcon = Icons.Filled.ThumbUp,
                        description = if (liked) "Quitar el me gustó de ${entry.title}" else "Ya vi ${entry.title} y me gustó",
                        checkedTint = scheme.secondary,
                    )
                    ToggleIconButton(
                        checked = disliked,
                        onClick = onDislike,
                        icon = Icons.Outlined.ThumbDown,
                        checkedIcon = Icons.Filled.ThumbDown,
                        description = if (disliked) "Quitar ${entry.title} de las que no me gustan" else "No me gusta ${entry.title}",
                        checkedTint = scheme.error,
                    )
                }
            }
        }
    }
}

internal val sampleEntry = Entry(
    movieId = 157336,
    owner = OWNER_ME,
    title = "Interestelar",
    overview = "Un grupo de exploradores viaja a través de un agujero de gusano en busca de un nuevo hogar para la humanidad.",
    posterPath = null,
    year = "2014",
    watchlist = true,
)

@ThemePreviews
@Composable
private fun MovieCardPreview() {
    PeliTheme {
        BrandBackground {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MovieCard(sampleEntry, sampleEntry, {}, {}, {}, {}, badge = "Les interesa a las dos")
                MovieCard(sampleEntry.copy(watchlist = false, liked = true, seen = true), sampleEntry.copy(watchlist = false, liked = true, seen = true), {}, {}, {}, {})
                MovieCard(sampleEntry.copy(watchlist = false, disliked = true), sampleEntry.copy(watchlist = false, disliked = true), {}, {}, {}, {})
                MovieCard(sampleEntry.copy(title = "Estreno futuro", watchlist = false, year = "2099", releaseDate = "2099-01-15"), null, {}, {}, {}, {})
            }
        }
    }
}
