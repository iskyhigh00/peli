package com.peli.app.data

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong

/**
 * Una mutación pendiente de enviar. Hay UNA por (destino, película): una marca nueva reemplaza a la anterior
 * pendiente y sube su revisión. [payload] es el estado completo de la marca (una marca vaciada también es un
 * estado: así las eliminaciones se envían y se reintentan igual que cualquier otro cambio).
 */
data class OutboxItem(
    val scope: String,
    val movieId: Int,
    val rev: Long,
    val payload: String,
    val createdAt: Long,
    val attempts: Int,
)

/** Almacén de la cola de envíos (en producción es Room; en las pruebas, memoria). */
interface OutboxTable {
    suspend fun get(scope: String, movieId: Int): OutboxItem?
    suspend fun put(item: OutboxItem)
    suspend fun batch(scope: String, limit: Int): List<OutboxItem>

    /** Borra solo si lo guardado sigue siendo la revisión [rev]; true si borró. */
    suspend fun deleteIfRev(scope: String, movieId: Int, rev: Long): Boolean
    suspend fun recordFailure(scope: String, movieId: Int, rev: Long)
    suspend fun count(scope: String): Int
}

/** Cola de envíos durable: se escribe junto con la marca y se vacía con un único drenador. */
class Outbox(private val table: OutboxTable, private val now: () -> Long = { System.currentTimeMillis() }) {
    /** El destino se fija AL ENCOLAR (cuenta o sala), no cuando se envía. */
    suspend fun enqueue(scope: String, movieId: Int, payload: String) {
        val previous = table.get(scope, movieId)
        table.put(
            OutboxItem(
                scope = scope,
                movieId = movieId,
                rev = (previous?.rev ?: 0L) + 1L,
                payload = payload,
                createdAt = previous?.createdAt ?: now(),
                attempts = 0,
            ),
        )
    }

    suspend fun pending(scope: String): Int = table.count(scope)

    /**
     * Envía por lotes. Cada lote confirmado se borra SOLO si no apareció una revisión más nueva mientras
     * se enviaba (el acuse de una revisión vieja no borra una nueva). Si [send] falla, lo no enviado queda
     * en la cola y el error se propaga. Devuelve cuántos elementos se enviaron.
     */
    suspend fun drain(scope: String, batchSize: Int = 100, maxBatches: Int = 100, send: suspend (List<OutboxItem>) -> Unit): Int {
        var sent = 0
        repeat(maxBatches) {
            val items = table.batch(scope, batchSize)
            if (items.isEmpty()) return sent
            try {
                send(items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                items.forEach { table.recordFailure(scope, it.movieId, it.rev) }
                throw e
            }
            items.forEach { table.deleteIfRev(scope, it.movieId, it.rev) }
            sent += items.size
        }
        return sent
    }
}

/**
 * Invalida el trabajo en curso cuando cambia el contexto (cuenta, amigo, sala). Una operación toma un
 * [token] al empezar y, antes de aplicar su resultado, comprueba [valid]: si el contexto cambió, se descarta.
 */
class ContextGuard {
    private val generation = AtomicLong(0)
    fun token(): Long = generation.get()
    fun valid(token: Long): Boolean = generation.get() == token
    fun invalidate() {
        generation.incrementAndGet()
    }
}

/** Lecturas por páginas: la lista completa se arma antes de reemplazar nada. */
object Pages {
    /**
     * Pide páginas de [pageSize] hasta recibir una incompleta. Si una página falla, el error se propaga y NO
     * se devuelve una lista parcial (quien llama conserva su instantánea anterior).
     */
    suspend fun <T> fetchAll(pageSize: Int, maxPages: Int = 400, fetch: suspend (offset: Int, limit: Int) -> List<T>): List<T> {
        require(pageSize > 0)
        val out = ArrayList<T>()
        var offset = 0
        repeat(maxPages) {
            val page = fetch(offset, pageSize)
            out += page
            if (page.size < pageSize) return out
            offset += pageSize
        }
        throw IllegalStateException("La lista tiene demasiadas páginas")
    }
}

/** Qué colección local está activa y a dónde se envían los cambios. */
object Scopes {
    const val GUEST = "guest"
    fun account(userId: String) = "acct:$userId"
    fun room(code: String) = "room:$code"

    /** Dueño con el que se guarda una colección que NO está activa (la activa siempre es OWNER_ME). */
    fun stash(scope: String) = "STASH|$scope"

    /**
     * Destinos de envío activos. Mi cuenta solo recibe mis marcas si la colección activa ES la de esa cuenta
     * (y ya tengo usuario); las salas del modo anterior solo funcionan sin cuenta con usuario.
     */
    fun destinations(scope: String, userId: String?, hasProfile: Boolean, room: String?): List<String> {
        val accountActive = userId != null && hasProfile
        return when {
            accountActive && scope == account(userId!!) -> listOf(account(userId))
            !accountActive && room != null -> listOf(room(room))
            else -> emptyList()
        }
    }

    enum class LoginPlan { Same, Restore, Adopt, AskClaim }

    /**
     * Qué hacer con las películas de este teléfono al iniciar sesión:
     * - Same: ya es la colección de esa cuenta.
     * - Restore: la cuenta ya tenía datos guardados aquí (o veníamos de otra cuenta): se cambia de colección sin mezclar.
     * - Adopt: no hay nada guardado que decidir.
     * - AskClaim: hay películas sin cuenta; el usuario decide si pasan a esta cuenta (nunca es automático).
     */
    fun loginPlan(from: String, to: String, hasStash: Boolean, meCount: Int): LoginPlan = when {
        from == to -> LoginPlan.Same
        hasStash -> LoginPlan.Restore
        meCount == 0 -> LoginPlan.Adopt
        from == GUEST -> LoginPlan.AskClaim
        else -> LoginPlan.Restore
    }
}
