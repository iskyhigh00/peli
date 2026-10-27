package com.peli.app.data

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

/** Datos de una candidata que no se guardan en [Entry]: votos de TMDB y fuerza de la señal colaborativa. */
data class CandMeta(val votes: Int? = null, val collab: Double = 0.0)

/** Lo que una marca dice del gusto: [y] en -1..1 y qué tan fiable es ([confidence] 0..1). */
data class Signal(val y: Double, val confidence: Double)

/**
 * Recomendador local, explicable y sin servidor.
 *
 * - Una sola señal explícita vigente por película (la nota manda; "la vi" sin nota no dice nada del gusto).
 * - Perfil por características (géneros y década) con dos memorias: larga (730 días) y corta (45 días),
 *   regularizado: una sola marca no convierte un género entero en favorito ni en enemigo.
 * - Puntaje = relevancia + señal de TMDB (recomendaciones de mis películas) + calidad regularizada por votos
 *   + serendipia; con más evidencia pesa más lo personal y menos la calidad.
 * - Selección con diversidad (MMR) y un cupo de exploración fuera de mis géneros principales.
 *
 * Los pesos y plazos son valores iniciales a validar con uso real.
 */
object Recommender {
    const val GENRE_COUNT = 19
    private const val DAY_MS = 86_400_000.0
    private const val LONG_HALF_LIFE_DAYS = 730.0
    private const val SHORT_HALF_LIFE_DAYS = 45.0
    private const val UNKNOWN_AGE_DAYS = 365.0
    private const val LAMBDA = 2.0
    private const val QUALITY_PRIOR_VOTES = 100.0
    private const val QUALITY_PRIOR_MEAN = 0.65

    /** Peso de cada familia en la relevancia (por ahora solo géneros y década están disponibles). */
    private const val GENRE_WEIGHT = 0.8
    private const val DECADE_WEIGHT = 0.2

    class Stat(var evidence: Double = 0.0, var signed: Double = 0.0) {
        fun theta() = signed / (LAMBDA + evidence)
    }

    class Profile internal constructor(
        internal val long: Map<String, Stat>,
        internal val short: Map<String, Stat>,
        /** Evidencia reciente (45 días): decide cuánto pesa el perfil corto. */
        val recentEvidence: Double,
        /** Evidencia efectiva (730 días): decide cuánto pesa lo personal frente a la calidad. */
        val effectiveEvidence: Double,
        /** 0 = gusto muy concentrado, 1 = muy repartido. */
        val entropy: Double,
        /** Hasta 30 películas que me gustaron, las más representativas. */
        internal val positives: List<Entry>,
        /** Las últimas 30 que rechacé. */
        internal val negatives: List<Entry>,
    ) {
        val isEmpty get() = effectiveEvidence == 0.0
        internal val rho get() = 0.35 * recentEvidence / (recentEvidence + 5.0)

        private fun theta(key: String): Double =
            (1 - rho) * (long[key]?.theta() ?: 0.0) + rho * (short[key]?.theta() ?: 0.0)

        /** Géneros que más me gustan (solo con gusto positivo), de mayor a menor. */
        fun topGenres(n: Int): List<Int> =
            long.keys.plus(short.keys).filter { it.startsWith("g") }.distinct()
                .map { it.drop(1).toInt() to theta(it) }
                .filter { it.second > 0 }
                .sortedWith(compareByDescending<Pair<Int, Double>> { it.second }.thenBy { it.first })
                .take(n).map { it.first }

        internal fun affinity(c: Entry, stats: Map<String, Stat>): Double {
            var sum = 0.0
            for (f in features(c)) sum += f.groupWeight * f.x * (stats[f.key]?.theta() ?: 0.0)
            return sum
        }
    }

    private class Feature(val key: String, val groupWeight: Double, val x: Double)

    /** Géneros (peso repartido, suman 1) y década (interpolada entre décadas vecinas). */
    private fun features(e: Entry): List<Feature> {
        val out = ArrayList<Feature>()
        val genres = e.genreIds.distinct()
        for (g in genres) out += Feature("g$g", GENRE_WEIGHT, 1.0 / genres.size)
        val year = e.year.take(4).toIntOrNull()
        if (year != null && year in 1880..2100) {
            val d0 = year / 10 * 10
            val frac = (year - d0) / 10.0
            out += Feature("d$d0", DECADE_WEIGHT, 1.0 - frac)
            if (frac > 0) out += Feature("d${d0 + 10}", DECADE_WEIGHT, frac)
        }
        return out
    }

    /**
     * La señal explícita vigente de una película, o null si no dice nada del gusto.
     * La nota prevalece sobre "me gustó"; "la vi" sin nota es neutral.
     */
    fun signalOf(e: Entry): Signal? = when {
        e.myRating > 0 -> Signal((e.myRating - 5.5) / 4.5, 1.0)
        e.liked -> Signal(0.85, 0.90)
        e.disliked -> Signal(-0.65, 0.35)
        e.watchlist -> Signal(0.45, 0.45)
        else -> null
    }

