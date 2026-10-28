package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeTest {
    private val now = System.currentTimeMillis()
    private fun rated(id: Int, rating: Int, genres: String = "18", year: String = "2010") =
        entry(id, genres = genres, year = year, myRating = rating, seen = true, liked = rating >= 8, updatedAt = now - id * 1000L)

    @Test fun `hacen falta al menos cinco notas o gustos`() {
        assertFalse(Safe.enoughData(emptyList()))
        assertFalse(Safe.enoughData((1..4).map { rated(it, 9) }))
        assertTrue(Safe.enoughData((1..5).map { rated(it, 9) }))
        assertEquals(2, Safe.signals(listOf(rated(1, 3), entry(2, liked = true), entry(3, watchlist = true))))
    }

    @Test fun `las semillas son lo mejor calificado`() {
        val marked = listOf(rated(1, 10), rated(2, 8), rated(3, 5), entry(4, liked = true, seen = true), rated(5, 9), entry(6, disliked = true))
        val seeds = Safe.seeds(marked, 3).map { it.movieId }
        assertEquals(listOf(1, 5, 2), seeds)   // 10, 9 y 8 (la «me gustó» sin nota cuenta como 8 pero no tiene fecha: va después)
        assertFalse(3 in Safe.seeds(marked, 10).map { it.movieId })
        assertFalse(6 in Safe.seeds(marked, 10).map { it.movieId })
    }

    @Test fun `solo entran estrenadas con buena nota y votos suficientes`() {
        assertTrue(Safe.eligible(entry(1, rating = 7.5, year = "2015"), 1000))
        assertFalse(Safe.eligible(entry(1, rating = 6.5, year = "2015"), 1000))
        assertFalse(Safe.eligible(entry(1, rating = 7.5, year = "2015"), 100))
        assertFalse(Safe.eligible(entry(1, rating = 7.5, year = "2015"), null))
        assertFalse(Safe.eligible(entry(1, rating = null, year = "2015"), 1000))
        assertFalse(Safe.eligible(entry(1, rating = 8.0, year = "2999"), 5000))   // aún no se estrena
    }

    @Test fun `puntaje mayor con parecido, recomendacion y calidad`() {
        val profile = Recommender.buildProfile((1..6).map { rated(it, 10, "18,53") })
        val near = entry(100, genres = "18,53", year = "2011", rating = 8.0)
        val far = entry(101, genres = "35", year = "1975", rating = 8.0)
        val a = Safe.score(near, profile, CandMeta(votes = 5000, collab = 0.6))
        val b = Safe.score(far, profile, CandMeta(votes = 5000, collab = 0.0))
        assertTrue("$a > $b", a > b)
        assertTrue(a in 0.0..1.0 && b in 0.0..1.0)
    }

    @Test fun `parecerse a lo que rechace baja el puntaje`() {
        val liked = (1..6).map { rated(it, 9, "18") }
        val bad = entry(50, genres = "27", year = "2010", disliked = true, updatedAt = now)
        val c = entry(100, genres = "27", year = "2010", rating = 8.0)
        val with = Safe.score(c, Recommender.buildProfile(liked + bad), CandMeta(votes = 5000))
        val without = Safe.score(c, Recommender.buildProfile(liked), CandMeta(votes = 5000))
        assertTrue("$with < $without", with < without)
    }

    @Test fun `los motivos nombran las peliculas mias y la nota`() {
        val marked = listOf(rated(1, 10).copy(title = "Inception"), rated(2, 9).copy(title = "Memento"))
        val c = entry(100, genres = "18", rating = 8.4)
        val r = Safe.reasons(c, CandMeta(votes = 12_300, seeds = listOf(1, 2, 99)), marked, emptyMap())
        assertEquals(3, r.size)
        assertTrue(r[0].contains("Inception") && r[0].contains("10"))
        assertTrue(r[1].contains("Memento"))
        assertTrue(r[2].contains("8.4") && r[2].contains("12.3K"))
    }

    @Test fun `mismo director o actor en comun aparece como motivo`() {
        val marked = listOf(rated(1, 10).copy(title = "Inception"))
        val markedFacts = mapOf(1 to MovieFacts(1, tmdbAt = 1, director = "Christopher Nolan", actors = listOf("Actor Uno", "Actor Dos")))
        val facts = MovieFacts(100, tmdbAt = 1, director = "christopher nolan", actors = listOf("Otro", "actor dos"))
        val r = Safe.reasons(entry(100, rating = null), CandMeta(facts = facts), marked, markedFacts)
        assertTrue(r.any { it.contains("mismo director") && it.contains("Inception") })
        assertTrue(r.any { it.contains("Actor Dos") })
    }
}
