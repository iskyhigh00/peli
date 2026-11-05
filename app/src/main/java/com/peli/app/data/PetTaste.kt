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

    /** Palabras clave de TMDB que delatan una película ruidosa o violenta aunque su género diga "drama". */
    private val loudWords = listOf(
        "violen", "gore", "murder", "serial killer", "brutal", "gun", "shootout", "explosion", "torture", "massacre",
        "gang", "fight", "assassin", "bomb", "monster", "zombie", "demon", "haunt", "slasher", "battle", "revenge", "kill",
    )
    /** Lo que a Chuba le mueve la cola: persecuciones, carreras, comida, playa y perros. */
    private val energyWords = listOf("chase", "race", "racing", "explosion", "fight", "heist", "beach", "surf", "food", "cooking", "dog")
    /** Lo que a Pepa le da calma: perros, amistad, naturaleza y familia. */
    private val cozyWords = listOf("dog", "friendship", "nature", "family", "love", "cat")

    fun score(pet: Pet, movieId: Int, genreIds: List<Int>, year: Int?, rating: Double?, keywords: List<String> = emptyList()): PetVerdict {
        val kw = keywords.map { it.lowercase() }
        fun hits(words: List<String>) = words.count { w -> kw.any { it.contains(w) } }
        val loudHit = hits(loudWords)
        val dogHit = kw.any { it.contains("dog") }
        val kwAdj = if (pet == Pet.Chuba) minOf(1.5, 0.6 * hits(energyWords))
        else -minOf(5.0, 1.8 * loudHit) + minOf(1.2, 0.6 * hits(cozyWords))
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
        var raw = 5.5 + 1.1 * g + quality + era + 0.7 * mood(pet, movieId) + kwAdj
        // Lo que de verdad no soportan limita la nota.
        if (pet == Pet.Pepa && 27 in genreIds) raw = minOf(raw, 3.0)          // el susto la hace arrancar
        if (pet == Pet.Chuba && genreIds == listOf(99)) raw = minOf(raw, 4.0)  // un documental sentada no es lo suyo
        val score = raw.coerceIn(1.0, 10.0).let { Math.round(it).toInt() }.coerceIn(1, 10)
        return PetVerdict(pet, score, reason(pet, movieId, score, best, worst, rating, year, loudHit > 0, dogHit))
    }

    fun score(pet: Pet, e: Entry, keywords: List<String> = emptyList()): PetVerdict =
        score(pet, e.movieId, e.genreIds, e.year.toIntOrNull(), e.rating, keywords)

    /** Varía la frase según la película (siempre la misma para una misma película). */
    private fun pick(movieId: Int, vararg options: String) = options[Math.floorMod(movieId, options.size)]

    // Comentarios en primera persona: lo que diría cada una de esta película en concreto.
    private val chubaLikes = mapOf(
        28 to listOf("¡Persecuciones! Yo corro con ellos, ¿quién pilla primero la mosca?", "Tanta acción y yo sentada... mejor corro por el living", "Golpes y carreras: ladro a la pantalla antes que la Pepa"),
        12 to listOf("Aventura: yo ya estoy en la playa corriendo como loca", "¡Viaje y peligro! Yo me lanzo primero", "Quiero salir a explorar, ¿hay snacks en el camino?"),
        35 to listOf("Me hace mover la cola, hasta me olvido de la comida (casi)", "Risas y desorden: mi tipo de tarde"),
        16 to listOf("Colores y movimiento: se me van los ojos tras cada dibujo", "Todo se mueve, y yo persigo lo que se mueve"),
        878 to listOf("Naves y cosas que vuelan: ¡yo las persigo!", "Mucho futuro y mucho movimiento, me gusta"),
        53 to listOf("Tensión y corridas: yo igual me quedo con las orejas paradas", "Misterio con persecución, me tiene alerta"),
        10752 to listOf("Mucho correr y gritar: me siento parte del grupo", "Ruido y acción: yo sí me quedo"),
        80 to listOf("Fugas y persecuciones, como cuando me escapo tras un perro", "Hay carreras y trampas: ¡yo también me escapo!"),
    )
    private val chubaDislikes = mapOf(
        18 to listOf("Todos hablan y hablan y yo sin comida, me aburro", "Mucho drama quieto, yo mejor busco un snack", "Drama: me duermo antes que se muera alguien"),
        10749 to listOf("Puro beso y suspiro, sin carreras: me voy a la cocina", "Romance sin perseguir a nadie: no es lo mío"),
        99 to listOf("Un documental sentada no se puede, ¡me muero de aburrimiento!", "Nada se mueve, yo me paro a ladrarle a alguien"),
        36 to listOf("Mucha historia hablada, ¿dónde está la comida?", "Vestidos y charlas largas: me aburro"),
        9648 to listOf("Tanto misterio quieto, yo prefiero perseguir moscas"),
        10402 to listOf("Cantan mucho y no corren: me voy a mirar la ventana"),
    )
    private val pepaLikes = mapOf(
        18 to listOf("Un drama tranquilo, mi manta y mi rincón: perfecto", "Me emociona y nadie grita: quiero verla acurrucada", "Es sentida y calmada, justo lo que quiero esta tarde"),
        10749 to listOf("Romance suave, cariño sin que me lo hagan a la fuerza", "Historia de amor y silencio: me quedo quietita", "Qué lindo todo, me acurruco hasta el final"),
        14 to listOf("Magia sin ruido: me gusta soñar en mi rincón", "Fantasía bonita, sin sustos"),
        10751 to listOf("Familia y calma: para ver a mi ritmo", "Cálida y sin peligro: la aprobada de la casa"),
        99 to listOf("Cosas reales y calmadas, me quedo mirando tranquila", "Un documental sin ruido: lo veo en mi espacio"),
        10402 to listOf("Música suave: me hace querer echarme a escuchar", "Canciones lindas y sin gritos, me gusta"),
        16 to listOf("Dibujos lindos y tranquilos, me los miro de a poco"),
        35 to listOf("Risas suaves, sin sobresaltos: me gusta"),
    )
    private val pepaDislikes = mapOf(
        27 to listOf("¡Sustos! Ni la miro, yo arranco a esconderme", "Terror: me meto bajo la cama y no salgo"),
        10752 to listOf("Bombas y gritos: ¡yo arranco!", "Guerra: demasiado ruido, me voy a otra pieza"),
        28 to listOf("Tiros y golpes: me tapo las orejas y arranco", "Tanta explosión me hace buscar la puerta"),
        53 to listOf("Tanta tensión me tiene nerviosa, prefiero mi rincón", "Peligro todo el rato: yo me escondo"),
        80 to listOf("Violencia y gente mala: me voy a mi espacio", "Crimen y gritos: no es para mí"),
        878 to listOf("Máquinas y ruido: demasiado para mi gusto"),
    )

    private fun reason(
        pet: Pet, movieId: Int, score: Int, best: Pair<Int, Double>?, worst: Pair<Int, Double>?, rating: Double?, year: Int?,
        loudHit: Boolean, dogHit: Boolean,
    ): String {
        val likes = if (pet == Pet.Chuba) chubaLikes else pepaLikes
        val dislikes = if (pet == Pet.Chuba) chubaDislikes else pepaDislikes
        val liked = score >= LIKE_FROM
        val bad = score <= 5
        return if (pet == Pet.Chuba) when {
            dogHit && liked -> pick(movieId, "¡HAY PERROS! Los miro y ladro con ganas", "Sale un perro: ya estoy ladrándole a la pantalla", "Perritos: me paro y los persigo con la mirada")
            liked && best != null && likes[best.first] != null -> likes.getValue(best.first).let { pick(movieId, *it.toTypedArray()) }
            liked -> pick(movieId, "Se ve entretenida, la veo si hay snacks", "Algo se mueve en pantalla, ¡me quedo!")
            bad && worst != null && dislikes[worst.first] != null -> dislikes.getValue(worst.first).let { pick(movieId, *it.toTypedArray()) }
            bad && rating != null && rating in 0.1..5.5 -> pick(movieId, "Ni la comida me anima con esa mala nota", "Mala y aburrida: me voy por un snack")
            bad -> pick(movieId, "Me aburro rápido, mejor me voy a la cocina", "Nada pasa: ¿dónde está mi comida?")
            else -> pick(movieId, "Ni fu ni fa, la veo con un snack", "Me da lo mismo, si hay comida me quedo", "Un rato la miro y luego me voy a perseguir moscas")
        } else when {
            loudHit && score <= 7 -> pick(movieId, "Peleas, violencia y ruido: yo arranco", "Demasiado ruido y golpes, me escondo y arranco")
            dogHit && liked -> pick(movieId, "Sale un perrito y hay calma: me quedo quietita", "Con un perro por ahí me acurruco feliz")
            liked && best != null && likes[best.first] != null -> likes.getValue(best.first).let { pick(movieId, *it.toTypedArray()) }
            liked -> pick(movieId, "Se ve calmada, la apruebo desde mi rincón", "Sin ruido ni sustos: me gusta")
            bad && worst != null && dislikes[worst.first] != null -> dislikes.getValue(worst.first).let { pick(movieId, *it.toTypedArray()) }
            bad -> pick(movieId, "Mucho ruido o peligro: yo arranco", "Prefiero mi espacio, no me llama")
            year != null && year > 2018 -> pick(movieId, "Muy moderna y ruidosa para mí, prefiero mi rincón", "Tanto ruido nuevo... no me dejan tranquila")
            else -> pick(movieId, "Ni fu ni fa, prefiero mi rincón", "La veo un ratito y me voy, que no me molesten")
        }
    }
}
