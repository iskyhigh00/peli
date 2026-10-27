package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedAvoidTest {
    @Test fun appendKeepsOrderAndMovesRepeatedToEnd() {
        assertEquals(listOf(1, 3, 2, 4), FeedAvoid.append(listOf(1, 2, 3), listOf(2, 4)).let { listOf(1, 3) + it.takeLast(2) })
        assertEquals(listOf(1, 3, 2, 4), FeedAvoid.append(listOf(1, 2, 3), listOf(2, 4)))
    }

    @Test fun appendForgetsOldestBeyondCapacity() {
        val out = FeedAvoid.append((1..10).toList(), listOf(11, 12), capacity = 5)
        assertEquals(listOf(8, 9, 10, 11, 12), out)
    }

    @Test fun levelsGoFromStrictToNothing() {
        val history = (1..100).toList()
        val levels = FeedAvoid.levels(history)
        assertEquals(history.toSet(), levels.first())
        assertTrue(levels.last().isEmpty())
        // cada nivel evita menos que el anterior y recuerda lo más reciente
        levels.zipWithNext().forEach { (a, b) -> assertTrue(b.size < a.size) }
        assertTrue(100 in levels[1] && 1 !in levels[1])
    }

    @Test fun emptyHistoryAvoidsNothing() {
        assertEquals(listOf(emptySet<Int>()), FeedAvoid.levels(emptyList()))
    }
}
