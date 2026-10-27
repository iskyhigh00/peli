package com.peli.app.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peli.app.data.Entry
import com.peli.app.log.AppLog
import com.peli.app.ui.components.AnimatedNavigationBar
import com.peli.app.ui.components.BrandBackground
import com.peli.app.ui.components.CrashReportDialog
import com.peli.app.ui.components.LocalShared
import com.peli.app.ui.components.MatchOverlay
import com.peli.app.ui.components.NavItem
import com.peli.app.ui.components.SharedScopes
import com.peli.app.ui.components.SystemBarScrims
import com.peli.app.ui.components.UpdateBanner
import com.peli.app.ui.components.WhatsNewSheet
import com.peli.app.ui.theme.PeliTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Tab(val label: String, val icon: ImageVector, val title: String) {
    Search("Buscar", Icons.Default.Search, "Buscar"),
    Discover("Descubrir", Icons.Default.Explore, "Descubrir"),
    Mine("Mis pelis", Icons.Default.Movie, "Mis pelis"),
    Together("Juntos", Icons.Default.Group, "Juntos"),
}

/**
 * Raíz de la app. Aquí vive todo lo que debe sobrevivir a cambiar de pestaña, abrir una película o rotar:
 * la pestaña actual y la película abierta (rememberSaveable), mientras que búsqueda, filtros y orden
 * viven en el ViewModel (SavedStateHandle).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PeliScreen(vm: MainViewModel = viewModel()) {
    PeliTheme {
        var tab by rememberSaveable { mutableStateOf(Tab.Search) }
        var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
        val mine by vm.mine.collectAsState()
        val partner by vm.partner.collectAsState()
        val results by vm.results.collectAsState()
        val queue by vm.queue.collectAsState()
        val pendingMatches by vm.pendingMatches.collectAsState()
        val saveableStateHolder = rememberSaveableStateHolder()
        val found = remember(selectedId, mine, partner, results, queue) { selectedId?.let(vm::lookup) }
        // Si la película deja de estar en la lista de la que se abrió (p. ej. le quitas la marca desde "Mis pelis"),
        // el detalle sigue mostrando la última copia en vez de cerrarse solo.
        val lastSelected = remember { arrayOfNulls<Entry>(1) }
        if (found != null) lastSelected[0] = found
        val selected = found ?: lastSelected[0]?.takeIf { it.movieId == selectedId }
        val crashReport by vm.crashReport.collectAsState()
        val context = LocalContext.current

        // Sincroniza con la sala cada 6 s, solo mientras la app está visible.
        val syncActive by vm.syncActive.collectAsState()
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(syncActive, lifecycleOwner) {
            if (syncActive) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) {
                        vm.sync()
                        delay(6000)
                    }
                }
            }
        }

        LaunchedEffect(tab) { AppLog.i("Navegación", "pestaña: ${tab.name}") }
        LaunchedEffect(selectedId) {
            AppLog.i("Navegación", if (selectedId == null) "detalle cerrado" else "detalle abierto: película $selectedId")
        }

        // Atrás desde otra pestaña vuelve a la primera; recién ahí sale de la app.
        BackHandler(enabled = tab != Tab.Search && selected == null && pendingMatches.isEmpty()) {
            AppLog.d("Navegación", "Atrás: de ${tab.name} a Search")
            tab = Tab.Search
        }

        BrandBackground {
            SharedTransitionLayout {
                AnimatedContent(
                    targetState = selected,
                    contentKey = { it?.movieId },
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                    label = "detailTransition",
                ) { entry ->
                    CompositionLocalProvider(
                        LocalShared provides SharedScopes(this@SharedTransitionLayout, this@AnimatedContent),
                    ) {
                        if (entry == null) {
                            // Conserva pestañas, scroll y barra superior mientras se mira una película.
                            saveableStateHolder.SaveableStateProvider("home") {
                                Home(
                                    vm = vm,
                                    tab = tab,
                                    onTabChange = { tab = it },
                                    // Un toque rápido sobre otra tarjeta durante la transición se ignora.
                                    onOpen = { if (selectedId == null) selectedId = it.movieId },
                                )
                            }
                        } else {
                            DetailScreen(
                                entry = entry,
                                mine = mine.firstOrNull { it.movieId == entry.movieId },
                                vm = vm,
                                onBack = { selectedId = null },
                            )
                        }
                    }
                }
            }

            crashReport?.let { report ->
                CrashReportDialog(
                    report = report,
                    onShare = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, report)
                        }
                        context.startActivity(Intent.createChooser(send, "Enviar informe del cierre"))
                    },
                    onDismiss = vm::dismissCrashReport,
                )
            }

            // Por encima de todo (también del detalle). Atrás lo cierra primero.
            pendingMatches.firstOrNull()?.let { match ->
                MatchOverlay(
                    entry = match,
                    more = pendingMatches.size - 1,
                    onKeep = vm::dismissMatch,
                    onSeeList = {
                        selectedId = null
                        tab = Tab.Together
                        vm.dismissMatch()
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(vm: MainViewModel, tab: Tab, onTabChange: (Tab) -> Unit, onOpen: (Entry) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    val mine by vm.mine.collectAsState()
    val matches by vm.matches.collectAsState()
    val message by vm.message.collectAsState()
    val undo by vm.undo.collectAsState()
    val newVersion by vm.newVersion.collectAsState()
    val progress by vm.progress.collectAsState()
    val whatsNew by vm.whatsNew.collectAsState()
    val sources by vm.sources.collectAsState()
    var showSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.message.value = null }
    }
    // Respuesta equivocada en "Descubrir": se puede deshacer unos segundos.
    LaunchedEffect(undo) {
        val offer = undo ?: return@LaunchedEffect
        val result = snack.showSnackbar(offer.message, actionLabel = "Deshacer", duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) vm.undoAnswer() else vm.clearUndo()
    }

    val searchState = rememberLazyListState()
    val mineState = rememberLazyListState()
    val togetherState = rememberLazyListState()
    val behaviors = Tab.entries.map { TopAppBarDefaults.exitUntilCollapsedScrollBehavior() }
    val scrollBehavior = behaviors[tab.ordinal]

    val activeList = when (tab) {
        Tab.Search -> searchState
        Tab.Mine -> mineState
        Tab.Together -> togetherState
        Tab.Discover -> null
    }
    val scrolled by remember(activeList) {
        derivedStateOf {
            activeList != null && (activeList.firstVisibleItemIndex > 0 || activeList.firstVisibleItemScrollOffset > 0)
        }
    }
    val canScrollMore by remember(activeList) { derivedStateOf { activeList?.canScrollForward == true } }
    val topScrim by animateFloatAsState(if (scrolled) 0.85f else 0f, tween(250), label = "topScrim")
    val bottomScrim by animateFloatAsState(if (canScrollMore) 0.7f else 0f, tween(250), label = "bottomScrim")

    // Con el teclado abierto se ocultan la barra y el botón flotante para no tapar ni estorbar al escribir.
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val imeVisible by remember { derivedStateOf { ime.getBottom(density) > 0 } }

    val items = remember { Tab.entries.map { NavItem(it.label, it.icon) } }
    val subtitle = when (tab) {
        Tab.Search -> "Encuentra tu próxima película"
        Tab.Discover -> "Cuéntanos qué te gusta"
        Tab.Mine -> "${mine.size} en tu lista"
        Tab.Together -> "${matches.size} para ver con tu pareja"
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Column {
                            Text(tab.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                            AnimatedVisibility(visible = scrollBehavior.state.collapsedFraction < 0.5f) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = scheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                        }
                        IconButton(onClick = { vm.checkForUpdate(manual = true) }) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "Buscar actualización")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = scheme.surface.copy(alpha = 0.8f),
                        titleContentColor = scheme.onBackground,
                        actionIconContentColor = scheme.onSurfaceVariant,
                    ),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                AnimatedVisibility(
                    visible = newVersion != null,
                    enter = fadeIn(spring(Spring.DampingRatioMediumBouncy)) +
                        slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { -it },
                    exit = fadeOut() + slideOutVertically { -it },
                ) {
                    UpdateBanner(
                        version = newVersion.orEmpty(),
                        progress = progress,
                        onUpdate = vm::installUpdate,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                Box(Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            (fadeIn(spring(Spring.DampingRatioMediumBouncy)) +
                                slideInVertically(spring(Spring.DampingRatioMediumBouncy)) { it / 3 }) togetherWith
                                fadeOut(tween(120))
                        },
                        label = "tabContent",
                    ) { t ->
                        when (t) {
                            Tab.Search -> SearchTab(vm, searchState, onOpen)
                            Tab.Discover -> DiscoverTab(vm)
                            Tab.Mine -> MineTab(vm, mineState, onOpen, onGoSearch = { onTabChange(Tab.Search) })
                            Tab.Together -> TogetherTab(vm, togetherState, onOpen)
                        }
                    }
                }
            }
        }

        SystemBarScrims(topAlpha = topScrim, bottomAlpha = bottomScrim)

        // FAB extendido: se colapsa a FAB normal al hacer scroll.
        AnimatedVisibility(
            visible = (tab == Tab.Search || tab == Tab.Mine) && !imeVisible,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp).navigationBarsPadding().padding(bottom = 88.dp),
            enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            val share = tab == Tab.Mine
            val label = if (share) "Compartir" else "Sorpréndeme"
            ExtendedFloatingActionButton(
                text = { Text(label) },
                icon = { Icon(if (share) Icons.Default.Share else Icons.Default.Explore, contentDescription = label) },
                onClick = {
                    if (share) {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, vm.exportCode())
                        }
                        context.startActivity(Intent.createChooser(send, "Enviar mi lista a mi pareja"))
                    } else {
                        onTabChange(Tab.Discover)
                    }
                },
                expanded = !scrolled,
                containerColor = scheme.secondary,
                contentColor = scheme.onSecondary,
                shape = MaterialTheme.shapes.large,
            )
        }

        AnimatedVisibility(
            visible = !imeVisible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            AnimatedNavigationBar(
                items = items,
                selectedIndex = tab.ordinal,
                onSelect = { index ->
                    val target = Tab.entries[index]
                    if (target == tab) {
                        // Tocar la pestaña actual lleva la lista arriba (y despliega la barra superior).
                        scope.launch {
                            scrollBehavior.state.heightOffset = 0f
                            activeList?.animateScrollToItem(0)
                        }
                    } else {
                        onTabChange(target)
                    }
                },
            )
        }

        SnackbarHost(
            snack,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (imeVisible) 16.dp else 88.dp),
        )
    }

    val ratePrompt by vm.ratePrompt.collectAsState()
    ratePrompt?.let { RatePromptSheet(it, onRate = vm::rateFromPrompt, onSkip = vm::dismissRatePrompt) }

    if (whatsNew.isNotEmpty()) WhatsNewSheet(whatsNew, onDismiss = vm::dismissWhatsNew)
    if (showSettings) {
        SettingsSheet(
            sources = sources,
            omdbAvailable = vm.omdbAvailable,
            onToggle = vm::toggleSource,
            onCheckUpdate = { vm.checkForUpdate(manual = true) },
            onMessage = { vm.message.value = it },
            onDismiss = { showSettings = false },
        )
    }
}
