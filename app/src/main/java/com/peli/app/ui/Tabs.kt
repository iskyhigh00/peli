package com.peli.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.peli.app.data.Entry
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
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.SectionSpacing
import com.peli.app.ui.components.StatusChip
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.components.UpcomingChip
import com.peli.app.ui.components.entrance
import com.peli.app.ui.components.screenContentPadding
import com.peli.app.ui.theme.SheetShape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SearchFilter(val label: String) { All("Todas"), Favorites("Favoritas"), Seen("Vistas"), Disliked("No me gustan"), Unmarked("Sin marcar") }
private enum class MineFilter(val label: String) { All("Todas"), Favorites("Favoritas"), Seen("Vistas"), Disliked("No me gustan") }

@Composable
private fun <T : Enum<T>> ChipRow(values: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CompactSpacing),
    ) {
        values.forEach { PeliFilterChip(label(it), it == selected, { onSelect(it) }) }
    }
}

@Composable
fun SearchTab(vm: MainViewModel, listState: LazyListState, onOpen: (Entry) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(SearchFilter.All) }
    val results by vm.results.collectAsState()
    val mine by vm.mine.collectAsState()
    val error by vm.error.collectAsState()
    val loading by vm.loading.collectAsState()
    val byId = mine.associateBy { it.movieId }

    // Espera 350ms tras la última tecla antes de consultar.
    LaunchedEffect(query) {
        delay(350)
        vm.search(query)
    }

    val shown = results.filter {
        val m = byId[it.movieId]
        when (filter) {
            SearchFilter.All -> true
            SearchFilter.Favorites -> m?.favorite == true
            SearchFilter.Seen -> m?.seen == true
            SearchFilter.Disliked -> m?.disliked == true
            SearchFilter.Unmarked -> m == null
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                PeliTextField(
                    query, { query = it }, "Buscar película",
                    leadingIcon = Icons.Default.Search, onClear = { query = "" },
                )
                ChipRow(SearchFilter.entries, filter, { it.label }) { filter = it }
            }
        }
        when {
            loading && results.isEmpty() -> items(4) { MovieCardSkeleton() }
            error != null && results.isEmpty() -> item { ErrorState(error!!, onRetry = { vm.search(query) }) }
            shown.isEmpty() -> item {
                EmptyState(
                    title = "Sin resultados",
                    message = if (query.isBlank()) "Aún no hay películas con este filtro." else "No encontramos \"$query\". Prueba con otro título.",
                    icon = Icons.Default.Search,
                )
            }
            else -> items(shown, key = { it.movieId }) { e ->
                val m = byId[e.movieId]
                MovieCard(
                    e, m, onOpen = { onOpen(e) },
                    onFavorite = { vm.toggleFavorite(m ?: e) },
                    onSeen = { vm.toggleSeen(m ?: e) },
                    onDislike = { vm.toggleDislike(m ?: e) },
                    modifier = Modifier.animateItem().entrance(shown.indexOf(e)),
                )
            }
        }
    }
}

