package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailersTest {
    private fun v(
        key: String?, site: String? = "YouTube", type: String? = "Trailer", official: Boolean? = true,
        lang: String? = "en", country: String? = null, name: String? = null,
    ) = TrailerVideo(key, site, type, official, lang, country, name)

    @Test fun `español latino va primero`() {
        val videos = listOf(
            v("es-es", lang = "es", country = "ES"),
            v("en-sub", lang = "en", name = "Trailer subtitulado"),
            v("en", lang = "en"),
            v("mx", lang = "es", country = "MX"),
        )
        assertEquals("mx", Trailers.pick(videos))
    }

    @Test fun `el nombre tambien delata el español latino`() {
        assertEquals("lat", Trailers.pick(listOf(v("en"), v("lat", lang = "es", country = "ES", name = "Tráiler oficial (Español Latino)"))))
    }

    @Test fun `sin latino va ingles con subtitulos, luego sin subtitulos`() {
        assertEquals("sub", Trailers.pick(listOf(v("en"), v("sub", name = "Official Trailer (English with Spanish subtitles)"), v("es-es", lang = "es", country = "ES"))))
        assertEquals("en", Trailers.pick(listOf(v("es-es", lang = "es", country = "ES"), v("en"))))
    }

    @Test fun `español de España solo despues del ingles`() {
        assertEquals("en", Trailers.pick(listOf(v("es-es", lang = "es", country = "ES"), v("en"))))
        assertEquals("es-es", Trailers.pick(listOf(v("es-es", lang = "es", country = "ES"), v("fr", lang = "fr"))))
    }

    @Test fun `dentro del mismo grupo prefiere Trailer sobre Teaser y los oficiales`() {
        assertEquals("tr", Trailers.pick(listOf(v("te", type = "Teaser", lang = "es", country = "MX"), v("tr", lang = "es", country = "AR"))))
        assertEquals("of", Trailers.pick(listOf(v("fan", official = false, lang = "es", country = "MX"), v("of", official = true, lang = "es", country = "MX"))))
    }

    @Test fun `un teaser latino gana a un trailer en ingles`() {
        assertEquals("te", Trailers.pick(listOf(v("tr", lang = "en"), v("te", type = "Teaser", lang = "es", country = "MX"))))
    }

    @Test fun `ignora lo que no es de YouTube ni tráiler`() {
        assertNull(Trailers.pick(listOf(v("a", site = "Vimeo"), v("b", type = "Featurette"), v("c", type = "Clip"))))
        assertNull(Trailers.pick(listOf(v(null), v(" "))))
        assertNull(Trailers.pick(emptyList()))
    }

    @Test fun `otros idiomas van al final`() {
        assertEquals("es-es", Trailers.pick(listOf(v("fr", lang = "fr"), v("nul", lang = null), v("es-es", lang = "es", country = "ES"))))
        assertEquals("fr", Trailers.pick(listOf(v("fr", lang = "fr"))))
    }

    @Test fun `direcciones del video`() {
        assertEquals("https://www.youtube.com/watch?v=abc", Trailers.watchUrl("abc"))
    }

    @Test fun `datos viejos sin id de director piden volver a consultar TMDB`() {
        assertTrue(MovieFacts(1).needsTmdb)
        assertTrue(MovieFacts(1, tmdbAt = 1, director = "Nolan").needsTmdb)
        assertFalse(MovieFacts(1, tmdbAt = 1, director = "Nolan", directorId = 525).needsTmdb)
        assertFalse(MovieFacts(1, tmdbAt = 1, director = null).needsTmdb)
    }
}
