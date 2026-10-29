package com.peli.app.data

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/** Lo mínimo de una película que la otra persona necesita para ver su lista y comparar (nada de sinopsis ni notas ajenas). */
fun Entry.slimForSharing() = copy(overview = "", originalTitle = null, rating = null, releaseDate = null, genres = null)

/** Compresión de lo que se comparte; [gunzip] rechaza lo que al descomprimir pase de [maxChars] (bombas de compresión). */
object SharePack {
    fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    fun gunzip(bytes: ByteArray, maxChars: Int): String? {
        GZIPInputStream(bytes.inputStream()).use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > maxChars) return null
            }
            return String(out.toByteArray())
        }
    }
}
