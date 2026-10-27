package com.peli.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peli.app.ui.theme.PeliTheme

/** Aviso al abrir la app tras un cierre inesperado, con el informe listo para enviar. */
@Composable
fun CrashReportDialog(report: String, onShare: () -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("La app se cerró la última vez") },
        text = {
            Column {
                Text(
                    "Para corregirlo necesito este informe. Toca «Enviar informe» y mándamelo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    report,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                )
            }
        },
        confirmButton = { TextButton(onClick = onShare) { Text("Enviar informe") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Descartar") } },
        containerColor = scheme.surface,
        titleContentColor = scheme.onSurface,
        textContentColor = scheme.onSurface,
    )
}

@ThemePreviews
@Composable
private fun CrashReportDialogPreview() {
    PeliTheme {
        CrashReportDialog("Peli v1.1.5 · Samsung · Android 14\nIllegalStateException: ejemplo\n  at com.peli.app.Foo.bar(Foo.kt:12)", {}, {})
    }
}
