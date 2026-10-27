package com.peli.app.data

/** Página siguiente a pedir de cada consulta de TMDB; sobrevive entre cargas para no repetir páginas. */
interface PageCursors {
    fun get(key: String): Int
    fun set(key: String, page: Int)
}

class MemoryCursors : PageCursors {
    private val map = HashMap<String, Int>()
    override fun get(key: String) = map[key] ?: 1
    override fun set(key: String, page: Int) { map[key] = page }
}

object Cursors {
    /** TMDB solo entrega hasta la página 500. */
    const val MAX_PAGE = 500

    /** Después de pedir [page] con éxito: la siguiente, o vuelta a la 1 al llegar al final. */
    fun next(page: Int, totalPages: Int?): Int {
        val last = minOf(totalPages ?: MAX_PAGE, MAX_PAGE).coerceAtLeast(1)
        return if (page >= last) 1 else page + 1
    }
}

/**
 * Candidatas que sobraron de cargas anteriores, compartidas entre la lista principal y Descubrir:
 * así no se pide a la red lo que ya se tiene. Acotado en tamaño (se olvidan primero las más antiguas).
 */
class CandidateStash(private val capacity: Int = 600) {
    private val items = LinkedHashMap<Int, Pair<Entry, CandMeta>>()

    fun add(entry: Entry, meta: CandMeta) {
        val old = items.remove(entry.movieId)
        // Se conserva la mejor señal conocida de la película.
        val merged = if (old == null) meta else CandMeta(meta.votes ?: old.second.votes, maxOf(meta.collab, old.second.collab))
        items[entry.movieId] = entry to merged
        while (items.size > capacity) items.remove(items.keys.first())
    }

    fun remove(ids: Collection<Int>) = ids.forEach { items.remove(it) }
    fun clear() = items.clear()
    val size get() = items.size

    /** Las que sirven ahora: no marcadas por mí y que [usable] acepta (p. ej. cumplen la memoria de lo mostrado). */
    fun usable(known: Set<Int>, usable: (Int) -> Boolean = { true }): List<Pair<Entry, CandMeta>> =
        items.values.filter { it.first.movieId !in known && usable(it.first.movieId) }
}
