package com.peli.app.data

import android.content.Context

class SearchLinkPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("peli_searchlink", Context.MODE_PRIVATE)

    var template: String
        get() = prefs.getString("template", null) ?: SearchLink.DEFAULT
        set(v) = prefs.edit().putString("template", v).apply()
}
