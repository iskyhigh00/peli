package com.peli.app.ui

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.peli.app.log.AppLog
import java.io.File

/** Hasta este tamaño el código va como texto del mensaje; más grande, como archivo adjunto. */
private const val MAX_TEXT_CHARS = 60_000

/**
 * Comparte el código de mi lista. Un fallo (mensaje demasiado grande para el sistema, sin app para recibirlo) se
 * registra y se avisa en vez de cerrar la app o no hacer nada.
 */
internal fun shareList(context: Context, code: String, chooserTitle: String, onError: (String) -> Unit) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain" }
        if (code.length <= MAX_TEXT_CHARS) {
            send.putExtra(Intent.EXTRA_TEXT, code)
        } else {
            val file = File(context.cacheDir, "peli-lista.txt").apply { writeText(code) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            send.putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        AppLog.i("Compartir", "lista: ${code.length} caracteres (${if (code.length <= MAX_TEXT_CHARS) "texto" else "archivo"})")
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        AppLog.e("Compartir", "no se pudo compartir la lista", e)
        onError("No se pudo compartir: ${e.message ?: e.javaClass.simpleName}")
    }
}
