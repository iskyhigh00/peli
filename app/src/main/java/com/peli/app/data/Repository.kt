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

class Repository(private val db: AppDb, private val api: TmdbApi) {
    private companion object {
        const val PAGE_SIZE = 500
        const val MAX_PASSES = 2
        const val MAX_CONCURRENT_CALLS = 4
        const val SEARCH_PAGES_WITH_YEARS = 3
    }

    private val dao = db.entries()
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
        Pool(usable.map { it.first }, usable.associate { it.first.movieId to it.second }, profile)
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
        fun add(result: Result<List<TmdbMovie>>, seed: Entry? = null) {
            tally.record(result)?.forEachIndexed { pos, m ->
                found.putIfAbsent(m.id, m.toEntry(OWNER_ME))
                m.vote_count?.let { votes[m.id] = it }
                if (seed != null) contributions.getOrPut(m.id) { ArrayList() }.add(Recommender.seedStrength(seed) to pos)
            }
        }
        seedCalls.forEach { (seed, d) -> add(d.await(), seed) }
        others.forEach { add(it.await()) }

        // Si NINGUNA fuente respondió, es un fallo de red o de servicio (lo decide quien llama).
        if (tally.failed > 0 && tally.ok > 0) com.peli.app.log.AppLog.w("Candidatas", "${tally.failed} fuentes fallaron y ${tally.ok} respondieron")
        synchronized(stashLock) {
            for ((id, e) in found) {
                if (years != null && e !in years) continue
                stash.add(e, CandMeta(votes[id], contributions[id]?.let { c -> Recommender.collab(c) } ?: 0.0))
            }
        }
        tally
    }

    suspend fun importPartner(entries: List<Entry>) = dao.replaceAll(OWNER_PARTNER, entries)
    suspend fun clearPartner() = dao.clear(OWNER_PARTNER)
}
