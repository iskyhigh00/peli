package com.peli.app.ui.features

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Películas «disponibles»: las marcadas a mano (arrendadas, compradas, en una plataforma) más las que están en tu servidor. */
class AvailabilityFeature(private val env: VmEnv, private val server: ServerFeature) {
    val manual = MutableStateFlow(env.peli.availablePrefs.ids)

    val all: StateFlow<Set<Int>> = combine(manual, server.ids) { a, s -> a + s }
        .stateIn(env.scopeForFlows, SharingStarted.Eagerly, manual.value + server.ids.value)

    fun toggle(movieId: Int) {
        if (movieId in server.ids.value) {
            env.message.value = "Ya está en tu servidor: se marca como disponible sola."
            return
        }
        val next = manual.value.let { if (movieId in it) it - movieId else it + movieId }
        manual.value = next
        env.peli.availablePrefs.ids = next
    }
}
