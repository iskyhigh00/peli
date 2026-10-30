package com.peli.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.net.Uri
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import com.peli.app.data.Trailers
import com.peli.app.log.AppLog
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.peli.app.data.Entry
import com.peli.app.ui.components.GlassCard
import com.peli.app.ui.components.GradientButton
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.PosterImage
import com.peli.app.ui.components.PosterViewer
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.SectionSpacing
import com.peli.app.ui.components.StatusChip
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.components.UpcomingChip
import com.peli.app.ui.components.sharedPoster
import com.peli.app.ui.components.sharedTitle

@Composable
fun DetailScreen(entry: Entry, mine: Entry?, vm: MainViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scheme = MaterialTheme.colorScheme
    val want = mine?.watchlist == true
    val liked = mine?.liked == true
    val seen = mine?.seen == true
    val disliked = mine?.disliked == true
    var zoomed by remember { mutableStateOf(false) }
    val sources by vm.sources.collectAsState()
    val insights by vm.insights.collectAsState()
    val partner by vm.partner.collectAsState()
    val partnerRating = remember(partner, entry.movieId) { partner.firstOrNull { it.movieId == entry.movieId }?.myRating ?: 0 }
    LaunchedEffect(entry.movieId) { vm.loadInsights(entry.movieId); vm.loadFacts(entry.movieId) }
    val allFacts by vm.facts.collectAsState()
    val trailers by vm.trailers.collectAsState()
    val pager = rememberPagerState(pageCount = { 2 })
    // El tráiler se pide al deslizar el cartel (página 2) y se quita al volver, para que deje de sonar.
    LaunchedEffect(pager.currentPage) { if (pager.currentPage == 1) vm.loadTrailer(entry.movieId) }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // Con el tráiler a la vista el video queda fijo arriba (se lee la ficha mientras se sigue viendo); con el cartel, se va con el scroll.
    val trailerMode = pager.currentPage == 1

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val headerHeight by animateDpAsState(
            if (trailerMode) maxWidth * 9 / 16 else maxWidth * 3 / 2,
            tween(250),
            label = "headerHeight",
        )
        Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Spacer(Modifier.height(headerHeight))
            Column(
                Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = SectionSpacing),
                verticalArrangement = Arrangement.spacedBy(SectionSpacing),
            ) {
                GlassCard {
                    Text(entry.displayTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.sharedTitle(entry.movieId))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (entry.dateLabel.isNotBlank()) StatusChip(entry.dateLabel)
                        if (entry.isUpcoming) UpcomingChip()
                    }
                    Text(
                        entry.overview.ifBlank { "Sin descripción" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = scheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = ItemSpacing),
                    )
                }
                RatingsSection(
                    state = insights[entry.movieId],
                    sources = sources,
                    tmdbFallback = entry.rating?.takeIf { it > 0 },
                    myRating = mine?.myRating ?: 0,
                    partnerRating = partnerRating,
                    omdbAvailable = vm.omdbAvailable,
                    onRate = { vm.setMyRating(mine ?: entry, it) },
                    onRetry = { vm.loadInsights(entry.movieId, force = true) },
                )
                ReviewsSection(insights[entry.movieId], sources)
                allFacts[entry.movieId]?.let { f ->
                    if (f.director != null || f.actors.isNotEmpty()) {
                        GlassCard {
                            f.director?.let { d ->
                                Text("Dirección", style = MaterialTheme.typography.titleMedium)
                                Text(d, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                            }
                            if (f.actors.isNotEmpty()) {
                                Text(
                                    "Reparto",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = if (f.director != null) ItemSpacing else 0.dp),
                                )
                                Text(f.actors.joinToString(", "), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                    GradientButton(
                        if (want) "En mi lista para ver" else "Quiero verla",
                        { vm.toggleWant(mine ?: entry) },
                        Modifier.fillMaxWidth(),
                        icon = if (want) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                    )
                    TonalButton(
                        if (liked) "La vi y me gustó" else "Ya la vi y me gustó",
                        { vm.toggleLiked(mine ?: entry) },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Default.ThumbUp,
                    )
                    if (!liked) {
                        TonalButton(
                            if (seen) "La vi (sin opinión)" else "Ya la vi, sin opinión",
                            { vm.toggleSeen(mine ?: entry) },
                            Modifier.fillMaxWidth(),
                            icon = Icons.Default.Check,
                        )
                    }
                    TonalButton(
                        if (disliked) "Marcada como no me gusta" else "No me gusta",
                        { vm.toggleDislike(mine ?: entry) },
                        Modifier.fillMaxWidth(),
                        icon = Icons.Default.ThumbDown,
                    )
                }
                Box(Modifier.height(32.dp))
            }
        }
        // Encabezado (cartel / tráiler): va encima del contenido; solo con el tráiler se queda fijo.
        Box(
            Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .graphicsLayer { translationY = if (trailerMode) 0f else -scroll.value.toFloat() }
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta -> scope.launch { scroll.scrollBy(-delta) } },
                )
                .sharedPoster(entry.movieId),
        ) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                if (page == 0) {
                    PosterImage(
                        path = entry.posterPath,
                        contentDescription = "Carátula de ${entry.title}",
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                        onClick = { zoomed = true },
                    )
                } else {
                    TrailerPage(
                        state = trailers[entry.movieId],
                        active = pager.currentPage == 1,
                        onRetry = { vm.loadTrailer(entry.movieId, force = true) },
                    )
                }
            }
            // Puntos de página (arriba con el tráiler, para no tapar sus controles) y pista de que se puede deslizar.
            Column(
                Modifier.align(if (trailerMode) Alignment.TopCenter else Alignment.BottomCenter).padding(vertical = if (trailerMode) 10.dp else 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (pager.currentPage == 0) {
                    Surface(shape = RoundedCornerShape(50), color = Color.Black.copy(alpha = 0.55f)) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("Desliza para ver el tráiler", style = MaterialTheme.typography.labelMedium, color = Color.White)
                        }
                    }
                    Box(Modifier.height(8.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(2) { i ->
                        Box(
                            Modifier.size(7.dp).clip(CircleShape)
                                .background(if (i == pager.currentPage) Color.White else Color.White.copy(alpha = 0.4f)),
                        )
                    }
                }
            }
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(12.dp)
                .clip(CircleShape)
                .background(scheme.surface.copy(alpha = 0.6f))
                .align(Alignment.TopStart),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
        if (zoomed) PosterViewer(entry.posterPath, entry.title) { zoomed = false }
    }
}

