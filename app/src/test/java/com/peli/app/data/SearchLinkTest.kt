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

    @Test fun `nombre corto del sitio`() {
        assertEquals("justwatch.com", SearchLink.host(SearchLink.DEFAULT))
        assertNull(SearchLink.host("no es una direccion"))
    }
}
