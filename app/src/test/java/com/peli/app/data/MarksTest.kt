package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarksTest {
    private val partnerWants = entry(7, owner = OWNER_PARTNER, watchlist = true, updatedAt = 5, genres = "28", rating = 7.1)

    @Test fun firstWatchTapOnAPartnerOnlyMovieSavesMyWatchlist() {
        val next = Marks.apply(current = null, base = partnerWants, now = 100) { it.toggleWatch() }
        assertTrue(next.watchlist)
        assertEquals(OWNER_ME, next.owner)
        assertEquals(100, next.updatedAt)
        assertEquals("28", next.genres) // los metadatos sí se conservan
    }

    @Test fun neverInheritsTheOthersRatingOrFlags() {
        val rated = partnerWants.copy(watchlist = false, seen = true, liked = true, myRating = 9)
        val next = Marks.apply(null, rated, 100) { it.toggleWatch() }
        assertEquals(0, next.myRating)
        assertFalse(next.liked)
        assertFalse(next.seen)
        assertTrue(next.watchlist)
    }

    @Test fun existingOwnMarkIsUsedInsteadOfTheBase() {
        val mine = entry(7, liked = true, seen = true, myRating = 8, updatedAt = 3)
        val next = Marks.apply(mine, partnerWants, 100) { it.withRating(10) }
        assertTrue(next.liked)
        assertEquals(10, next.myRating)
    }

    @Test fun asUnmarkedKeepsMetadataOnly() {
        val u = partnerWants.copy(myRating = 6, liked = true).asUnmarked(OWNER_ME)
        assertTrue(u.isEmpty)
        assertEquals(0, u.updatedAt)
        assertEquals(0, u.myRating)
        assertEquals(partnerWants.title, u.title)
    }

    @Test fun restoreReturnsThePreviousMarkWithItsOwnDate() {
        val previous = entry(7, liked = true, seen = true, myRating = 9, updatedAt = 42)
        val restored = Marks.restored(partnerWants, previous, now = 1000)
        assertEquals(previous, restored)
        assertEquals(42, restored.updatedAt)
    }

    @Test fun restoreWithoutPreviousClearsEverythingIncludingTheRating() {
        val dirty = entry(7, watchlist = true, myRating = 9)
        val restored = Marks.restored(dirty, null, now = 1000)
        assertTrue(restored.isEmpty)
        assertEquals(0, restored.myRating)
    }

    // ---- R08: invariantes nota / vista / pendiente ----

    @Test fun ratedThenWatchlistDropsTheRating() {
        val rated = entry(1, seen = true, myRating = 9)
        val pending = rated.toggleWatch()
        assertTrue(pending.watchlist)
        assertFalse(pending.seen)
        assertEquals(0, pending.myRating)
    }

    @Test fun removingWatchlistAfterThatLeavesAnEmptyMark() {
        val pending = entry(1, seen = true, myRating = 9).toggleWatch()
        assertTrue(pending.toggleWatch().isEmpty)
    }

    @Test fun normalizedRepairsIncoherentImportedStates() {
        // nota + pendiente: manda la nota (la vi)
        val a = entry(1, watchlist = true, myRating = 8).normalized()
        assertTrue(a.seen); assertFalse(a.watchlist); assertEquals(8, a.myRating)
        // me gustó + no me gusta: gana "me gustó"
        val b = entry(2, liked = true, disliked = true).normalized()
        assertTrue(b.liked && b.seen); assertFalse(b.disliked)
        // no me gusta + pendiente: sale de pendiente
        val c = entry(3, disliked = true, watchlist = true).normalized()
        assertTrue(c.disliked); assertFalse(c.watchlist)
        // nota fuera de rango se acota
        assertEquals(10, entry(4, seen = true, myRating = 99).normalized().myRating)
        // un estado ya coherente no cambia
        val ok = entry(5, liked = true, seen = true, myRating = 9)
        assertEquals(ok, ok.normalized())
    }
}
