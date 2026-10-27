package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecommenderTest {
    private val now = 2_000_000_000_000L
    private val day = 86_400_000L

    private fun profileOf(vararg e: Entry) = Recommender.buildProfile(e.toList(), now)

    // ---- Señal única por película ----------------------------------------------------------

    @Test fun ratingOverridesLikedAndSeenIsNeutral() {
        assertEquals((10 - 5.5) / 4.5, Recommender.signalOf(entry(1, liked = true, seen = true, myRating = 10))!!.y, 1e-9)
        assertEquals(1.0, Recommender.signalOf(entry(1, seen = true, myRating = 10))!!.confidence, 1e-9)
        assertNull(Recommender.signalOf(entry(1, seen = true)))
    }

    @Test fun lowRatingOnLikedIsOneNegativeSignalNotTwo() {
        val s = Recommender.signalOf(entry(1, liked = true, seen = true, myRating = 3))!!
        assertEquals((3 - 5.5) / 4.5, s.y, 1e-9)
        assertTrue(s.y < 0)
    }

    @Test fun explicitSignalValues() {
        assertEquals(0.85, Recommender.signalOf(entry(1, liked = true, seen = true))!!.y, 1e-9)
        assertEquals(0.45, Recommender.signalOf(entry(1, watchlist = true))!!.y, 1e-9)
        assertEquals(-0.65, Recommender.signalOf(entry(1, disliked = true))!!.y, 1e-9)
        assertEquals(0.35, Recommender.signalOf(entry(1, disliked = true))!!.confidence, 1e-9)
    }

    // ---- Perfil y relevancia ----------------------------------------------------------------

    @Test fun emptyProfileIsNeutralAndFinite() {
        val p = Recommender.buildProfile(emptyList(), now)
        assertTrue(p.isEmpty)
        assertEquals(0.5, Recommender.relevance(entry(1, genres = "28"), p), 1e-9)
        assertTrue(p.topGenres(3).isEmpty())
        val score = Recommender.baseScore(entry(1, genres = "28", rating = 7.0), p, null)
        assertTrue(score.isFinite())
    }

    @Test fun likedGenreRaisesRelevanceAndDislikedGenreLowersIt() {
        val p = profileOf(
            entry(1, liked = true, seen = true, genres = "28", updatedAt = now),
            entry(2, disliked = true, genres = "27", updatedAt = now),
        )
        assertTrue(Recommender.relevance(entry(9, genres = "28"), p) > 0.5)
        assertTrue(Recommender.relevance(entry(9, genres = "27"), p) < 0.5)
        assertEquals(0.5, Recommender.relevance(entry(9, genres = "99"), p), 0.05)
    }

    @Test fun oneObservationDoesNotMakeAGenreAFullFavorite() {
        val p = profileOf(entry(1, liked = true, seen = true, genres = "16", updatedAt = now))
        val r = Recommender.relevance(entry(9, genres = "16"), p)
        assertTrue("relevancia $r", r > 0.5 && r < 0.75)
    }

    @Test fun manyRejectionsOfAGenreDoNotCancelAnEarlierLike() {
        // 6 "me gustó" de acción y 10 "no me interesa" de acción: el gusto sigue siendo positivo (el rechazo vale mucho menos).
        val likes = (1..6).map { entry(it, liked = true, seen = true, genres = "28", updatedAt = now) }
        val nos = (11..20).map { entry(it, disliked = true, genres = "28", updatedAt = now) }
        val p = Recommender.buildProfile(likes + nos, now)
        assertTrue(Recommender.relevance(entry(99, genres = "28"), p) > 0.5)
        assertTrue(28 in p.topGenres(3))
    }

    @Test fun recentTastesWeighMoreButOldOnesRemain() {
        val p = profileOf(
            entry(1, liked = true, seen = true, genres = "35", updatedAt = now - 3 * 365 * day),
            entry(2, liked = true, seen = true, genres = "27", updatedAt = now),
        )
        val old = Recommender.relevance(entry(9, genres = "35"), p)
        val recent = Recommender.relevance(entry(9, genres = "27"), p)
        assertTrue(recent > old)
        assertTrue(old > 0.5)
    }

    @Test fun topGenresOnlyIncludesPositiveOnes() {
        val p = profileOf(
            entry(1, liked = true, seen = true, genres = "28,12", updatedAt = now),
            entry(2, disliked = true, genres = "27", updatedAt = now),
        )
        assertEquals(setOf(28, 12), p.topGenres(5).toSet())
    }

    @Test fun decadeIsInterpolatedBetweenNeighbours() {
        // 1998 aporta 0.2 a la década del 90 y 0.8 a la del 2000: una película de 2001 se parece más a ella que una de 1975.
        val p = profileOf(entry(1, liked = true, seen = true, year = "1998", updatedAt = now))
        assertTrue(Recommender.relevance(entry(2, year = "2001"), p) > Recommender.relevance(entry(3, year = "1975"), p))
    }

    // ---- Calidad y puntaje ------------------------------------------------------------------

    @Test fun qualityIsRegularizedByVotes() {
        val fewVotes = Recommender.quality(entry(1, rating = 9.5), 4)
        val manyVotes = Recommender.quality(entry(2, rating = 8.1), 20_000)
        assertTrue("pocos votos $fewVotes", fewVotes < 0.67)
        assertTrue("muchos votos $manyVotes", manyVotes > 0.80)
        assertEquals(0.65, Recommender.quality(entry(3, rating = 9.0), 0), 1e-9)
        assertEquals(0.65, Recommender.quality(entry(4, rating = null), 500), 1e-9)
    }

    @Test fun upcomingMoviesGetTheNeutralQuality() {
        val future = entry(1, rating = 0.0).copy(releaseDate = "2999-01-01", year = "2999")
        assertEquals(0.65, Recommender.quality(future, 0), 1e-9)
    }

    @Test fun collabGrowsWithMoreSeedsAndEarlierPositions() {
        val one = Recommender.collab(listOf(1.0 to 0))
        val two = Recommender.collab(listOf(1.0 to 0, 1.0 to 0))
        val late = Recommender.collab(listOf(1.0 to 20))
        assertEquals(0.632, one, 1e-3)
        assertEquals(0.865, two, 1e-3)
        assertTrue(late < one)
        assertEquals(0.0, Recommender.collab(emptyList()), 1e-9)
    }

    @Test fun likedGenresBeatDislikedOnesAtEqualRating() {
        val p = profileOf(
            entry(1, liked = true, seen = true, genres = "28", updatedAt = now),
            entry(2, disliked = true, genres = "27", updatedAt = now),
        )
        val action = entry(10, genres = "28", rating = 8.0)
        val horror = entry(11, genres = "27", rating = 8.0)
        assertTrue(Recommender.baseScore(action, p, CandMeta(1000)) > Recommender.baseScore(horror, p, CandMeta(1000)))
    }

    @Test fun lookingLikeSomethingRejectedLowersTheScore() {
        val p = profileOf(
            entry(1, liked = true, seen = true, genres = "18", updatedAt = now),
            entry(2, disliked = true, genres = "27,53", updatedAt = now),
        )
        val twin = entry(10, genres = "27,53", rating = 7.0)
        val other = entry(11, genres = "99", rating = 7.0)
        // Misma calidad y sin relación con lo que me gusta: gana la que no se parece a lo rechazado.
        assertTrue(Recommender.baseScore(other, p, null) > Recommender.baseScore(twin, p, null))
    }

    // ---- Ordenar y elegir -------------------------------------------------------------------

    /** Reproduce el fallo real: ordenar con azar dentro del comparador lanzaba IllegalArgumentException. */
    @Test fun rankingManyCandidatesWithNoiseNeverThrowsAndKeepsEveryone() {
        val p = profileOf(entry(1, liked = true, seen = true, genres = "28", updatedAt = now))
        val candidates = (100..700).map { id ->
            entry(id, genres = listOf("28", "12", "27", "18").shuffled(Random(id)).take(2).joinToString(","), rating = (id % 100) / 10.0)
        }
        repeat(60) { seed ->
            val ranked = Recommender.rank(candidates, p, emptyMap(), noise = 0.3, random = Random(seed))
            assertEquals(601, ranked.size)
            assertEquals(candidates.map { it.movieId }.toSet(), ranked.map { it.movieId }.toSet())
        }
    }

    @Test fun rankingWithoutNoiseIsByScoreDescendingAndReproducible() {
        val p = profileOf(entry(1, liked = true, seen = true, genres = "28", updatedAt = now))
        val a = entry(10, genres = "28", rating = 9.0)
        val b = entry(11, genres = "28", rating = 6.0)
        val c = entry(12, genres = "99", rating = 3.0)
        val meta = mapOf(10 to CandMeta(5000), 11 to CandMeta(5000), 12 to CandMeta(5000))
        assertEquals(listOf(10, 11, 12), Recommender.rank(listOf(c, b, a), p, meta, noise = 0.0).map { it.movieId })
    }

    @Test fun selectIsReproducibleForTheSameSeedAndDiffersAcrossSeeds() {
        val p = profileOf(entry(1, liked = true, seen = true, genres = "28", updatedAt = now))
        val pool = (100..220).map { entry(it, genres = listOf("28", "12", "27", "18", "35").random(Random(it)), rating = 5.0 + it % 5) }
        val one = Recommender.select(pool, p, emptyMap(), 20, Random(7)).map { it.movieId }
        val two = Recommender.select(pool, p, emptyMap(), 20, Random(7)).map { it.movieId }
        assertEquals(one, two)
        assertEquals(20, one.size)
        assertEquals(20, one.toSet().size)
    }

    @Test fun selectSpreadsAClusterOfAlmostIdenticalMovies() {
        val p = profileOf(entry(1, liked = true, seen = true, genres = "28", updatedAt = now))
        val clones = (100..129).map { entry(it, genres = "28", rating = 8.0, year = "2020") }
        val varied = (200..229).map { entry(it, genres = listOf("12", "35", "18", "878", "10749").random(Random(it)), rating = 7.0) }
        val picked = Recommender.select(clones + varied, p, emptyMap(), 10, Random(1))
        assertTrue("acción: ${picked.count { it.genres == "28" }}", picked.count { it.genres == "28" } < 10)
    }

    @Test fun upcomingMoviesAreCappedAtAboutTenPercent() {
        val p = profileOf(entry(1, liked = true, seen = true, genres = "28", updatedAt = now))
        val upcoming = (100..139).map { entry(it, genres = "28", rating = 8.0).copy(releaseDate = "2999-01-01", year = "2999") }
        val released = (200..299).map { entry(it, genres = "28", rating = 7.0) }
        val picked = Recommender.select(upcoming + released, p, emptyMap(), 20, Random(3))
        assertEquals(20, picked.size)
        assertTrue(picked.count { it.isUpcoming } <= 2)
    }

    @Test fun selectNeverFailsOnEmptyPoolsOrZeroK() {
        val p = Recommender.buildProfile(emptyList(), now)
        assertTrue(Recommender.select(emptyList(), p, emptyMap(), 10).isEmpty())
        assertTrue(Recommender.select(listOf(entry(1)), p, emptyMap(), 0).isEmpty())
        assertEquals(1, Recommender.select(listOf(entry(1, genres = "28", rating = 7.0)), p, emptyMap(), 10).size)
    }

    // ---- Semillas ---------------------------------------------------------------------------

    @Test fun seedsComeFromDifferentGenresAndOnlyFromPositiveSignals() {
        val entries = listOf(
            entry(1, liked = true, seen = true, genres = "28", updatedAt = now),
            entry(2, liked = true, seen = true, genres = "28", updatedAt = now),
            entry(3, liked = true, seen = true, genres = "16", updatedAt = now),
            entry(4, watchlist = true, genres = "35", updatedAt = now),
            entry(5, disliked = true, genres = "27", updatedAt = now),
            entry(6, seen = true, genres = "18", updatedAt = now),
        )
        repeat(20) { seed ->
            val seeds = Recommender.pickSeeds(entries, 3, Random(seed), now)
            assertEquals(3, seeds.size)
            assertEquals(3, seeds.map { it.genreIds.first() }.toSet().size)
            assertTrue(seeds.none { it.movieId in setOf(5, 6) })
        }
        assertEquals(4, Recommender.pickSeeds(entries, 10, Random(1), now).size)
        assertTrue(Recommender.pickSeeds(emptyList(), 3, Random(1), now).isEmpty())
    }

    @Test fun entropyIsHalfWithFewPositivesAndLowForOneGenre() {
        assertEquals(0.5, profileOf(entry(1, liked = true, seen = true, genres = "28", updatedAt = now)).entropy, 1e-9)
        val narrow = Recommender.buildProfile((1..12).map { entry(it, liked = true, seen = true, genres = "27", updatedAt = now) }, now)
        val wide = Recommender.buildProfile(
            (1..12).map { entry(it, liked = true, seen = true, genres = listOf("28", "35", "18", "27", "16", "878")[it % 6], updatedAt = now) },
            now,
        )
        assertTrue(narrow.entropy < wide.entropy)
        assertNotNull(wide.entropy)
        assertFalse(wide.entropy.isNaN())
    }
}