    private fun decay(ageDays: Double, halfLife: Double) = Math.pow(2.0, -max(0.0, ageDays) / halfLife)

    private fun ageDays(e: Entry, now: Long) =
        if (e.updatedAt > 0) (now - e.updatedAt) / DAY_MS else UNKNOWN_AGE_DAYS

    fun buildProfile(entries: List<Entry>, now: Long = System.currentTimeMillis()): Profile {
        val long = HashMap<String, Stat>()
        val short = HashMap<String, Stat>()
        var recent = 0.0
        var effective = 0.0
        val positives = ArrayList<Pair<Entry, Double>>()
        val negatives = ArrayList<Entry>()
        for (e in entries) {
            val s = signalOf(e) ?: continue
            val age = ageDays(e, now)
            val wLong = s.confidence * decay(age, LONG_HALF_LIFE_DAYS)
            val wShort = s.confidence * decay(age, SHORT_HALF_LIFE_DAYS)
            recent += wShort
            effective += wLong
            for (f in features(e)) {
                val l = long.getOrPut(f.key) { Stat() }
                l.evidence += wLong * f.x
                l.signed += wLong * f.x * s.y
                val sh = short.getOrPut(f.key) { Stat() }
                sh.evidence += wShort * f.x
                sh.signed += wShort * f.x * s.y
            }
            if (s.y > 0) positives += e to s.confidence * s.y * decay(age, 180.0)
            if (e.disliked) negatives += e
        }
        val representative = positives.sortedWith(compareByDescending<Pair<Entry, Double>> { it.second }.thenBy { it.first.movieId })
            .take(30).map { it.first }
        val lastNegatives = negatives.sortedWith(compareByDescending<Entry> { it.updatedAt }.thenBy { it.movieId }).take(30)
        return Profile(long, short, recent, effective, genreEntropy(positives), representative, lastNegatives)
    }

    /** Entropía normalizada de los géneros de lo que me gusta; con menos de 10 películas positivas vale 0.5. */
    private fun genreEntropy(positives: List<Pair<Entry, Double>>): Double {
        if (positives.size < 10) return 0.5
        val mass = HashMap<Int, Double>()
        for ((e, w) in positives) {
            val genres = e.genreIds.distinct()
            for (g in genres) mass[g] = (mass[g] ?: 0.0) + w / genres.size
        }
        val total = mass.values.sum()
        if (total <= 0.0 || mass.size < 2) return 0.0
        val h = -mass.values.sumOf { (it / total).let { p -> p * ln(p) } } / ln(GENRE_COUNT.toDouble())
        return h.coerceIn(0.0, 1.0)
    }

    /** Afinidad 0..1 (0.5 = neutral): mezcla de la memoria larga y la corta. */
    fun relevance(c: Entry, profile: Profile): Double {
        val a = (1 - profile.rho) * profile.affinity(c, profile.long) + profile.rho * profile.affinity(c, profile.short)
        return ((1.0 + a) / 2.0).coerceIn(0.0, 1.0)
    }

    /**
     * Calidad 0..1 regularizada por votos: con pocos votos se acerca a 0.65 (ni 0 ni perfecta).
     * Sin cantidad de votos conocida cuenta como 100 votos; sin nota, 0.65.
     */
    fun quality(c: Entry, votes: Int?): Double {
        if (c.isUpcoming) return QUALITY_PRIOR_MEAN
        val v = (votes ?: QUALITY_PRIOR_VOTES.toInt()).coerceAtLeast(0).toDouble()
        val r = (c.rating ?: return QUALITY_PRIOR_MEAN) / 10.0
        return v / (v + QUALITY_PRIOR_VOTES) * r + QUALITY_PRIOR_VOTES / (v + QUALITY_PRIOR_VOTES) * QUALITY_PRIOR_MEAN
    }

    /** Parecido 0..1 entre dos películas: géneros (Jaccard) y cercanía de años; 0.5 si no hay con qué comparar. */
    fun similarity(a: Entry, b: Entry): Double {
        val ga = a.genreIds.toSet()
        val gb = b.genreIds.toSet()
        if (ga.isEmpty() || gb.isEmpty()) return 0.5
        val jaccard = ga.intersect(gb).size.toDouble() / ga.union(gb).size
        val ya = a.year.take(4).toIntOrNull()
        val yb = b.year.take(4).toIntOrNull()
        val decade = if (ya != null && yb != null) (1.0 - kotlin.math.abs(ya - yb) / 50.0).coerceIn(0.0, 1.0) else 0.5
        return 0.8 * jaccard + 0.2 * decade
    }

    /** Puntaje base de una candidata (sin azar ni diversidad). */
    fun baseScore(c: Entry, profile: Profile, meta: CandMeta?): Double {
        val r = relevance(c, profile)
        val z = profile.effectiveEvidence / (profile.effectiveEvidence + 20.0)
        val serendipity = if (profile.positives.isEmpty()) 0.0
        else r * (1.0 - profile.positives.maxOf { similarity(c, it) })
        val rejected = if (profile.negatives.isEmpty()) 0.0
        else 0.15 * max(0.0, profile.negatives.maxOf { similarity(c, it) } - 0.4)
        return (0.40 + 0.15 * z) * r +
            0.20 * (meta?.collab ?: 0.0) +
            (0.30 - 0.15 * z) * quality(c, meta?.votes) +
            0.10 * serendipity -
            rejected
    }

