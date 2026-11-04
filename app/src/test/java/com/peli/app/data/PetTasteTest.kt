package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetTasteTest {
    private val animation = listOf(16, 10751)
    private val thriller = listOf(53, 9648)
    private val horror = listOf(27)
    private val musical = listOf(10402, 10749)

    @Test fun `la misma pelicula siempre recibe la misma nota`() {
        val a = PetTaste.score(Pet.Chuba, 123, animation, 2001, 7.5)
        val b = PetTaste.score(Pet.Chuba, 123, animation, 2001, 7.5)
        assertEquals(a, b)
    }

    @Test fun `Chuba prefiere lo dulce y Pepa lo tenso`() {
        val chubaAnim = PetTaste.score(Pet.Chuba, 10, animation, 2000, 7.6).score
        val chubaThr = PetTaste.score(Pet.Chuba, 10, thriller, 2000, 7.6).score
        val pepaAnim = PetTaste.score(Pet.Pepa, 10, animation, 2000, 7.6).score
        val pepaThr = PetTaste.score(Pet.Pepa, 10, thriller, 2000, 7.6).score
        assertTrue("$chubaAnim > $chubaThr", chubaAnim > chubaThr)
        assertTrue("$pepaThr > $pepaAnim", pepaThr > pepaAnim)
    }

    @Test fun `el terror limita la nota de Chuba y los musicales no entusiasman a Pepa`() {
        assertTrue(PetTaste.score(Pet.Chuba, 5, horror, 2015, 8.5).score <= 3)
        assertFalse(PetTaste.score(Pet.Pepa, 5, musical, 2015, 7.0).likes)
    }

    @Test fun `tienen gustos distintos en un catalogo variado`() {
        val genresPool = listOf(animation, thriller, horror, musical, listOf(35), listOf(18), listOf(878), listOf(10749), listOf(80), listOf(10751))
        val different = (1..40).count { id ->
            val g = genresPool[id % genresPool.size]
            PetTaste.score(Pet.Chuba, id, g, 1990 + id % 30, 6.0 + (id % 30) / 10.0).score !=
                PetTaste.score(Pet.Pepa, id, g, 1990 + id % 30, 6.0 + (id % 30) / 10.0).score
        }
        assertTrue("$different de 40", different >= 28)
    }

    @Test fun `las notas van de 1 a 10 y siempre hay un motivo`() {
        for (pet in Pet.entries) for (id in 1..300) {
            val g = listOf(16, 27, 53, 10749, 35, 18, 878).shuffled(java.util.Random(id.toLong())).take(1 + id % 3)
            val v = PetTaste.score(pet, id, g, 1950 + id % 75, (id % 100) / 10.0)
            assertTrue(v.score in 1..10)
            assertTrue(v.reason.isNotBlank())
        }
    }

    @Test fun `sin generos ni datos igual opinan`() {
        val v = PetTaste.score(Pet.Pepa, 99, emptyList(), null, null)
        assertTrue(v.score in 1..10)
    }
}
