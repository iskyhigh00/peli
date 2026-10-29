package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectorsTest {
    private fun f(movie: Int, dirId: Int?, name: String? = "Dir $dirId", wins: Int = 0, oscars: Int = 0, omdb: Boolean = true) =
        MovieFacts(movie, tmdbAt = 1, director = name, directorId = dirId, wins = wins, oscarWins = oscars, omdbAt = if (omdb) 1 else 0)

    @Test fun `solo directores con tres o mas peliculas`() {
        val facts = listOf(f(1, 10), f(2, 10), f(3, 10), f(4, 20), f(5, 20))
        assertEquals(listOf(10), Directors.rank(facts).map { it.id })
        assertEquals(listOf(10, 20), Directors.rank(facts, minFilms = 2).map { it.id })
    }

    @Test fun `ordena por peliculas y a igualdad por premios`() {
        val facts = (1..4).map { f(it, 1, wins = 0) } +                       // 4 películas, 0 premios
            (11..13).map { f(it, 2, wins = 50) } +                            // 3 películas, 150 premios
            (21..23).map { f(it, 3, wins = 10, oscars = 1) } +                // 3 películas, 30 premios
            (31..33).map { f(it, 4, wins = 10, oscars = 3) }                  // 3 películas, 30 premios y más Oscars
        assertEquals(listOf(1, 2, 4, 3), Directors.rank(facts).map { it.id })
    }

    @Test fun `los premios solo cuentan con datos de OMDb`() {
        val facts = listOf(f(1, 5, wins = 99, omdb = false), f(2, 5, wins = 1), f(3, 5, wins = 2))
        val d = Directors.rank(facts).single()
        assertEquals(3, d.wins)
        assertEquals(3, d.films)
    }

    @Test fun `ignora filas sin director o sin id y repetidas`() {
        val facts = listOf(f(1, null), f(2, 7, name = null), f(3, 7, name = " "), f(4, 7), f(4, 7), f(5, 7), f(6, 7))
        val d = Directors.rank(facts, minFilms = 1).single()
        assertEquals(7, d.id)
        assertEquals(3, d.films)           // 4, 5 y 6 (la 4 repetida cuenta una vez)
    }

    @Test fun `el nombre es el mas usado`() {
        val facts = listOf(f(1, 9, name = "C. Nolan"), f(2, 9, name = "Christopher Nolan"), f(3, 9, name = "Christopher Nolan"))
        assertEquals("Christopher Nolan", Directors.rank(facts).single().name)
    }

    @Test fun `el filtro de director se guarda y se lee, incluso con simbolos en el nombre`() {
        val filters = MovieFilters(directorId = 525, directorName = "Denis Villeneuve; ¿ñandú=1?")
        assertEquals(filters, MovieFilters.decode(filters.encode()))
        assertTrue(filters.isActive)
        assertEquals(1, filters.count())
        assertTrue(filters.summary().contains("Denis Villeneuve"))
        assertEquals(MovieFilters.NONE, MovieFilters.decode(MovieFilters.NONE.encode()))
        assertEquals(0, MovieFilters.decode("d=-4;dn=%ZZ").directorId)
    }

    @Test fun `el filtro de director no pide datos de OMDb`() {
        assertTrue(!MovieFilters(directorId = 5, directorName = "X").needsOmdb)
    }
}
