package com.peli.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

class Repository(private val db: AppDb, private val api: TmdbApi) {
    private val dao = db.entries()

    val mine = dao.observe(OWNER_ME)
    val partner = dao.observe(OWNER_PARTNER)

    suspend fun search(q: String) = api.search(q).results.map { it.toEntry(OWNER_ME) }
    suspend fun popular(page: Int) = api.popular(page).results.map { it.toEntry(OWNER_ME) }

    /** Aplica un cambio sobre mi marca de la película; si queda vacía se borra. Devuelve el resultado. */
    suspend fun update(base: Entry, change: (Entry) -> Entry): Entry {
        val current = dao.get(OWNER_ME, base.movieId) ?: base.copy(owner = OWNER_ME)
        val next = change(current).copy(updatedAt = System.currentTimeMillis())
        if (next.isEmpty) dao.delete(OWNER_ME, next.movieId) else dao.upsert(next)
        return next
    }

    /** Sube un cambio a la sala (también las marcas vaciadas, para que la pareja las vea quitadas). */
    suspend fun push(room: String, userId: String, e: Entry) =
        Remote.api.upsert(room, listOf(e.toRemote(room, userId)))

    /**
     * Sincroniza con la sala: sube lo marcado desde [since] (todo si es 0) y reemplaza la lista de
     * la pareja con lo que hay en la nube. Es idempotente, así que se puede repetir sin problema.
     */
    suspend fun sync(room: String, userId: String, mine: List<Entry>, since: Long) {
        val rows = mine.filter { since == 0L || it.updatedAt > since }.map { it.toRemote(room, userId) }
        if (rows.isNotEmpty()) Remote.api.upsert(room, rows)
        val others = Remote.api.others(room, "eq.$room", "neq.$userId").map { it.toEntry() }.filter { !it.isEmpty }
        dao.replaceAll(OWNER_PARTNER, others)
    }

    /**
     * Sugerencias para "Descubrir", ordenadas por [Recommender]:
     *  - candidatas = recomendaciones de TMDB para mis últimas "me gustó"
     *    + mejor valoradas de mis géneros favoritos + algo popular (exploración).
     *  - sin historial aún: populares.
     */
    suspend fun recommend(marked: List<Entry>): List<Entry> = coroutineScope {
        val known = marked.map { it.movieId }.toSet()
        val profile = Recommender.buildProfile(marked)
        val seeds = marked.filter { it.liked }.sortedByDescending { it.updatedAt }.take(5)

        val pool = LinkedHashMap<Int, Entry>()
        val hits = HashMap<Int, Int>()
        fun add(list: List<TmdbMovie>, countHit: Boolean) = list.forEach { m ->
            pool.putIfAbsent(m.id, m.toEntry(OWNER_ME))
            if (countHit) hits[m.id] = (hits[m.id] ?: 0) + 1
        }

        val seedJobs = seeds.map { s ->
            async { runCatching { api.recommendations(s.movieId).results }.getOrDefault(emptyList()) }
        }
        val topGenres = profile.topGenres(2)
        val byGenre = async {
            if (topGenres.isEmpty()) emptyList()
            else runCatching { api.discover(topGenres.joinToString("|"), page = (1..3).random()).results }
                .getOrDefault(emptyList())
        }
        val popular = async { runCatching { api.popular((1..5).random()).results }.getOrDefault(emptyList()) }

        seedJobs.awaitAll().forEach { add(it, countHit = true) }
        add(byGenre.await(), countHit = false)
        add(popular.await(), countHit = false)

        val seedCount = seeds.size
        pool.values
            .filter { it.movieId !in known && it.posterPath != null && it.overview.isNotBlank() }
            // Un poco de ruido para no ver siempre el mismo orden (exploración).
            .sortedByDescending {
                Recommender.score(it, profile, hits[it.movieId] ?: 0, seedCount) + Random.nextDouble(0.0, 0.08)
            }
    }

    suspend fun importPartner(entries: List<Entry>) = dao.replaceAll(OWNER_PARTNER, entries)
    suspend fun clearPartner() = dao.clear(OWNER_PARTNER)
}
