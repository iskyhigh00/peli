package com.peli.app.data

import android.content.Context

/** Películas que ya se pueden ver (arrendadas, compradas o en una plataforma). Se marca a mano, solo en este teléfono. */
class AvailablePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("peli_available", Context.MODE_PRIVATE)

    var ids: Set<Int>
        get() = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("ids", v.map { it.toString() }.toSet()).apply()
}
