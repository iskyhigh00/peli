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

    /** Varias direcciones del mismo servidor (casa, Tailscale, hotspot…), una por línea o separadas por coma. */
    fun baseUrls(raw: String): List<String> =
        raw.split('\n', ',', ';', ' ', '\t').mapNotNull { if (it.isBlank()) null else baseUrl(it) }.distinct()

    /** Lo que responde un servidor Jellyfin al aviso «who is JellyfinServer?» (UDP 7359). */
    data class Discovered(val address: String?, val id: String?, val name: String?)

    private fun field(json: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }

    fun parseDiscovery(json: String): Discovered? {
        val id = field(json, "Id")
        val address = field(json, "Address")
        if (id == null && address == null) return null
        return Discovered(address, id, field(json, "Name"))
    }

    /**
     * La dirección para conectar: la IP de quien respondió (la real en esa red) con el puerto que anuncia el servidor
     * (8096 si no dice). Lo que el servidor cree que es su dirección a veces es de Docker o de otra red.
     */
    fun addressFrom(sourceIp: String, announced: String?): String {
        val port = announced?.let { runCatching { URI(it).port }.getOrNull() }?.takeIf { it > 0 } ?: 8096
        val host = sourceIp.substringBefore('%')
        return if (host.contains(':')) "http://[$host]:$port" else "http://$host:$port"
    }

    /** Qué pasó al conectar, en palabras: [type] es el nombre corto del error y [httpCode] el código si el servidor respondió. */
    fun reason(type: String, httpCode: Int?): String = when {
        httpCode == 401 || httpCode == 403 -> "la clave de API fue rechazada ($httpCode)"
        httpCode == 404 -> "ahí no está Jellyfin (404): revisa el puerto"
        httpCode != null -> "el servidor respondió con error $httpCode"
        type.contains("UnknownHost") -> "no se encontró ese nombre (¿VPN apagada?)"
        type.contains("Timeout") || type.contains("Connect") || type.contains("NoRoute") -> "no responde (¿otra red, VPN apagada o puerto equivocado?)"
        type.contains("SSL") || type.contains("Certificate") -> "problema con el certificado https (prueba con http y el puerto 8096)"
        type.contains("Json") || type.contains("Malformed") || type.contains("EOF") -> "respondió algo que no es Jellyfin (¿puerto equivocado?)"
        else -> "falló ($type)"
    }

    /** Una página de películas con sus ids externos (TMDB, IMDb…). */
    fun pageUrl(base: String, start: Int): String =
        "$base/Items?Recursive=true&IncludeItemTypes=Movie&Fields=ProviderIds&EnableTotalRecordCount=false&StartIndex=$start&Limit=$PAGE"

    /** Los ids de TMDB que traen las películas del servidor (la clave puede venir como Tmdb, tmdb…). */
    fun tmdbIds(providerIds: List<Map<String, String>?>): Set<Int> =
        providerIds.mapNotNull { m -> m?.entries?.firstOrNull { it.key.equals("tmdb", ignoreCase = true) }?.value?.trim()?.toIntOrNull() }.toSet()
}