    /**
     * Ordena por puntaje con un poco de azar ([noise]) calculado UNA vez por película: si el azar se sortea
     * dentro del comparador, Java lanza "Comparison method violates its general contract".
     */
    fun rank(
        candidates: Collection<Entry>,
        profile: Profile,
        meta: Map<Int, CandMeta>,
        noise: Double,
        random: Random = Random.Default,
    ): List<Entry> = candidates
        .map { c ->
            val jitter = if (noise > 0) random.nextDouble(0.0, noise) else 0.0
            c to baseScore(c, profile, meta[c.movieId]) + jitter
        }
        .sortedWith(compareByDescending<Pair<Entry, Double>> { it.second }.thenBy { it.first.movieId })
        .map { it.first }

    /**
     * Elige [k] películas: puntaje + diversidad (MMR) + cupo de exploración fuera de mis géneros principales
     * + tope de próximos estrenos (10%). El azar solo interviene en los cupos de exploración.
     */
    fun select(
        candidates: Collection<Entry>,
        profile: Profile,
        meta: Map<Int, CandMeta>,
        k: Int,
        random: Random = Random.Default,
    ): List<Entry> {
        val pool = candidates.distinctBy { it.movieId }.sortedBy { it.movieId }
        if (pool.isEmpty() || k <= 0) return emptyList()
        val base = pool.associate { it.movieId to baseScore(it, profile, meta[it.movieId]) }
        val lambdaD = 0.10 + 0.15 * profile.entropy
        val epsilon = 0.10 + 0.20 * exp(-profile.effectiveEvidence / 20.0)
        val period = max(2, (1.0 / epsilon).roundToInt())
        val top = profile.topGenres(3).toSet()
        val maxUpcoming = max(1, k / 10)

        val remaining = pool.toMutableList()
        val chosen = ArrayList<Entry>(min(k, pool.size))
        var upcoming = 0
        while (chosen.size < k && remaining.isNotEmpty()) {
            val allowed = remaining.filter { upcoming < maxUpcoming || !it.isUpcoming }.ifEmpty { remaining }
            val explore = top.isNotEmpty() && chosen.size % period == period - 1
            val options = if (explore) {
                allowed.filter { it.genreIds.isNotEmpty() && it.genreIds.first() !in top }.ifEmpty { allowed }
            } else allowed
            val ranked = options
                .map { e -> e to base.getValue(e.movieId) - lambdaD * (chosen.maxOfOrNull { similarity(e, it) } ?: 0.0) }
                .sortedWith(compareByDescending<Pair<Entry, Double>> { it.second }.thenBy { it.first.movieId })
            val pick = if (explore) ranked.take(3).let { it[random.nextInt(it.size)] }.first else ranked.first().first
            chosen += pick
            remaining.removeAll { it.movieId == pick.movieId }
            if (pick.isUpcoming) upcoming++
        }
        return chosen
    }

    /** Intensidad de una semilla (película que me gustó) para pedirle recomendaciones a TMDB. */
    fun seedStrength(e: Entry): Double = signalOf(e)?.let { it.confidence * max(0.0, it.y) } ?: 0.0

    /** Fuerza colaborativa 0..1: cada semilla que recomienda la película suma según su posición en la lista de TMDB. */
    fun collab(contributions: List<Pair<Double, Int>>): Double =
        1.0 - exp(-contributions.sumOf { (strength, position) -> strength / (1.0 + position / 8.0) })

    /** Elige hasta [n] semillas repartidas entre grupos (género principal), para no apoyarse en un solo gusto. */
    fun pickSeeds(entries: List<Entry>, n: Int, random: Random, now: Long = System.currentTimeMillis()): List<Entry> {
        val groups = entries.filter { seedStrength(it) > 0 }
            .groupBy { it.genreIds.firstOrNull() ?: 0 }
            .mapValues { (_, list) -> list.sortedBy { it.movieId }.toMutableList() }
            .toSortedMap()
            .toMutableMap()
        val out = ArrayList<Entry>()
        // Por rondas: cada ronda visita los grupos en orden al azar y toma una semilla de cada uno,
        // así no se repite un género hasta haber usado todos.
        while (out.size < n && groups.isNotEmpty()) {
            for (key in groups.keys.toList().shuffled(random)) {
                if (out.size >= n) break
                val list = groups.getValue(key)
                val weights = list.map { seedStrength(it) * (0.4 + 0.6 * decay(ageDays(it, now), 180.0)) }
                var r = random.nextDouble() * weights.sum()
                var idx = 0
                while (idx < list.lastIndex && r > weights[idx]) {
                    r -= weights[idx]
                    idx++
                }
                out += list.removeAt(idx)
                if (list.isEmpty()) groups.remove(key)
            }
        }
        return out
    }
}
