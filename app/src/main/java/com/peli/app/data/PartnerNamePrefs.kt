package com.peli.app.data

import android.content.Context

/** El apodo de la pareja, uno por persona (amigo con cuenta o sala), para que otra persona pueda tener otro. */
class PartnerNamePrefs(context: Context) {
    private val prefs = context.getSharedPreferences("peli_partner_name", Context.MODE_PRIVATE)

    fun get(key: String): String = PartnerText.clean(prefs.getString(key, null).orEmpty())
    fun put(key: String, base: String) = prefs.edit().putString(key, PartnerText.clean(base)).apply()
}
