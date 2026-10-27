package com.peli.app.data

import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * Recomendador basado en contenido + señal colaborativa de TMDB.
 *
 * 1. Perfil de gustos: cada género acumula peso según lo que marqué
 *    (mi nota 1..10 mapeada a -1..+1; si no hay nota: me gustó +1, quiero verla +0.5, la vi sin opinión +0.15, no me gusta -0.8 / -1 si la vi),
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
                // Mi nota (1..10) manda: 10 -> +1, 6 -> +0.11, 1 -> -1.
                e.myRating > 0 -> (e.myRating - 5.5) / 4.5
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

    /**
     * Ordena las candidatas de mayor a menor puntaje, con un poco de azar ([noise]) para variar el orden.
     * El puntaje (con su azar) se calcula UNA vez por película y luego se ordena: si el azar se sortea
     * dentro del comparador, el orden es inconsistente y Java lanza "Comparison method violates its
     * general contract".
     */
    fun rank(
        candidates: Collection<Entry>,
        profile: Profile,
        hits: Map<Int, Int>,
        seedCount: Int,
        noise: Double,
        random: Random = Random.Default,
    ): List<Entry> = candidates
        .map { c ->
            val jitter = if (noise > 0) random.nextDouble(0.0, noise) else 0.0
            c to score(c, profile, hits[c.movieId] ?: 0, seedCount) + jitter
        }
        .sortedByDescending { it.second }
        .map { it.first }

    /** @param seedHits cuántas de mis [seedCount] películas "me gustó" recomiendan a [c]. */
    fun score(c: Entry, profile: Profile, seedHits: Int, seedCount: Int): Double {
        val overlap = if (seedCount > 0) seedHits.toDouble() / seedCount else 0.0
        return 0.5 * affinity(c, profile) + 0.3 * quality(c) + 0.2 * overlap
    }
}
