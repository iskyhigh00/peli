package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryStateTest {
    @Test fun wantingKeepsSeenAndLikedButClearsDislike() {
        val s = entry(seen = true, liked = true, disliked = true, myRating = 8).toggleWatch()
        assertTrue(s.watchlist)
        assertTrue(s.seen && s.liked)
        assertEquals(8, s.myRating)
        assertFalse(s.disliked)
    }

    @Test fun likingImpliesSeenKeepsWatchAndClearsDislike() {
        val s = entry(watchlist = true, disliked = true).toggleLiked()
        assertTrue(s.liked && s.seen && s.watchlist)
        assertFalse(s.disliked)
    }

    @Test fun unlikingKeepsItSeen() {
        val s = entry().toggleLiked().toggleLiked()
        assertFalse(s.liked)
        assertTrue(s.seen)
    }

    @Test fun unmarkingSeenAlsoClearsLiked() {
        val s = entry(liked = true, seen = true).toggleSeen()
        assertFalse(s.seen || s.liked)
    }

    @Test fun markingSeenKeepsItInTheWatchlist() {
        val s = entry(watchlist = true).toggleSeen()
        assertTrue(s.seen)
        assertTrue(s.watchlist)
    }

    @Test fun dislikingClearsLikedAndWatchlistButNotSeen() {
        val s = entry(liked = true, seen = true, watchlist = true).toggleDisliked()
        assertTrue(s.disliked)
        assertFalse(s.liked || s.watchlist)
        assertTrue(s.seen)
    }

    @Test fun togglingTwiceReturnsToEmpty() {
        assertTrue(entry().isEmpty)
        assertTrue(entry().toggleWatch().toggleWatch().isEmpty)
        assertTrue(entry().toggleDisliked().toggleDisliked().isEmpty)
        assertTrue(entry().toggleSeen().toggleSeen().isEmpty)
    }

    @Test fun ratingImpliesSeenAndKeepsTheWatchlist() {
        val s = entry(watchlist = true).withRating(8)
        assertEquals(8, s.myRating)
        assertTrue(s.seen)
        assertTrue(s.watchlist)
    }

    @Test fun ratingIsClampedToZeroToTen() {
        assertEquals(10, entry().withRating(15).myRating)
        assertEquals(0, entry().withRating(-3).myRating)
    }

    @Test fun removingTheRatingKeepsTheOtherStates() {
        val s = entry(liked = true, seen = true, myRating = 7).withRating(0)
        assertEquals(0, s.myRating)
        assertTrue(s.liked && s.seen)
    }

    @Test fun unmarkingSeenAlsoClearsTheRating() {
        assertEquals(0, entry(seen = true, myRating = 9).toggleSeen().myRating)
    }

    @Test fun stateLabels() {
        assertEquals("Quiero verla", entry(watchlist = true).stateLabel())
        assertEquals("La vi · me gustó", entry(liked = true, seen = true).stateLabel())
        assertEquals("La vi · quiero verla otra vez", entry(watchlist = true, seen = true, liked = true).stateLabel())
        assertEquals("La vi", entry(seen = true).stateLabel())
        assertEquals("No me gusta", entry(disliked = true).stateLabel())
        assertEquals("La vi · no me gustó", entry(disliked = true, seen = true).stateLabel())
        assertNull(entry().stateLabel())
    }

    @Test fun genreIdsParseAndIgnoreNoise() {
        assertEquals(listOf(28, 12), entry(genres = "28, 12").genreIds)
        assertEquals(listOf(28), entry(genres = "28,x").genreIds)
        assertTrue(entry(genres = null).genreIds.isEmpty())
    }
}
