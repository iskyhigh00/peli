package com.peli.app.data

import kotlin.math.max

/** Una película "segura" para mí, con los motivos que se le muestran. */
data class SafePick(val entry: Entry, val score: Double, val reasons: List<String>)

/**
 * "Película segura": la candidata con más probabilidad de gustarme según mis notas. No es una garantía: es la que
 * mejor reúne parecido con lo que califiqué alto, recomendación de TMDB desde esas películas y buena calidad
 * (nota con muchos votos). Los pesos son valores iniciales a validar con uso real.
 */
/** Nombres de géneros de TMDB para los motivos. */
object GenreNames {
    private val names = mapOf(
        28 to "acción", 12 to "aventura", 16 to "animación", 35 to "comedia", 80 to "crimen", 99 to "documental",
        18 to "drama", 10751 to "familiar", 14 to "fantasía", 36 to "historia", 27 to "terror", 10402 to "música",
        9648 to "misterio", 10749 to "romance", 878 to "ciencia ficción", 53 to "suspenso", 10752 to "bélica", 37 to "western",
    )

    fun of(id: Int): String? = names[id]
}

object Safe {
    /** Con menos notas o gustos que esto no hay base para prometer nada. */
    const val MIN_SIGNALS = 5
    private const val MIN_TMDB_RATING = 6.8
    private const val MIN_VOTES = 300

    /** Cuenta las películas con las que se puede aprender (con nota o «me gustó»). */
    fun signals(marked: List<Entry>) = marked.count { it.myRating > 0 || it.liked }

    fun enoughData(marked: List<Entry>) = signals(marked) >= MIN_SIGNALS

    /** Lo que de verdad me gustó: nota 8 o más, o «me gustó» sin nota. */
    fun strongLike(e: Entry) = e.myRating >= 8 || (e.liked && e.myRating == 0)

    /** Hasta [n] películas de partida para pedir parecidas: las mejor calificadas, y entre iguales las más recientes. */
    fun seeds(marked: List<Entry>, n: Int): List<Entry> =
        marked.filter { strongLike(it) }
            .sortedWith(compareByDescending<Entry> { if (it.myRating > 0) it.myRating else 8 }.thenByDescending { it.updatedAt }.thenBy { it.movieId })
            .take(n)

    /** Una candidata solo entra si ya se estrenó y tiene buena nota con votos suficientes. */
    fun eligible(c: Entry, votes: Int?): Boolean = !c.isUpcoming && (c.rating ?: 0.0) >= MIN_TMDB_RATING && (votes ?: 0) >= MIN_VOTES

    /** Margen (años) bajo la época de lo que me gusta. */
    private const val ERA_MARGIN = 3

    /**
     * Desde qué año es «seguro» proponer: el 10 % más antiguo de lo que me gustó, menos [ERA_MARGIN] años. Si nunca
     * califiqué alto nada anterior al 2000, una película de 1985 no es una apuesta segura aunque sea muy buena.
     * null si hay pocas películas para saberlo (o no tienen año).
     */
    fun comfortFrom(marked: List<Entry>): Int? {
        val years = marked.filter { strongLike(it) }.mapNotNull { it.year.take(4).toIntOrNull() }.sorted()
        if (years.size < MIN_SIGNALS) return null
        // Percentil 10 (con pocas películas es la más antigua): una sola rareza muy vieja no abre toda la época.
        return years[(years.size - 1) / 10] - ERA_MARGIN
    }

    /** ¿La candidata cae en la época de lo que me gusta? Sin año conocido no se descarta. */
    fun inComfortZone(c: Entry, from: Int?): Boolean {
        val year = c.year.take(4).toIntOrNull() ?: return true
        return from == null || year >= from
    }

    /** Puntaje 0..1: afinidad con mi perfil, recomendaciones desde mis favoritas y calidad, menos el parecido con lo que rechacé. */
    fun score(c: Entry, profile: Recommender.Profile, meta: CandMeta?): Double {
        val rel = ((Recommender.relevance(c, profile, meta?.facts) - 0.5) * 2).coerceIn(0.0, 1.0)
        val q = ((Recommender.quality(c, meta?.votes) - 0.5) / 0.5).coerceIn(0.0, 1.0)
        val col = meta?.collab ?: 0.0
        val rejected = if (profile.negatives.isEmpty()) 0.0
        else 0.30 * max(0.0, profile.negatives.maxOf { Recommender.similarity(c, it) } - 0.4)
        return (0.40 * rel + 0.35 * col + 0.25 * q - rejected).coerceIn(0.0, 1.0)
    }

    /** Motivos legibles: de qué películas mías se parece, director o actor en común y su nota en TMDB. */
    fun reasons(
        c: Entry,
        meta: CandMeta?,
        marked: List<Entry>,
        markedFacts: Map<Int, MovieFacts>,
    ): List<String> {
        val byId = marked.associateBy { it.movieId }
        val out = ArrayList<String>()
        // Ojo: esto NO es parecido de contenido. TMDB la sugiere a quienes vieron esa película (según lo que ve su gente).
        for (s in (meta?.seeds.orEmpty()).mapNotNull { byId[it] }.take(2)) {
            out += if (s.myRating > 0) "TMDB la recomienda a quienes vieron «${s.displayTitle}», que calificaste con ${s.myRating}"
            else "TMDB la recomienda a quienes vieron «${s.displayTitle}», que te gustó"
        }
        val facts = meta?.facts
        val liked = marked.filter { strongLike(it) }
        facts?.director?.let { d ->
            liked.firstOrNull { markedFacts[it.movieId]?.director.equals(d, ignoreCase = true) }
                ?.let { out += "Del mismo director que «${it.displayTitle}» ($d)" }
        }
        val actors = facts?.actors.orEmpty().map { it.lowercase() }.toSet()
        if (actors.isNotEmpty()) {
            for (m in liked) {
                val shared = markedFacts[m.movieId]?.actors.orEmpty().firstOrNull { it.lowercase() in actors } ?: continue
                out += "Con $shared, como en «${m.displayTitle}»"
                break
            }
        }
        // Géneros y temas que de verdad se repiten en lo que me gustó (con cuántas de mis favoritas).
        if (liked.size >= 2) {
            val sharedGenres = c.genreIds.map { g -> g to liked.count { g in it.genreIds } }
                .filter { it.second >= 2 && GenreNames.of(it.first) != null }
                .sortedByDescending { it.second }.take(2)
            if (sharedGenres.isNotEmpty()) {
                out += "Géneros que se repiten en tus favoritas: " +
                    sharedGenres.joinToString(" y ") { "${GenreNames.of(it.first)} (${it.second} de ${liked.size})" }
            }
            val candKw = facts?.keywords.orEmpty().map { it.lowercase() }.toSet()
            if (candKw.isNotEmpty()) {
                val sharedKw = candKw.map { k -> k to liked.count { m -> markedFacts[m.movieId]?.keywords.orEmpty().any { it.lowercase() == k } } }
                    .filter { it.second >= 2 }.sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }).take(3)
                if (sharedKw.isNotEmpty()) {
                    out += "Temas que ya viste y te gustaron: " + sharedKw.joinToString(", ") { "${it.first} (${it.second} películas)" }
                }
            }
        }
        c.rating?.takeIf { it > 0 }?.let { r ->
            out += "Nota ${Ratings.formatScore(r)} en TMDB" + (meta?.votes?.let { " con ${Ratings.formatVotes(it)} votos" } ?: "")
        }
        return out
    }
}
