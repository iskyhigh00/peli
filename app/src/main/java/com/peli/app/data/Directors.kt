package com.peli.app.data

/** Un director con las películas suyas que la app conoce y sus premios (los de esas películas, según OMDb). */
data class DirectorStat(val id: Int, val name: String, val films: Int, val wins: Int, val oscarWins: Int)

object Directors {
    const val MIN_FILMS = 3

    /**
     * Directores con al menos [minFilms] películas entre [facts] (las que la app ya conoce), de más a menos películas y,
     * a igualdad, de más a menos premios ganados; luego Oscars y por último el nombre (orden estable).
     * Los premios solo cuentan de películas con datos de OMDb.
     */
    fun rank(facts: Collection<MovieFacts>, minFilms: Int = MIN_FILMS): List<DirectorStat> =
        facts.filter { it.directorId != null && !it.director.isNullOrBlank() }
            .distinctBy { it.movieId }
            .groupBy { it.directorId!! }
            .map { (id, list) ->
                val name = list.groupingBy { it.director!!.trim() }.eachCount().maxWithOrNull(compareBy({ it.value }, { it.key }))!!.key
                DirectorStat(
                    id = id,
                    name = name,
                    films = list.size,
                    wins = list.filter { it.hasOmdb }.sumOf { it.wins },
                    oscarWins = list.filter { it.hasOmdb }.sumOf { it.oscarWins },
                )
            }
            .filter { it.films >= minFilms }
            .sortedWith(
                compareByDescending<DirectorStat> { it.films }
                    .thenByDescending { it.wins }
                    .thenByDescending { it.oscarWins }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id },
            )
}
