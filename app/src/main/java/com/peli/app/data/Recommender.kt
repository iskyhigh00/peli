package com.peli.app.data

import kotlin.math.abs
import kotlin.math.max

/**
 * Recomendador basado en contenido + señal colaborativa de TMDB.
 *
 * 1. Perfil de gustos: cada género acumula peso según lo que marqué
 *    (me gustó +1, quiero verla +0.5, la vi sin opinión +0.15, no me gusta -0.8 / -1 si la vi),
 *    con más peso a lo reciente. Se normaliza a -1..1.
 * 2. Cada candidata se puntúa: 50% afinidad de géneros, 30% calidad (nota TMDB),
 *    20% cuántas de mis "me gustó" la recomiendan.
 */
object Recommender {
    class Profile(val genreWeights: Map<Int, Double>) {
        /** Los géneros que más me gustan (solo con peso positivo). */
        fun topGenres(n: Int): List<Int> =
            genreWeights.filter { it.value > 0 }.entries.sortedByDescending { it.value }.take(n).map { it.key }
    }

    private const val DAY_MS = 86_400_000.0

    fun buildProfile(entries: List<Entry>, now: Long = System.currentTimeMillis()): Profile {
        val raw = HashMap<Int, Double>()
        for (e in entries) {
            val base = when {
                e.liked -> 1.0
                e.disliked && e.seen -> -1.0
                e.disliked -> -0.8
                e.watchlist -> 0.5
                e.seen -> 0.15
                else -> 0.0
            }
            if (base == 0.0) continue
            // Lo reciente pesa hasta 1.5x; sin fecha (datos antiguos) pesa 1.25x.
            val recency = if (e.updatedAt > 0) 1.0 / (1.0 + (now - e.updatedAt) / DAY_MS / 30.0) else 0.5
            val w = base * (1.0 + 0.5 * recency)
            for (g in e.genreIds) raw[g] = (raw[g] ?: 0.0) + w
        }
        val peak = raw.values.maxOfOrNull { abs(it) } ?: return Profile(emptyMap())
        return Profile(raw.mapValues { it.value / max(peak, 1e-9) })
    }

    /** Afinidad -1..1: promedio de los pesos de sus géneros (géneros desconocidos cuentan 0). */
    fun affinity(c: Entry, profile: Profile): Double {
        val ids = c.genreIds
        if (ids.isEmpty()) return 0.0
        return ids.sumOf { profile.genreWeights[it] ?: 0.0 } / ids.size
    }

    /** Calidad 0..1: nota 5 -> 0, nota 10 -> 1. */
    fun quality(c: Entry): Double = (((c.rating ?: 0.0) - 5.0) / 5.0).coerceIn(0.0, 1.0)

    /** @param seedHits cuántas de mis [seedCount] películas "me gustó" recomiendan a [c]. */
    fun score(c: Entry, profile: Profile, seedHits: Int, seedCount: Int): Double {
        val overlap = if (seedCount > 0) seedHits.toDouble() / seedCount else 0.0
        return 0.5 * affinity(c, profile) + 0.3 * quality(c) + 0.2 * overlap
    }
}
