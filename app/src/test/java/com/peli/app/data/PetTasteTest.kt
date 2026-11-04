package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetTasteTest {
    private val action = listOf(28, 12)
    private val drama = listOf(18, 10749)
    private val horror = listOf(27)
    private val war = listOf(10752, 28)

    @Test fun `la misma pelicula siempre recibe la misma nota`() {
        val a = PetTaste.score(Pet.Chuba, 123, action, 2005, 7.5)
        val b = PetTaste.score(Pet.Chuba, 123, action, 2005, 7.5)
        assertEquals(a, b)
    }

    @Test fun `Chuba prefiere la accion y Pepa lo calmado`() {
        val chubaAction = PetTaste.score(Pet.Chuba, 10, action, 2006, 7.2).score
        val chubaDrama = PetTaste.score(Pet.Chuba, 10, drama, 2006, 7.2).score
        val pepaAction = PetTaste.score(Pet.Pepa, 10, action, 2006, 7.2).score
        val pepaDrama = PetTaste.score(Pet.Pepa, 10, drama, 2006, 7.2).score
        assertTrue("$chubaAction > $chubaDrama", chubaAction > chubaDrama)
        assertTrue("$pepaDrama > $pepaAction", pepaDrama > pepaAction)
    }

    @Test fun `el terror y la guerra espantan a Pepa`() {
        assertTrue(PetTaste.score(Pet.Pepa, 5, horror, 2015, 8.5).score <= 3)
        assertFalse(PetTaste.score(Pet.Pepa, 5, war, 2015, 7.5).likes)
        assertTrue(PetTaste.score(Pet.Chuba, 5, action, 2008, 7.5).likes || PetTaste.score(Pet.Chuba, 6, action, 2008, 7.5).likes)
    }

    @Test fun `tienen gustos distintos en un catalogo variado`() {
        val genresPool = listOf(action, drama, horror, war, listOf(35), listOf(18), listOf(878), listOf(10749), listOf(80), listOf(10751))
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
