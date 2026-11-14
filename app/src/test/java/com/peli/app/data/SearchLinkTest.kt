package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchLinkTest {
    @Test fun `arma la direccion con el titulo codificado`() {
        assertEquals(
            "https://example.org/browse/harry%20potter/all",
            SearchLink.build("https://example.org/browse/{titulo}/all", "harry potter"),
        )
    }

    @Test fun `codifica tildes y signos`() {
        assertEquals("https://x.org/s?q=Am%C3%A9lie%20%26%20co", SearchLink.build("https://x.org/s?q={titulo}", "Amélie & co"))
    }

    @Test fun `sin marcador usa la direccion tal cual`() {
        assertEquals("https://x.org/", SearchLink.build("https://x.org/", "algo"))
    }

    @Test fun `plantilla vacia o invalida no arma boton`() {
        assertNull(SearchLink.build("", "algo"))
        assertNull(SearchLink.build("ftp://x.org/{titulo}", "algo"))
        assertNull(SearchLink.build("javascript:alert({titulo})", "algo"))
        assertNull(SearchLink.build("https://x.org/{titulo}", "   "))
    }

    @Test fun `lista de enlaces sin repetidos`() {
        val l = SearchLink.list("https://x.org/{titulo}/ok", listOf("Up", "Heat", "Up", " "))
        assertEquals(listOf("https://x.org/Up/ok", "https://x.org/Heat/ok"), l)
    }

    @Test fun `las tres listas no se repiten y respetan las disponibles`() {
        fun e(id: Int) = Entry(movieId = id, owner = "x", title = "T$id", overview = "", posterPath = null, year = "2000")
        val ms = listOf(Match(e(1), iWant = true, partnerWants = true), Match(e(2), true, false), Match(e(3), false, true), Match(e(4), false, true))
        fun ids(k: ExportList, hidden: Set<Int> = emptySet()) = exportMatches(k, ms, hidden).map { it.entry.movieId }
        assertEquals(listOf(1), ids(ExportList.Both))
        assertEquals(listOf(2), ids(ExportList.Mine))
        assertEquals(listOf(3, 4), ids(ExportList.Partner))
        assertEquals(listOf(4), ids(ExportList.Partner, setOf(3)))
    }

    @Test fun `nombre corto del sitio`() {
        assertEquals("justwatch.com", SearchLink.host(SearchLink.DEFAULT))
        assertNull(SearchLink.host("no es una direccion"))
    }
}

class AwardDetailsTest {
    @Test fun `la consulta solo acepta ids de IMDb`() {
        assertNull(AwardDetails.query("tt0137523\" } ; DROP"))
        assertNull(AwardDetails.query("0137523"))
        assert(AwardDetails.query("tt0137523")!!.contains("\"tt0137523\""))
    }

    @Test fun `limpia repetidos, etiquetas sin traducir y el nombre de la pelicula`() {
        val rows = listOf(
            AwardRow("Premio Óscar a la mejor película", false, "Fight Club", 2000),
            AwardRow("Premio Óscar a la mejor película", false, "Fight Club", 2000),
            AwardRow("Q12345", true, null, null),
            AwardRow("Premio BAFTA al mejor actor", true, "Edward Norton", 2000),
            AwardRow("Premio BAFTA al mejor actor", true, "Q99", 2000),
        )
        val c = AwardDetails.clean(rows, setOf("fight club"))
        assertEquals(3, c.size)
        assertNull(c[0].who)
        assertEquals("Edward Norton", c[1].who)
    }

    @Test fun `ordena ganados primero y lo mas reciente arriba`() {
        val s = AwardDetails.sorted(
            listOf(
                AwardRow("A", false, null, 2005), AwardRow("B", true, null, 1999), AwardRow("C", true, null, 2001),
            ),
        )
        assertEquals(listOf("C", "B", "A"), s.map { it.award })
    }

    @Test fun `linea con persona y anio`() {
        assertEquals("Mejor actor — Ana (2001)", AwardDetails.line(AwardRow("Mejor actor", true, "Ana", 2001)))
        assertEquals("Mejor película", AwardDetails.line(AwardRow("Mejor película", false, null, null)))
    }
}
