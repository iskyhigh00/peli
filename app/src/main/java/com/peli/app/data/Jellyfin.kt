package com.peli.app.data

import java.net.URI

/** Parte pura de la conexión con un servidor Jellyfin: arma direcciones y lee los ids de TMDB de sus películas. */
object Jellyfin {
    const val PAGE = 500

    /** «192.168.1.5:8096» -> «http://192.168.1.5:8096»; sin barra final. null si no es una dirección válida. */
    fun baseUrl(raw: String): String? {
        var t = raw.trim()
        if (t.isEmpty()) return null
        if (!t.startsWith("http://", ignoreCase = true) && !t.startsWith("https://", ignoreCase = true)) t = "http://$t"
        t = t.trimEnd('/')
        return runCatching { URI(t).takeIf { it.host != null }?.toString()?.trimEnd('/') }.getOrNull()
    }

    /** Una página de películas con sus ids externos (TMDB, IMDb…). */
    fun pageUrl(base: String, start: Int): String =
        "$base/Items?Recursive=true&IncludeItemTypes=Movie&Fields=ProviderIds&EnableTotalRecordCount=false&StartIndex=$start&Limit=$PAGE"

    /** Los ids de TMDB que traen las películas del servidor (la clave puede venir como Tmdb, tmdb…). */
    fun tmdbIds(providerIds: List<Map<String, String>?>): Set<Int> =
        providerIds.mapNotNull { m -> m?.entries?.firstOrNull { it.key.equals("tmdb", ignoreCase = true) }?.value?.trim()?.toIntOrNull() }.toSet()
}
