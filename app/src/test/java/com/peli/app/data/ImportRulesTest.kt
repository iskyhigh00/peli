package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportRulesTest {
    @Test fun validListIsKeptAsThePartnersAndRepaired() {
        val out = ImportRules.sanitize(listOf(entry(1, watchlist = true, myRating = 8), entry(2, liked = true, seen = true)))!!
        assertEquals(2, out.size)
        assertTrue(out.all { it.owner == OWNER_PARTNER })
        assertTrue(out[0].seen && !out[0].watchlist)      // nota + pendiente: manda la nota
    }

    @Test fun duplicatesAndEmptyMarksAreDropped() {
        val out = ImportRules.sanitize(listOf(entry(1, watchlist = true), entry(1, liked = true, seen = true), entry(2)))!!
        assertEquals(listOf(1), out.map { it.movieId })
        assertTrue(out.single().watchlist)   // se queda la primera
    }

    @Test fun invalidIdsOrTooManyEntriesRejectTheWholeImport() {
        assertNull(ImportRules.sanitize(listOf(entry(1, watchlist = true), entry(0, watchlist = true))))
        assertNull(ImportRules.sanitize(listOf(entry(-5, watchlist = true))))
        assertNull(ImportRules.sanitize((1..ImportRules.MAX_ENTRIES + 1).map { entry(it, watchlist = true) }))
        assertNull(ImportRules.sanitize(null))
    }

    @Test fun ratingsAreClampedAndLongTextsTrimmed() {
        val long = "x".repeat(1000)
        val out = ImportRules.sanitize(listOf(entry(1, seen = true, myRating = 99).copy(title = long, year = "20201")))!!.single()
        assertEquals(10, out.myRating)
        assertEquals(300, out.title.length)
        assertEquals("2020", out.year)
    }

    @Test fun absurdlyLongTextRejectsTheImport() {
        assertNull(ImportRules.sanitize(listOf(entry(1, watchlist = true).copy(title = "x".repeat(5000)))))
    }

    @Test fun negativeUpdateDatesBecomeZero() {
        val out = ImportRules.sanitize(listOf(entry(1, watchlist = true, updatedAt = -5)))!!.single()
        assertEquals(0, out.updatedAt)
        assertFalse(out.isEmpty)
    }
}
