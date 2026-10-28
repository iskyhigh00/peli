package com.peli.app.ui

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peli.app.data.Entry
import com.peli.app.data.Match
import com.peli.app.data.OWNER_ME
import com.peli.app.data.RoomCodes
import com.peli.app.data.accepts
import kotlin.math.abs
import kotlin.math.sign
import com.peli.app.data.asUnmarked
import com.peli.app.ui.components.ActionButton
import com.peli.app.ui.components.CompactSpacing
import com.peli.app.ui.components.DiscoverSkeleton
import com.peli.app.ui.components.EmptyState
import com.peli.app.ui.components.ErrorState
import com.peli.app.ui.components.GlassCard
import com.peli.app.ui.components.GradientButton
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.MovieCard
import com.peli.app.ui.components.MovieCardSkeleton
import com.peli.app.ui.components.PeliFilterChip
import com.peli.app.ui.components.PeliTextField
import com.peli.app.ui.components.PosterImage
import com.peli.app.ui.components.PosterViewer
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.SectionSpacing
import com.peli.app.ui.components.StatusChip
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.components.UpcomingChip
import com.peli.app.ui.components.entrance
import com.peli.app.ui.components.screenContentPadding
import com.peli.app.ui.theme.SheetShape
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

@Composable
private fun <T : Enum<T>> ChipRow(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    leading: @Composable () -> Unit = {},
    onSelect: (T) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CompactSpacing),
    ) {
        leading()
        values.forEach { PeliFilterChip(label(it), it == selected, { onSelect(it) }) }
    }
}

/** Oculta el teclado y quita el foco del campo de texto. */
@Composable
private fun rememberDismissKeyboard(): () -> Unit {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    return remember(focus, keyboard) {
        {
            keyboard?.hide()
            focus.clearFocus()
        }
    }
}

