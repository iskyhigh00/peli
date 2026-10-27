package com.peli.app.log

import java.io.File

/**
 * Archivo de registro con rotación: cuando el actual pasa de [maxBytes] se convierte en el anterior
 * (y el anterior se descarta), así el total nunca supera ~2 x [maxBytes]. Sin dependencias de Android.
 */
class LogStore(private val dir: File, private val maxBytes: Long = 400_000L) {
    private val current get() = File(dir, "peli.log")
    private val previous get() = File(dir, "peli.1.log")

    init {
        dir.mkdirs()
    }

    @Synchronized
    fun append(line: String) {
        if (current.length() > maxBytes) {
            previous.delete()
            current.renameTo(previous)
        }
        current.appendText(line + "\n")
    }

    /** Todo el registro en orden cronológico (primero el archivo anterior). */
    @Synchronized
    fun readAll(): String = buildString {
        if (previous.exists()) append(previous.readText())
        if (current.exists()) append(current.readText())
    }

    @Synchronized
    fun sizeBytes(): Long =
        (if (previous.exists()) previous.length() else 0L) + (if (current.exists()) current.length() else 0L)

    @Synchronized
    fun clear() {
        previous.delete()
        current.delete()
    }
}
