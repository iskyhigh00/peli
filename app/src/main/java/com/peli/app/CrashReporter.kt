package com.peli.app

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de cierres inesperados para poder diagnosticarlos desde el propio teléfono:
 *  - excepciones de Kotlin/Java: se guarda el error antes de que la app muera;
 *  - cierres que Java no puede capturar (fallo nativo, falta de memoria, ANR): en Android 11+ se lee
 *    el motivo que registró el sistema (ApplicationExitInfo).
 * Al abrir la app se ofrece compartir el informe.
 */
object CrashReporter {
    private const val FILE = "last_crash.txt"
    private const val PREFS = "peli_crash"
    private const val KEY_EXIT_TS = "exit_ts"
    private const val MAX_CHARS = 6000

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(app, "fatal", error, thread)
            previous?.uncaughtException(thread, error)
        }
    }

    /** Guarda el error (también los no fatales capturados por la app) para mostrarlo al próximo arranque. */
    fun record(context: Context, source: String, error: Throwable, thread: Thread = Thread.currentThread()) {
        runCatching {
            File(context.filesDir, FILE).writeText(
                buildString {
                    appendLine("Peli v$APP_VERSION · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine(time(System.currentTimeMillis()))
                    appendLine("Origen: $source · hilo: ${thread.name}")
                    appendLine()
                    append(error.stackTraceToString().take(MAX_CHARS))
                },
            )
        }
    }

    /** Informe pendiente de mostrar (error guardado y/o cierre registrado por el sistema), o null. */
    fun pending(context: Context): String? {
        val parts = mutableListOf<String>()
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()
            ?.takeIf { it.isNotBlank() }?.let { parts += it }
        systemExit(context)?.let { parts += it }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n— — —\n\n")
    }

    /** Descarta el informe y marca como vistos los cierres anteriores. */
    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_EXIT_TS, System.currentTimeMillis()).apply()
    }

    private fun time(ms: Long) = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(ms))

    @SuppressLint("NewApi")
    private fun systemExit(context: Context): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val lastSeen = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_EXIT_TS, 0L)
        val exit = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 8) }
            .getOrNull().orEmpty()
            .firstOrNull { it.timestamp > lastSeen && isInteresting(it) }
            ?: return null
        return buildString {
            appendLine("Cierre registrado por Android: ${reasonName(exit.reason)}")
            appendLine("Detalle: ${exit.description}")
            appendLine("Memoria (KB): rss=${exit.rss} pss=${exit.pss}")
            appendLine("Hora: ${time(exit.timestamp)}")
            nativeTrace(exit)?.let {
                appendLine()
                appendLine("Pistas del fallo nativo:")
                append(it)
            }
        }
    }

    /** Fallos reales siempre; falta de memoria/recursos solo si la app estaba a la vista (no al cerrarse en segundo plano). */
    @SuppressLint("NewApi")
    private fun isInteresting(e: ApplicationExitInfo) = when (e.reason) {
        ApplicationExitInfo.REASON_CRASH,
        ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_ANR,
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> true
        ApplicationExitInfo.REASON_LOW_MEMORY,
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE,
        ApplicationExitInfo.REASON_SIGNALED -> e.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        else -> false
    }

    @SuppressLint("NewApi")
    private fun reasonName(reason: Int) = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "excepción (CRASH)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "fallo nativo (CRASH_NATIVE)"
        ApplicationExitInfo.REASON_ANR -> "app no responde (ANR)"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "poca memoria (LOW_MEMORY)"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "uso excesivo de recursos"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "falló al iniciar"
        ApplicationExitInfo.REASON_SIGNALED -> "terminada por señal (SIGNALED)"
        else -> "motivo $reason"
    }

    /** El informe de un fallo nativo viene en binario; se extraen los textos legibles (librerías, funciones). */
    @SuppressLint("NewApi")
    private fun nativeTrace(exit: ApplicationExitInfo): String? {
        if (exit.reason != ApplicationExitInfo.REASON_CRASH_NATIVE) return null
        val bytes = runCatching {
            exit.traceInputStream?.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (out.size() < 300_000) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        }.getOrNull() ?: return null
        return Regex("[ -~]{8,}").findAll(String(bytes, Charsets.ISO_8859_1))
            .map { it.value.trim() }.distinct().take(60).joinToString("\n").take(3500).ifBlank { null }
    }
}
