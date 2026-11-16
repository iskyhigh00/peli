package com.peli.app.ui.features

import com.peli.app.data.AwardRow
import com.peli.app.data.Entry
import com.peli.app.data.Wikidata
import com.peli.app.data.attempt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

sealed interface AwardsState {
    data object Loading : AwardsState
    data class Ready(val rows: List<AwardRow>) : AwardsState
    data class Failed(val message: String) : AwardsState
}

/** Detalle de premios y nominaciones (de Wikidata) de las películas cuyo detalle se pidió. */
class AwardsFeature(private val env: VmEnv) {
    val details = MutableStateFlow<Map<Int, AwardsState>>(emptyMap())

    fun load(entry: Entry, imdbId: String?, force: Boolean = false) {
        val id = entry.movieId
        if (!force && details.value[id].let { it is AwardsState.Ready || it is AwardsState.Loading }) return
        if (imdbId == null) {
            details.update { it + (id to AwardsState.Failed("No se conoce el id de IMDb de esta película.")) }
            return
        }
        details.update { it + (id to AwardsState.Loading) }
        env.launch(Dispatchers.IO) {
            val titles = setOfNotNull(entry.title, entry.originalTitle?.takeIf { it.isNotBlank() })
            val r = attempt { Wikidata.awards(imdbId, titles) }
            val state = r.getOrNull()?.let { AwardsState.Ready(it) }
                ?: AwardsState.Failed("No se pudo consultar el detalle (¿sin conexión?).")
            details.update { it + (id to state) }
        }
    }
}
