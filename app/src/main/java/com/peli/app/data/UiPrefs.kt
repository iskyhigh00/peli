package com.peli.app.data

import android.content.Context

/** Preferencias de pantalla. */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("peli_ui", Context.MODE_PRIVATE)

    /** Descubrir muestra el tráiler (en silencio) en vez de la carátula. Gasta datos: se puede apagar en Ajustes. */
    var discoverVideo: Boolean
        get() = prefs.getBoolean("discover_video", true)
        set(v) = prefs.edit().putBoolean("discover_video", v).apply()
}
