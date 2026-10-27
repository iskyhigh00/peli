package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorsTest {
    @Test fun cursorAdvancesAndWrapsAtTheLastPage() {
        assertEquals(2, Cursors.next(1, 5))
        assertEquals(5, Cursors.next(4, 5))
        assertEquals(1, Cursors.next(5, 5))
        assertEquals(1, Cursors.next(7, 5))
        assertEquals(1, Cursors.next(1, 1))
        assertEquals(1, Cursors.next(500, null))
        assertEquals(2, Cursors.next(1, null))
    }

    @Test fun memoryCursorsStartAtOneAndRemember() {
        val c = MemoryCursors()
        assertEquals(1, c.get("popular"))
        c.set("popular", 4)
        assertEquals(4, c.get("popular"))
        assertEquals(1, c.get("otra"))
    }

    @Test fun stashKeepsLeftoversDedupedAndBounded() {
        val stash = CandidateStash(capacity = 3)
        (1..5).forEach { stash.add(entry(it), CandMeta(votes = it)) }
        assertEquals(3, stash.size)
        assertEquals(listOf(3, 4, 5), stash.usable(emptySet()).map { it.first.movieId })
    }

    @Test fun stashMergesTheBestSignalAndFiltersKnownAndUnusable() {
        val stash = CandidateStash()
        stash.add(entry(1), CandMeta(votes = null, collab = 0.2))
        stash.add(entry(1), CandMeta(votes = 500, collab = 0.1))
        val merged = stash.usable(emptySet()).single().second
        assertEquals(500, merged.votes)
        assertEquals(0.2, merged.collab, 1e-9)
        stash.add(entry(2), CandMeta())
        stash.add(entry(3), CandMeta())
        assertEquals(listOf(1, 3), stash.usable(known = setOf(2)).map { it.first.movieId })
        assertEquals(listOf(1, 2), stash.usable(known = emptySet()) { it != 3 }.map { it.first.movieId })
        stash.remove(listOf(1, 2))
        assertEquals(1, stash.size)
        assertTrue(stash.usable(emptySet()).single().first.movieId == 3)
    }
}
