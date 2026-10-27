package com.peli.app.log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formato de las líneas del registro y protección de datos sensibles. Sin dependencias de Android. */
object LogFormat {
    private val SECRET = Regex("""(?i)\b(api_?key|access_token|refresh_token|token|authorization|password)=([^&\s]+)""")
    private val ROOM = Regex("""(?i)(room=eq\.)[A-Za-z0-9]+""")
    private val BEARER = Regex("""(?i)Bearer\s+[A-Za-z0-9._\-]+""")
    private val ENTRY_START = Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} ([DIWE])/""")

    /** Oculta claves, tokens y códigos de sala que pudieran aparecer en un texto (URL, excepción…). */
    fun redact(text: String): String = text
        .replace(SECRET) { "${it.groupValues[1]}=***" }
        .replace(ROOM) { "${it.groupValues[1]}***" }
        .replace(BEARER, "Bearer ***")

    /** "2026-10-27 18:44:45.120 I/Nav: pestaña Buscar". Avisos y errores incluyen el hilo. */
    fun line(timeMs: Long, level: Char, tag: String, message: String, thread: String? = null): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date(timeMs))
        val where = if (thread != null && (level == 'W' || level == 'E')) " [$thread]" else ""
        return "$time $level/$tag$where: ${redact(message)}"
    }

    /** La URL sin claves ni código de sala, lista para registrar. */
    fun sanitizeUrl(url: String): String = redact(url)

    /** Nivel de una línea de entrada (D/I/W/E) o null si es una línea de continuación (p. ej. de un stack trace). */
    fun levelOf(line: String): Char? = ENTRY_START.find(line)?.groupValues?.get(1)?.first()

    /** Solo avisos (W) y errores (E), con las líneas de continuación que les siguen. */
    fun onlyProblems(lines: List<String>): List<String> {
        val out = ArrayList<String>()
        var include = false
        for (l in lines) {
            val level = levelOf(l)
            if (level != null) include = level == 'W' || level == 'E'
            if (include) out += l
        }
        return out
    }
}