// ---- Buscar ----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTab(vm: MainViewModel, listState: LazyListState, onOpen: (Entry) -> Unit) {
    // El texto vive en el ViewModel (sobrevive a cambiar de pestaña); aquí solo se refleja el campo.
    var text by remember { mutableStateOf(vm.searchQuery.value) }
    val filter by vm.searchFilter.collectAsState()
    val years by vm.searchYears.collectAsState()
    val movieFilters by vm.movieFilters.collectAsState()
    val loadingMore by vm.loadingMore.collectAsState()
    val feedEnd by vm.feedEnd.collectAsState()
    var showYears by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val results by vm.results.collectAsState()
    val mine by vm.mine.collectAsState()
    val error by vm.searchError.collectAsState()
    val loading by vm.searchLoading.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val byId = remember(mine) { mine.associateBy { it.movieId } }
    val feedMode = text.isBlank()
    val shown = remember(results, mine, byId, filter, feedMode, years) {
        if (feedMode && filter != SearchFilter.All && filter != SearchFilter.Unmarked) {
            // El feed solo trae películas sin marcar: para estos filtros se muestran tus marcas.
            mine.filter { filter.accepts(it) && years.accepts(it.year) }.orderedBy(MineSort.Recent)
        } else {
            results.filter { filter.accepts(byId[it.movieId]) && years.accepts(it.year) }
        }
    }
    val pullState = rememberPullToRefreshState()
    val dismissKeyboard = rememberDismissKeyboard()
    val suggestions = feedMode && (filter == SearchFilter.All || filter == SearchFilter.Unmarked) && shown.isNotEmpty()

    // Al llegar cerca del final de las sugerencias se piden más: no hace falta volver arriba para actualizar.
    LaunchedEffect(listState, suggestions) {
        if (!suggestions) return@LaunchedEffect
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }.distinctUntilChanged().filter { it }.collect { vm.loadMore() }
    }

    // Al empezar a desplazar la lista se oculta el teclado.
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.filter { it }.collect { dismissKeyboard() }
    }

    // Lo que llega a verse en pantalla se anota: sirve para decidir cuándo puede volver a salir.
    val shownIds by rememberUpdatedState(shown.map { it.movieId }.toSet())
    LaunchedEffect(listState, feedMode) {
        if (!feedMode) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Int } }
            .distinctUntilChanged()
            .collect { keys -> vm.markVisible(keys.filter { it in shownIds }) }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = vm::refreshSearch,
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                    PeliTextField(
                        value = text,
                        onValueChange = { text = it; vm.setSearchQuery(it) },
                        label = "Buscar película",
                        leadingIcon = Icons.Default.Search,
                        onClear = { text = ""; vm.setSearchQuery("") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.submitSearch(); dismissKeyboard() }),
                    )
                    ChipRow(
                        SearchFilter.entries, filter, { it.label },
                        leading = {
                            YearChip(years) { showYears = true }
                            MovieFiltersChip(movieFilters) { showFilters = true }
                            if (years != null || movieFilters.isActive) {
                                PeliFilterChip("Quitar filtros", false, vm::clearFilters)
                            }
                        },
                    ) { vm.setSearchFilter(it) }
                    if (movieFilters.isActive) {
                        Text(
                            movieFilters.summary(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (feedMode && (filter == SearchFilter.All || filter == SearchFilter.Unmarked) && shown.isNotEmpty()) {
                item {
                    Column {
                        Text("Sugerencias para ti", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Desliza hacia abajo para ver otras",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            when {
                loading && results.isEmpty() -> items(4) { MovieCardSkeleton() }
                error != null && results.isEmpty() -> item { ErrorState(error!!, onRetry = vm::submitSearch) }
                shown.isEmpty() -> item {
                    EmptyState(
                        title = "Sin resultados",
                        message = when {
                            movieFilters.isActive -> "Ninguna película cumple estos filtros. Prueba con otros o toca «Quitar filtros»."
                            years != null -> "No hay películas de ${years!!.label()} con estos filtros. Prueba con otro rango de años."
                            text.isBlank() -> "Aún no hay películas con este filtro."
                            else -> "No encontramos \"$text\". Prueba con otro título."
                        },
                        icon = Icons.Default.Search,
                    )
                }
                else -> itemsIndexed(shown, key = { _, e -> e.movieId }) { index, e ->
                    val m = byId[e.movieId]
                    MovieCard(
                        e, m, onOpen = { dismissKeyboard(); onOpen(e) },
                        onWant = { vm.toggleWant(m ?: e) },
                        onLiked = { vm.toggleLiked(m ?: e) },
                        onDislike = { vm.toggleDislike(m ?: e) },
                        modifier = Modifier.animateItem().entrance(index, e.movieId),
                    )
                }
            }
            if (suggestions) {
                item(key = "footer") {
                    Box(Modifier.fillMaxWidth().padding(vertical = SectionSpacing), contentAlignment = Alignment.Center) {
                        when {
                            loadingMore -> CircularProgressIndicator()
                            feedEnd -> Text(
                                "No hay más sugerencias por ahora. Desliza hacia abajo para actualizar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showYears) {
        YearFilterSheet(years, onApply = vm::setSearchYears, onDismiss = { showYears = false })
    }
    if (showFilters) {
        MovieFiltersSheet(
            current = movieFilters,
            omdbAvailable = vm.omdbAvailable,
            onApply = vm::setMovieFilters,
            onClearAll = vm::clearFilters,
            onDismiss = { showFilters = false },
        )
    }
}

// ---- Descubrir -------------------------------------------------------------

@Composable
fun DiscoverTab(vm: MainViewModel) {
    val queue by vm.discoverQueue.collectAsState()
    val loading by vm.queueLoading.collectAsState()
    val error by vm.queueError.collectAsState()
    val partner by vm.partner.collectAsState()
    val partnerWants = remember(partner) { partner.filter { it.watchlist }.map { it.movieId }.toSet() }
    val years by vm.searchYears.collectAsState()
    val movieFilters by vm.movieFilters.collectAsState()
    var showYears by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var zoomed by remember { mutableStateOf<Entry?>(null) }
    val current = queue.firstOrNull()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val scroll = rememberScrollState()

    // La tarjeta de arriba cuenta como mostrada una vez por presentación (no la precargada ni una recomposición).
    LaunchedEffect(current?.movieId) { current?.let(vm::onDiscoverCardShown) }

    Column(Modifier.fillMaxSize()) {
        // Los mismos filtros que en Buscar (años, puntajes y premios): valen en las dos pantallas.
        Column(
            Modifier.padding(horizontal = ScreenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(CompactSpacing),
        ) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(CompactSpacing),
            ) {
                YearChip(years) { showYears = true }
                MovieFiltersChip(movieFilters) { showFilters = true }
                if (years != null || movieFilters.isActive) {
                    PeliFilterChip("Quitar filtros", false, vm::clearFilters)
                }
            }
            if (movieFilters.isActive) {
                Text(
                    movieFilters.summary(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (current != null) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    // En horizontal o con poca altura la tarjeta no cabe junto a los botones: pasa a ser desplazable.
                    val compact = maxHeight < 520.dp
                    Column(
                        Modifier
                            .fillMaxSize()
                            .then(if (compact) Modifier.verticalScroll(scroll) else Modifier)
                            .padding(start = ScreenHorizontalPadding, end = ScreenHorizontalPadding, top = CompactSpacing)
                            .padding(bottom = 88.dp + navInset),
                        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
                    ) {
                        AnimatedContent(
                            targetState = current,
                            contentKey = { it.movieId },
                            modifier = if (compact) Modifier.fillMaxWidth() else Modifier.weight(1f).fillMaxWidth(),
                            transitionSpec = {
                                (fadeIn(spring(Spring.DampingRatioMediumBouncy)) +
                                    slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { it / 3 }) togetherWith
                                    fadeOut()
                            },
                            label = "discoverCard",
                        ) { e ->
                            GlassCard(
                                (if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize())
                                    .swipeToSkip(e.movieId) { vm.answer(e, MainViewModel.Answer.Skip) },
                                contentPadding = PaddingValues(16.dp),
                            ) {
                                DiscoverCardBody(
                                    e = e,
                                    partnerWants = e.movieId in partnerWants,
                                    poster = if (compact) Modifier.fillMaxWidth().height(260.dp) else Modifier.fillMaxWidth().weight(1f),
                                    onZoom = { zoomed = e },
                                    onSkip = { vm.answer(e, MainViewModel.Answer.Skip) },
                                )
                            }
                        }
                        // "Ya la vi y me gustó" enseña tus gustos a la app; "Quiero verla" va a la lista que se comparte.
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                            ActionButton(
                                "No me interesa", Icons.Default.ThumbDown,
                                { vm.answer(current, MainViewModel.Answer.NotInterested) }, Modifier.weight(1f),
                            )
                            ActionButton(
                                "La vi y me gustó", Icons.Default.ThumbUp,
                                { vm.answer(current, MainViewModel.Answer.SeenLiked) }, Modifier.weight(1f),
                            )
                            ActionButton(
                                "Quiero verla", Icons.Default.Bookmark,
                                { vm.answer(current, MainViewModel.Answer.Want) }, Modifier.weight(1f), gradient = true,
                            )
                        }
                    }
                }
                zoomed?.let { PosterViewer(it.posterPath, it.title) { zoomed = null } }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(screenContentPadding()),
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                ) {
                    when {
                        loading -> DiscoverSkeleton()
                        error != null -> ErrorState(error!!, onRetry = vm::refillQueue)
                        else -> EmptyState(
                            title = "No hay más sugerencias",
                            message = if (movieFilters.isActive || years != null) {
                                "Ninguna película cumple estos filtros por ahora. Prueba con otros o toca «Quitar filtros»."
                            } else "Responde algunas para que la app aprenda tus gustos.",
                            icon = Icons.Default.Explore,
                            actionLabel = "Cargar más",
                            onAction = vm::refillQueue,
                        )
                    }
                }
            }
        }
    }
    if (showYears) {
        YearFilterSheet(years, onApply = vm::setSearchYears, onDismiss = { showYears = false })
    }
    if (showFilters) {
        MovieFiltersSheet(
            current = movieFilters,
            omdbAvailable = vm.omdbAvailable,
            onApply = vm::setMovieFilters,
            onClearAll = vm::clearFilters,
            onDismiss = { showFilters = false },
        )
    }
}

/** Deslizar la tarjeta hacia cualquier lado la omite; si se suelta antes de llegar lejos, vuelve a su lugar. */
@Composable
private fun Modifier.swipeToSkip(key: Any, onSkip: () -> Unit): Modifier {
    val offset = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val skip by rememberUpdatedState(onSkip)
    var width by remember(key) { mutableFloatStateOf(1f) }
    return this
        .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
        .pointerInput(key) {
            detectHorizontalDragGestures(
                onDragEnd = {
                    scope.launch {
                        if (abs(offset.value) > width * 0.3f) {
                            offset.animateTo(sign(offset.value) * width * 1.2f, tween(160))
                            skip()
                        } else {
                            offset.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                        }
                    }
                },
                onDragCancel = { scope.launch { offset.animateTo(0f) } },
            ) { change, drag ->
                change.consume()
                scope.launch { offset.snapTo(offset.value + drag) }
            }
        }
        .graphicsLayer {
            translationX = offset.value
            rotationZ = offset.value / width * 10f
            alpha = (1f - abs(offset.value) / (width * 1.5f)).coerceIn(0.3f, 1f)
        }
}

@Composable
private fun ColumnScope.DiscoverCardBody(
    e: Entry,
    partnerWants: Boolean,
    poster: Modifier,
    onZoom: () -> Unit,
    onSkip: () -> Unit,
) {
    PosterImage(e.posterPath, "Carátula de ${e.title}", poster, contentScale = ContentScale.Fit, onClick = onZoom)
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            e.title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSkip) { Text("Omitir") }
    }
    Row(Modifier.padding(vertical = CompactSpacing), horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
        if (e.dateLabel.isNotBlank()) StatusChip(e.dateLabel)
        if (e.isUpcoming) UpcomingChip()
        if (partnerWants) StatusChip("A tu pareja le interesa", accent = MaterialTheme.colorScheme.tertiary)
    }
    Text(
        e.overview,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

// ---- Mis pelis -------------------------------------------------------------

@Composable
fun MineTab(vm: MainViewModel, listState: LazyListState, onOpen: (Entry) -> Unit, onGoSearch: () -> Unit) {
    val filter by vm.mineFilter.collectAsState()
    val sort by vm.mineSort.collectAsState()
    val years by vm.mineYears.collectAsState()
    var showYears by rememberSaveable { mutableStateOf(false) }
    val mine by vm.mine.collectAsState()
    val shown = remember(mine, filter, sort, years) {
        mine.filter { filter.accepts(it) && years.accepts(it.year) }.orderedBy(sort)
    }
    var menuOpen by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                ChipRow(
                    MineFilter.entries, filter, { it.label },
                    leading = { YearChip(years) { showYears = true } },
                ) { vm.setMineFilter(it) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${shown.size} " + if (shown.size == 1) "película" else "películas",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.foundation.layout.Box {
                        TextButton(onClick = { menuOpen = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text("Orden: ${sort.label}")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            MineSort.entries.forEach { s ->
                                DropdownMenuItem(
                                    text = { Text(s.label) },
                                    onClick = { vm.setMineSort(s); menuOpen = false },
                                    leadingIcon = if (s == sort) {
                                        { Icon(Icons.Default.Check, contentDescription = "Seleccionado") }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    title = if (mine.isEmpty()) "Tu lista está vacía" else "Nada con este filtro",
                    message = if (mine.isEmpty()) {
                        "Marca las que quieres ver y las que ya viste y te gustaron: aparecerán aquí."
                    } else {
                        "Prueba con otro filtro para ver el resto de tu lista."
                    },
                    actionLabel = if (mine.isEmpty()) "Buscar películas" else null,
                    actionIcon = Icons.Default.Search,
                    onAction = if (mine.isEmpty()) onGoSearch else null,
                )
            }
        } else {
            itemsIndexed(shown, key = { _, e -> e.movieId }) { index, e ->
                MovieCard(
                    e, e, onOpen = { onOpen(e) },
                    onWant = { vm.toggleWant(e) },
                    onLiked = { vm.toggleLiked(e) },
                    onDislike = { vm.toggleDislike(e) },
                    modifier = Modifier.animateItem().entrance(index, e.movieId),
                )
            }
        }
    }

    if (showYears) {
        YearFilterSheet(years, onApply = vm::setMineYears, onDismiss = { showYears = false })
    }
}

// ---- Juntos ----------------------------------------------------------------

private enum class TogetherSheet { None, Import, Join }
private enum class Confirm { None, Leave, ClearPartner }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TogetherTab(vm: MainViewModel, listState: LazyListState, onOpen: (Entry) -> Unit) {
    val matches by vm.matches.collectAsState()
    val partner by vm.partner.collectAsState()
    val mine by vm.mine.collectAsState()
    val room by vm.room.collectAsState()
    val profile by vm.profile.collectAsState()
    val syncError by vm.syncError.collectAsState()
    val hasProfile = profile != null
    val connected = room != null || hasProfile
    val byId = remember(mine) { mine.associateBy { it.movieId } }
    var sheet by rememberSaveable { mutableStateOf(TogetherSheet.None) }
    var confirm by rememberSaveable { mutableStateOf(Confirm.None) }
    val context = LocalContext.current
    val refreshing by vm.refreshing.collectAsState()
    val pullState = rememberPullToRefreshState()

    val both = remember(matches) { matches.filter { it.both } }
    val single = remember(matches) { matches.filter { !it.both } }
    fun mineOrNew(m: Match) = byId[m.entry.movieId] ?: m.entry.asUnmarked(OWNER_ME)

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = vm::refreshSync,
        state = pullState,
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = pullState,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = screenContentPadding(),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            if (vm.googleAvailable) item {
                GlassCard { AccountCardContent(vm, syncError) }
            }
            if (!vm.googleAvailable || !hasProfile) item {
                GlassCard {
                    when {
                        !vm.cloudAvailable -> {
                            Text("Lista de tu pareja", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (partner.isEmpty()) "Aún no la importas." else "${partner.size} películas importadas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(ItemSpacing))
                            Row(horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                                GradientButton("Importar lista", { sheet = TogetherSheet.Import }, Modifier.weight(1f), icon = Icons.Default.Download)
                                if (partner.isNotEmpty()) TonalButton("Borrar", { confirm = Confirm.ClearPartner })
                            }
                        }
                        room == null -> {
                            Text("Conecta con tu pareja", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Creen una sala o únanse con un código. Cuando a los dos les interese la misma película, será un match.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(ItemSpacing))
                            Row(horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                                GradientButton("Crear sala", vm::createRoom, Modifier.weight(1f), icon = Icons.Default.Group)
                                TonalButton("Tengo un código", { sheet = TogetherSheet.Join }, Modifier.weight(1f))
                            }
                        }
                        else -> {
                            Text("Sala ${RoomCodes.format(room!!)}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    syncError != null -> "No se pudo sincronizar: $syncError"
                                    partner.isEmpty() -> "Esperando a tu pareja… compárteles el código."
                                    else -> "Conectados · tu pareja tiene ${partner.size} películas marcadas."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (syncError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(ItemSpacing))
                            Row(horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                                GradientButton(
                                    "Compartir código",
                                    {
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, vm.roomShareText())
                                        }
                                        context.startActivity(Intent.createChooser(send, "Invitar a tu pareja"))
                                    },
                                    Modifier.weight(1f),
                                    icon = Icons.Default.Share,
                                )
                                TonalButton("Salir", { confirm = Confirm.Leave })
                            }
                        }
                    }
                }
            }
            when {
                matches.isEmpty() -> item {
                    EmptyState(
                        title = if (connected && partner.isEmpty()) "Esperando a tu pareja" else "Aún no hay matches",
                        message = if (connected) {
                            "Cuando los dos marquen «Quiero verla» en la misma película, aparecerá aquí con un match."
                        } else {
                            "Conéctate con tu pareja y marquen películas con «Quiero verla»."
                        },
                        icon = Icons.Default.Group,
                    )
                }
                else -> {
                    if (both.isNotEmpty()) {
                        item { Text("Matches (${both.size})", style = MaterialTheme.typography.titleLarge) }
                        itemsIndexed(both, key = { _, m -> m.entry.movieId }) { index, m ->
                            MovieCard(
                                m.entry, byId[m.entry.movieId], onOpen = { onOpen(m.entry) },
                                onWant = { vm.toggleWant(mineOrNew(m)) },
                                onLiked = { vm.toggleLiked(mineOrNew(m)) },
                                onDislike = { vm.toggleDislike(mineOrNew(m)) },
                                badge = "Les interesa a las dos",
                                modifier = Modifier.animateItem().entrance(index, m.entry.movieId),
                            )
                        }
                    }
                    if (single.isNotEmpty()) {
                        item { Text("A uno le interesa (${single.size})", style = MaterialTheme.typography.titleLarge) }
                        itemsIndexed(single, key = { _, m -> m.entry.movieId }) { index, m ->
                            MovieCard(
                                m.entry, byId[m.entry.movieId], onOpen = { onOpen(m.entry) },
                                onWant = { vm.toggleWant(mineOrNew(m)) },
                                onLiked = { vm.toggleLiked(mineOrNew(m)) },
                                onDislike = { vm.toggleDislike(mineOrNew(m)) },
                                badge = if (m.iWant) "Te interesa a ti" else "Le interesa a tu pareja",
                                modifier = Modifier.animateItem().entrance(index, m.entry.movieId),
                            )
                        }
                    }
                }
            }
        }
    }

    when (sheet) {
        TogetherSheet.Import -> CodeSheet(
            title = "Importar lista de tu pareja",
            help = "Pega el código que te compartió desde \"Mis pelis\".",
            field = "Código",
            action = "Importar",
            minLines = 3,
            keyboardOptions = KeyboardOptions.Default,
            onDismiss = { sheet = TogetherSheet.None },
            onSubmit = vm::importCode,
        )
        TogetherSheet.Join -> CodeSheet(
            title = "Unirme a la sala de mi pareja",
            help = "Escribe el código de 10 letras y números que te compartió.",
            field = "Código de sala",
            action = "Unirme",
            minLines = 1,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            onDismiss = { sheet = TogetherSheet.None },
            onSubmit = vm::joinRoom,
        )
        TogetherSheet.None -> Unit
    }

    when (confirm) {
        Confirm.Leave -> ConfirmDialog(
            title = "¿Salir de la sala?",
            text = "Dejarás de sincronizar con tu pareja y ya no verán los matches. Tus películas se conservan en este teléfono.",
            confirmLabel = "Salir",
            onConfirm = { confirm = Confirm.None; vm.leaveRoom() },
            onDismiss = { confirm = Confirm.None },
        )
        Confirm.ClearPartner -> ConfirmDialog(
            title = "¿Borrar la lista de tu pareja?",
            text = "Se quitará la lista importada y los matches con ella. Puedes volver a importarla cuando quieras.",
            confirmLabel = "Borrar",
            onConfirm = { confirm = Confirm.None; vm.clearPartner() },
            onDismiss = { confirm = Confirm.None },
        )
        Confirm.None -> Unit
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = scheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeSheet(
    title: String,
    help: String,
    field: String,
    action: String,
    minLines: Int,
    keyboardOptions: KeyboardOptions,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    fun submit() {
        if (code.isBlank()) return
        onSubmit(code)
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
            // imePadding: el campo y el botón suben con el teclado y no quedan tapados.
            Modifier.padding(horizontal = ScreenHorizontalPadding).padding(bottom = SectionSpacing).imePadding(),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Text(help, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PeliTextField(
                code, { code = it }, field,
                singleLine = minLines == 1, minLines = minLines, onClear = { code = "" },
                keyboardOptions = keyboardOptions,
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            GradientButton(action, { submit() }, Modifier.fillMaxWidth(), icon = Icons.Default.Download, enabled = code.isNotBlank())
        }
    }
}
