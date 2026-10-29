package com.peli.app.data

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Formato para intercambiar la lista con la pareja. "PELI2:" + Base64(GZIP(JSON)): comprimido, para que quepa en un
 * mensaje aunque la lista sea larga (el formato anterior, "PELI1:" + Base64(JSON), sigue pudiéndose importar).
 * Se puede pegar por WhatsApp, mail, etc.
 */
object Sharing {
    private const val PREFIX_V1 = "PELI1:"
    private const val PREFIX = "PELI2:"
    private const val SHARED_OVERVIEW_CHARS = 160
    private val gson = Gson()

    /** Solo lo que la otra persona necesita: sinopsis corta y sin título original (se vuelve a pedir allí). */
    fun export(entries: List<Entry>): String {
        val slim = entries.map { it.copy(overview = it.overview.take(SHARED_OVERVIEW_CHARS), originalTitle = null) }
        return PREFIX + Base64.encodeToString(SharePack.gzip(gson.toJson(slim).toByteArray()), Base64.NO_WRAP)
    }

    /** Devuelve null si el texto no es un código válido (formato, tamaño, ids o campos obligatorios). */
    fun import(text: String): List<Entry>? {
        val t = text.trim()
        if (text.length > ImportRules.MAX_CHARS) return null
        return runCatching {
            val json = when {
                t.startsWith(PREFIX) -> SharePack.gunzip(Base64.decode(t.removePrefix(PREFIX), Base64.DEFAULT), ImportRules.MAX_CHARS)
                t.startsWith(PREFIX_V1) -> String(Base64.decode(t.removePrefix(PREFIX_V1), Base64.DEFAULT))
                else -> return null
            } ?: return null
            val type = object : TypeToken<List<Entry>>() {}.type
            ImportRules.sanitize(gson.fromJson<List<Entry>>(json, type))
        }.getOrNull()
    }
}
