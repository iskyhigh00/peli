package com.peli.app.data

import android.content.Context
import java.security.SecureRandom
import java.util.UUID

/** Identidad anónima de este teléfono, sala actual y matches ya avisados (SharedPreferences). */
class PairingStore(context: Context) {
    private val prefs = context.getSharedPreferences("peli_pairing", Context.MODE_PRIVATE)

    val userId: String = prefs.getString("user_id", null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("user_id", it).apply() }

    var room: String?
        get() = prefs.getString("room", null)
        set(v) = prefs.edit().putString("room", v).apply()

    /** Hora (ms) de la última sincronización completa; lo marcado después se vuelve a subir. */
    var lastPush: Long
        get() = prefs.getLong("last_push", 0L)
        set(v) = prefs.edit().putLong("last_push", v).apply()

    var announced: Set<Int>
        get() = prefs.getStringSet("announced", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("announced", v.map { it.toString() }.toSet()).apply()
}

/** Códigos de sala de 10 caracteres sin letras ambiguas (0/O, 1/I): ~50 bits de azar. */
object RoomCodes {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val LENGTH = 10

    fun newCode(): String {
        val rnd = SecureRandom()
        return buildString { repeat(LENGTH) { append(ALPHABET[rnd.nextInt(ALPHABET.length)]) } }
    }

    /** Acepta minúsculas, guiones y espacios; null si no queda un código válido. */
    fun normalize(input: String): String? =
        input.uppercase().filter { it in ALPHABET }.takeIf { it.length == LENGTH }

    fun format(code: String) = code.chunked(5).joinToString("-")
}
