package com.peli.app.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFormatTest {
    @Test fun redactsApiKeysTokensAndBearer() {
        val url = "https://api.themoviedb.org/3/movie/1?api_key=SECRETO123&language=es-ES"
        assertEquals("https://api.themoviedb.org/3/movie/1?api_key=***&language=es-ES", LogFormat.redact(url))
        assertEquals("apikey=***", LogFormat.redact("apikey=abc123"))
        assertEquals("access_token=***&x=1", LogFormat.redact("access_token=eyJabc.def&x=1"))
        assertEquals("Authorization: Bearer ***", LogFormat.redact("Authorization: Bearer eyJabc.def-ghi_1"))
    }

    @Test fun redactsTheRoomCodeInSupabaseQueries() {
        val url = "https://x.supabase.co/rest/v1/entries?room=eq.ABCDEFGHJK&user_id=neq.u1"
        val clean = LogFormat.sanitizeUrl(url)
        assertFalse(clean.contains("ABCDEFGHJK"))
        assertTrue(clean.contains("room=eq.***"))
        assertTrue(clean.contains("user_id=neq.u1"))
    }

    @Test fun textWithoutSecretsIsUntouched() {
        val text = "marca «Interestelar» (#157336) → Quiero verla"
        assertEquals(text, LogFormat.redact(text))
    }

    @Test fun lineHasTimestampLevelTagAndMessage() {
        val line = LogFormat.line(0L, 'I', "Nav", "pestaña Buscar")
        assertTrue(Regex("""^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} I/Nav: pestaña Buscar$""").matches(line))
    }

    @Test fun onlyWarningsAndErrorsShowTheThread() {
        assertFalse(LogFormat.line(0L, 'I', "A", "m", "main").contains("[main]"))
        assertTrue(LogFormat.line(0L, 'W', "A", "m", "main").contains("[main]"))
        assertTrue(LogFormat.line(0L, 'E', "A", "m", "peli-log").contains("[peli-log]"))
    }

    @Test fun lineAppliesRedactionToTheMessage() {
        assertFalse(LogFormat.line(0L, 'W', "Red", "GET /x?api_key=SECRETO").contains("SECRETO"))
    }

    @Test fun levelIsReadFromEntryLinesOnly() {
        assertEquals('W', LogFormat.levelOf(LogFormat.line(0L, 'W', "A", "m")))
        assertEquals('D', LogFormat.levelOf(LogFormat.line(0L, 'D', "A", "m")))
        assertNull(LogFormat.levelOf("    at com.peli.app.Foo.bar(Foo.kt:12)"))
        assertNull(LogFormat.levelOf(""))
    }

    @Test fun problemFilterKeepsStackTraceContinuations() {
        val lines = listOf(
            LogFormat.line(0L, 'I', "Nav", "ok"),
            LogFormat.line(0L, 'E', "Red", "falló\njava.io.IOException: boom"),
            "    at okhttp3.Foo.bar(Foo.kt:1)",
            LogFormat.line(0L, 'D', "Red", "otra"),
            "    detalle de una línea D",
            LogFormat.line(0L, 'W', "DB", "aviso"),
        ).flatMap { it.split("\n") }
        val problems = LogFormat.onlyProblems(lines)
        // Línea E, su excepción, su "at ..." y la línea W.
        assertEquals(4, problems.size)
        assertTrue(problems.any { it.contains("java.io.IOException") })
        assertTrue(problems.any { it.contains("okhttp3.Foo") })
        assertFalse(problems.any { it.contains("otra") || it.contains("detalle de una línea D") })
    }
}
