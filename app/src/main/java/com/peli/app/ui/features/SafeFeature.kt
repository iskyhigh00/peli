package com.peli.app.ui.features

import com.peli.app.data.Entry
import com.peli.app.data.MovieFilters
import com.peli.app.data.Repository
import com.peli.app.data.Safe
import com.peli.app.data.SafePick
import com.peli.app.data.YearRange
import com.peli.app.log.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

sealed interface SafeState {
    data object Idle : SafeState
    data object Loading : SafeState
    data class NeedMore(val have: Int, val need: Int) : SafeState
    data class Ready(val pick: SafePick) : SafeState
    data object Empty : SafeState
    data class Failed(val message: String) : SafeState
}

enum class SafeAnswer { Seen, Want, NotInterested, Another }
/** La «película segura»: la candidata con más probabilidad de gustarme, con sus motivos, una a la vez. */
class SafeFeature(
    private val env: VmEnv,
    private val repo: Repository,
    private val mine: StateFlow<List<Entry>>,
    private val years: () -> YearRange?,
    private val filters: () -> MovieFilters,
    private val markSeen: suspend (Entry) -> Entry,
    private val askRating: (Entry) -> Unit,
    private val want: (Entry) -> Unit,
    private val dislike: (Entry) -> Unit,
) {
    private companion object { const val BATCH = 6 }

    // ---- Película segura -----------------------------------------------------


    /** Estado de la hoja «Película segura» (Idle = cerrada). */
    val state = MutableStateFlow<SafeState>(SafeState.Idle)

    /** Ya mostradas en esta sesión de la hoja: no vuelven a salir. */
    private val safeSeen = HashSet<Int>()
    private val safeQueue = ArrayDeque<SafePick>()
    private var safeJob: Job? = null

    fun open() {
        AppLog.i("Segura", "se abre la película segura")
        safeSeen.clear()
        safeQueue.clear()
        load()
    }

    fun close() {
        safeJob?.cancel()
        state.value = SafeState.Idle
    }

    private fun load() {
        safeJob?.cancel()
        state.value = SafeState.Loading
        safeJob = env.launch {
            try {
                val marked = repo.mine.first()
                if (!Safe.enoughData(marked)) {
                    state.value = SafeState.NeedMore(Safe.signals(marked), Safe.MIN_SIGNALS)
                    return@launch
                }
                // Respeta los filtros elegidos (años, nota de TMDB) y la época de lo que me gusta.
                val picks = repo.safePicks(marked, safeSeen.toSet(), BATCH, years(), filters())
                AppLog.i("Segura", "${picks.size} candidatas; mejor: ${picks.firstOrNull()?.let { "«${it.entry.title}» %.2f".format(it.score) } ?: "ninguna"}")
                safeQueue.clear()
                safeQueue.addAll(picks)
                if (!showNextSafe()) state.value = SafeState.Empty
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.w("Segura", "falló el cálculo", e)
                state.value = SafeState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Muestra la siguiente de la cola que no esté ya marcada ni vista en esta hoja; false si no queda ninguna. */
    private fun showNextSafe(): Boolean {
        val known = mine.value.map { it.movieId }.toSet()
        while (safeQueue.isNotEmpty()) {
            val p = safeQueue.removeFirst()
            if (p.entry.movieId in known || p.entry.movieId in safeSeen) continue
            safeSeen += p.entry.movieId
            state.value = SafeState.Ready(p)
            return true
        }
        return false
    }

    fun retry() = load()

    /** Respuesta a la película segura: «ya la vi» (y se pide la nota), «quiero verla», «no me interesa» u «otra». */
    fun answer(e: Entry, answer: SafeAnswer) {
        AppLog.i("Segura", "$answer → «${e.title}» (${e.movieId})")
        when (answer) {
            SafeAnswer.Seen -> env.launch {
                val next = markSeen(e)
                if (next.seen && next.myRating == 0) askRating(next)
            }
            SafeAnswer.Want -> want(e)
            SafeAnswer.NotInterested -> dislike(e)
            SafeAnswer.Another -> {}
        }
        if (!showNextSafe()) load()
    }
}
