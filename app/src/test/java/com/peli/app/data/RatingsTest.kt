package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingsTest {
    @Test fun percentFromRottenTomatoesText() {
        assertEquals(91, Ratings.parsePercent("91%"))
        assertEquals(100, Ratings.parsePercent("100%"))
        assertNull(Ratings.parsePercent("101%"))
        assertNull(Ratings.parsePercent("N/A"))
        assertNull(Ratings.parsePercent(null))
    }

    @Test fun metascoreAcceptsBothFormats() {
        assertEquals(73, Ratings.parseMetascore("73"))
        assertEquals(73, Ratings.parseMetascore("73/100"))
        assertNull(Ratings.parseMetascore("N/A"))
        assertNull(Ratings.parseMetascore("120"))
        assertNull(Ratings.parseMetascore(null))
    }

    @Test fun votesWithThousandsSeparators() {
        assertEquals(2345, Ratings.parseVotes("2,345"))
        assertEquals(1_250_000, Ratings.parseVotes("1,250,000"))
        assertNull(Ratings.parseVotes("N/A"))
        assertNull(Ratings.parseVotes(null))
    }

    @Test fun votesAreFormattedCompactly() {
        assertEquals("950", Ratings.formatVotes(950))
        assertEquals("2.3K", Ratings.formatVotes(2345))
        assertEquals("1.3M", Ratings.formatVotes(1_250_000))
    }

    @Test fun scoresHaveOneDecimal() {
        assertEquals("7.3", Ratings.formatScore(7.25))
        assertEquals("8.0", Ratings.formatScore(8.0))
    }

    @Test fun reviewTextIsCleanedAndShortened() {
        assertEquals("Genial\nMuy buena", Ratings.cleanReview("**Genial**\r\n\n\nMuy _buena_"))
        val long = Ratings.cleanReview("a".repeat(800))
        assertEquals(701, long.length)
        assertTrue(long.endsWith("…"))
        assertEquals("corto", Ratings.cleanReview("corto"))
    }

    @Test fun savedSourcesDecodeToTheEnabledSet() {
        assertEquals(RatingSource.entries.toSet(), RatingSource.decode(null))
        assertEquals(setOf(RatingSource.Imdb), RatingSource.decode(setOf("Imdb", "FuenteInventada")))
        assertTrue(RatingSource.decode(emptySet()).isEmpty())
    }

    @Test fun onlyOmdbSourcesNeedTheKey() {
        assertTrue(RatingSource.Imdb.needsOmdb)
        assertTrue(RatingSource.RottenTomatoes.needsOmdb)
        assertTrue(RatingSource.Metacritic.needsOmdb)
        assertFalse(RatingSource.Tmdb.needsOmdb)
        assertFalse(RatingSource.Partner.needsOmdb)
        assertFalse(RatingSource.Reviews.needsOmdb)
    }
}
