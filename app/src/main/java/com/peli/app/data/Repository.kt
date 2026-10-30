package com.peli.app.data

import androidx.room.withTransaction
import com.google.gson.Gson
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

class Repository(private val db: AppDb, private val api: TmdbApi) {
    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_PASSES = 2
        const val MAX_CONCURRENT_CALLS = 4
        const val SEARCH_PAGES_WITH_YEARS = 3
        const val FILTER_CHUNK = 6
        const val PROFILE_FACT_FETCH = 25
        const val BACKGROUND_BATCH = 40
        const val ENRICH_TOP = 24
        const val SAFE_SEEDS = 8
        const val SAFE_SHORTLIST = 25
        const val OMDB_PAUSE_MS = 6L * 60 * 60 * 1000
        const val DAY_MS = 86_400_000L
    }

    private val dao = db.entries()
    private val factsDao = db.facts()
    private val gson = Gson()
    private val outbox = Outbox(RoomOutboxTable(db.outbox()))

    /** Destinos de envío activos ahora; lo asigna PeliApp. Cada marca se encola para los destinos de ESE momento. */
    var destinations: () -> List<String> = { emptyList() }

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
                async { attempt { api.search(q, p).results }.getOrDefault(emptyList()) }
            }
            listOf(first.await()) + rest.awaitAll()
        }
        return pages.flatten().map { it.toEntry(OWNER_ME) }.distinctBy { it.movieId }.filter { it in years }
    }

    private operator fun YearRange.contains(e: Entry) = contains(e.year)
    suspend fun popular(page: Int) = api.popular(page).results.map { it.toEntry(OWNER_ME) }

    /** Serializa leer-modificar-guardar de mis marcas: dos cambios seguidos sobre un título no se pisan. */
    private val writeLock = Mutex()

    /**
     * Aplica un cambio sobre MI marca de la película; si queda vacía se borra. Devuelve el resultado.
     * Si aún no tengo marca, se parte de los metadatos de [base] sin heredar nada de nadie más.
     */
    suspend fun update(base: Entry, change: (Entry) -> Entry): Entry = writeLock.withLock {
        db.withTransaction {
            val next = Marks.apply(dao.get(OWNER_ME, base.movieId), base, System.currentTimeMillis(), change)
            store(next)
            next
        }
    }

    /** Guarda mi marca y, en la misma transacción, la encola para cada destino activo (una marca vaciada también). */
    private suspend fun store(next: Entry) {
        if (next.isEmpty) dao.delete(OWNER_ME, next.movieId) else dao.upsert(next)
        val payload = gson.toJson(next)
        for (dest in destinations()) outbox.enqueue(dest, next.movieId, payload)
    }

    /** Encola TODO lo guardado hacia un destino (al activarlo): los envíos son idempotentes. */
    suspend fun enqueueAll(dest: String) = writeLock.withLock {
        db.withTransaction {
            for (e in dao.all(OWNER_ME)) outbox.enqueue(dest, e.movieId, gson.toJson(e))
        }
    }

    suspend fun pending(dest: String): Int = outbox.pending(dest)

    /** Envía lo pendiente de un destino, por lotes. Un solo llamador a la vez (lo garantiza el ViewModel). */
    suspend fun drain(dest: String, deviceId: String): Int = outbox.drain(dest) { items ->
        val entries = items.map { gson.fromJson(it.payload, Entry::class.java) }
        when {
            dest.startsWith("acct:") -> {
                val uid = dest.removePrefix("acct:")
                check(Social.currentUserId() == uid) { "La sesión activa no es la de este destino" }
                Social.api.upsert(entries.map { it.toUserEntry(uid) })
            }
            dest.startsWith("room:") -> {
                val room = dest.removePrefix("room:")
                Remote.api.upsert(room, entries.map { it.toRemote(room, deviceId) })
            }
            else -> error("Destino desconocido: $dest")
        }
    }

    // ---- Colecciones por cuenta (sin borrar ni mezclar) ----------------------

    suspend fun meCount(): Int = dao.count(OWNER_ME)
    suspend fun stashCount(scope: String): Int = dao.count(Scopes.stash(scope))
    suspend fun switchScope(from: String, to: String) = writeLock.withLock { dao.switchScope(from, to) }

    /**
     * Valoraciones de una película: TMDB (nota + reseñas) y, con clave de OMDb, IMDb / Rotten Tomatoes /
     * Metacritic. Si falla algo opcional (reseñas, OMDb) se muestra lo que sí llegó; si fallan los
     * detalles de TMDB, el error se propaga para poder reintentar.
     */
    suspend fun insights(movieId: Int): MovieInsights = coroutineScope {
        val details = async { api.details(movieId) }
        val reviews = async { attempt { api.reviews(movieId).results }.getOrDefault(emptyList()) }
        val d = details.await()
        val imdbId = d.external_ids?.imdb_id?.takeIf { it.isNotBlank() }
        val omdb = if (Omdb.configured && imdbId != null) {
            attempt { Omdb.api.byImdb(imdbId, com.peli.app.BuildConfig.OMDB_API_KEY) }
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

    // ---- Datos extra por película (palabras clave, reparto, valoraciones y premios) ----------------

    /** Hasta cuándo no se vuelve a llamar a OMDb (límite diario alcanzado o clave rechazada). */
    private val omdbBlockedUntil = AtomicLong(0)

    /** ¿OMDb está bloqueado ahora? (límite diario o clave inválida) */
    val omdbBlocked get() = System.currentTimeMillis() < omdbBlockedUntil.get()

    /** Minutos que faltan para volver a consultar OMDb (0 si no está bloqueado). */
    val omdbBlockedMinutes get() = ((omdbBlockedUntil.get() - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0L).toInt()

    /** El límite diario de OMDb se renueva a medianoche UTC (un minuto de margen). */
    private fun nextUtcMidnight(now: Long) = (now / DAY_MS + 1) * DAY_MS + 60_000L

    /** Lo que ya se guardó de esas películas; no usa la red. */
    suspend fun cachedFacts(ids: Collection<Int>): Map<Int, MovieFacts> =
        ids.distinct().chunked(400).flatMap { factsDao.get(it) }.associate { it.movieId to it.toFacts() }

    /**
     * Datos extra de una película: se piden a TMDB una sola vez y, si [needOmdb], también a OMDb (una sola vez).
     * Un fallo de red no guarda nada (se reintenta después); "no existe en OMDb" sí queda anotado.
     * Devuelve lo que haya (puede faltar la parte que no se pudo pedir).
     */
    suspend fun ensureFacts(id: Int, needOmdb: Boolean): MovieFacts? = gate.withPermit {
        val now = System.currentTimeMillis()
        var f = factsDao.one(id)?.toFacts()
        if (f == null || f.needsTmdb) {
            val d = attempt { api.details(id, "external_ids,keywords,credits") }.getOrNull()
            if (d != null) {
                val director = d.credits?.crew.orEmpty().firstOrNull { it.job == "Director" }
                f = (f ?: MovieFacts(id)).copy(
                    imdbId = d.external_ids?.imdb_id?.takeIf { it.isNotBlank() },
                    keywords = d.keywords?.keywords.orEmpty().mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotEmpty() } }.take(12),
                    director = director?.name?.takeIf { it.isNotBlank() },
                    directorId = director?.id,
                    actors = d.credits?.cast.orEmpty().mapNotNull { it.name?.trim()?.takeIf { n -> n.isNotEmpty() } }.take(8),
                    tmdbAt = now,
                )
            }
        }
        if (f != null && needOmdb && !f.hasOmdb) f = fillOmdb(f, now)
        f?.also { factsDao.put(it.toRow()) }
    }

    private suspend fun fillOmdb(f: MovieFacts, now: Long): MovieFacts {
        // Solo se puede saber que no está en IMDb cuando TMDB ya respondió (sin datos de TMDB no se sabe el id).
        if (!f.hasTmdb) return f
        val imdb = f.imdbId ?: return f.copy(omdbAt = now)
        if (!Omdb.configured || omdbBlocked) return f
        val call = attempt { Omdb.api.byImdb(imdb, com.peli.app.BuildConfig.OMDB_API_KEY) }
        val http = call.exceptionOrNull() as? retrofit2.HttpException
        if (http?.code() == 401) {
            val body = runCatching { http?.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
            val daily = body.contains("limit", ignoreCase = true)
            omdbBlockedUntil.set(if (daily) nextUtcMidnight(now) else now + OMDB_PAUSE_MS)
            com.peli.app.log.AppLog.w("OMDb", "401: ${if (daily) "límite diario (se renueva a medianoche UTC)" else "clave rechazada"} $body")
            return f
        }
        val r = call.getOrNull() ?: return f
        if (r.response != "True") {
            val error = r.error.orEmpty()
            return when {
                error.contains("limit", ignoreCase = true) || error.contains("key", ignoreCase = true) -> {
                    omdbBlockedUntil.set(if (error.contains("limit", ignoreCase = true)) nextUtcMidnight(now) else now + OMDB_PAUSE_MS)
                    com.peli.app.log.AppLog.w("OMDb", "no disponible: $error")
                    f
                }
                error.contains("not found", ignoreCase = true) || error.contains("incorrect", ignoreCase = true) -> f.copy(omdbAt = now)
                else -> f
            }
        }
        val awards = Awards.parse(r.awards)
        return f.copy(
            imdbRating = r.imdbRating?.toDoubleOrNull(),
            imdbVotes = Ratings.parseVotes(r.imdbVotes),
            rottenTomatoes = Ratings.parsePercent(r.ratings?.firstOrNull { it.source == "Rotten Tomatoes" }?.value),
            metacritic = Ratings.parseMetascore(r.metascore)
                ?: Ratings.parseMetascore(r.ratings?.firstOrNull { it.source == "Metacritic" }?.value),
            oscarWins = awards.oscarWins,
            oscarNominations = awards.oscarNominations,
            wins = awards.wins,
            nominations = awards.nominations,
            omdbAt = now,
        )
    }

    /** Resultado de aplicar los filtros: lo que pasó, lo que no (para no volver a evaluarlo) y cuántas quedaron sin datos. */
    class FilterPass(val kept: List<Entry>, val rejected: Set<Int>, val unknown: Int, val limited: Boolean, val fetched: Int)

    /**
     * Aplica [filters] a [candidates] (ya en el orden en que se quieren): la nota de TMDB se comprueba al instante y
     * lo demás con los datos guardados; lo que falta se pide en tandas hasta juntar [want] aceptadas o gastar
     * [maxFetch] películas nuevas. Las que no se alcanzaron a evaluar quedan fuera de esta vuelta, no rechazadas.
     */
    suspend fun applyFilters(candidates: List<Entry>, filters: MovieFilters, want: Int, maxFetch: Int): FilterPass {
        val rejected = HashSet<Int>()
        // Lo que por su nota de TMDB no puede cumplir se descarta sin gastar consultas de OMDb.
        val base = candidates.filter { e ->
            (filters.acceptsTmdb(e.rating) && filters.worthChecking(e.rating)).also { if (!it) rejected += e.movieId }
        }
        if (!filters.needsOmdb) return FilterPass(base, rejected, 0, false, 0)
        val facts = cachedFacts(base.map { it.movieId }).toMutableMap()
        val kept = ArrayList<Entry>()
        var fetched = 0
        var unknown = 0
        var limited = false
        for (chunk in base.chunked(FILTER_CHUNK)) {
            if (kept.size >= want) break
            val missing = chunk.filter { facts[it.movieId]?.hasOmdb != true }
            if (missing.isNotEmpty()) {
                if (fetched >= maxFetch || omdbBlocked) {
                    limited = true
                } else {
                    fetched += missing.size
                    coroutineScope { missing.map { e -> async { e.movieId to ensureFacts(e.movieId, true) } }.awaitAll() }
                        .forEach { (id, f) -> if (f != null) facts[id] = f }
                }
            }
            for (e in chunk) {
                val f = facts[e.movieId]
                when {
                    f?.hasOmdb != true -> unknown++
                    filters.accepts(e.rating, f) -> kept += e
                    else -> rejected += e.movieId
                }
            }
        }
        return FilterPass(kept, rejected, unknown, limited, fetched)
    }

    /** Directores con 3 o más películas conocidas (las que la app tiene guardadas), ordenados por películas y premios. */
    suspend fun topDirectors(): List<DirectorStat> = Directors.rank(factsDao.withDirector().map { it.toFacts() })

    private val filmographies = HashMap<Int, List<Entry>>()

    /**
     * Las películas que dirigió esa persona (TMDB), de más a menos votadas. Un fallo de red se propaga; lo ya traído se
     * recuerda mientras la app siga abierta.
     */
    suspend fun directorFilmography(directorId: Int): List<Entry> {
        synchronized(filmographies) { filmographies[directorId]?.let { return it } }
        val list = api.personCredits(directorId).crew.orEmpty()
            .filter { it.job == "Director" && !it.title.isNullOrBlank() }
            .distinctBy { it.id }
            .sortedWith(compareByDescending<TmdbCreditMovie> { it.vote_count ?: 0 }.thenBy { it.id })
            .map { c ->
                TmdbMovie(
                    id = c.id, title = c.title, overview = c.overview, poster_path = c.poster_path,
                    release_date = c.release_date, genre_ids = c.genre_ids, vote_average = c.vote_average,
                    vote_count = c.vote_count, original_title = c.original_title,
                ).toEntry(OWNER_ME)
            }
        synchronized(filmographies) { filmographies[directorId] = list }
        return list
    }

    private val trailerCache = HashMap<Int, String?>()

    /**
     * Clave de YouTube del mejor tráiler de la película; null si no tiene. Un fallo de red se propaga (para poder
     * reintentar); lo ya resuelto se recuerda mientras la app siga abierta.
     */
    suspend fun trailerKey(movieId: Int): String? {
        synchronized(trailerCache) { if (trailerCache.containsKey(movieId)) return trailerCache[movieId] }
        val key = Trailers.pick(
            api.videos(movieId).results.orEmpty().map { TrailerVideo(it.key, it.site, it.type, it.official, it.iso_639_1) },
        )
        synchronized(trailerCache) { trailerCache[movieId] = key }
        return key
    }

    /** Mi marca actual de una película (null si no la marqué). */
    suspend fun snapshot(id: Int): Entry? = dao.get(OWNER_ME, id)

    /** Vuelve a [previous] (o quita la marca si era null). Devuelve el estado resultante. */
    suspend fun restore(base: Entry, previous: Entry?): Entry {
        return writeLock.withLock {
            db.withTransaction {
                val result = Marks.restored(base, previous, System.currentTimeMillis())
                store(result)
                result
            }
        }
    }

    // ---- Cuenta de Google y amigos -------------------------------------------

    suspend fun setPartner(list: List<Entry>) = dao.replaceAll(OWNER_PARTNER, list)

    /** Mis amigos y, si hay un amigo aceptado elegido, su lista COMPLETA (se pide por páginas antes de reemplazar nada). */
    class AccountPull(val friends: List<Friend>, val partner: List<Entry>?)

    suspend fun pullAccount(friendId: String?): AccountPull {
        val friends = Social.api.myFriends()
        val partner = friendId?.takeIf { id -> friends.any { it.friend_id == id && it.accepted } }?.let { id ->
            Pages.fetchAll(PAGE_SIZE) { offset, limit -> Social.api.entriesOf("eq.$id", limit = limit, offset = offset) }
                .map { it.toEntry() }.filter { !it.isEmpty }.distinctBy { it.movieId }
        }
        return AccountPull(friends, partner)
    }

    /** La lista completa de las demás personas de la sala (modo anterior), por páginas. */
    suspend fun pullRoom(room: String, userId: String): List<Entry> =
        Pages.fetchAll(PAGE_SIZE) { offset, limit ->
            Remote.api.others(room, "eq.$room", "neq.$userId", limit = limit, offset = offset)
        }.map { it.toEntry() }.filter { !it.isEmpty }.distinctBy { it.movieId }

    suspend fun myProfile(): Profile? = Social.api.myProfile().firstOrNull()
    suspend fun createProfile(username: String): Profile =
        Social.api.createProfile(mapOf("p_username" to username)).first()
    suspend fun requestFriend(query: String): String = Social.api.requestFriend(mapOf("p_query" to query))
    suspend fun acceptFriend(id: String) = Social.api.acceptFriend(mapOf("p_friend" to id))
    suspend fun removeFriend(id: String) = Social.api.removeFriend(mapOf("p_friend" to id))
    suspend fun friends(): List<Friend> = Social.api.myFriends()

    /** Candidatas ya buscadas en TMDB, sin lo que ya marqué, con su perfil y datos extra para puntuarlas. */
    class Pool(val candidates: List<Entry>, val meta: Map<Int, CandMeta>, val profile: Recommender.Profile)

    /** Páginas ya recorridas de cada consulta de TMDB (se guardan en el teléfono); lo asigna PeliApp. */
    var cursors: PageCursors = MemoryCursors()

    private val poolMutex = Mutex()
    private val stashLock = Any()
    private val stash = CandidateStash()
    private var stashKey: String? = null
    private val gate = Semaphore(MAX_CONCURRENT_CALLS)

    /** Lo que ya se entregó sale del depósito compartido. */
    fun consume(ids: Collection<Int>) = synchronized(stashLock) { stash.remove(ids) }

    /**
     * Candidatas para la lista principal y para Descubrir. Comparten un depósito de sobrantes: solo se pide a
     * TMDB cuando faltan al menos [needed] candidatas utilizables ([isUsable] aplica la memoria de lo mostrado).
     * Cada pasada recorre varias fuentes con cursores de página (no repite páginas), rotando orden de TMDB,
     * décadas y semillas; como mucho [MAX_PASSES] pasadas por llamada. Un máximo de [MAX_CONCURRENT_CALLS]
     * peticiones a la vez. Si ninguna fuente responde y no hay nada guardado, falla (no es "sin resultados").
     */
    suspend fun candidatePool(
        marked: List<Entry>,
        years: YearRange? = null,
        needed: Int = 0,
        isUsable: (Int) -> Boolean = { true },
        random: kotlin.random.Random = kotlin.random.Random.Default,
    ): Pool = poolMutex.withLock {
        val key = years?.encode().orEmpty()
        val known = marked.map { it.movieId }.toSet()
        val profile = Recommender.buildProfile(marked)
        synchronized(stashLock) {
            if (stashKey != key) {
                stash.clear()
                stashKey = key
            }
        }
        fun usableCount() = synchronized(stashLock) { stash.usable(known, isUsable).size }
        var passes = 0
        while (usableCount() < needed && passes < MAX_PASSES) {
            val tally = fetchPass(marked, profile, years, random)
            passes++
            if (tally.allFailed) {
                if (usableCount() == 0) tally.requireAny()
                break
            }
        }
        val usable = synchronized(stashLock) { stash.usable(known) }
        // Con los datos extra de lo que ya marqué (y de las mejores candidatas) el perfil y el puntaje afinan más.
        val profileFacts = attempt { profileFacts(marked, PROFILE_FACT_FETCH) }.getOrDefault(emptyMap())
        val rich = if (profileFacts.isEmpty()) profile else Recommender.buildProfile(marked, facts = profileFacts)
        val candidateFacts = attempt { candidateFacts(usable, rich) }.getOrDefault(emptyMap())
        Pool(
            usable.map { it.first },
            usable.associate { (e, m) -> e.movieId to (candidateFacts[e.movieId]?.let { m.copy(facts = it) } ?: m) },
            rich,
        )
    }

    /**
     * Datos extra de las películas que marqué con una señal de gusto. Se piden las que falten (hasta [maxFetch]
     * por llamada; con 0 solo se leen los guardados).
     */
    suspend fun profileFacts(marked: List<Entry>, maxFetch: Int): Map<Int, MovieFacts> {
        val ids = marked.filter { Recommender.signalOf(it) != null }.map { it.movieId }
        val facts = cachedFacts(ids).toMutableMap()
        val missing = ids.filter { facts[it]?.needsTmdb != false }.take(maxFetch)
        if (missing.isNotEmpty()) {
            coroutineScope { missing.map { id -> async { ensureFacts(id, false) } }.awaitAll() }
                .forEach { f -> if (f != null) facts[f.movieId] = f }
        }
        return facts
    }

    /** Completa en segundo plano los datos extra de todo lo que marqué (en tandas, hasta que no falte nada). */
    suspend fun enrichMarked(marked: List<Entry>) {
        var left = Int.MAX_VALUE
        while (true) {
            val ids = marked.filter { Recommender.signalOf(it) != null }.map { it.movieId }
            val have = cachedFacts(ids)
            val missing = ids.count { have[it]?.needsTmdb != false }
            if (missing == 0 || missing >= left) return   // terminó, o la última tanda no avanzó (sin red)
            left = missing
            profileFacts(marked, BACKGROUND_BATCH)
        }
    }

    /**
     * Las películas "seguras" para mí, de la más a la menos probable. Parte de lo que califiqué alto: pide a TMDB
     * las parecidas de cada una y los mejores títulos de mis géneros, se queda con las que tienen buena nota y
     * votos, completa los datos extra de las mejores y las ordena con [Safe.score]. Si ninguna fuente responde falla.
     */
    suspend fun safePicks(marked: List<Entry>, exclude: Set<Int>, limit: Int): List<SafePick> {
        val profileFacts = attempt { profileFacts(marked, PROFILE_FACT_FETCH) }.getOrDefault(emptyMap())
        val profile = Recommender.buildProfile(marked, facts = profileFacts)
        val known = marked.map { it.movieId }.toSet() + exclude
        val seeds = Safe.seeds(marked, SAFE_SEEDS)
        val genres = profile.topGenres(2).joinToString("|")

        val tally = SourceTally()
        val found = LinkedHashMap<Int, Entry>()
        val votes = HashMap<Int, Int>()
        val fromSeeds = HashMap<Int, MutableList<Pair<Double, Int>>>()
        coroutineScope {
            val seedCalls = seeds.map { s ->
                s to async { gate.withPermit { attempt { api.recommendations(s.movieId, 1).results } } }
            }
            val genreCalls = if (genres.isBlank()) emptyList() else (1..2).map { page ->
                async { gate.withPermit { attempt { api.discover(genres, page, "vote_average.desc", 1500).results } } }
            }
            for ((seed, call) in seedCalls) {
                tally.record(call.await())?.forEachIndexed { pos, m ->
                    found.putIfAbsent(m.id, m.toEntry(OWNER_ME))
                    m.vote_count?.let { votes[m.id] = it }
                    fromSeeds.getOrPut(m.id) { ArrayList() }.add(Recommender.seedStrength(seed) / (1.0 + pos / 8.0) to seed.movieId)
                }
            }
            for (call in genreCalls) {
                tally.record(call.await())?.forEach { m ->
                    found.putIfAbsent(m.id, m.toEntry(OWNER_ME))
                    m.vote_count?.let { votes[m.id] = it }
                }
            }
        }
        tally.requireAny()

        fun metaOf(id: Int) = CandMeta(
            votes = votes[id],
            collab = fromSeeds[id]?.let { c -> 1.0 - kotlin.math.exp(-c.sumOf { it.first }) } ?: 0.0,
            seeds = fromSeeds[id]?.sortedByDescending { it.first }?.take(3)?.map { it.second }.orEmpty(),
        )
        val shortlist = found.values
            .filter { it.movieId !in known && Safe.eligible(it, votes[it.movieId]) }
            .sortedWith(compareByDescending<Entry> { Recommender.baseScore(it, profile, metaOf(it.movieId)) }.thenBy { it.movieId })
            .take(SAFE_SHORTLIST)
        if (shortlist.isEmpty()) return emptyList()

        val facts = cachedFacts(shortlist.map { it.movieId }).toMutableMap()
        val missing = shortlist.map { it.movieId }.filter { facts[it]?.needsTmdb != false }
        if (missing.isNotEmpty()) {
            attempt {
                coroutineScope { missing.map { id -> async { ensureFacts(id, false) } }.awaitAll() }
                    .forEach { f -> if (f != null) facts[f.movieId] = f }
            }
        }
        return shortlist
            .map { e -> e to metaOf(e.movieId).copy(facts = facts[e.movieId]) }
            .map { (e, m) -> Triple(e, m, Safe.score(e, profile, m)) }
            .sortedWith(compareByDescending<Triple<Entry, CandMeta, Double>> { it.third }.thenBy { it.first.movieId })
            .take(limit)
            .map { (e, m, score) -> SafePick(e, score, Safe.reasons(e, m, marked, profileFacts)) }
    }

    /**
     * Completa el título original de lo que marqué antes de guardarse ese dato (en tandas, sin tocar mi marca).
     * Se anota "" cuando TMDB no trae otro, para no volver a preguntar. Si una tanda entera falla (sin red) se detiene.
     */
    suspend fun backfillOriginalTitles(marked: List<Entry>) {
        val missing = marked.filter { it.originalTitle == null }.map { it.movieId }
        for (chunk in missing.chunked(BACKGROUND_BATCH)) {
            val done = coroutineScope {
                chunk.map { id ->
                    async {
                        gate.withPermit {
                            val d = attempt { api.details(id) }.getOrNull()
                            if (d != null) dao.setOriginalTitle(OWNER_ME, id, d.original_title?.trim().orEmpty())
                            d != null
                        }
                    }
                }.awaitAll()
            }
            if (done.none { it }) return
        }
    }

    /** Datos extra de las [ENRICH_TOP] mejores candidatas (por puntaje sin ellos); el resto usa solo lo guardado. */
    private suspend fun candidateFacts(usable: List<Pair<Entry, CandMeta>>, profile: Recommender.Profile): Map<Int, MovieFacts> {
        if (usable.isEmpty()) return emptyMap()
        val facts = cachedFacts(usable.map { it.first.movieId }).toMutableMap()
        val best = usable.sortedWith(
            compareByDescending<Pair<Entry, CandMeta>> { Recommender.baseScore(it.first, profile, it.second) }.thenBy { it.first.movieId },
        )
        val missing = best.map { it.first.movieId }.filter { facts[it]?.needsTmdb != false }.take(ENRICH_TOP)
        if (missing.isNotEmpty()) {
            coroutineScope { missing.map { id -> async { ensureFacts(id, false) } }.awaitAll() }
                .forEach { f -> if (f != null) facts[f.movieId] = f }
        }
        return facts
    }

    /** Una pasada por todas las fuentes; lo encontrado va al depósito. Los cursores avanzan solo si la llamada salió bien. */
    private suspend fun fetchPass(
        marked: List<Entry>,
        profile: Recommender.Profile,
        years: YearRange?,
        random: kotlin.random.Random,
    ): SourceTally = coroutineScope {
        val tally = SourceTally()
        val rotation = cursors.get("rotation")
        cursors.set("rotation", rotation + 1)
        val seeds = Recommender.pickSeeds(marked, 6, random)
        // De mis 3 géneros favoritos se sortean 2: así cada vez salen cosas distintas.
        val topGenres = profile.topGenres(3).shuffled(random).take(2)
        val genreList = topGenres.joinToString("|")
        val sort = listOf("vote_average.desc", "popularity.desc", "vote_count.desc")[rotation % 3]
        val votesFloor = listOf(500, 100, 50)[rotation % 3]
        val decade = listOf(1970, 1980, 1990, 2000, 2010, 2020)[rotation % 6]
        // Con rango que llega al año actual o futuro casi no hay votos todavía: se baja el mínimo (hasta 0).
        val future = years != null && years.to >= ReleaseInfo.currentYear()
        val yearVotes = if (future) 0 else 100

        /** Pide la página que toca de una consulta; el cursor solo avanza si salió bien. */
        fun call(key: String, start: Int? = null, fetch: suspend (Int) -> TmdbPage) = async {
            gate.withPermit {
                attempt {
                    val saved = cursors.get(key)
                    val page = if (start != null && saved == 1) start else saved
                    val r = fetch(page)
                    cursors.set(key, Cursors.next(page, r.total_pages))
                    r.results
                }
            }
        }

        val seedCalls = seeds.map { s -> s to call("rec:${s.movieId}") { api.recommendations(s.movieId, it) } }
        val others = ArrayList<Deferred<Result<List<TmdbMovie>>>>()
        if (years != null) {
            val y = years.encode()
            others += call("yr:$y") { api.discover(null, it, "popularity.desc", yearVotes, years.startDate, years.endDate) }
            if (topGenres.isNotEmpty()) {
                others += call("gy:$genreList:$y") { api.discover(genreList, it, "popularity.desc", yearVotes, years.startDate, years.endDate) }
            }
        } else {
            if (topGenres.isNotEmpty()) {
                others += call("g:$genreList:$sort") { api.discover(genreList, it, sort, votesFloor) }
                others += call("gp:$genreList") { api.discover(genreList, it, "popularity.desc", votesFloor) }
            }
            // Una década rotativa (con mis géneros si los hay): material que no sale en las listas de siempre.
            others += call("dec:$decade:$genreList") {
                api.discover(genreList.ifBlank { null }, it, "popularity.desc", 100, "$decade-01-01", "${decade + 9}-12-31")
            }
            others += call("popular") { api.popular(it) }
            // Sin gustos aún se piden dos páginas distintas (la segunda arranca lejos de la primera).
            if (profile.isEmpty) others += call("popular:b", start = 9) { api.popular(it) }
        }

        val found = LinkedHashMap<Int, Entry>()
        val votes = HashMap<Int, Int>()
        val contributions = HashMap<Int, MutableList<Pair<Double, Int>>>()
        val seedsOf = HashMap<Int, MutableList<Pair<Double, Int>>>()
        fun add(result: Result<List<TmdbMovie>>, seed: Entry? = null) {
            tally.record(result)?.forEachIndexed { pos, m ->
                found.putIfAbsent(m.id, m.toEntry(OWNER_ME))
                m.vote_count?.let { votes[m.id] = it }
                if (seed != null) {
                    contributions.getOrPut(m.id) { ArrayList() }.add(Recommender.seedStrength(seed) to pos)
                    seedsOf.getOrPut(m.id) { ArrayList() }.add(Recommender.seedStrength(seed) / (1.0 + pos / 8.0) to seed.movieId)
                }
            }
        }
        seedCalls.forEach { (seed, d) -> add(d.await(), seed) }
        others.forEach { add(it.await()) }

        // Si NINGUNA fuente respondió, es un fallo de red o de servicio (lo decide quien llama).
        if (tally.failed > 0 && tally.ok > 0) com.peli.app.log.AppLog.w("Candidatas", "${tally.failed} fuentes fallaron y ${tally.ok} respondieron")
        synchronized(stashLock) {
            for ((id, e) in found) {
                if (years != null && e !in years) continue
                stash.add(
                    e,
                    CandMeta(
                        votes = votes[id],
                        collab = contributions[id]?.let { c -> Recommender.collab(c) } ?: 0.0,
                        seeds = seedsOf[id]?.sortedByDescending { it.first }?.take(3)?.map { it.second }.orEmpty(),
                    ),
                )
            }
        }
        tally
    }

    suspend fun importPartner(entries: List<Entry>) = dao.replaceAll(OWNER_PARTNER, entries)
    suspend fun clearPartner() = dao.clear(OWNER_PARTNER)
}
