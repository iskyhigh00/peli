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

    // Comentarios en primera persona: lo que diría cada una de esta película en concreto. Hablan de la película;
    // las manías de cada una (Chuba y los perros, Pepa y el ruido) asoman solo de vez en cuando, no en cada frase.
    private val chubaLikes = mapOf(
        28 to listOf("Esto sí. Alguien persigue a alguien y yo ya tomé partido", "Cuando empieza la pelea ya estoy parada en el sillón", "Si no me dejan correr, que al menos pase en la tele"),
        12 to listOf("Hay viaje y hay problemas, o sea, perfecto", "Mucho camino por recorrer. Cuenten conmigo", "Que alguien me explique por qué yo no voy en esa expedición"),
        35 to listOf("Me río con la cola, que es como me río yo", "Parte tonta, parte buena. Me quedo hasta el final", "Esta se la mostraría a todos los perros del barrio"),
        16 to listOf("Todo se mueve y de colores, ¿qué más se puede pedir?", "Mis ojos no saben a cuál dibujo seguir", "La miro tan fijo que me van a preguntar si estoy bien"),
        878 to listOf("Naves, luces y velocidad: estoy dentro", "Hay cosas que vuelan y yo no las alcanzo. Frustrante, pero entretenida", "Futuro y ruido, justo lo mío"),
        53 to listOf("Tensión: ya tengo las orejas paradas", "No sé quién es el malo, pero ya le ladré a uno", "Suena algo en la peli, ladro, y sigue sonando"),
        10752 to listOf("Todos corren para el mismo lado, y yo igual", "Gritos, marchas y estruendo. Lo aguanto bien"),
        80 to listOf("Persecución, trampa y escape: me suena", "La gente que se escapa me cae bien"),
    )
    private val chubaDislikes = mapOf(
        18 to listOf("Todos hablan en voz baja y nadie corre. Me dormí", "Alguien llora y yo no sé qué hacer con eso", "Seria y lenta: no aguanto ni diez minutos", "Mucha charla seria. Me fui a ver qué hacía la Pepa"),
        10749 to listOf("Se miran mucho y no pasa nada", "Casi todo el rato son dos personas mirándose. Paso"),
        99 to listOf("Nadie corre ni ladra. Se me hace eterna", "Explican cosas con voz tranquila: es una siesta con imágenes"),
        36 to listOf("Todo es vestidos y mesas largas. Me pierdo", "Mucha charla antigua. Me bajé"),
        9648 to listOf("Tanto pensar quieta... yo prefiero que pase algo"),
        10402 to listOf("Cantan y se quedan parados. Nada que perseguir"),
    )
    private val pepaLikes = mapOf(
        18 to listOf("Es triste y a la vez calma. Me encanta", "Empiezo a regañadientes y termino con la mirada fija en la pantalla", "Hay lágrimas, pero en voz baja. Mi nivel", "Las que se sienten, las aguanto bien"),
        10749 to listOf("Cariño sin escándalo. Pase", "Quiero que se junten y que no griten", "Me hago la desinteresada mientras la miro entera", "Dos personas que se quieren a su ritmo. Lo respeto"),
        14 to listOf("Mundo inventado y silencio. Me gusta", "Magia sin trucos ruidosos", "Un cuento: justo lo que quería"),
        10751 to listOf("Cálida y sin sobresaltos. La apruebo", "Se parece a un domingo largo, y lo digo con cariño"),
        99 to listOf("Aprendo cosas sin que nadie me grite", "Voz suave y naturaleza: me quedo"),
        10402 to listOf("Música tranquila de fondo. Perfecto", "Cantan bajito, qué detalle"),
        16 to listOf("Dibujos suaves, a mi ritmo"),
        35 to listOf("Risa suave, sin sobresaltos. Me sirve"),
    )
    private val pepaDislikes = mapOf(
        27 to listOf("Ni la miro. Todos esos gritos de repente...", "Terror: prefiero no enterarme del final", "Me suena a susto en cada esquina. No"),
        10752 to listOf("Mucho estruendo. No es para mí", "Guerra: demasiada gente gritando a la vez"),
        28 to listOf("Demasiado golpe y estruendo. Me voy a otra pieza", "Todo explota y nadie baja la voz", "Si sube el volumen, me bajo yo"),
        53 to listOf("Tensión permanente: el cuerpo se me pone en alerta", "Mucho peligro y sin pausa"),
        80 to listOf("Gente mala y gritos. No, gracias", "Crimen y escándalo. Paso"),
        878 to listOf("Máquinas haciendo ruido. Demasiado"),
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
            dogHit && liked -> pick(movieId, "¡Hay un perro en pantalla! Ya no veo nada más", "Sale un perro y yo me paro. Eso es todo lo que tengo que decir", "Un perro, dos perros... les voy a ladrar a todos", "Perro en la tele: me pongo a su altura")
            liked && best != null && likes[best.first] != null -> likes.getValue(best.first).let { pick(movieId, *it.toTypedArray()) }
            liked -> pick(movieId, "Se ve entretenida. Me quedo", "Se mueve lo suficiente como para que la mire", "Me tiene atenta, y eso ya es mucho")
            bad && worst != null && dislikes[worst.first] != null -> dislikes.getValue(worst.first).let { pick(movieId, *it.toTypedArray()) }
            bad && rating != null && rating in 0.1..5.5 -> pick(movieId, "Con esa nota ya lo sabía. Y igual la miré", "Mala nota y lenta: se me pasó el ánimo")
            bad -> pick(movieId, "Pasa poco y se tarda mucho", "Me aburrí antes de que empezara lo bueno, si es que lo hay", "Esta no me levantó ni una oreja")
            else -> pick(movieId, "Está bien. La veo mientras hago otra cosa", "Ni la amo ni la odio, estoy ahí", "La miro un rato y vuelvo", "Cumple. Sin gritar de emoción")
        } else when {
            loudHit && score <= 7 -> pick(movieId, "Hay golpes y mucho ruido. Prefiero no", "Violencia y ruido sin pausa: no es para mí")
            dogHit && liked -> pick(movieId, "Hay un perrito y todo está en calma: me gusta", "Con un perro en pantalla me quedo hasta el final")
            liked && best != null && likes[best.first] != null -> likes.getValue(best.first).let { pick(movieId, *it.toTypedArray()) }
            liked -> pick(movieId, "No molesta y se deja ver. Aprobada", "Tranquila y sin sustos. Se queda")
            bad && worst != null && dislikes[worst.first] != null -> dislikes.getValue(worst.first).let { pick(movieId, *it.toTypedArray()) }
            bad -> pick(movieId, "Demasiado ruido, demasiado todo", "No me llama. Y no es por ti, es por mí", "Si pasan a verla, la veo de lejos")
            year != null && year > 2018 -> pick(movieId, "Es muy nueva y suena a mucho", "Tanto efecto nuevo me cansa")
            else -> pick(movieId, "No me dice mucho. La dejo pasar", "La veo un rato y me voy", "Sin opinión fuerte. Quizás otro día", "Está bien, pero no me convence")
        }
    }
}
