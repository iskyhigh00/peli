package com.peli.app.data

/** Un video de TMDB (lo que hace falta para elegir el tráiler). */
data class TrailerVideo(val key: String?, val site: String?, val type: String?, val official: Boolean?, val language: String?)

object Trailers {
    /**
     * Elige el mejor tráiler de YouTube: «Trailer» antes que «Teaser», en español antes que en inglés y antes que otros
     * idiomas, y los oficiales primero. null si no hay ninguno utilizable.
     */
    fun pick(videos: List<TrailerVideo>): String? = videos
        .filter { !it.key.isNullOrBlank() && it.site.equals("YouTube", ignoreCase = true) && (it.type == "Trailer" || it.type == "Teaser") }
        .sortedWith(
            compareBy<TrailerVideo> { if (it.type == "Trailer") 0 else 1 }
                .thenBy { when (it.language) { "es" -> 0; "en" -> 1; null, "" -> 2; else -> 3 } }
                .thenBy { if (it.official == true) 0 else 1 },
        )
        .firstOrNull()?.key

    /** Dirección web del video (para abrirlo en YouTube). */
    fun watchUrl(key: String) = "https://www.youtube.com/watch?v=$key"
}
