package com.peli.app.reminders

import android.content.Context

/** Preferencias de los avisos de estreno y qué avisos ya se mandaron. */
class ReminderStore(context: Context) {
    private val prefs = context.getSharedPreferences("peli_reminders", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    /** Ya se pidió el permiso de notificaciones (no insistir en cada apertura). */
    var permissionAsked: Boolean
        get() = prefs.getBoolean("asked", false)
        set(value) = prefs.edit().putBoolean("asked", value).apply()

    var notified: Set<String>
        get() = prefs.getStringSet("notified", emptySet()).orEmpty().toSet()
        set(value) = prefs.edit().putStringSet("notified", value).apply()
}
