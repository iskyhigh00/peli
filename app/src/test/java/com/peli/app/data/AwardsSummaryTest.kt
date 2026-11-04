package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AwardsSummaryTest {
    private fun facts(oscarWins: Int = 0, oscarNoms: Int = 0, wins: Int = 0, noms: Int = 0, omdb: Boolean = true) =
        MovieFacts(1, oscarWins = oscarWins, oscarNominations = oscarNoms, wins = wins, nominations = noms, omdbAt = if (omdb) 1 else 0)

    @Test fun `sin datos de OMDb o sin premios no hay resumen`() {
        assertEquals(emptyList<String>(), Awards.summary(null))
        assertEquals(emptyList<String>(), Awards.summary(facts(omdb = false, wins = 5)))
        assertEquals(emptyList<String>(), Awards.summary(facts()))
    }

    @Test fun `ganadora de Oscar con totales`() {
        val p = Awards.parse("Won 4 Oscars. 142 wins & 332 nominations total")
        val f = facts(p.oscarWins, p.oscarNominations, p.wins, p.nominations)
        assertEquals(listOf("Ganó 4 Óscares", "142 premios · 332 nominaciones"), Awards.summary(f))
    }

    @Test fun `solo nominada y singulares`() {
        assertEquals(listOf("Nominada a 1 Óscar", "1 premio · 1 nominación"), Awards.summary(facts(oscarNoms = 1, wins = 1, noms = 1)))
        assertEquals(listOf("Ganó 1 Óscar"), Awards.summary(facts(oscarWins = 1)))
    }

    @Test fun `solo otros premios`() {
        assertEquals(listOf("3 premios"), Awards.summary(facts(wins = 3)))
        assertEquals(listOf("12 nominaciones"), Awards.summary(facts(noms = 12)))
    }
}
