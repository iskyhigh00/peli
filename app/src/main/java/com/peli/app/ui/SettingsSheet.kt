package com.peli.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Close
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
    update: com.peli.app.ui.features.ManualUpdate,
    progress: Int,
    onInstall: (String) -> Unit,
    onCheckUpdate: () -> Unit,
    onMessage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Pantalla completa (no una hoja inferior): la hoja arrastrable peleaba con el desplazamiento de esta lista larga
    // (a veces no avanzaba y a veces se cerraba al intentar subir).
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
    androidx.compose.material3.Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = ScreenHorizontalPadding, end = 4.dp, top = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            androidx.compose.material3.IconButton(onClick = onDismiss) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Cerrar")
            }
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding)
                .padding(bottom = SectionSpacing),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (vm.googleAvailable) {
                val syncError by vm.syncError.collectAsState()
                Text("Cuenta y amigos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = ItemSpacing))
                com.peli.app.ui.components.GlassCard { AccountCardContent(vm, syncError) }
                HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            }
            Text("Valoraciones y reseñas", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = ItemSpacing))
            RatingSource.entries.forEach { source ->
                val available = !source.needsOmdb || omdbAvailable
                SettingRow(
                    title = (if (source == com.peli.app.data.RatingSource.Partner) PartnerName.miCap else source.label) + if (available) "" else " (necesita clave de OMDb)",
                    checked = available && source in sources,
                    enabled = available,
                    onChange = { onToggle(source) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            SettingRow(
                title = "Avisos de estreno",
                checked = remindersOn,
                enabled = true,
                onChange = onReminders,
            )
            SettingRow(
                title = "Recomendaciones de amigos",
                checked = recsOn,
                enabled = true,
                onChange = onRecs,
            )
            Text("Idioma de títulos y sinopsis", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.peli.app.data.Lang.options.forEach { (tag, name) ->
                    androidx.compose.material3.FilterChip(
                        selected = tag == language,
                        onClick = { onLanguage(tag) },
                        label = { Text(name) },
                    )
                }
            }
            Text("Apodo de la pareja", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            val nick by vm.partnerName.nickname.collectAsState()
            androidx.compose.material3.OutlinedTextField(
                value = nick,
                onValueChange = vm.partnerName::set,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                label = { Text("Apodo") },
                singleLine = true,
            )
            Text("Mensaje de bienvenida", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            val welcomeText by vm.welcome.text.collectAsState()
            androidx.compose.material3.OutlinedTextField(
                value = welcomeText,
                onValueChange = vm.welcome::setText,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Mensaje") },
                supportingText = { Text("${welcomeText.length}/${com.peli.app.data.WelcomeMessage.MAX_CHARS}") },
                minLines = 2,
            )
            val activeFriendId by vm.activeFriend.collectAsState()
            val friendList by vm.friends.collectAsState()
            val friendName = friendList.firstOrNull { it.friend_id == activeFriendId && it.accepted }?.username
            if (friendName != null) {
                TonalButton("Enviar a @$friendName", { vm.welcome.save(welcomeText) }, Modifier.fillMaxWidth())
            }
            if (welcomeText.isNotBlank()) {
                TonalButton("Vista previa", { onDismiss(); vm.welcome.splashVisible.value = true }, Modifier.fillMaxWidth())
            }
            Text("Mi servidor Jellyfin", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            val serverUrl by vm.server.url.collectAsState()
            val serverKey by vm.server.key.collectAsState()
            val serverStatus by vm.server.status.collectAsState()
            val serverIds by vm.server.ids.collectAsState()
            androidx.compose.material3.OutlinedTextField(
                value = serverUrl,
                onValueChange = vm.server::setUrl,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Direcciones extra (una por línea)") },
                placeholder = { Text("192.168.1.20:8096\n100.x.x.x:8096") },
                minLines = 2,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            )
            androidx.compose.material3.OutlinedTextField(
                value = serverKey,
                onValueChange = vm.server::setKey,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                label = { Text("Clave de API de Jellyfin") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            )
            TonalButton("Actualizar servidor", { vm.server.refresh(manual = true) }, Modifier.fillMaxWidth())
            (serverStatus ?: if (serverIds.isNotEmpty()) "${serverIds.size} películas en tu servidor." else null)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val lastSync by vm.server.lastSync.collectAsState()
            var nowMs by androidx.compose.runtime.remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) { kotlinx.coroutines.delay(30_000); nowMs = System.currentTimeMillis() }
            }
            Text(
                if (lastSync > 0L) {
                    val at = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("es")).format(java.util.Date(lastSync))
                    "Última actualización con tu servidor: $at (${com.peli.app.data.Jellyfin.ago(nowMs, lastSync)})"
                } else {
                    "Todavía no se ha actualizado con tu servidor."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Enlace de búsqueda", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            val searchLink by vm.links.template.collectAsState()
            androidx.compose.material3.OutlinedTextField(
                value = searchLink,
                onValueChange = vm.links::setTemplate,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Dirección") },
                placeholder = { Text(com.peli.app.data.SearchLink.DEFAULT) },
                singleLine = false,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
            )
            androidx.compose.material3.TextButton(onClick = { vm.links.setTemplate(com.peli.app.data.SearchLink.DEFAULT) }) { Text("Restablecer") }
            var linksText by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            var linksKind by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(com.peli.app.data.ExportList.Both) }
            val linksContext = androidx.compose.ui.platform.LocalContext.current
            Text("Exportar enlaces de películas por ver", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            com.peli.app.data.ExportList.entries.forEach { kind ->
                TonalButton(
                    when (kind) {
                        com.peli.app.data.ExportList.Both -> "Matches"
                        com.peli.app.data.ExportList.Mine -> "Las que quiero ver yo"
                        com.peli.app.data.ExportList.Partner -> "Las que quiere ver ${PartnerName.tu}"
                    },
                    {
                        onMessage("Preparando los enlaces…")
                        vm.links.export(kind) { text ->
                            if (text == null) onMessage("No hay películas pendientes en esa lista o falta el enlace de búsqueda.") else { linksKind = kind; linksText = text }
                        }
                    },
                    Modifier.fillMaxWidth(),
                )
            }
            linksText?.let { text ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { linksText = null },
                    title = {
                        val name = when (linksKind) {
                            com.peli.app.data.ExportList.Both -> "Matches"
                            com.peli.app.data.ExportList.Mine -> "Las que quiero ver yo"
                            com.peli.app.data.ExportList.Partner -> "Las que quiere ver ${PartnerName.tu}"
                        }
                        Text("$name (${text.lines().size})")
                    },
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
            TonalButton("Actualizar títulos", onRefreshTitles, Modifier.fillMaxWidth().padding(bottom = 8.dp))
            SettingRow(
                title = "Chuba y Pepa recomiendan",
                checked = petsOn,
                enabled = true,
                onChange = onPets,
            )
            val discoverVideo by vm.detail.discoverVideo.collectAsState()
            SettingRow(
                title = "Tráiler en Descubrir",
                checked = discoverVideo,
                enabled = true,
                onChange = vm.detail::setDiscoverVideo,
            )
            SettingRow(
                title = "Fin de semana",
                checked = weekendOn,
                enabled = true,
                onChange = onWeekend,
            )
            HorizontalDivider(Modifier.padding(vertical = ItemSpacing))
            Text("Versión $APP_VERSION", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when (update) {
                is com.peli.app.ui.features.ManualUpdate.Available -> UpdateBanner(
                    version = update.version,
                    progress = progress,
                    onUpdate = { onInstall(update.version) },
                    modifier = Modifier.padding(top = 8.dp),
                )
                else -> {
                    val status = when (update) {
                        com.peli.app.ui.features.ManualUpdate.Checking -> "Buscando…"
                        com.peli.app.ui.features.ManualUpdate.UpToDate -> "Estás al día: tienes la última versión."
                        is com.peli.app.ui.features.ManualUpdate.Failed -> "No se pudo consultar: ${update.error ?: "sin conexión"}"
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
    }
}
