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

    /** Página mínima con el reproductor incrustado de YouTube. */
    fun embedHtml(key: String) =
        "<html><body style=\"margin:0;background:#000\"><iframe src=\"https://www.youtube.com/embed/$key?rel=0&playsinline=1\" " +
            "style=\"position:absolute;top:0;left:0;width:100%;height:100%;border:0\" " +
            "allow=\"autoplay; encrypted-media; picture-in-picture\" allowfullscreen></iframe></body></html>"
}
