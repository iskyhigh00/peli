package com.peli.app.data

import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Formato para intercambiar la lista con la pareja: "PELI1:" + Base64(JSON).
 * Se puede pegar por WhatsApp, mail, etc.
 */
object Sharing {
    private const val PREFIX = "PELI1:"
    private val gson = Gson()

    fun export(entries: List<Entry>): String =
        PREFIX + Base64.encodeToString(gson.toJson(entries).toByteArray(), Base64.NO_WRAP)

    /** Devuelve null si el texto no es un código válido (formato, tamaño, ids o campos obligatorios). */
    fun import(text: String): List<Entry>? {
        if (!text.trim().startsWith(PREFIX) || text.length > ImportRules.MAX_CHARS) return null
        return runCatching {
            val body = text.trim().removePrefix(PREFIX)
            val json = String(Base64.decode(body, Base64.DEFAULT))
            val type = object : TypeToken<List<Entry>>() {}.type
            ImportRules.sanitize(gson.fromJson<List<Entry>>(json, type))
        }.getOrNull()
    }
}
