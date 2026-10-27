package com.peli.app.data

import kotlinx.coroutines.CancellationException

/**
 * Como `runCatching`, pero la cancelación de la corrutina NO se captura: se vuelve a lanzar.
 * (Capturarla dentro de `runCatching` deja trabajo cancelado "seguir como si nada").
 */
suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

/** Ninguna fuente de películas respondió: es un fallo de red/servicio, no que "no haya películas". */
class SourcesUnavailableException(cause: Throwable?) :
    Exception(cause?.message ?: "No se pudo contactar el servicio de películas", cause)

/** Cuenta cuántas fuentes respondieron y cuántas fallaron en una misma carga. */
class SourceTally {
    var ok = 0
        private set
    var failed = 0
        private set
    var firstError: Throwable? = null
        private set

    fun <T> record(result: Result<T>): T? {
        result.onSuccess { ok++ }.onFailure {
            failed++
            if (firstError == null) firstError = it
        }
        return result.getOrNull()
    }

    /** true si hubo fuentes y todas fallaron. */
    val allFailed get() = failed > 0 && ok == 0

    fun requireAny() {
        if (allFailed) throw SourcesUnavailableException(firstError)
    }
}
