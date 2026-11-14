package com.peli.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.peli.app.APP_VERSION
import com.peli.app.data.RatingSource
import com.peli.app.ui.components.ItemSpacing
import com.peli.app.ui.components.ScreenHorizontalPadding
import com.peli.app.ui.components.SectionSpacing
import com.peli.app.ui.components.TonalButton
import com.peli.app.ui.components.UpdateBanner
import com.peli.app.ui.theme.SheetShape

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // El Row completo es el control (toggleable); el Switch solo refleja el estado.
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/** Ajustes: de qué fuentes mostrar valoraciones y reseñas, versión y búsqueda de actualización. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    vm: MainViewModel,
    sources: Set<RatingSource>,
    omdbAvailable: Boolean,
    onToggle: (RatingSource) -> Unit,
    remindersOn: Boolean,
    onReminders: (Boolean) -> Unit,
    weekendOn: Boolean,
    onWeekend: (Boolean) -> Unit,
    recsOn: Boolean,
    onRecs: (Boolean) -> Unit,
    petsOn: Boolean,
    onPets: (Boolean) -> Unit,
    language: String,
    onLanguage: (String) -> Unit,
    onRefreshTitles: () -> Unit,
    update: MainViewModel.ManualUpdate,
    progress: Int,
    onInstall: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(bottom = SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
            if (vm.googleAvailable) {
                val syncError by vm.syncError.collectAsState()
                Text("Cuenta y amigos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = ItemSpacing))
                com.peli.app.ui.components.GlassCard { AccountCardContent(vm, syncError) }
                HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            }
            Text("Valoraciones y reseñas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = ItemSpacing))
            Text(
                "Elige de qué lugares quieres ver notas y reseñas en el detalle de cada película.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            RatingSource.entries.forEach { source ->
                val available = !source.needsOmdb || omdbAvailable
                SettingRow(
                    title = source.label,
                    subtitle = if (available) source.hint else "Necesita la clave gratuita de OMDb (OMDB_API_KEY)",
                    checked = available && source in sources,
                    enabled = available,
                    onChange = { onToggle(source) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            SettingRow(
                title = "Avisos de estreno",
                subtitle = "Una notificación cuando se acerca el estreno en el cine de una película de \"Quiero verla\": a una semana, el día anterior y el mismo día.",
                checked = remindersOn,
                enabled = true,
                onChange = onReminders,
            )
            SettingRow(
                title = "Recomendaciones de amigos",
                subtitle = "Una notificación cuando un amigo te recomienda una película.",
                checked = recsOn,
                enabled = true,
                onChange = onRecs,
            )
            Text("Idioma de títulos y sinopsis", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Las traducciones vienen de TMDB. Latinoamérica usa los títulos de México, que son los más parecidos a los de toda la región; los títulos de España son distintos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.peli.app.data.Lang.options.forEach { (tag, name) ->
                    androidx.compose.material3.FilterChip(
                        selected = tag == language,
                        onClick = { onLanguage(tag) },
                        label = { Text(name) },
                    )
                }
            }
            Text("Mensaje de bienvenida", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Un texto corto que aparece animado unos segundos al abrir la app. Déjalo vacío para no mostrarlo.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val welcomeText by vm.welcomeMessage.collectAsState()
            androidx.compose.material3.OutlinedTextField(
                value = welcomeText,
                onValueChange = vm::setWelcomeMessage,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Mensaje") },
                supportingText = { Text("${welcomeText.length}/${com.peli.app.data.WelcomeMessage.MAX_CHARS}") },
                minLines = 2,
            )
            val activeFriendId by vm.activeFriend.collectAsState()
            val friendList by vm.friends.collectAsState()
            val friendName = friendList.firstOrNull { it.friend_id == activeFriendId && it.accepted }?.username
            if (friendName != null) {
                Text(
                    "Es un solo mensaje compartido con @$friendName: lo que guardes lo ve ella al abrir la app, y lo que ella guarde lo ves tú.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TonalButton("Guardar y enviar a @$friendName", { vm.saveWelcome(welcomeText) }, Modifier.fillMaxWidth())
            }
            if (welcomeText.isNotBlank()) {
                TonalButton("Ver cómo se ve", { onDismiss(); vm.splashVisible.value = true }, Modifier.fillMaxWidth())
            }
            Text("Enlace de búsqueda", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text(
                "Botón al final de cada película que abre este enlace con su nombre. Escribe la dirección completa y pon {titulo} donde va el nombre. Si lo dejas vacío, el botón no aparece.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val searchLink by vm.searchLink.collectAsState()
            androidx.compose.material3.OutlinedTextField(
                value = searchLink,
                onValueChange = vm::setSearchLink,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Dirección") },
                placeholder = { Text(com.peli.app.data.SearchLink.DEFAULT) },
                singleLine = false,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            )
            androidx.compose.material3.TextButton(onClick = { vm.setSearchLink(com.peli.app.data.SearchLink.DEFAULT) }) { Text("Restablecer") }
            var linksText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            var linksKind by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.peli.app.data.ExportList.Both) }
            val linksContext = androidx.compose.ui.platform.LocalContext.current
            Text("Exportar enlaces", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            Text(
                "Un enlace por película, sin las que marcaste como «Disponible». Cada película sale en una sola lista.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            com.peli.app.data.ExportList.entries.forEach { kind ->
                TonalButton(
                    when (kind) {
                        com.peli.app.data.ExportList.Both -> "Enlaces de los matches"
                        com.peli.app.data.ExportList.Mine -> "Enlaces de solo las mías"
                        com.peli.app.data.ExportList.Partner -> "Enlaces de solo mi pareja"
                    },
                    {
                        val text = vm.matchLinks(kind)
                        if (text == null) onMessage("No hay películas pendientes en esa lista o falta el enlace de búsqueda.") else { linksKind = kind; linksText = text }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
            linksText?.let { text ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { linksText = null },
                    title = { Text("${linksKind.label} (${text.lines().size})") },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            androidx.compose.foundation.text.selection.SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
                        }
                    },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            val cm = linksContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Enlaces", text))
                            onMessage("Enlaces copiados.")
                            linksText = null
                        }) { Text("Copiar") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, text)
                            }
                            linksContext.startActivity(android.content.Intent.createChooser(send, "Enviar enlaces"))
                        }) { Text("Compartir") }
                    },
                )
            }
            TonalButton("Actualizar títulos de mis películas", onRefreshTitles, Modifier.fillMaxWidth().padding(bottom = 8.dp))
            SettingRow(
                title = "Chuba y Pepa recomiendan",
                subtitle = "Unos 3 días a la semana, al azar, Chuba o Pepa te recomiendan una película según su gusto, con su explicación.",
                checked = petsOn,
                enabled = true,
                onChange = onPets,
            )
            val discoverVideo by vm.discoverVideo.collectAsState()
            SettingRow(
                title = "Tráiler en Descubrir",
                subtitle = "En Descubrir se ve el tráiler (sin sonido hasta que lo actives) en vez de la carátula. Gasta más datos móviles.",
                checked = discoverVideo,
                enabled = true,
                onChange = vm::setDiscoverVideo,
            )
            SettingRow(
                title = "Fin de semana",
                subtitle = "Te recuerda preparar las películas del fin de semana: 2 avisos el miércoles, 4 el jueves y 1 el viernes en la mañana.",
                checked = weekendOn,
                enabled = true,
                onChange = onWeekend,
            )
            HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            Text("Versión $APP_VERSION", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (update) {
                is MainViewModel.ManualUpdate.Available -> UpdateBanner(
                    version = update.version,
                    progress = progress,
                    onUpdate = { onInstall(update.version) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> {
                    val status = when (update) {
                        MainViewModel.ManualUpdate.Checking -> "Buscando…"
                        MainViewModel.ManualUpdate.UpToDate -> "Estás al día: tienes la última versión."
                        is MainViewModel.ManualUpdate.Failed -> "No se pudo consultar: ${update.error ?: "sin conexión"}"
                        else -> null
                    }
                    status?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                    }
                    TonalButton("Buscar actualización", onCheckUpdate, Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
            HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            LogSection(onMessage)
        }
    }
}
