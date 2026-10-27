package com.peli.app.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun appendsLinesInOrder() {
        val store = LogStore(tmp.newFolder("logs"))
        store.append("uno")
        store.append("dos")
        assertEquals("uno\ndos\n", store.readAll())
    }

    @Test fun emptyStoreReadsAsEmpty() {
        val store = LogStore(tmp.newFolder("logs"))
        assertEquals("", store.readAll())
        assertEquals(0L, store.sizeBytes())
    }

    @Test fun rotationKeepsTheNewestAndBoundsTheSize() {
        val store = LogStore(tmp.newFolder("logs"), maxBytes = 100)
        repeat(200) { store.append("línea número $it con relleno para ocupar espacio") }
        val text = store.readAll()
        // Lo más reciente siempre está; lo más antiguo se descartó.
        assertTrue(text.contains("línea número 199 "))
        assertFalse(text.contains("línea número 0 "))
        // Nunca pasa de ~2 archivos de tamaño máximo (+ una línea).
        assertTrue(store.sizeBytes() < 2 * (100 + 60))
    }

    @Test fun rotationKeepsChronologicalOrder() {
        val store = LogStore(tmp.newFolder("logs"), maxBytes = 60)
        repeat(30) { store.append("evento %03d relleno".format(it)) }
        val numbers = store.readAll().lines().filter { it.isNotBlank() }.map { it.substring(7, 10).toInt() }
        assertEquals(numbers.sorted(), numbers)
        assertEquals(29, numbers.last())
    }

    @Test fun clearRemovesEverything() {
        val store = LogStore(tmp.newFolder("logs"), maxBytes = 50)
        repeat(20) { store.append("algo de texto $it") }
        store.clear()
        assertEquals("", store.readAll())
        assertEquals(0L, store.sizeBytes())
        store.append("nuevo")
        assertEquals("nuevo\n", store.readAll())
    }
}
