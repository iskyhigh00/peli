package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    // Sin fecha de marca, cada peso se multiplica por 1.25, así que la normalización queda exacta.
    private val profile = Recommender.buildProfile(
        listOf(
            entry(1, liked = true, seen = true, genres = "28,12"),
            entry(2, disliked = true, genres = "27"),
        ),
        now = 1_000L,
    )

    @Test fun likedGenresAreStrongestAndDislikedAreNegative() {
        assertEquals(1.0, profile.genreWeights[28]!!, 1e-9)
        assertEquals(1.0, profile.genreWeights[12]!!, 1e-9)
        assertEquals(-0.8, profile.genreWeights[27]!!, 1e-9)
    }

    @Test fun emptyHistoryGivesEmptyProfile() {
        assertTrue(Recommender.buildProfile(emptyList()).genreWeights.isEmpty())
    }

    @Test fun seenWithoutOpinionCountsLittle() {
        val p = Recommender.buildProfile(
            listOf(entry(1, liked = true, seen = true, genres = "28"), entry(2, seen = true, genres = "18")),
            now = 1_000L,
        )
        assertEquals(0.15, p.genreWeights[18]!!, 1e-9)
    }

    @Test fun recentTastesWeighMoreThanOldOnes() {
        val now = 2_000_000_000_000L
        val year = 365L * 86_400_000L
        val p = Recommender.buildProfile(
            listOf(
                entry(1, liked = true, seen = true, genres = "1", updatedAt = now),
                entry(2, liked = true, seen = true, genres = "2", updatedAt = now - year),
            ),
            now = now,
        )
        assertEquals(1.0, p.genreWeights[1]!!, 1e-9)
        assertTrue(p.genreWeights[2]!! < p.genreWeights[1]!!)
        assertTrue(p.genreWeights[2]!! > 0.5)
    }

    @Test fun affinityAveragesKnownGenresAndIgnoresUnknown() {
        assertEquals(0.1, Recommender.affinity(entry(9, genres = "28,27"), profile), 1e-9)
        assertEquals(0.0, Recommender.affinity(entry(9, genres = "99"), profile), 1e-9)
        assertEquals(0.0, Recommender.affinity(entry(9, genres = null), profile), 1e-9)
    }

    @Test fun qualityMapsRatingFiveToZeroAndTenToOne() {
        assertEquals(0.5, Recommender.quality(entry(1, rating = 7.5)), 1e-9)
        assertEquals(1.0, Recommender.quality(entry(1, rating = 10.0)), 1e-9)
        assertEquals(1.0, Recommender.quality(entry(1, rating = 12.0)), 1e-9)
        assertEquals(0.0, Recommender.quality(entry(1, rating = 4.0)), 1e-9)
        assertEquals(0.0, Recommender.quality(entry(1, rating = null)), 1e-9)
    }

    @Test fun scoreCombinesAffinityQualityAndOverlap() {
        val action = entry(10, genres = "28", rating = 8.0)
        assertEquals(0.5 * 1.0 + 0.3 * 0.6, Recommender.score(action, profile, 0, 0), 1e-9)
        assertEquals(0.68 + 0.2, Recommender.score(action, profile, 2, 2), 1e-9)
    }

    @Test fun likedGenresBeatDislikedOnesAtEqualRating() {
        val action = entry(10, genres = "28", rating = 8.0)
        val horror = entry(11, genres = "27", rating = 8.0)
        assertTrue(Recommender.score(action, profile, 0, 0) > Recommender.score(horror, profile, 0, 0))
    }

    /** Reproduce el fallo real: ordenar con azar dentro del comparador lanzaba IllegalArgumentException. */
    @Test fun rankingManyCandidatesWithNoiseNeverThrowsAndKeepsEveryone() {
        val candidates = (1..600).map { id ->
            entry(id, genres = listOf("28", "12", "27", "18").shuffled(kotlin.random.Random(id)).take(2).joinToString(","), rating = (id % 100) / 10.0)
        }
        repeat(60) { seed ->
            val ranked = Recommender.rank(candidates, profile, emptyMap(), 0, noise = 0.3, random = kotlin.random.Random(seed))
            assertEquals(600, ranked.size)
            assertEquals(candidates.map { it.movieId }.toSet(), ranked.map { it.movieId }.toSet())
        }
    }

    @Test fun rankingWithoutNoiseIsByScoreDescending() {
        val a = entry(1, genres = "28", rating = 9.0)
        val b = entry(2, genres = "28", rating = 6.0)
        val c = entry(3, genres = "27", rating = 9.0)
        val ranked = Recommender.rank(listOf(c, b, a), profile, emptyMap(), 0, noise = 0.0)
        assertEquals(listOf(1, 2, 3), ranked.map { it.movieId })
    }

    @Test fun rankingIsReproducibleForTheSameSeed() {
        val candidates = (1..50).map { entry(it, genres = "28", rating = 5.0 + (it % 5)) }
        val one = Recommender.rank(candidates, profile, emptyMap(), 0, 0.5, kotlin.random.Random(7)).map { it.movieId }
        val two = Recommender.rank(candidates, profile, emptyMap(), 0, 0.5, kotlin.random.Random(7)).map { it.movieId }
        assertEquals(one, two)
    }

    @Test fun topGenresOnlyIncludesPositiveOnes() {
        assertEquals(setOf(28, 12), profile.topGenres(2).toSet())
        assertEquals(2, profile.topGenres(5).size)
    }
}
