package com.peli.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class Repository(private val db: AppDb, private val api: TmdbApi) {
    private companion object {
        const val MIN_FRESH = 12
        const val SEARCH_PAGES_WITH_YEARS = 3
    }

    private val dao = db.entries()

    val mine = dao.observe(OWNER_ME)
    val partner = dao.observe(OWNER_PARTNER)

    /**
     * Búsqueda por título. TMDB no filtra una búsqueda por rango de años, así que con rango se piden
     * varias páginas y se filtra aquí (la primera página propaga el error; las demás son opcionales).
     */
    suspend fun search(q: String, years: YearRange? = null): List<Entry> {
        if (years == null) return api.search(q).results.map { it.toEntry(OWNER_ME) }
        val pages = coroutineScope {
            val first = async { api.search(q, 1).results }
            val rest = (2..SEARCH_PAGES_WITH_YEARS).map { p ->
                async { runCatching { api.search(q, p).results }.getOrDefault(emptyList()) }
            }
            listOf(first.await()) + rest.awaitAll()
        }
        return pages.flatten().map { it.toEntry(OWNER_ME) }.distinctBy { it.movieId }.filter { it in years }
    }

    private operator fun YearRange.contains(e: Entry) = contains(e.year)
    suspend fun popular(page: Int) = api.popular(page).results.map { it.toEntry(OWNER_ME) }

    /** Aplica un cambio sobre mi marca de la película; si queda vacía se borra. Devuelve el resultado. */
    suspend fun update(base: Entry, change: (Entry) -> Entry): Entry {
        val current = dao.get(OWNER_ME, base.movieId) ?: base.copy(owner = OWNER_ME)
        val next = change(current).copy(updatedAt = System.currentTimeMillis())
        if (next.isEmpty) dao.delete(OWNER_ME, next.movieId) else dao.upsert(next)
        return next
    }

    /**
     * Valoraciones de una película: TMDB (nota + reseñas) y, con clave de OMDb, IMDb / Rotten Tomatoes /
     * Metacritic. Si falla algo opcional (reseñas, OMDb) se muestra lo que sí llegó; si fallan los
     * detalles de TMDB, el error se propaga para poder reintentar.
     */
    suspend fun insights(movieId: Int): MovieInsights = coroutineScope {
        val details = async { api.details(movieId) }
        val reviews = async { runCatching { api.reviews(movieId).results }.getOrDefault(emptyList()) }
        val d = details.await()
        val imdbId = d.external_ids?.imdb_id?.takeIf { it.isNotBlank() }
        val omdb = if (Omdb.configured && imdbId != null) {
            runCatching { Omdb.api.byImdb(imdbId, com.peli.app.BuildConfig.OMDB_API_KEY) }
                .getOrNull()?.takeIf { it.response == "True" }
        } else null
        MovieInsights(
            tmdbRating = d.vote_average?.takeIf { it > 0 },
            tmdbVotes = d.vote_count ?: 0,
            imdbRating = omdb?.imdbRating?.toDoubleOrNull(),
            imdbVotes = Ratings.parseVotes(omdb?.imdbVotes),
            rottenTomatoes = Ratings.parsePercent(omdb?.ratings?.firstOrNull { it.source == "Rotten Tomatoes" }?.value),
            metacritic = Ratings.parseMetascore(omdb?.metascore)
                ?: Ratings.parseMetascore(omdb?.ratings?.firstOrNull { it.source == "Metacritic" }?.value),
            reviews = reviews.await()
                .filter { !it.content.isNullOrBlank() }
                .map { r ->
                    Review(
                        author = r.author?.takeIf { it.isNotBlank() } ?: "Anónimo",
                        rating = r.author_details?.rating?.toInt()?.takeIf { it in 1..10 },
                        text = Ratings.cleanReview(r.content.orEmpty()),
                        date = r.created_at.orEmpty().take(10),
                    )
                }
                .take(5),
        )
    }

    /** Mi marca actual de una película (null si no la marqué). */
    suspend fun snapshot(id: Int): Entry? = dao.get(OWNER_ME, id)

    /** Vuelve a [previous] (o quita la marca si era null). Devuelve el estado resultante. */
    suspend fun restore(base: Entry, previous: Entry?): Entry {
        val cleared = base.copy(owner = OWNER_ME, watchlist = false, liked = false, seen = false, disliked = false)
        val result = (previous ?: cleared).copy(updatedAt = System.currentTimeMillis())
        if (result.isEmpty) dao.delete(OWNER_ME, result.movieId) else dao.upsert(result)
        return result
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

    // ---- Cuenta de Google y amigos -------------------------------------------

    suspend fun setPartner(list: List<Entry>) = dao.replaceAll(OWNER_PARTNER, list)

    /** Sube un cambio mío a mi cuenta (también las marcas vaciadas, para que mis amigos las vean quitadas). */
    suspend fun pushAccount(userId: String, e: Entry) = Social.api.upsert(listOf(e.toUserEntry(userId)))

    data class AccountSync(val friends: List<Friend>, val partner: List<Entry>?)

    /**
     * Un ciclo con mi cuenta: sube lo marcado desde [since] (todo si es 0), trae amigos y solicitudes y,
     * si hay un amigo aceptado elegido, sus marcas. Es idempotente.
     */
    suspend fun syncAccount(userId: String, mine: List<Entry>, since: Long, friendId: String?): AccountSync {
        val rows = mine.filter { since == 0L || it.updatedAt > since }.map { it.toUserEntry(userId) }
        if (rows.isNotEmpty()) Social.api.upsert(rows)
        val friends = Social.api.myFriends()
        val partner = friendId?.takeIf { id -> friends.any { it.friend_id == id && it.accepted } }
            ?.let { id -> Social.api.entriesOf("eq.$id").map { it.toEntry() }.filter { !it.isEmpty } }
        return AccountSync(friends, partner)
    }

    suspend fun myProfile(): Profile? = Social.api.myProfile().firstOrNull()
    suspend fun createProfile(username: String): Profile =
        Social.api.createProfile(mapOf("p_username" to username)).first()
    suspend fun requestFriend(query: String): String = Social.api.requestFriend(mapOf("p_query" to query))
    suspend fun acceptFriend(id: String) = Social.api.acceptFriend(mapOf("p_friend" to id))
    suspend fun removeFriend(id: String) = Social.api.removeFriend(mapOf("p_friend" to id))
    suspend fun friends(): List<Friend> = Social.api.myFriends()

    /**
     * Sugerencias para "Descubrir", ordenadas por [Recommender]:
     *  - candidatas = recomendaciones de TMDB para mis últimas "me gustó"
     *    + mejor valoradas de mis géneros favoritos + algo popular (exploración).
     *  - sin historial aún: populares.
     */
    suspend fun recommend(
        marked: List<Entry>,
        avoid: Set<Int> = emptySet(),
        noise: Double = 0.08,
        years: YearRange? = null,
    ): List<Entry> = coroutineScope {
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
        val genreList = topGenres.joinToString("|")

        // Con rango de años, TMDB filtra por fecha de estreno: así hay candidatas de sobra en ese rango.
        // Si el rango llega al año actual o futuro, casi no hay votos todavía: se baja el mínimo.
        val minVotes = if (years != null && years.to >= ReleaseInfo.currentYear()) 10 else 100
        val byYears = years?.let { r ->
            listOf(1, (2..4).random()).map { p ->
                async {
                    runCatching {
                        api.discover(null, p, "popularity.desc", minVotes, r.startDate, r.endDate).results
                    }.getOrDefault(emptyList())
                }
            }
        }.orEmpty()
        val byGenreYears = async {
            if (years == null || topGenres.isEmpty()) emptyList()
            else runCatching {
                api.discover(genreList, (1..2).random(), "popularity.desc", minVotes, years.startDate, years.endDate).results
            }.getOrDefault(emptyList())
        }
        val byGenre = async {
            if (years != null || topGenres.isEmpty()) emptyList()
            else runCatching { api.discover(genreList, page = (1..3).random()).results }.getOrDefault(emptyList())
        }
        val popular = async {
            if (years != null) emptyList()
            else runCatching { api.popular((1..5).random()).results }.getOrDefault(emptyList())
        }

        seedJobs.awaitAll().forEach { add(it, countHit = true) }
        byYears.awaitAll().forEach { add(it, countHit = false) }
        add(byGenreYears.await(), countHit = false)
        add(byGenre.await(), countHit = false)
        add(popular.await(), countHit = false)

        val seedCount = seeds.size
        // Más ruido = más sorpresa en el orden (exploración).
        val ranked = Recommender.rank(
            candidates = pool.values.filter {
                it.movieId !in known && it.posterPath != null && it.overview.isNotBlank() && (years == null || it in years)
            },
            profile = profile,
            hits = hits,
            seedCount = seedCount,
            noise = noise,
        )
        // Prefiere lo que aún no se mostró; si casi no queda, no se descarta nada.
        val fresh = ranked.filter { it.movieId !in avoid }
        if (fresh.size >= MIN_FRESH) fresh else ranked
    }

    suspend fun importPartner(entries: List<Entry>) = dao.replaceAll(OWNER_PARTNER, entries)
    suspend fun clearPartner() = dao.clear(OWNER_PARTNER)
}
