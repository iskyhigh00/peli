package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoodTest {
    private fun m(id: Int, genres: String?, rating: Double? = null, both: Boolean = false) =
        Match(entry(id = id, genres = genres, rating = rating), iWant = true, partnerWants = both)

    @Test fun aMoodNeedsOneOfItsGenresAndNoneOfTheExcluded() {
        assertTrue(Mood.Tranqui.accepts(listOf(35, 10749)))
        assertFalse(Mood.Tranqui.accepts(listOf(35, 27)))   // comedia de terror: no es tranqui
        assertFalse(Mood.Tranqui.accepts(listOf(28)))
        assertTrue(Mood.Terror.accepts(listOf(27, 53)))
        assertFalse(Mood.Terror.accepts(listOf(35)))
    }

    @Test fun aMovieWithoutGenresOnlyFitsAnything() {
        assertFalse(Mood.Accion.accepts(emptyList()))
        assertTrue(Mood.Cualquiera.accepts(emptyList()))
    }

    @Test fun picksOnlyKeepAvailableOnesWhenAsked() {
        val list = listOf(m(1, "28"), m(2, "28"), m(3, "35"))
        assertEquals(listOf(1), moodPicks(list, setOf(1, 3), Mood.Accion, onlyAvailable = true).map { it.entry.movieId })
        assertEquals(setOf(1, 2), moodPicks(list, setOf(1, 3), Mood.Accion, onlyAvailable = false).map { it.entry.movieId }.toSet())
    }

    @Test fun picksPutTheOnesBothWantFirstThenTheBestRated() {
        val list = listOf(m(1, "35", rating = 9.0), m(2, "35", rating = 6.0, both = true), m(3, "35", rating = 8.0))
        assertEquals(listOf(2, 1, 3), moodPicks(list, emptySet(), Mood.Reir, onlyAvailable = false).map { it.entry.movieId })
    }

    @Test fun countsMatchThePicks() {
        val list = listOf(m(1, "27"), m(2, "35"), m(3, null))
        val counts = moodCounts(list, setOf(1, 2, 3), onlyAvailable = true)
        assertEquals(1, counts[Mood.Terror])
        assertEquals(1, counts[Mood.Reir])
        assertEquals(3, counts[Mood.Cualquiera])
        assertEquals(0, counts[Mood.Romantica])
    }
}
