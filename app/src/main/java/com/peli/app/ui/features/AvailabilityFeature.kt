package com.peli.app.ui.features

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Películas «disponibles»: las marcadas a mano (arrendadas, compradas, en una plataforma) más las que están en tu servidor. */
class AvailabilityFeature(private val env: VmEnv, private val server: ServerFeature) {
    val manual = MutableStateFlow(env.peli.availablePrefs.ids)

    /** Marcadas a mano, en mi servidor o en el de mi amigo (lo que comparte). */
    val all: StateFlow<Set<Int>> = combine(manual, server.ids, server.shared) { a, s, f -> a + s + f }
        .stateIn(env.scopeForFlows, SharingStarted.Eagerly, manual.value + server.ids.value + server.shared.value)

    fun toggle(movieId: Int) {
        if (movieId in server.ids.value || movieId in server.shared.value) {
            env.message.value = "Ya está en el servidor: se marca como disponible sola."
            return
        }
        val next = manual.value.let { if (movieId in it) it - movieId else it + movieId }
        manual.value = next
        env.peli.availablePrefs.ids = next
    }
}
