package com.peli.app.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.peli.app.data.Entry
import com.peli.app.data.Match
import com.peli.app.data.TmdbApi

private enum class Tab(val label: String) { Search("Buscar"), Discover("Descubrir"), Mine("Mis pelis"), Together("Juntos") }

@Composable
fun PeliScreen(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(Tab.Search) }
    val snack = remember { SnackbarHostState() }
    val message by vm.message.collectAsState()
    LaunchedEffect(message) {
        message?.let { snack.showSnackbar(it); vm.message.value = null }
    }

    MaterialTheme {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = {
                                Icon(
                                    when (t) {
                                        Tab.Search -> Icons.Default.Search
                                        Tab.Discover -> Icons.Default.Explore
                                        Tab.Mine -> Icons.Default.Movie
                                        Tab.Together -> Icons.Default.Group
                                    }, null
                                )
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    Tab.Search -> SearchTab(vm)
                    Tab.Discover -> DiscoverTab(vm)
                    Tab.Mine -> MineTab(vm)
                    Tab.Together -> TogetherTab(vm)
                }
            }
        }
    }
}

@Composable
private fun MovieRow(entry: Entry, mine: Entry?, vm: MainViewModel, extra: String? = null) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.padding(8.dp)) {
            Poster(entry.posterPath, Modifier.width(90.dp).height(135.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "${entry.title}${if (entry.year.isNotBlank()) " (${entry.year})" else ""}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (extra != null) Text(extra, color = MaterialTheme.colorScheme.primary)
                Text(
                    entry.overview.ifBlank { "Sin descripción" },
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row {
                    IconButton(onClick = { vm.toggleFavorite(mine ?: entry) }) {
                        Icon(
                            if (mine?.favorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            "Favorita",
                            tint = if (mine?.favorite == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    IconButton(onClick = { vm.toggleSeen(mine ?: entry) }) {
                        Icon(
                            if (mine?.seen == true) Icons.Default.Check else Icons.Outlined.CheckCircle,
                            "Ya la vi",
                            tint = if (mine?.seen == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(path: String?, modifier: Modifier) {
    AsyncImage(
        model = path?.let { TmdbApi.POSTER_BASE + it },
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(MaterialTheme.shapes.small),
    )
}

@Composable
private fun SearchTab(vm: MainViewModel) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by vm.results.collectAsState()
    val mine by vm.mine.collectAsState()
    val error by vm.error.collectAsState()
    val loading by vm.loading.collectAsState()
    val byId = mine.associateBy { it.movieId }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it; vm.search(it) },
            label = { Text("Buscar película") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
        if (loading && results.isEmpty()) Text("Cargando…", Modifier.padding(12.dp))
        LazyColumn {
            items(results, key = { it.movieId }) { MovieRow(it, byId[it.movieId], vm) }
        }
    }
}

@Composable
private fun DiscoverTab(vm: MainViewModel) {
    val queue by vm.queue.collectAsState()
    val loading by vm.loading.collectAsState()
    val current = queue.firstOrNull()

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("¿Te gusta?", style = MaterialTheme.typography.headlineSmall)
        Text("La app aprende de tus respuestas para sugerirte más.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        if (current == null) {
            Text(if (loading) "Buscando sugerencias…" else "No hay más sugerencias por ahora.")
            Button(onClick = vm::refillQueue) { Text("Cargar más") }
        } else {
            Poster(current.posterPath, Modifier.height(320.dp).fillMaxWidth(0.6f))
            Spacer(Modifier.height(8.dp))
            Text("${current.title} (${current.year})", style = MaterialTheme.typography.titleLarge)
            Text(current.overview, maxLines = 5, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { vm.answer(current, false) }) {
                    Icon(Icons.Default.ThumbDown, null); Spacer(Modifier.width(6.dp)); Text("No")
                }
                TextButton(onClick = { vm.answer(current, null) }) { Text("Omitir") }
                Button(onClick = { vm.answer(current, true) }) {
                    Icon(Icons.Default.ThumbUp, null); Spacer(Modifier.width(6.dp)); Text("Sí")
                }
            }
        }
    }
}

@Composable
private fun MineTab(vm: MainViewModel) {
    val mine by vm.mine.collectAsState()
    val context = LocalContext.current
    val shown = mine.filter { it.favorite || it.seen }
    val byId = mine.associateBy { it.movieId }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Mi lista (${shown.size})", style = MaterialTheme.typography.titleLarge)
            Button(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, vm.exportCode())
                }
                context.startActivity(Intent.createChooser(send, "Enviar mi lista a mi pareja"))
            }) { Text("Compartir") }
        }
        if (shown.isEmpty()) Text("Aún no marcaste películas. Usa Buscar o Descubrir.", Modifier.padding(12.dp))
        LazyColumn {
            items(shown, key = { it.movieId }) { MovieRow(it, byId[it.movieId], vm) }
        }
    }
}

@Composable
private fun TogetherTab(vm: MainViewModel) {
    val matches by vm.matches.collectAsState()
    val partner by vm.partner.collectAsState()
    val mine by vm.mine.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    val byId = mine.associateBy { it.movieId }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(12.dp)) {
                Text("Lista de tu pareja: ${if (partner.isEmpty()) "sin importar" else "${partner.size} películas"}")
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Pega aquí el código de tu pareja") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.importCode(code); code = "" }, enabled = code.isNotBlank()) { Text("Importar") }
                    if (partner.isNotEmpty()) OutlinedButton(onClick = vm::clearPartner) { Text("Borrar") }
                }
                Spacer(Modifier.height(12.dp))
                Text("Para ver juntos (${matches.size})", style = MaterialTheme.typography.titleLarge)
                if (matches.isEmpty()) Text("Marquen favoritas para que aparezcan aquí.", style = MaterialTheme.typography.bodySmall)
            }
        }
        items(matches, key = { it.entry.movieId }) { m: Match ->
            MovieRow(
                m.entry, byId[m.entry.movieId], vm,
                extra = when {
                    m.both -> "💕 Les gusta a las dos"
                    m.iLike -> "Te gusta a ti"
                    else -> "Le gusta a tu pareja"
                },
            )
        }
    }
}
