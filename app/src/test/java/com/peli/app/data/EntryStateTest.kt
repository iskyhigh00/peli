package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryStateTest {
    @Test fun wantingClearsEveryOtherState() {
        val s = entry(seen = true, liked = true, disliked = true).toggleWatch()
        assertTrue(s.watchlist)
        assertFalse(s.seen || s.liked || s.disliked)
    }

    @Test fun likingImpliesSeenAndClearsWatchAndDislike() {
        val s = entry(watchlist = true, disliked = true).toggleLiked()
        assertTrue(s.liked && s.seen)
        assertFalse(s.watchlist || s.disliked)
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

    @Test fun markingSeenRemovesItFromWatchlist() {
        val s = entry(watchlist = true).toggleSeen()
        assertTrue(s.seen)
        assertFalse(s.watchlist)
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

    @Test fun stateLabels() {
        assertEquals("Quiero verla", entry(watchlist = true).stateLabel())
        assertEquals("La vi · me gustó", entry(liked = true, seen = true).stateLabel())
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
