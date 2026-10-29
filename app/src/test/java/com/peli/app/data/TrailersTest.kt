package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailersTest {
    private fun v(key: String?, site: String? = "YouTube", type: String? = "Trailer", official: Boolean? = true, lang: String? = "en") =
        TrailerVideo(key, site, type, official, lang)

    @Test fun `prefiere tráiler en español antes que en inglés`() {
        assertEquals("es1", Trailers.pick(listOf(v("en1"), v("es1", lang = "es"))))
    }

    @Test fun `prefiere Trailer antes que Teaser aunque el teaser esté en español`() {
        assertEquals("tr", Trailers.pick(listOf(v("te", type = "Teaser", lang = "es"), v("tr", lang = "en"))))
    }

    @Test fun `a igualdad prefiere el oficial`() {
        assertEquals("of", Trailers.pick(listOf(v("fan", official = false), v("of", official = true))))
    }

    @Test fun `ignora lo que no es de YouTube ni tráiler`() {
        assertNull(Trailers.pick(listOf(v("a", site = "Vimeo"), v("b", type = "Featurette"), v("c", type = "Clip"))))
        assertNull(Trailers.pick(listOf(v(null), v(" "))))
        assertNull(Trailers.pick(emptyList()))
    }

    @Test fun `sin idioma va entre inglés y otros`() {
        assertEquals("nul", Trailers.pick(listOf(v("fr", lang = "fr"), v("nul", lang = null))))
    }

    @Test fun `direcciones del video`() {
        assertEquals("https://www.youtube.com/watch?v=abc", Trailers.watchUrl("abc"))
        assertTrue(Trailers.embedHtml("abc").contains("https://www.youtube.com/embed/abc"))
    }

    @Test fun `datos viejos sin id de director piden volver a consultar TMDB`() {
        assertTrue(MovieFacts(1).needsTmdb)                                              // nunca consultado
        assertTrue(MovieFacts(1, tmdbAt = 1, director = "Nolan").needsTmdb)              // sin id del director
        assertFalse(MovieFacts(1, tmdbAt = 1, director = "Nolan", directorId = 525).needsTmdb)
        assertFalse(MovieFacts(1, tmdbAt = 1, director = null).needsTmdb)                // sin director: nada que pedir
    }
}
