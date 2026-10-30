package com.peli.app.data

import com.peli.app.entry
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleTest {
    @Test fun `original distinto muestra original con la traduccion entre parentesis`() {
        assertEquals("Spirited Away (El viaje de Chihiro)", TitleFormat.display("El viaje de Chihiro", "Spirited Away"))
        assertEquals("千と千尋の神隠し (El viaje de Chihiro)", TitleFormat.display("El viaje de Chihiro", "千と千尋の神隠し"))
    }

    @Test fun `si son iguales no se repite`() {
        assertEquals("Roma", TitleFormat.display("Roma", "Roma"))
        assertEquals("Amélie", TitleFormat.display("Amélie", "Amelie"))
        assertEquals("Alien: el octavo pasajero", TitleFormat.display("Alien: el octavo pasajero", "Alien: El Octavo Pasajero"))
    }

    @Test fun `sin original conocido se deja el titulo`() {
        assertEquals("Titanic", TitleFormat.display("Titanic", null))
        assertEquals("Titanic", TitleFormat.display("Titanic", ""))
        assertEquals("Titanic", TitleFormat.display("Titanic", "   "))
    }

    @Test fun `sin titulo usa el original`() {
        assertEquals("Original", TitleFormat.display("", "Original"))
    }

    @Test fun `la entrada usa el formato`() {
        val e = entry(1, title = "Los descendientes").copy(originalTitle = "Descendants")
        assertEquals("Descendants (Los descendientes)", e.displayTitle)
        assertEquals("Los descendientes", entry(2, title = "Los descendientes").displayTitle)
    }

    @Test fun `si el titulo traducido ya trae el original entre parentesis no se duplica`() {
        assertEquals(
            "The Art of Travel (El arte de viajar)",
            TitleFormat.display("El arte de viajar (The Art of Travel)", "The Art of Travel"),
        )
        assertEquals("Spaceman (El astronauta)", TitleFormat.display("El astronauta (Spaceman)", "Spaceman"))
    }

    @Test fun `un parentesis distinto del original se conserva`() {
        assertEquals("Dune (Dune (parte uno))", TitleFormat.display("Dune (parte uno)", "Dune"))
        assertEquals("Alien (El octavo pasajero)", TitleFormat.display("El octavo pasajero", "Alien"))
    }

    @Test fun `si tras quitar el original queda igual se muestra uno solo`() {
        assertEquals("Roma", TitleFormat.display("Roma (Roma)", "Roma"))
    }
}
