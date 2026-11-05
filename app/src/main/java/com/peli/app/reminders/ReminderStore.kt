package com.peli.app.reminders

import android.content.Context

/** Preferencias de los avisos de estreno y qué avisos ya se mandaron. */
class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("peli_reminders", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** Aviso cuando un amigo te recomienda una película. Activado por defecto (solo le llega a quien recibe la recomendación). */
    var recsEnabled: Boolean
        get() = prefs.getBoolean("recs", true)
        set(value) = prefs.edit().putBoolean("recs", value).apply()

    /** Avisos de fin de semana (miércoles, jueves y viernes). Apagados por defecto: cada persona los activa en Ajustes. */
    var weekendEnabled: Boolean
        get() = prefs.getBoolean("weekend", false)
        set(value) = prefs.edit().putBoolean("weekend", value).apply()

    /** Ya se pidió el permiso de notificaciones (no insistir en cada apertura). */
    var permissionAsked: Boolean
        get() = prefs.getBoolean("asked", false)
        set(value) = prefs.edit().putBoolean("asked", value).apply()

    var notified: Set<String>
        get() = prefs.getStringSet("notified", emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet("notified", value).apply()

    /** Recomendaciones al azar de Chuba y Pepa durante la semana. Apagadas por defecto. */
    var petsEnabled: Boolean
        get() = prefs.getBoolean("pets", false)
        set(value) = prefs.edit().putBoolean("pets", value).apply()

    /** Último día (aaaa-mm-dd) en que recomendaron una película. */
    var petLastDay: String?
        get() = prefs.getString("pet_day", null)
        set(value) = prefs.edit().putString("pet_day", value).apply()

    /** Películas ya recomendadas por ellas (para no repetir). */
    var petSentIds: Set<Int>
        get() = prefs.getStringSet("pet_sent", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        set(value) = prefs.edit().putStringSet("pet_sent", value.map { it.toString() }.toSet()).apply()

    /** Las últimas recomendaciones de ellas (las más nuevas primero), para mostrarlas en Juntos. */
    var petRecs: List<com.peli.app.data.PetRec>
        get() = runCatching {
            com.google.gson.Gson().fromJson(prefs.getString("pet_recs", "[]"), Array<com.peli.app.data.PetRec>::class.java).toList()
        }.getOrDefault(emptyList())
        set(value) = prefs.edit().putString("pet_recs", com.google.gson.Gson().toJson(value)).apply()
}
