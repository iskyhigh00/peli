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

    /**
     * Matches ya avisados, por contexto (un amigo o una sala): la misma película puede ser un match nuevo con
     * otra persona. Lo guardado por versiones anteriores (solo ids) se asigna al primer contexto que lo consulte.
     */
    fun announced(ctx: String): Set<Int> {
        migrateLegacy(ctx)
        return prefs.getStringSet("announced2", emptySet()).orEmpty()
            .filter { it.substringBeforeLast('|') == ctx }
            .mapNotNull { it.substringAfterLast('|').toIntOrNull() }
            .toSet()
    }

    fun addAnnounced(ctx: String, ids: Collection<Int>) {
        migrateLegacy(ctx)
        val current = prefs.getStringSet("announced2", emptySet()).orEmpty()
        prefs.edit().putStringSet("announced2", current + ids.map { "$ctx|$it" }).apply()
    }

    private fun migrateLegacy(ctx: String) {
        if (prefs.getBoolean("announced_migrated", false)) return
        val legacy = prefs.getStringSet("announced", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
        val current = prefs.getStringSet("announced2", emptySet()).orEmpty()
        prefs.edit().putStringSet("announced2", current + legacy.map { "$ctx|$it" }).putBoolean("announced_migrated", true).apply()
    }
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
