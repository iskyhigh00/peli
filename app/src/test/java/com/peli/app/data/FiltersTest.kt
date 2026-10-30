package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiltersTest {
    private fun facts(
        imdb: Double? = null, rt: Int? = null, meta: Int? = null,
        oscarWins: Int = 0, oscarNoms: Int = 0, wins: Int = 0, noms: Int = 0, omdbAt: Long = 1,
    ) = MovieFacts(1, "tt1", omdbAt = omdbAt, imdbRating = imdb, rottenTomatoes = rt, metacritic = meta,
        oscarWins = oscarWins, oscarNominations = oscarNoms, wins = wins, nominations = noms, tmdbAt = 1)

    @Test fun `premios de OMDb con Oscars ganados`() {
        val a = Awards.parse("Won 3 Oscars. 150 wins & 200 nominations total")
        assertEquals(Awards.Parsed(3, 0, 150, 200), a)
    }

    @Test fun `premios de OMDb solo nominada`() {
        val a = Awards.parse("Nominated for 2 Oscars. 20 wins & 40 nominations total")
        assertEquals(Awards.Parsed(0, 2, 20, 40), a)
    }

    @Test fun `premios sin Oscar y sin dato`() {
        assertEquals(Awards.Parsed(0, 0, 12, 20), Awards.parse("12 wins & 20 nominations"))
        assertEquals(Awards.Parsed(0, 0, 0, 0), Awards.parse("N/A"))
        assertEquals(Awards.Parsed(0, 0, 0, 0), Awards.parse(null))
        assertEquals(Awards.Parsed(0, 0, 0, 0), Awards.parse(""))
    }

    @Test fun `premios con una sola victoria y con comas`() {
        assertEquals(Awards.Parsed(1, 0, 1, 0), Awards.parse("Won 1 Oscar."))
        assertEquals(Awards.Parsed(0, 0, 2, 0), Awards.parse("Won 2 BAFTA Awards."))
        assertEquals(1234, Awards.parse("1,234 wins & 10 nominations").wins)
        assertEquals(Awards.Parsed(0, 0, 0, 1), Awards.parse("Nominated for 1 Primetime Emmy."))
    }

    @Test fun `sin filtros todo pasa y no se necesita OMDb`() {
        val f = MovieFilters.NONE
        assertFalse(f.isActive)
        assertFalse(f.needsOmdb)
        assertTrue(f.accepts(null, null))
    }

    @Test fun `la nota de TMDB se decide sin datos extra`() {
        val f = MovieFilters(minTmdb = 7.0)
        assertTrue(f.isActive)
        assertFalse(f.needsOmdb)
        assertTrue(f.accepts(7.5, null))
        assertFalse(f.accepts(6.9, null))
        assertFalse(f.accepts(null, null))
    }

    @Test fun `un filtro de OMDb sin datos rechaza`() {
        val f = MovieFilters(minImdb = 7.0)
        assertTrue(f.needsOmdb)
        assertFalse(f.accepts(8.0, null))
        assertFalse(f.accepts(8.0, facts(omdbAt = 0)))
        assertFalse(f.accepts(8.0, facts(imdb = null)))
        assertTrue(f.accepts(8.0, facts(imdb = 7.0)))
        assertFalse(f.accepts(8.0, facts(imdb = 6.9)))
    }

    @Test fun `Rotten Tomatoes y Metacritic`() {
        val f = MovieFilters(minRottenTomatoes = 80, minMetacritic = 70)
        assertTrue(f.accepts(null, facts(rt = 80, meta = 70)))
        assertFalse(f.accepts(null, facts(rt = 79, meta = 90)))
        assertFalse(f.accepts(null, facts(rt = 99, meta = null)))
    }

    @Test fun `filtro de Oscar nominada o ganadora`() {
        val nominated = MovieFilters(oscar = OscarFilter.Nominated)
        val won = MovieFilters(oscar = OscarFilter.Won)
        assertTrue(nominated.accepts(null, facts(oscarNoms = 1)))
        assertTrue(nominated.accepts(null, facts(oscarWins = 1)))
        assertFalse(nominated.accepts(null, facts()))
        assertTrue(won.accepts(null, facts(oscarWins = 2)))
        assertFalse(won.accepts(null, facts(oscarNoms = 3)))
    }

    @Test fun `premios y nominaciones totales`() {
        val f = MovieFilters(minWins = 10, minNominations = 20)
        assertTrue(f.accepts(null, facts(wins = 10, noms = 20)))
        assertFalse(f.accepts(null, facts(wins = 9, noms = 50)))
        assertFalse(f.accepts(null, facts(wins = 50, noms = 19)))
    }

    @Test fun `guardar y leer conserva los filtros`() {
        val f = MovieFilters(7.5, 7.0, 85, 60, OscarFilter.Won, 10, 25)
        assertEquals(f, MovieFilters.decode(f.encode()))
        assertEquals(7, f.count())
        assertEquals(MovieFilters.NONE, MovieFilters.decode(MovieFilters.NONE.encode()))
    }

    @Test fun `un texto guardado dañado vuelve a sin filtros sin fallar`() {
        assertEquals(MovieFilters.NONE, MovieFilters.decode(null))
        assertEquals(MovieFilters.NONE, MovieFilters.decode("basura"))
        assertEquals(MovieFilters.NONE, MovieFilters.decode("t=99;i=-3;r=500;o=Nada;w=x"))
        assertEquals(MovieFilters(minImdb = 7.0), MovieFilters.decode("i=7.0;r=abc"))
    }

    @Test fun `el resumen nombra solo lo activo`() {
        assertEquals("", MovieFilters.NONE.summary())
        val s = MovieFilters(minImdb = 7.5, oscar = OscarFilter.Won).summary()
        assertTrue(s.contains("IMDb") && s.contains("Oscar") && !s.contains("Metacritic"))
    }
}

class PrefilterTest {
    @Test fun `sin filtros de OMDb todo vale la pena`() {
        assertTrue(MovieFilters.NONE.worthChecking(1.0))
        assertTrue(MovieFilters(minTmdb = 7.0).worthChecking(1.0))
    }

    @Test fun `IMDb alto descarta notas de TMDB muy bajas pero deja margen`() {
        val f = MovieFilters(minImdb = 7.5)
        assertFalse(f.worthChecking(6.0))
        assertTrue(f.worthChecking(6.6))     // TMDB y IMDb pueden diferir en ~1 punto
        assertTrue(f.worthChecking(8.0))
    }

    @Test fun `filtros de premios descartan notas muy bajas`() {
        assertFalse(MovieFilters(oscar = OscarFilter.Won).worthChecking(4.9))
        assertTrue(MovieFilters(oscar = OscarFilter.Won).worthChecking(6.0))
        assertFalse(MovieFilters(minWins = 10).worthChecking(5.0))
        assertTrue(MovieFilters(minWins = 1).worthChecking(5.0))      // pedir pocos premios no prejuzga
    }

    @Test fun `la crítica exigente descarta lo que TMDB califica mal`() {
        assertFalse(MovieFilters(minRottenTomatoes = 90).worthChecking(5.5))
        assertTrue(MovieFilters(minRottenTomatoes = 60).worthChecking(5.5))
        assertFalse(MovieFilters(minMetacritic = 80).worthChecking(5.0))
    }

    @Test fun `sin nota de TMDB no se prejuzga`() {
        assertTrue(MovieFilters(minImdb = 8.0, oscar = OscarFilter.Won).worthChecking(null))
    }
}
