package com.peli.app.data

import java.net.URI
import java.net.URLEncoder

/**
 * Enlace de búsqueda configurable: el usuario escribe en Ajustes una dirección con {titulo} donde va el nombre de la
 * película (p. ej. https://www.justwatch.com/search?q={titulo}) y el botón del detalle la arma con cada película.
 */
object SearchLink {
    const val PLACEHOLDER = "{titulo}"
    /** Donde va el año de la película (cualquiera de estas escrituras sirve). */
    val YEAR_PLACEHOLDERS = listOf("{anio}", "{año}", "{year}")
    const val DEFAULT = "https://www.justwatch.com/search?q={titulo}"

    /** El título listo para ir dentro de una dirección: espacios como %20, sin caracteres que rompan el enlace. */
    fun encode(title: String): String = URLEncoder.encode(title.trim(), "UTF-8").replace("+", "%20")

    /** La dirección final, o null si la plantilla está vacía, no es http(s) o no es una dirección válida. */
    fun build(template: String, title: String, year: String? = null): String? {
        val t = template.trim()
        if (t.isEmpty() || title.isBlank()) return null
        if (!t.startsWith("http://", ignoreCase = true) && !t.startsWith("https://", ignoreCase = true)) return null
        val y = year?.trim()?.take(4)?.takeIf { it.length == 4 && it.all(Char::isDigit) }.orEmpty()
        var url = if (PLACEHOLDER in t) t.replace(PLACEHOLDER, encode(title)) else t
        YEAR_PLACEHOLDERS.forEach { url = url.replace(it, y) }
        return runCatching { URI(url).takeIf { it.host != null }?.toString() }.getOrNull()
    }

    /** Nombre corto del sitio para el botón ("justwatch.com"). */
    fun host(template: String): String? =
        runCatching { URI(template.trim().replace(PLACEHOLDER, "x").let { u -> YEAR_PLACEHOLDERS.fold(u) { acc, p -> acc.replace(p, "0") } }).host?.removePrefix("www.") }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Un enlace por título (en el orden dado), sin repetidos ni títulos que no arman enlace. */
    fun list(template: String, titles: List<String>): List<String> =
        titles.mapNotNull { build(template, it) }.distinct()

    /** Igual, pero cada película con su año (título, año). */
    fun listWithYears(template: String, items: List<Pair<String, String?>>): List<String> =
        items.mapNotNull { (title, year) -> build(template, title, year) }.distinct()

    /** «1. enlace», «2. enlace»… (para compartir o copiar la lista numerada). */
    fun numbered(links: List<String>): String = links.mapIndexed { i, l -> "${i + 1}. $l" }.joinToString("\n")
}
