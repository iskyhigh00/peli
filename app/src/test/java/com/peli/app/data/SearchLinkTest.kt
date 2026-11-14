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
