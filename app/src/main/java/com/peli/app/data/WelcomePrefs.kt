package com.peli.app.data

import android.content.Context

/** Texto del mensaje de bienvenida (vacío = no se muestra). Se escribe en Ajustes y se guarda en este teléfono. */
class WelcomePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("peli_welcome", Context.MODE_PRIVATE)

    var text: String
        get() = prefs.getString("text", null).orEmpty()
        set(v) = prefs.edit().putString("text", v.take(WelcomeMessage.MAX_CHARS)).apply()
}
