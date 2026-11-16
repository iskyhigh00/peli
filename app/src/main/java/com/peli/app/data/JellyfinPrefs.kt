package com.peli.app.data

import android.content.Context

/** Dirección y clave de tu servidor Jellyfin, y las películas que tenía la última vez (no viaja en copias de seguridad). */
class JellyfinPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("peli_jellyfin", Context.MODE_PRIVATE)

    var url: String
        get() = prefs.getString("url", null).orEmpty()
        set(v) = prefs.edit().putString("url", v).apply()

    var key: String
        get() = prefs.getString("key", null).orEmpty()
        set(v) = prefs.edit().putString("key", v).apply()

    var ids: Set<Int>
        get() = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("ids", v.map { it.toString() }.toSet()).apply()
}