/** Página del tráiler: se reproduce aquí mismo (reproductor de YouTube incrustado). */
@Composable
private fun TrailerPage(state: MainViewModel.TrailerState?, active: Boolean, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            null, MainViewModel.TrailerState.Loading -> CircularProgressIndicator(color = Color.White)
            MainViewModel.TrailerState.None -> Text(
                "Esta película no tiene tráiler disponible.",
                color = Color.White,
                modifier = Modifier.padding(24.dp),
            )
            is MainViewModel.TrailerState.Failed -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Text("No se pudo cargar el tráiler.", color = Color.White)
                Box(Modifier.height(8.dp))
                TonalButton("Reintentar", onRetry)
            }
            is MainViewModel.TrailerState.Ready -> {
                if (active) TrailerPlayer(state.key)
            }
        }
    }
}

/**
 * Reproductor de YouTube dentro de la app. Si YouTube no deja reproducir ese video aquí (el dueño lo prohíbe fuera de
 * YouTube), se explica y solo entonces se ofrece abrirlo en YouTube.
 */
@Composable
private fun TrailerPlayer(key: String) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var problem by remember(key) { mutableStateOf<String?>(null) }
    if (problem != null) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Text(problem.orEmpty(), color = Color.White)
            Box(Modifier.height(12.dp))
            TonalButton(
                "Abrir en YouTube",
                {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Trailers.watchUrl(key))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        AppLog.w("Tráiler", "no se pudo abrir YouTube", e)
                    }
                },
            )
        }
        return
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            YouTubePlayerView(ctx).apply {
                enableAutomaticInitialization = false
                lifecycleOwner.lifecycle.addObserver(this)
                // El botón de pantalla completa no está cableado: se oculta. El origen lo pone la biblioteca (el paquete de la app).
                val options = IFramePlayerOptions.Builder(ctx).controls(1).rel(0).fullscreen(0).build()
                initialize(
                    object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            youTubePlayer.loadVideo(key, 0f)
                        }

                        override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                            AppLog.w("Tráiler", "YouTube no reprodujo $key: $error")
                            problem = when (error) {
                                PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
                                    "Su dueño no permite reproducir este tráiler fuera de YouTube."
                                PlayerConstants.PlayerError.VIDEO_NOT_FOUND -> "Este tráiler ya no existe en YouTube."
                                PlayerConstants.PlayerError.REQUEST_MISSING_HTTP_REFERER ->
                                    "YouTube rechazó la reproducción dentro de la app."
                                else -> "No se pudo reproducir el tráiler."
                            }
                        }
                    },
                    true,
                    options,
                )
            }
        },
        onRelease = { view ->
            lifecycleOwner.lifecycle.removeObserver(view)
            view.release()
        },
    )
}
