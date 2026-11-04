package com.peli.app.data

import kotlin.math.abs
import kotlin.math.exp

/** Las dos críticas de la casa. Cada una tiene su propio gusto fijo: la misma película siempre recibe la misma nota. */
enum class Pet(val label: String, val seed: Long, val discoverGenres: String) {
    Chuba("Chuba", 7919L, "16|10751|10749|35|10402"),
    Pepa("Pepa", 104729L, "53|9648|27|80|878"),
}

data class PetVerdict(val pet: Pet, val score: Int, val reason: String) {
    val likes get() = score >= PetTaste.LIKE_FROM
}

object PetTaste {
    const val LIKE_FROM = 8

    private val names = mapOf(
        28 to "la acción", 12 to "las aventuras", 16 to "la animación", 35 to "la comedia", 80 to "el crimen",
        99 to "los documentales", 18 to "el drama", 10751 to "las películas familiares", 14 to "la fantasía",
        36 to "las de historia", 27 to "el terror", 10402 to "los musicales", 9648 to "el misterio",
        10749 to "el romance", 878 to "la ciencia ficción", 10770 to "las de TV", 53 to "el suspenso",
        10752 to "las de guerra", 37 to "los westerns",
    )

    /** Chuba: tierna y tranquila. Ama lo dulce y luminoso; el terror y lo violento le dan miedo. Época favorita: fines de los 90. */
    private val chuba = mapOf(
        16 to 3.0, 10751 to 3.0, 10749 to 2.5, 35 to 2.0, 10402 to 2.0, 14 to 1.5, 12 to 1.0, 18 to 0.5, 99 to 0.5,
        28 to -0.5, 37 to -0.5, 80 to -1.0, 53 to -1.5, 10752 to -2.0, 27 to -3.0,
    )

    /** Pepa: alerta e intensa. Le gusta lo oscuro, lo tenso y lo que hace pensar; el romance y los musicales la aburren. Época favorita: años 2010. */
    private val pepa = mapOf(
        53 to 3.0, 9648 to 3.0, 80 to 2.5, 878 to 2.5, 27 to 2.5, 28 to 1.5, 12 to 1.0, 10752 to 1.0, 18 to 0.8,
        36 to 0.5, 14 to 0.5, 35 to -0.5, 16 to -1.0, 10751 to -1.5, 10749 to -2.0, 10402 to -2.0,
    )

    private fun weights(p: Pet) = if (p == Pet.Chuba) chuba else pepa
    private fun favoriteYear(p: Pet) = if (p == Pet.Chuba) 1998.0 else 2012.0
    private fun yearSpread(p: Pet) = if (p == Pet.Chuba) 14.0 else 11.0
    private fun exigence(p: Pet) = if (p == Pet.Chuba) 0.7 else 1.2          // cuánto pesa la nota del público
    private fun pivot(p: Pet) = if (p == Pet.Chuba) 6.8 else 7.2

    /** Ruido estable entre -1 y 1: el "estado de ánimo" de esa perrita con esa película, siempre el mismo. */
    private fun mood(pet: Pet, movieId: Int): Double {
        var h = (movieId.toLong() * 2654435761L) xor (pet.seed * 40503L)
        h = h xor (h ushr 13); h *= 1274126177L; h = h xor (h ushr 16)
        val m = ((h and 0xFFFF) / 65535.0)
        return m * 2 - 1
    }

    fun score(pet: Pet, movieId: Int, genreIds: List<Int>, year: Int?, rating: Double?): PetVerdict {
        val w = weights(pet)
        val gw = genreIds.mapNotNull { id -> w[id]?.let { id to it } }
        val mean = if (genreIds.isEmpty()) 0.0 else genreIds.sumOf { w[it] ?: 0.0 } / genreIds.size
        val best = gw.maxByOrNull { it.second }
        val worst = gw.minByOrNull { it.second }
        val g = (0.6 * mean + 0.4 * (best?.second ?: 0.0).coerceAtLeast(worst?.second ?: 0.0).let { top ->
            if ((worst?.second ?: 0.0) < -2.0) worst!!.second else top
        }).coerceIn(-3.0, 3.0)
        val quality = if (rating != null && rating > 0) (rating - pivot(pet)) * exigence(pet) else 0.0
        val era = if (year == null) 0.0 else {
            val d = (year - favoriteYear(pet)) / yearSpread(pet)
            exp(-d * d) - 0.4
        }
        var raw = 5.5 + 1.1 * g + quality + era + 0.7 * mood(pet, movieId)
        // Lo que de verdad no soportan limita la nota.
        if (genreIds.any { it == 27 } && pet == Pet.Chuba) raw = minOf(raw, 3.0)
        if (rating != null && rating in 0.1..5.9 && pet == Pet.Pepa) raw = minOf(raw, 4.0)
        val score = raw.coerceIn(1.0, 10.0).let { Math.round(it).toInt() }.coerceIn(1, 10)
        return PetVerdict(pet, score, reason(pet, score, best, worst, rating, year))
    }

    fun score(pet: Pet, e: Entry): PetVerdict =
        score(pet, e.movieId, e.genreIds, e.year.toIntOrNull(), e.rating)

    private fun reason(pet: Pet, score: Int, best: Pair<Int, Double>?, worst: Pair<Int, Double>?, rating: Double?, year: Int?): String {
        val bestName = best?.takeIf { it.second >= 1.5 }?.let { names[it.first] }
        val worstName = worst?.takeIf { it.second <= -1.5 }?.let { names[it.first] }
        return when {
            score >= 9 && bestName != null -> if (pet == Pet.Chuba) "Le fascina ${bestName}" else "Es de ${bestName}: justo lo suyo"
            score >= LIKE_FROM && bestName != null -> "Le gusta ${bestName}"
            score >= LIKE_FROM && rating != null && rating >= 7.5 -> "Muy bien calificada, la aprueba"
            score >= LIKE_FROM -> "Se ve buena según su olfato"
            score <= 3 && worstName != null -> if (pet == Pet.Chuba) "Le dan miedo o pena ${worstName}" else "Le aburre ${worstName}"
            score <= 4 && rating != null && rating in 0.1..6.0 -> "La nota del público no la convence"
            worstName != null -> "No es lo suyo: ${worstName}"
            year != null && abs(year - (if (pet == Pet.Chuba) 1998 else 2012)) > 25 -> "Le pilla de otra época"
            else -> "Ni fu ni fa"
        }
    }
}