@Composable
fun DiscoverTab(vm: MainViewModel) {
    val queue by vm.queue.collectAsState()
    val loading by vm.loading.collectAsState()
    val error by vm.error.collectAsState()
    val current = queue.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(screenContentPadding()),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        when {
            current != null -> {
                AnimatedContent(
                    targetState = current,
                    contentKey = { it.movieId },
                    transitionSpec = {
                        (fadeIn(spring(Spring.DampingRatioMediumBouncy)) +
                            slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { it / 3 }) togetherWith
                            fadeOut()
                    },
                    label = "discoverCard",
                ) { e ->
                    GlassCard {
                        PosterImage(
                            e.posterPath, "Carátula de ${e.title}",
                            Modifier.fillMaxWidth().height(340.dp),
                        )
                        Spacer(Modifier.height(ItemSpacing))
                        Text(e.title, style = MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                            if (e.dateLabel.isNotBlank()) StatusChip(e.dateLabel)
                            if (e.isUpcoming) UpcomingChip()
                        }
                        Spacer(Modifier.height(CompactSpacing))
                        Text(
                            e.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ItemSpacing)) {
                    TonalButton("No", { vm.answer(current, false) }, Modifier.weight(1f), icon = Icons.Default.ThumbDown)
                    GradientButton("Sí", { vm.answer(current, true) }, Modifier.weight(1f), icon = Icons.Default.ThumbUp)
                }
                TextButton(onClick = { vm.answer(current, null) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Omitir")
                }
            }
            loading -> DiscoverSkeleton()
            error != null -> ErrorState(error!!, onRetry = vm::refillQueue)
            else -> EmptyState(
                title = "No hay más sugerencias",
                message = "Responde algunas para que la app aprenda tus gustos.",
                icon = Icons.Default.Explore,
                actionLabel = "Cargar más",
                onAction = vm::refillQueue,
            )
        }
    }
}

@Composable
fun MineTab(vm: MainViewModel, listState: LazyListState, onOpen: (Entry) -> Unit, onGoSearch: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(MineFilter.All) }
    val mine by vm.mine.collectAsState()
    val byId = mine.associateBy { it.movieId }
    val shown = mine.filter {
        when (filter) {
            MineFilter.All -> true
            MineFilter.Favorites -> it.favorite
            MineFilter.Seen -> it.seen
            MineFilter.Disliked -> it.disliked
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        item { ChipRow(MineFilter.entries, filter, { it.label }) { filter = it } }
        if (shown.isEmpty()) {
            item {
                EmptyState(
                    title = "Tu lista está vacía",
                    message = "Marca películas como favoritas o vistas y aparecerán aquí.",
                    actionLabel = "Buscar películas",
                    actionIcon = Icons.Default.Search,
                    onAction = onGoSearch,
                )
            }
        } else {
            items(shown, key = { it.movieId }) { e ->
                MovieCard(
                    e, byId[e.movieId], onOpen = { onOpen(e) },
                    onFavorite = { vm.toggleFavorite(e) },
                    onSeen = { vm.toggleSeen(e) },
                    onDislike = { vm.toggleDislike(e) },
                    modifier = Modifier.animateItem().entrance(shown.indexOf(e)),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TogetherTab(vm: MainViewModel, listState: LazyListState, onOpen: (Entry) -> Unit) {
    val matches by vm.matches.collectAsState()
    val partner by vm.partner.collectAsState()
    val mine by vm.mine.collectAsState()
    val byId = mine.associateBy { it.movieId }
    var showSheet by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = screenContentPadding(),
        verticalArrangement = Arrangement.spacedBy(ItemSpacing),
    ) {
        item {
            GlassCard {
                Text("Lista de tu pareja", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (partner.isEmpty()) "Aún no la importas." else "${partner.size} películas importadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(ItemSpacing))
                Row(horizontalArrangement = Arrangement.spacedBy(CompactSpacing)) {
                    GradientButton("Importar lista", { showSheet = true }, Modifier.weight(1f), icon = Icons.Default.Download)
                    if (partner.isNotEmpty()) TonalButton("Borrar", vm::clearPartner)
                }
            }
        }
        when {
            partner.isEmpty() -> item {
                EmptyState(
                    title = "Comparen sus listas",
                    message = "Pídele a tu pareja que comparta su código desde \"Mis pelis\" e impórtalo aquí.",
                    icon = Icons.Default.Group,
                )
            }
            matches.isEmpty() -> item {
                EmptyState(
                    title = "Nada pendiente por ahora",
                    message = "Marquen más favoritas para que aparezcan películas para ver juntos.",
                    icon = Icons.Default.Group,
                )
            }
            else -> {
                item { Text("Para ver juntos (${matches.size})", style = MaterialTheme.typography.titleLarge) }
                items(matches, key = { it.entry.movieId }) { m ->
                    MovieCard(
                        m.entry, byId[m.entry.movieId], onOpen = { onOpen(m.entry) },
                        onFavorite = { vm.toggleFavorite(byId[m.entry.movieId] ?: m.entry.copy(owner = com.peli.app.data.OWNER_ME)) },
                        onSeen = { vm.toggleSeen(byId[m.entry.movieId] ?: m.entry.copy(owner = com.peli.app.data.OWNER_ME)) },
                        onDislike = { vm.toggleDislike(byId[m.entry.movieId] ?: m.entry.copy(owner = com.peli.app.data.OWNER_ME)) },
                        badge = when {
                            m.both -> "Les gusta a las dos"
                            m.iLike -> "Te gusta a ti"
                            else -> "Le gusta a tu pareja"
                        },
                        modifier = Modifier.animateItem().entrance(matches.indexOf(m)),
                    )
                }
            }
        }
    }

    if (showSheet) ImportSheet(onDismiss = { showSheet = false }, onImport = vm::importCode)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
    ) {
        Column(
            Modifier.padding(horizontal = ScreenHorizontalPadding).padding(bottom = SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(ItemSpacing),
        ) {
            Text("Importar lista de tu pareja", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Pega el código que te compartió desde \"Mis pelis\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PeliTextField(code, { code = it }, "Código", singleLine = false, minLines = 3, onClear = { code = "" })
            GradientButton(
                "Importar",
                {
                    onImport(code)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
                },
                Modifier.fillMaxWidth(),
                icon = Icons.Default.Download,
                enabled = code.isNotBlank(),
            )
        }
    }
}
