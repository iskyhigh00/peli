package com.peli.app.data

import kotlin.math.abs
import kotlin.math.exp

/** Las dos críticas de la casa. Cada una tiene su propio gusto fijo: la misma película siempre recibe la misma nota. */
enum class Pet(val label: String, val seed: Long, val discoverGenres: String) {
    Chuba("Chuba", 7919L, "28|12|35|16|10752"),
    Pepa("Pepa", 104729L, "18|10749|14|10751|99"),
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

    /**
     * Chuba: inquieta, ávida y de mucha energía (persigue moscas y perros, ladra primero, corre desesperada en la playa).
     * Le gusta la acción, las persecuciones y lo que se mueve; se aburre con lo lento y hablado. Época favorita: 2000s.
     */
    private val chuba = mapOf(
        28 to 3.0, 12 to 3.0, 35 to 2.0, 16 to 1.5, 878 to 1.5, 53 to 1.5, 10752 to 1.0, 80 to 1.0, 37 to 1.0, 10751 to 0.5,
        14 to 0.5, 9648 to -1.0, 10402 to -0.5, 18 to -1.5, 10749 to -1.5, 36 to -1.5, 99 to -2.0,
    )

    /**
     * Pepa: tranquila, le gusta su espacio, el ruido la molesta y cuando hay peligro arranca. Prefiere lo calmado, lo
     * emotivo y lo cálido; huye del ruido, la violencia y el susto. Le gustan los clásicos. Época favorita: años 90.
     */
    private val pepa = mapOf(
        18 to 3.0, 10749 to 3.0, 14 to 2.0, 10751 to 2.0, 99 to 2.0, 10402 to 1.5, 16 to 1.5, 35 to 1.5, 36 to 1.0, 9648 to 1.0,
        12 to 0.5, 878 to -0.5, 80 to -1.0, 53 to -1.5, 28 to -2.0, 10752 to -2.5, 27 to -3.0,
    )

    private fun weights(p: Pet) = if (p == Pet.Chuba) chuba else pepa
    private fun favoriteYear(p: Pet) = if (p == Pet.Chuba) 2008.0 else 1992.0
    private fun yearSpread(p: Pet) = if (p == Pet.Chuba) 12.0 else 20.0
    private fun exigence(p: Pet) = if (p == Pet.Chuba) 0.5 else 0.9          // cuánto pesa la nota del público
    private fun pivot(p: Pet) = if (p == Pet.Chuba) 6.4 else 7.0

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
        if (pet == Pet.Pepa && 27 in genreIds) raw = minOf(raw, 3.0)          // el susto la hace arrancar
        if (pet == Pet.Chuba && genreIds == listOf(99)) raw = minOf(raw, 4.0)  // un documental sentada no es lo suyo
        val score = raw.coerceIn(1.0, 10.0).let { Math.round(it).toInt() }.coerceIn(1, 10)
        return PetVerdict(pet, score, reason(pet, score, best, worst, rating, year))
    }

    fun score(pet: Pet, e: Entry): PetVerdict =
        score(pet, e.movieId, e.genreIds, e.year.toIntOrNull(), e.rating)

    private fun reason(pet: Pet, score: Int, best: Pair<Int, Double>?, worst: Pair<Int, Double>?, rating: Double?, year: Int?): String {
        val bestName = best?.takeIf { it.second >= 1.5 }?.let { names[it.first] }
        val worstName = worst?.takeIf { it.second <= -1.5 }?.let { names[it.first] }
        val loud = worst?.first in setOf(28, 10752, 27, 53)
        return if (pet == Pet.Chuba) when {
            score >= 9 && best?.first in setOf(28, 12) -> "Pura persecución y adrenalina (ojo con los tobillos)"
            score >= 9 && bestName != null -> "Le fascina ${bestName}: no para de moverse"
            score >= LIKE_FROM && bestName != null -> "Tiene movimiento: le gusta ${bestName}"
            score >= LIKE_FROM -> "Se ve entretenida, corre a verla"
            score <= 3 && worstName != null -> "Se aburre con ${worstName}: va por comida"
            worstName != null -> "Muy quieta para ella: ${worstName}"
            rating != null && rating in 0.1..5.5 -> "Ni la comida la entusiasma con esa nota"
            else -> "Ni fu ni fa, mejor un snack"
        } else when {
            score >= 9 && bestName != null -> "Tranquila y a su espacio: le encanta ${bestName}"
            score >= LIKE_FROM && bestName != null -> "Le gusta ${bestName}, para verla acurrucada"
            score >= LIKE_FROM -> "Se ve calmada, la aprueba"
            score <= 3 && loud -> "Mucho ruido o peligro: ella arranca"
            score <= 3 && worstName != null -> "No es lo suyo: ${worstName}"
            worstName != null -> "Le aburre ${worstName}"
            year != null && year > 2018 -> "Muy moderna y ruidosa para ella"
            else -> "Ni fu ni fa, prefiere su rincón"
        }
    }
}
