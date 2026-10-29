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
}
