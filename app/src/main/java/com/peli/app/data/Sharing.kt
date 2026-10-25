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

    /** Devuelve null si el texto no es un código válido. */
    fun import(text: String): List<Entry>? = runCatching {
        val body = text.trim().removePrefix(PREFIX)
        val json = String(Base64.decode(body, Base64.DEFAULT))
        val type = object : TypeToken<List<Entry>>() {}.type
        gson.fromJson<List<Entry>>(json, type).map { it.copy(owner = OWNER_PARTNER) }
    }.getOrNull().takeIf { text.trim().startsWith(PREFIX) }
}
