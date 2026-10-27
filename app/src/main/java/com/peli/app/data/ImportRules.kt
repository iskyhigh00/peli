package com.peli.app.data

/** Validación de una lista importada (código pegado): un JSON legible no es necesariamente una lista válida. */
object ImportRules {
    const val MAX_CHARS = 3_000_000
    const val MAX_ENTRIES = 5_000
    private const val MAX_TITLE = 300
    private const val MAX_OVERVIEW = 4_000

    /**
     * Devuelve la lista saneada (sin repetidos, estados coherentes, marcas vacías fuera) o null si la lista no
     * es válida (en ese caso quien llama conserva la lista anterior). Estructura inválida = se rechaza todo.
     */
    fun sanitize(raw: List<Entry>?): List<Entry>? {
        if (raw == null || raw.size > MAX_ENTRIES) return null
        val out = LinkedHashMap<Int, Entry>()
        for (e in raw) {
            // Campos obligatorios: con Gson un campo ausente llega como null aunque el tipo diga lo contrario.
            @Suppress("SENSELESS_COMPARISON")
            if (e == null || e.movieId <= 0 || e.title == null || e.overview == null || e.year == null) return null
            if (e.title.length > MAX_TITLE * 4 || e.overview.length > MAX_OVERVIEW * 4) return null
            out.putIfAbsent(
                e.movieId,
                e.copy(
                    owner = OWNER_PARTNER,
                    title = e.title.take(MAX_TITLE),
                    overview = e.overview.take(MAX_OVERVIEW),
                    year = e.year.take(4),
                    updatedAt = e.updatedAt.coerceAtLeast(0),
                ).normalized(),
            )
        }
        return out.values.filter { !it.isEmpty }
    }
}
