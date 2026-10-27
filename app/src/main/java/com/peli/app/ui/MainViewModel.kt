package com.peli.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.peli.app.APP_VERSION
import com.peli.app.BuildConfig
import com.peli.app.CrashReporter
import com.peli.app.PeliApp
import com.peli.app.auth.GoogleToken
import com.peli.app.data.Friend
import com.peli.app.data.FriendRequestResult
import com.peli.app.data.Profile
import com.peli.app.data.Social
import com.peli.app.data.SupabaseAuth
import com.peli.app.data.Usernames
import com.peli.app.data.accountMessage
import com.peli.app.data.Entry
import com.peli.app.data.InsightsState
import com.peli.app.data.RatingSource
import com.peli.app.data.Remote
import com.peli.app.data.RoomCodes
import com.peli.app.data.YearRange
import com.peli.app.data.Sharing
import com.peli.app.data.computeMatches
import com.peli.app.data.toggleDisliked
import com.peli.app.data.toggleLiked
import com.peli.app.data.toggleSeen
import com.peli.app.data.toggleWatch
import com.peli.app.data.stateLabel
import com.peli.app.data.withRating
import com.peli.app.log.AppLog
import com.peli.app.update.Changelog
import com.peli.app.update.Updater
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Ofrece deshacer la última respuesta de "Descubrir". */
data class UndoOffer(val message: String, val card: Entry, val previous: Entry?)

/**
 * Estado de la app. Lo que el usuario elige (búsqueda, filtros, orden) vive en [SavedStateHandle]:
 * sobrevive a cambios de pestaña, navegación, rotación y recuperación tras cerrar el proceso.
 */
class MainViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val peli = app as PeliApp
    private val repo = peli.repository
    private val pairing = peli.pairing
    private val sourcePrefs = peli.sourcePrefs
    private val account = peli.account

    val mine = repo.mine.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val partner = repo.partner.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val matches = combine(repo.mine, repo.partner) { m, p -> computeMatches(m, p) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Selecciones del usuario (persisten) ---------------------------------

    val searchQuery: StateFlow<String> = saved.getStateFlow(KEY_QUERY, "")
    val searchFilter: StateFlow<SearchFilter> = saved.getStateFlow(KEY_SEARCH_FILTER, SearchFilter.All.name)
        .map { SearchFilter.valueOf(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchFilter.All)
    val mineFilter: StateFlow<MineFilter> = saved.getStateFlow(KEY_MINE_FILTER, MineFilter.All.name)
        .map { MineFilter.valueOf(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, MineFilter.All)
    val searchYears: StateFlow<YearRange?> = saved.getStateFlow(KEY_SEARCH_YEARS, "")
        .map { YearRange.decode(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mineYears: StateFlow<YearRange?> = saved.getStateFlow(KEY_MINE_YEARS, "")
        .map { YearRange.decode(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val mineSort: StateFlow<MineSort> = saved.getStateFlow(KEY_MINE_SORT, MineSort.Recent.name)
        .map { MineSort.valueOf(it) }.stateIn(viewModelScope, SharingStarted.Eagerly, MineSort.Recent)

    fun setSearchQuery(q: String) { saved[KEY_QUERY] = q }
    fun setSearchFilter(f: SearchFilter) { AppLog.d("Filtro", "buscar: ${f.name}"); saved[KEY_SEARCH_FILTER] = f.name }
    fun setMineFilter(f: MineFilter) { AppLog.d("Filtro", "mi lista: ${f.name}"); saved[KEY_MINE_FILTER] = f.name }
    fun setMineSort(s: MineSort) { AppLog.d("Filtro", "orden de mi lista: ${s.name}"); saved[KEY_MINE_SORT] = s.name }
    fun setSearchYears(r: YearRange?) { AppLog.d("Filtro", "años (buscar): ${r?.encode() ?: "todos"}"); saved[KEY_SEARCH_YEARS] = r?.encode() ?: "" }
    fun setMineYears(r: YearRange?) { AppLog.d("Filtro", "años (mi lista): ${r?.encode() ?: "todos"}"); saved[KEY_MINE_YEARS] = r?.encode() ?: "" }

    // ---- Buscar --------------------------------------------------------------

    val results = MutableStateFlow<List<Entry>>(emptyList())
    val searchLoading = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)

    /** true mientras se actualiza con el gesto de deslizar hacia abajo. */
    val refreshing = MutableStateFlow(false)

    private val searchNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var firstSearch = true
    private var pullPending = false

    /** Último lote del feed personalizado: se reutiliza al volver a la lista vacía y cambia al deslizar. */
    private var feed: List<Entry> = emptyList()
    private var feedYears: YearRange? = null

    // ---- Descubrir -----------------------------------------------------------

    /** Cola de sugerencias (sin filtrar por marcas). */
    val queue = MutableStateFlow<List<Entry>>(emptyList())

    /**
     * Lo que realmente se muestra en "Descubrir": la cola sin lo que ya marcaste, en cualquier pantalla
     * y en el momento en que lo marcas (la cola se arma por adelantado y puede traer películas ya marcadas).
     */
    val discoverQueue: StateFlow<List<Entry>> = combine(queue, repo.mine) { q, marked -> q.withoutMarked(marked) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Las que omitiste en esta sesión no vuelven a aparecer. */
    private val skipped = HashSet<Int>()
    val queueLoading = MutableStateFlow(false)
    val queueError = MutableStateFlow<String?>(null)
    val undo = MutableStateFlow<UndoOffer?>(null)
    private var refillJob: Job? = null

    // ---- Valoraciones y reseñas ------------------------------------------------

    /** ¿Hay clave de OMDb? Sin ella no hay IMDb, Rotten Tomatoes ni Metacritic. */
    val omdbAvailable = com.peli.app.data.Omdb.configured

    /** Fuentes de valoraciones que el usuario quiere ver (se eligen en Ajustes). */
    val sources = MutableStateFlow(sourcePrefs.load())

    fun toggleSource(source: RatingSource) {
        val next = sources.value.let { if (source in it) it - source else it + source }
        sources.value = next
        sourcePrefs.save(next)
        AppLog.i("Ajustes", "fuentes de valoraciones: ${next.joinToString { it.name }.ifEmpty { "ninguna" }}")
    }

    /** Valoraciones por película, cargadas al abrir su detalle y guardadas mientras la app siga abierta. */
    val insights = MutableStateFlow<Map<Int, InsightsState>>(emptyMap())

    fun loadInsights(movieId: Int, force: Boolean = false) {
        val current = insights.value[movieId]
        if (!force && (current is InsightsState.Ready || current is InsightsState.Loading)) return
        insights.update { it + (movieId to InsightsState.Loading) }
        AppLog.d("Valoraciones", "cargando película $movieId")
        launch {
            val state = try {
                InsightsState.Ready(repo.insights(movieId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Valoraciones", "falló la carga de la película $movieId", e)
                InsightsState.Failed(e.message ?: e.javaClass.simpleName)
            }
            insights.update { it + (movieId to state) }
        }
    }

    /** Mi nota (1..10, 0 la quita). Calificar implica que ya la vi; se sincroniza con la sala. */
    fun setMyRating(e: Entry, score: Int) = update(e) { it.withRating(score) }

    // ---- Avisos y actualizaciones --------------------------------------------

    val message = MutableStateFlow<String?>(null)

    /** Versión nueva disponible (null = al día o sin consultar). */
    val newVersion = MutableStateFlow<String?>(null)

    /** Progreso de descarga: -2 inactivo, -1 sin tamaño conocido, 0..100. */
    val progress = MutableStateFlow(-2)

    /** Novedades a mostrar tras actualizar (versión -> cambios). */
    val whatsNew = MutableStateFlow<List<Pair<String, List<String>>>>(emptyList())

    // ---- Sala con la pareja --------------------------------------------------

    /** ¿Hay servidor configurado? Si no, solo funciona el intercambio manual de códigos. */
    val cloudAvailable = Remote.configured

    /** Código de la sala actual (null = sin conectar). */
    val room = MutableStateFlow(pairing.room)

    /** Último error de sincronización (null = bien). */
    val syncError = MutableStateFlow<String?>(null)

    /** Matches nuevos pendientes de mostrar (los dos quieren verla). */
    val pendingMatches = MutableStateFlow<List<Entry>>(emptyList())

    private val syncLock = Mutex()

    /** Informe del último cierre inesperado (null = no hubo). Se muestra una vez al abrir la app. */
    val crashReport = MutableStateFlow<String?>(null)

    /**
     * Cualquier error no controlado dentro de una corrutina se muestra como aviso y se registra,
     * en vez de cerrar la app.
     */
    private val errorHandler = CoroutineExceptionHandler { _, e ->
        if (e !is CancellationException) {
            CrashReporter.record(getApplication(), "corrutina", e)  // también lo deja en el registro
            message.value = "Algo falló: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun launch(
        context: kotlin.coroutines.CoroutineContext = kotlin.coroutines.EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch(context + errorHandler, block = block)

    init {
        launch(Dispatchers.IO) { Updater.cleanupDownloads(getApplication()) }
        whatsNew.value = Changelog.takeUnseen(getApplication())
        launch(Dispatchers.IO) { crashReport.value = CrashReporter.pending(getApplication()) }
        if (account.session != null) launch { if (account.profile == null) loadProfile() else syncAccount() }
        checkForUpdate(manual = false)
        refillQueue()

        // Búsqueda: espera a que dejes de escribir; si llega otra consulta, la anterior se cancela
        // (así nunca se muestra una respuesta vieja). "Buscar" del teclado o deslizar la lanza al instante.
        launch {
            // El rango se aplica al instante (sin la espera de escritura); su valor inicial no cuenta como cambio.
            merge(searchQuery.map { false }, searchYears.drop(1).map { true }, searchNow.map { true }).collectLatest { immediate ->
                if (!immediate && !firstSearch) delay(SEARCH_DEBOUNCE_MS)
                firstSearch = false
                runSearch(searchQuery.value)
            }
        }

        // Tinder: cada vez que aparece una película que a los dos nos interesa, se avisa una sola vez.
        launch {
            matches.collect { list ->
                val announced = pairing.announced
                val fresh = list.filter { it.both && it.entry.movieId !in announced }
                if (fresh.isNotEmpty()) {
                    AppLog.i("Match", "nuevos: ${fresh.joinToString { it.entry.title }}")
                    pairing.announced = announced + fresh.map { it.entry.movieId }
                    pendingMatches.update { it + fresh.map { m -> m.entry } }
                }
            }
        }
    }

    // ---- Buscar: lógica ------------------------------------------------------

    private suspend fun runSearch(q: String) {
        val pull = pullPending
        if (BuildConfig.TMDB_API_KEY.isBlank()) {
            searchError.value = "Falta TMDB_API_KEY en local.properties"
            endPull()
            return
        }
        searchLoading.value = true
        searchError.value = null
        val t0 = System.currentTimeMillis()
        try {
            val years = searchYears.value
            results.value = if (q.isBlank()) loadFeed(refresh = pull, years = years) else repo.search(q.trim(), years)
            AppLog.i(
                "Buscar",
                "${if (q.isBlank()) "sugerencias${if (pull) " (actualizadas)" else ""}" else "«${q.trim()}»"} " +
                    "años=${years?.encode() ?: "todos"} → ${results.value.size} resultados (${System.currentTimeMillis() - t0} ms)",
            )
        } catch (e: CancellationException) {
            AppLog.d("Buscar", "búsqueda «$q» cancelada (llegó otra)")
            throw e
        } catch (e: Exception) {
            AppLog.w("Buscar", "falló la búsqueda «$q»", e)
            if (pull && results.value.isNotEmpty()) {
                message.value = "No se pudo actualizar: ${e.message}"
            } else {
                results.value = emptyList()
                searchError.value = "Error de red: ${e.message}"
            }
        } finally {
            searchLoading.value = false
            endPull()
        }
    }

    /**
     * Sin búsqueda, la lista principal es como "Sorpréndeme": sugerencias según tus gustos.
     * Se mantiene estable mientras navegas y, al deslizar, trae un lote distinto sin repetir lo anterior.
     */
    private suspend fun loadFeed(refresh: Boolean, years: YearRange?): List<Entry> {
        // El lote guardado solo sirve si se pidió con el mismo rango de años.
        if (!refresh && feed.isNotEmpty() && feedYears == years) return feed
        val avoid = if (refresh) feed.map { it.movieId }.toSet() else emptySet()
        // Se lee de la base (no del StateFlow, que al arrancar aún puede estar vacío) para personalizar de verdad.
        val next = repo.recommend(repo.mine.first(), avoid = avoid, noise = FEED_NOISE, years = years).take(FEED_SIZE)
        feed = next
        feedYears = years
        return next
    }

    private fun endPull() {
        if (pullPending) {
            pullPending = false
            refreshing.value = false
        }
    }

    /** "Buscar" del teclado: consulta ya, sin esperar. */
    fun submitSearch() { searchNow.tryEmit(Unit) }

    /** Deslizar hacia abajo en la lista principal: vuelve a pedir el listado completo. */
    fun refreshSearch() {
        pullPending = true
        refreshing.value = true
        searchNow.tryEmit(Unit)
    }

    // ---- Marcar películas ----------------------------------------------------

    fun toggleWant(e: Entry) = update(e) { it.toggleWatch() }
    fun toggleLiked(e: Entry) {
        launch {
            val next = applyAndPush(e) { it.toggleLiked() }
            askRating(next)
        }
    }
    fun toggleSeen(e: Entry) = update(e) { it.toggleSeen() }
    fun toggleDislike(e: Entry) = update(e) { it.toggleDisliked() }

    private fun update(e: Entry, change: (Entry) -> Entry) {
        launch { applyAndPush(e, change) }
    }

    private fun describe(e: Entry) = "«${e.title}» (${e.movieId}) [${e.stateLabel()}${if (e.myRating > 0) ", nota ${e.myRating}" else ""}]"

    /** Guarda localmente y sube el cambio a la sala (si falla, el próximo sync lo reintenta). */
    private suspend fun applyAndPush(e: Entry, change: (Entry) -> Entry): Entry {
        AppLog.i("Marca", "antes: ${describe(e)}")
        val next = repo.update(e, change)
        AppLog.i("Marca", "después: ${describe(next)}")
        push(next)
        return next
    }

    /** Película recién marcada «La vi y me gustó» sin nota: se le pide una de 1 a 10 para afinar las recomendaciones. */
    val ratePrompt = MutableStateFlow<Entry?>(null)

    private fun askRating(e: Entry) {
        if (e.liked && e.myRating == 0) {
            AppLog.d("Nota", "se pide nota para «${e.title}»")
            ratePrompt.value = e
        }
    }

    fun rateFromPrompt(score: Int) {
        val e = ratePrompt.value ?: return
        ratePrompt.value = null
        AppLog.i("Nota", "«${e.title}» → $score")
        setMyRating(e, score)
    }

    fun dismissRatePrompt() {
        ratePrompt.value?.let { AppLog.d("Nota", "omitida para «${it.title}»") }
        ratePrompt.value = null
    }

    private suspend fun push(next: Entry) {
        val s = account.session
        if (s != null && account.profile != null) {
            try {
                repo.pushAccount(s.userId, next)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                AppLog.w("Cuenta", "no se pudo subir el cambio (se reintenta en el próximo sync)", ex)
            }
            return
        }
        val r = room.value ?: return
        try {
            repo.push(r, pairing.userId, next)
        } catch (e: CancellationException) {
            throw e
        } catch (ex: Exception) {
            // el siguiente sync vuelve a subir lo marcado
            AppLog.w("Sala", "no se pudo subir el cambio (se reintenta en el próximo sync)", ex)
        }
    }

    /** La película con ese id desde cualquier lista conocida (para reabrir el detalle tras rotar). */
    fun lookup(id: Int): Entry? =
        mine.value.firstOrNull { it.movieId == id }
            ?: results.value.firstOrNull { it.movieId == id }
            ?: queue.value.firstOrNull { it.movieId == id }
            ?: partner.value.firstOrNull { it.movieId == id }

    // ---- Descubrir -----------------------------------------------------------

    /** Respuesta en "Descubrir": con ella la app aprende tus gustos y pasa a la siguiente. */
    enum class Answer { Want, SeenLiked, NotInterested, Skip }

    fun answer(e: Entry, answer: Answer) {
        // Toques rápidos: solo cuenta la tarjeta que realmente está arriba.
        if (discoverQueue.value.firstOrNull()?.movieId != e.movieId) {
            AppLog.d("Descubrir", "respuesta ignorada: «${e.title}» ya no es la tarjeta de arriba")
            return
        }
        AppLog.i("Descubrir", "$answer → «${e.title}» (${e.movieId}); cola=${discoverQueue.value.size}")
        if (answer == Answer.Skip) skipped += e.movieId
        queue.update { it.filter { x -> x.movieId != e.movieId } }
        launch {
            if (answer != Answer.Skip) {
                val previous = repo.snapshot(e.movieId)
                when (answer) {
                    Answer.Want -> applyAndPush(e) { it.copy(watchlist = true, seen = false, liked = false, disliked = false) }
                    Answer.SeenLiked -> askRating(
                        applyAndPush(e) { it.copy(liked = true, seen = true, watchlist = false, disliked = false) },
                    )
                    Answer.NotInterested -> applyAndPush(e) { it.copy(disliked = true, liked = false, watchlist = false) }
                    Answer.Skip -> Unit
                }
                undo.value = UndoOffer(
                    message = when (answer) {
                        Answer.Want -> "Guardada en «Quiero verla»"
                        Answer.SeenLiked -> "Guardada como «La vi y me gustó»"
                        else -> "Descartada"
                    },
                    card = e,
                    previous = previous,
                )
            }
            if (discoverQueue.value.size < 4) refillQueue()
        }
    }

    /** Deshace la última respuesta: restaura la marca anterior y devuelve la tarjeta al frente. */
    fun undoAnswer() {
        val offer = undo.value ?: return
        AppLog.i("Descubrir", "deshacer «${offer.card.title}»")
        undo.value = null
        launch {
            push(repo.restore(offer.card, offer.previous))
            skipped -= offer.card.movieId
            queue.update { listOf(offer.card) + it.filter { x -> x.movieId != offer.card.movieId } }
        }
    }

    fun clearUndo() { undo.value = null }

    /** Películas que a tu pareja le interesan y yo aún no marqué: van primero en "Descubrir". */
    private fun partnerPicks(): List<Entry> {
        val known = mine.value.map { it.movieId }.toSet()
        return partner.value
            .filter { it.watchlist && !it.seen && !it.disliked && it.movieId !in known }
            .map { it.copy(owner = com.peli.app.data.OWNER_ME, watchlist = false) }
    }

    /** Sugerencias: primero las de tu pareja, luego las ordenadas por el recomendador. Una carga a la vez. */
    fun refillQueue() {
        if (BuildConfig.TMDB_API_KEY.isBlank()) {
            queueError.value = "Falta TMDB_API_KEY en local.properties"
            return
        }
        if (refillJob?.isActive == true) return
        refillJob = launch {
            queueLoading.value = true
            queueError.value = null
            val t0 = System.currentTimeMillis()
            try {
                val marked = repo.mine.first()
                val ranked = repo.recommend(marked)
                val known = repo.mine.first().map { it.movieId }.toSet()
                queue.value = (partnerPicks() + queue.value + ranked)
                    .filter { it.movieId !in known && it.movieId !in skipped }
                    .distinctBy { it.movieId }
                AppLog.i("Descubrir", "cola rellenada: ${queue.value.size} sugerencias (${System.currentTimeMillis() - t0} ms)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Descubrir", "falló al rellenar la cola", e)
                queueError.value = "Error de red: ${e.message}"
            } finally {
                queueLoading.value = false
            }
        }
    }

    // ---- Sala: crear / unirse / sincronizar ----------------------------------

    fun createRoom() = enterRoom(RoomCodes.newCode())

    fun joinRoom(input: String) {
        val code = RoomCodes.normalize(input)
        if (code == null) message.value = "Código no válido: son 10 letras y números" else enterRoom(code)
    }

    private fun enterRoom(code: String) {
        AppLog.i("Sala", "entrando a una sala (código ${code.take(2)}…)")
        pairing.room = code
        pairing.lastPush = 0
        room.value = code
        launch { sync() }
    }

    fun leaveRoom() {
        AppLog.i("Sala", "saliendo de la sala")
        pairing.room = null
        room.value = null
        syncError.value = null
        launch { repo.clearPartner() }
    }

    fun roomShareText(): String {
        val code = room.value?.let { RoomCodes.format(it) } ?: ""
        return "Únete a mi sala en Peli para ver qué películas queremos ver juntos. Código: $code\n" +
            "(Peli → Juntos → Tengo un código)"
    }

    /** Un ciclo de sincronización; se llama cada pocos segundos mientras la app está abierta. */
    suspend fun sync() {
        if (account.session != null && account.profile != null) return syncAccount()
        val r = room.value ?: return
        if (!Remote.configured) return
        syncLock.withLock {
            val started = System.currentTimeMillis()
            try {
                repo.sync(r, pairing.userId, mine.value, pairing.lastPush)
                if (syncError.value != null) AppLog.i("Sala", "sincronización recuperada")
                pairing.lastPush = started
                syncError.value = null
                // Lo que tu pareja guardó pasa al frente de tus sugerencias.
                val picks = partnerPicks()
                if (picks.isNotEmpty()) queue.update { (picks.filter { p -> p.movieId !in skipped } + it).distinctBy { x -> x.movieId } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (syncError.value == null) AppLog.w("Sala", "falló la sincronización", e)
                syncError.value = e.message ?: e.javaClass.simpleName
            }
        }
    }

    // ---- Cuenta de Google, usuario y amigos -----------------------------------

    /** Hay servidor y cliente de Google configurados en esta compilación. */
    val googleAvailable = Social.configured

    val signedIn = MutableStateFlow(account.session != null)
    val accountEmail = MutableStateFlow(account.session?.email)
    val profile = MutableStateFlow(account.profile)
    val friends = MutableStateFlow<List<Friend>>(emptyList())
    val activeFriend = MutableStateFlow(account.activeFriend)
    val accountBusy = MutableStateFlow(false)

    /** Hay algo que sincronizar en segundo plano: una sala (modo anterior) o mi cuenta con usuario. */
    val syncActive: StateFlow<Boolean> = combine(room, profile) { r, p -> r != null || p != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, room.value != null || profile.value != null)

    fun showMessage(text: String) { message.value = text }

    /** Google entregó su token: se canjea por una sesión de Supabase y se busca mi perfil. */
    fun signInWithGoogle(token: GoogleToken) {
        launch {
            accountBusy.value = true
            try {
                val session = withContext(Dispatchers.IO) { SupabaseAuth.signInWithGoogle(token.idToken, token.rawNonce) }
                account.save(session)
                signedIn.value = true
                accountEmail.value = session.email
                AppLog.i("Cuenta", "sesión iniciada")
                loadProfile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Cuenta", "falló el canje del token de Google", e)
                message.value = e.accountMessage()
            } finally {
                accountBusy.value = false
            }
        }
    }

    private suspend fun loadProfile() {
        val p = repo.myProfile()
        account.profile = p
        profile.value = p
        AppLog.i("Cuenta", if (p == null) "sin nombre de usuario todavía" else "perfil: @${p.username}")
        if (p != null) syncAccount()
    }

    fun createProfile(username: String) {
        val name = username.trim()
        if (!Usernames.isValid(name)) {
            message.value = Usernames.explain(name) ?: "Escribe un nombre de usuario."
            return
        }
        launch {
            accountBusy.value = true
            try {
                val p: Profile = repo.createProfile(name)
                account.profile = p
                profile.value = p
                AppLog.i("Cuenta", "perfil creado: @${p.username}")
                message.value = "Listo, @${p.username}. Tu ID de amigo es ${com.peli.app.data.FriendCodes.format(p.friend_code)}."
                syncAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Cuenta", "no se pudo crear el perfil", e)
                message.value = e.accountMessage()
            } finally {
                accountBusy.value = false
            }
        }
    }

    fun addFriend(query: String) {
        val q = query.trim()
        if (q.length < 3) {
            message.value = "Escribe el ID (ABCD-EFGH) o el @usuario de tu amigo."
            return
        }
        launch {
            accountBusy.value = true
            try {
                message.value = when (val r = FriendRequestResult.parse(repo.requestFriend(q))) {
                    is FriendRequestResult.Sent -> "Solicitud enviada a @${r.username}. Cuando la acepte, podrán comparar listas."
                    is FriendRequestResult.Accepted -> "¡Ahora eres amigo de @${r.username}!"
                    is FriendRequestResult.AlreadyExists -> "Ya tienes una solicitud o amistad con @${r.username}."
                    FriendRequestResult.NotFound -> "No encontré a nadie con ese ID o usuario."
                    FriendRequestResult.Self -> "Ese eres tú 🙂"
                    null -> "Respuesta inesperada del servidor."
                }
                AppLog.i("Amigos", "agregar «$q» → ${message.value}")
                syncAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Amigos", "no se pudo agregar a «$q»", e)
                message.value = e.accountMessage()
            } finally {
                accountBusy.value = false
            }
        }
    }

    fun acceptFriend(f: Friend) = friendAction("aceptar a @${f.username}") {
        repo.acceptFriend(f.friend_id)
        if (account.activeFriend == null) selectFriend(f.friend_id, sync = false)
    }

    fun removeFriend(f: Friend) = friendAction("quitar a @${f.username}") {
        repo.removeFriend(f.friend_id)
        if (account.activeFriend == f.friend_id) clearActiveFriend()
    }

    private fun friendAction(what: String, block: suspend () -> Unit) {
        launch {
            accountBusy.value = true
            try {
                AppLog.i("Amigos", what)
                block()
                syncAccount()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Amigos", "no se pudo $what", e)
                message.value = e.accountMessage()
            } finally {
                accountBusy.value = false
            }
        }
    }

    /** Elige con qué amigo se comparan las listas (matches, "Descubrir" y nota de tu pareja). */
    fun selectFriend(id: String, sync: Boolean = true) {
        if (account.activeFriend == id) return
        account.activeFriend = id
        account.baselineFriend = null
        activeFriend.value = id
        AppLog.i("Amigos", "se compara con el amigo $id")
        launch {
            repo.clearPartner()
            if (sync) syncAccount()
        }
    }

    private suspend fun clearActiveFriend() {
        account.activeFriend = null
        account.baselineFriend = null
        activeFriend.value = null
        repo.clearPartner()
    }

    fun signOut() {
        AppLog.i("Cuenta", "cerrar sesión")
        account.clear()
        signedIn.value = false
        accountEmail.value = null
        profile.value = null
        friends.value = emptyList()
        activeFriend.value = null
        launch { repo.clearPartner() }
    }

    /** Un ciclo con mi cuenta: sube mis marcas, trae amigos y las marcas del amigo elegido. */
    private suspend fun syncAccount() {
        val s = account.session ?: return
        if (account.profile == null) return
        syncLock.withLock {
            val started = System.currentTimeMillis()
            try {
                val result = repo.syncAccount(s.userId, mine.value, account.lastPush, account.activeFriend)
                account.lastPush = started
                friends.value = result.friends

                // Sin amigo elegido: si hay uno aceptado, se elige solo; si el elegido ya no es amigo, se quita.
                val accepted = result.friends.filter { it.accepted }
                val active = account.activeFriend
                if (active != null && accepted.none { it.friend_id == active }) {
                    clearActiveFriend()
                } else if (active == null && accepted.size == 1) {
                    selectFriend(accepted.first().friend_id, sync = false)
                }

                val partner = result.partner
                if (partner != null && active != null) {
                    // Los matches que ya existían al elegir al amigo no se "estrenan" todos de golpe.
                    if (account.baselineFriend != active) {
                        val existing = computeMatches(mine.value, partner).filter { it.both }.map { it.entry.movieId }
                        pairing.announced = pairing.announced + existing
                        account.baselineFriend = active
                    }
                    repo.setPartner(partner)
                    picksToFront()
                }
                syncError.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (syncError.value == null) AppLog.w("Cuenta", "falló la sincronización de la cuenta", e)
                syncError.value = e.accountMessage()
            }
        }
    }

    private fun picksToFront() {
        val picks = partnerPicks()
        if (picks.isNotEmpty()) queue.update { (picks.filter { p -> p.movieId !in skipped } + it).distinctBy { x -> x.movieId } }
    }

    /** Deslizar hacia abajo en "Juntos": sincroniza con la sala ahora mismo. */
    fun refreshSync() {
        launch {
            refreshing.value = true
            try { sync() } finally { refreshing.value = false }
        }
    }

    fun dismissMatch() = pendingMatches.update { it.drop(1) }

    fun dismissCrashReport() {
        crashReport.value = null
        launch(Dispatchers.IO) { CrashReporter.clear(getApplication()) }
    }

    // ---- Actualizaciones -----------------------------------------------------

    /** Al abrir la app es silencioso; manual (botón) informa también si está al día o falló. */
    fun checkForUpdate(manual: Boolean) {
        launch {
            AppLog.d("Actualizador", "consultando actualización (${if (manual) "manual" else "automática"})")
            val res = withContext(Dispatchers.IO) { Updater.checkLatest() }
            val v = res.version
            when {
                v == null -> if (manual) message.value = "No se pudo consultar: ${res.error}"
                Updater.isNewer(v, APP_VERSION) -> {
                    newVersion.value = v
                    if (manual) message.value = "Hay una versión nueva: v$v (tienes v$APP_VERSION)."
                }
                else -> {
                    newVersion.value = null
                    if (manual) message.value = "Estás al día (v$APP_VERSION)."
                }
            }
        }
    }

    fun installUpdate() {
        if (progress.value != -2) return   // ya se está descargando
        val v = newVersion.value ?: return
        AppLog.i("Actualizador", "el usuario pidió instalar v$v")
        launch {
            progress.value = -1
            try {
                withContext(Dispatchers.IO) {
                    Updater.downloadAndInstall(getApplication(), v) { p -> progress.value = p }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Actualizador", "no se pudo instalar v$v", e)
                message.value = "No se pudo actualizar: ${e.message}"
            } finally {
                progress.value = -2
            }
        }
    }

    fun dismissWhatsNew() { whatsNew.value = emptyList() }

    // ---- Intercambio manual de códigos (sin servidor) ------------------------

    fun exportCode() = Sharing.export(mine.value)

    fun importCode(text: String) {
        val list = Sharing.import(text)
        if (list == null) {
            message.value = "Código no válido"
        } else launch {
            repo.importPartner(list)
            message.value = "Lista de tu pareja importada (${list.size} películas)"
        }
    }

    fun clearPartner() { launch { repo.clearPartner() } }

    private companion object {
        const val KEY_QUERY = "search_query"
        const val KEY_SEARCH_FILTER = "search_filter"
        const val KEY_MINE_FILTER = "mine_filter"
        const val KEY_MINE_SORT = "mine_sort"
        const val KEY_SEARCH_YEARS = "search_years"
        const val KEY_MINE_YEARS = "mine_years"
        const val SEARCH_DEBOUNCE_MS = 350L
        const val FEED_SIZE = 40
        const val FEED_NOISE = 0.3
    }
}
