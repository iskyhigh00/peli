package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RichFeaturesTest {
    private fun facts(id: Int, keywords: List<String> = emptyList(), director: String? = null, actors: List<String> = emptyList()) =
        MovieFacts(id, "tt$id", keywords, director, actors, tmdbAt = 1)

    private val liked = entry(1, genres = "18", year = "2010", liked = true, seen = true, myRating = 9, updatedAt = System.currentTimeMillis())
    private val likedFacts = mapOf(1 to facts(1, listOf("time travel", "paradox"), "Director Uno", listOf("Actor A", "Actor B")))

    @Test fun `sin datos extra el resultado es el de siempre`() {
        val a = Recommender.buildProfile(listOf(liked))
        val b = Recommender.buildProfile(listOf(liked), facts = emptyMap())
        val c = entry(2, genres = "18", year = "2010")
        assertEquals(Recommender.relevance(c, a), Recommender.relevance(c, b), 1e-6)
        assertEquals(Recommender.relevance(c, a), Recommender.relevance(c, b, MovieFacts(2)), 1e-6)   // facts vacíos no cuentan
    }

    @Test fun `compartir palabras clave y director con lo que me gustó sube la afinidad`() {
        val profile = Recommender.buildProfile(listOf(liked), facts = likedFacts)
        val c = entry(2, genres = "18", year = "2010")
        val plain = Recommender.relevance(c, profile)
        val related = Recommender.relevance(c, profile, facts(2, listOf("time travel"), "Director Uno", listOf("Actor A")))
        val unrelated = Recommender.relevance(c, profile, facts(2, listOf("cooking"), "Otro", listOf("Nadie")))
        assertTrue("$related > $plain", related > plain)
        assertTrue("$unrelated <= $related", unrelated < related)
    }

    @Test fun `las palabras clave no cuentan como géneros favoritos`() {
        val profile = Recommender.buildProfile(listOf(liked), facts = likedFacts)
        assertEquals(listOf(18), profile.topGenres(5))
    }

    @Test fun `un director de algo que rechacé baja la afinidad`() {
        val bad = entry(3, genres = "18", year = "2010", disliked = true, updatedAt = System.currentTimeMillis())
        val profile = Recommender.buildProfile(listOf(bad), facts = mapOf(3 to facts(3, director = "Mal Director")))
        val c = entry(4, genres = "18", year = "2010")
        val same = Recommender.relevance(c, profile, facts(4, director = "Mal Director"))
        val other = Recommender.relevance(c, profile, facts(4, director = "Buen Director"))
        assertTrue(same < other)
    }

    @Test fun `el puntaje base usa los datos extra de la candidata`() {
        val profile = Recommender.buildProfile(listOf(liked), facts = likedFacts)
        val c = entry(2, genres = "18", year = "2010", rating = 7.0)
        val with = Recommender.baseScore(c, profile, CandMeta(votes = 500, facts = facts(2, listOf("time travel"), "Director Uno")))
        val without = Recommender.baseScore(c, profile, CandMeta(votes = 500))
        assertTrue(with > without)
    }
}
