package com.peli.app.data

/** Un video de TMDB (lo que hace falta para elegir el tráiler). [country] es el país de quien lo subió. */
data class TrailerVideo(
    val key: String?,
    val site: String?,
    val type: String?,
    val official: Boolean?,
    val language: String?,
    val country: String? = null,
    val name: String? = null,
)

object Trailers {
    /** Países de habla hispana de América (y EE. UU., donde los tráilers en español apuntan al público latino). */
    private val LATIN_AMERICA = setOf(
        "MX", "AR", "CL", "CO", "PE", "VE", "UY", "EC", "BO", "PY", "CR", "PA", "GT", "HN", "NI", "SV", "DO", "CU", "PR", "US",
    )

    /** Orden de preferencia de idioma (menor = mejor). */
    private enum class Pref { LatinSpanish, EnglishSubtitled, English, SpainSpanish, Other }

    private fun hasSubtitles(name: String?) = name?.contains("sub", ignoreCase = true) == true

    private fun isLatinName(name: String?) = name?.let {
        it.contains("latino", ignoreCase = true) || it.contains("latam", ignoreCase = true) ||
            it.contains("latinoam", ignoreCase = true) || it.contains("hispano", ignoreCase = true)
    } == true

    private fun preference(v: TrailerVideo): Pref = when (v.language?.lowercase()) {
        "es" -> when {
            v.country?.uppercase() in LATIN_AMERICA || isLatinName(v.name) -> Pref.LatinSpanish
            else -> Pref.SpainSpanish
        }
        "en" -> if (hasSubtitles(v.name)) Pref.EnglishSubtitled else Pref.English
        else -> Pref.Other
    }

    /**
     * Elige el mejor tráiler de YouTube según mi preferencia: español latino, luego inglés (con subtítulos y después
     * sin ellos), luego español de España y al final cualquier otro. Dentro de cada grupo, «Trailer» antes que
     * «Teaser» y los oficiales primero. null si no hay ninguno utilizable.
     */
    fun pick(videos: List<TrailerVideo>): String? = videos
        .filter { !it.key.isNullOrBlank() && it.site.equals("YouTube", ignoreCase = true) && (it.type == "Trailer" || it.type == "Teaser") }
        .sortedWith(
            compareBy<TrailerVideo> { preference(it) }
                .thenBy { if (it.type == "Trailer") 0 else 1 }
                .thenBy { if (it.official == true) 0 else 1 },
        )
        .firstOrNull()?.key

    /** Dirección web del video (para abrirlo en YouTube). */
    fun watchUrl(key: String) = "https://www.youtube.com/watch?v=$key"
}
