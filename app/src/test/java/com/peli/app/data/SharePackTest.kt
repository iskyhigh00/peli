package com.peli.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SharePackTest {
    @Test fun `comprimir y descomprimir devuelve lo mismo`() {
        val text = (1..200).joinToString(",") { """{"movieId":$it,"title":"Película $it","overview":"Una sinopsis larga de ejemplo"}""" }
        val packed = SharePack.gzip(text.toByteArray())
        assertTrue("comprime: ${packed.size} < ${text.length}", packed.size < text.length / 3)
        assertEquals(text, SharePack.gunzip(packed, 1_000_000))
    }

    @Test fun `una bomba de compresion se rechaza`() {
        val big = "a".repeat(2_000_000)
        val packed = SharePack.gzip(big.toByteArray())
        assertTrue(packed.size < 10_000)
        assertNull(SharePack.gunzip(packed, 1_000_000))
    }

    @Test fun `datos que no son gzip lanzan error y quien llama lo atrapa`() {
        val failed = runCatching { SharePack.gunzip("no es gzip".toByteArray(), 1000) }.isFailure
        assertTrue(failed)
    }
}
