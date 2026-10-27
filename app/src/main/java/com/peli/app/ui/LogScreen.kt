package com.peli.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.peli.app.log.AppLog
import com.peli.app.log.LogFormat
import com.peli.app.ui.components.TonalButton

/** Lo máximo que se copia al portapapeles (los más recientes); el archivo exportado lleva todo. */
private const val CLIPBOARD_CHARS = 60_000
private const val VIEWER_LINES = 2000

private fun shareLog(context: Context) {
    runCatching {
        val file = AppLog.exportFile(context)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Registro de Peli")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartir registro").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { AppLog.e("Registro", "no se pudo compartir el registro", it) }
}

/** Sección de Ajustes: ver, copiar, compartir y borrar el registro de la app. */
@Composable
fun LogSection(onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var verbose by remember { mutableStateOf(AppLog.verbose) }
    var showViewer by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(AppLog.sizeBytes()) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Registro de la app", style = MaterialTheme.typography.titleMedium)
        Text(
            "Anota todo lo que ocurre (pantallas, marcas, red, errores) para encontrar fallos de causa desconocida. " +
                "Cópialo o compártelo y envíalo. No incluye claves ni códigos de sala. Ocupa ${size / 1024} KB.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = verbose, role = Role.Switch) { verbose = it; AppLog.setVerbose(it) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Registro detallado", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Incluye también cada llamada de red correcta y los cambios de pantalla.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = verbose, onCheckedChange = null)
        }
        TonalButton("Ver registro", { showViewer = true; size = AppLog.sizeBytes() }, Modifier.fillMaxWidth())
        TonalButton(
            "Copiar registro",
            {
                val text = AppLog.readAll()
                clipboard.setText(AnnotatedString(LogFormat.redact(text.takeLast(CLIPBOARD_CHARS))))
                onMessage(if (text.length > CLIPBOARD_CHARS) "Copiado (lo más reciente). Para todo, usa «Compartir»." else "Registro copiado.")
            },
            Modifier.fillMaxWidth(),
        )
        TonalButton("Compartir archivo del registro", { shareLog(context) }, Modifier.fillMaxWidth())
        TonalButton(
            "Borrar registro",
            { AppLog.clear(); size = AppLog.sizeBytes(); onMessage("Registro borrado.") },
            Modifier.fillMaxWidth(),
        )
    }

    if (showViewer) LogViewerDialog(onDismiss = { showViewer = false }, onMessage = onMessage)
}

@Composable
private fun LogViewerDialog(onDismiss: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var onlyProblems by remember { mutableStateOf(false) }
    val all = remember { AppLog.readAll() }
    val lines = remember(onlyProblems) {
        val l = all.lines().filter { it.isNotBlank() }
        (if (onlyProblems) LogFormat.onlyProblems(l) else l).takeLast(VIEWER_LINES)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(lines) { if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Registro", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !onlyProblems, onClick = { onlyProblems = false }, label = { Text("Todo") })
                    FilterChip(selected = onlyProblems, onClick = { onlyProblems = true }, label = { Text("Avisos y errores") })
                    Text(
                        "${lines.size} líneas",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterVertically),
                    )
                }
                if (lines.isEmpty()) {
                    Text("Sin líneas.", modifier = Modifier.padding(16.dp))
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), state = listState) {
                    items(lines) { line ->
                        val color = when (LogFormat.levelOf(line)) {
                            'E' -> Color(0xFFFF6B6B)
                            'W' -> Color(0xFFFFC04D)
                            'D' -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(line, color = color, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TonalButton(
                        "Copiar",
                        {
                            clipboard.setText(AnnotatedString(LogFormat.redact(lines.joinToString("\n").takeLast(CLIPBOARD_CHARS))))
                            onMessage("Copiado.")
                        },
                        Modifier.weight(1f),
                    )
                    TonalButton("Compartir", { shareLog(context) }, Modifier.weight(1f))
                }
            }
        }
    }
}
