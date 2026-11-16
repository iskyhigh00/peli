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

    /** Última dirección en que se detectó el servidor en la red (se actualiza sola si cambia la IP). */
    var found: String
        get() = prefs.getString("found", null).orEmpty()
        set(v) = prefs.edit().putString("found", v).apply()

    /** Id del servidor detectado, para no confundirlo con otro Jellyfin de otra casa u oficina. */
    var serverId: String
        get() = prefs.getString("server_id", null).orEmpty()
        set(v) = prefs.edit().putString("server_id", v).apply()

    var ids: Set<Int>
        get() = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("ids", v.map { it.toString() }.toSet()).apply()
}
