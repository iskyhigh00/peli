package com.peli.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/** Almacén en memoria con el mismo contrato que el de Room. */
class MemoryOutbox : OutboxTable {
    val rows = LinkedHashMap<Pair<String, Int>, OutboxItem>()
    override suspend fun get(scope: String, movieId: Int) = rows[scope to movieId]
    override suspend fun put(item: OutboxItem) { rows[item.scope to item.movieId] = item }
    override suspend fun batch(scope: String, limit: Int) =
        rows.values.filter { it.scope == scope }.sortedWith(compareBy({ it.createdAt }, { it.movieId })).take(limit)
    override suspend fun deleteIfRev(scope: String, movieId: Int, rev: Long): Boolean {
        val cur = rows[scope to movieId] ?: return false
        if (cur.rev != rev) return false
        rows.remove(scope to movieId)
        return true
    }
    override suspend fun recordFailure(scope: String, movieId: Int, rev: Long) {
        val cur = rows[scope to movieId] ?: return
        if (cur.rev == rev) rows[scope to movieId] = cur.copy(attempts = cur.attempts + 1)
    }
    override suspend fun count(scope: String) = rows.values.count { it.scope == scope }
}

class OutboxTest {
    private val acct = "acct:u1"

    @Test fun anOfflineDeletionSurvivesAsATombstoneAndIsSentLater() = runBlocking {
        val table = MemoryOutbox()
        val outbox = Outbox(table) { 10 }
        outbox.enqueue(acct, 7, "{liked:true}")
        outbox.enqueue(acct, 7, "{vacia}")           // el usuario quitó la marca sin red
        assertEquals(1, outbox.pending(acct))
        assertEquals(2, table.get(acct, 7)!!.rev)
        // sin red: falla, no se pierde
        try { outbox.drain(acct) { throw IOException("sin red") } ; fail() } catch (_: IOException) {}
        assertEquals(1, outbox.pending(acct))
        assertEquals(1, table.get(acct, 7)!!.attempts)
        // "reinicio" y reconexión: otro Outbox sobre el mismo almacén
        val sent = ArrayList<List<OutboxItem>>()
        Outbox(table).drain(acct) { sent += it }
        assertEquals(listOf("{vacia}"), sent.single().map { it.payload })
        assertEquals(0, outbox.pending(acct))
    }

    @Test fun anOldAckNeverDeletesANewerRevision() = runBlocking {
        val table = MemoryOutbox()
        val outbox = Outbox(table)
        outbox.enqueue(acct, 1, "v1")
        var first = true
        outbox.drain(acct, maxBatches = 1) {
            // mientras se envía v1, el usuario cambia la marca
            if (first) {
                first = false
                outbox.enqueue(acct, 1, "v2")
            }
        }
        val left = table.get(acct, 1)
        assertNotNull(left)
        assertEquals("v2", left!!.payload)
        assertEquals(2, left.rev)
        // el siguiente ciclo envía v2 y recién entonces queda vacío
        val sent = ArrayList<String>()
        outbox.drain(acct) { sent += it.map { i -> i.payload } }
        assertEquals(listOf("v2"), sent)
        assertEquals(0, outbox.pending(acct))
    }

    @Test fun batchesAreBoundedAndEveryoneIsSent() = runBlocking {
        val table = MemoryOutbox()
        val outbox = Outbox(table)
        (1..250).forEach { outbox.enqueue(acct, it, "p$it") }
        val sizes = ArrayList<Int>()
        val total = outbox.drain(acct, batchSize = 100) { sizes += it.size }
        assertEquals(250, total)
        assertEquals(listOf(100, 100, 50), sizes)
        assertEquals(0, outbox.pending(acct))
    }

    @Test fun aFailureInTheMiddleKeepsWhatWasNotSent() = runBlocking {
        val table = MemoryOutbox()
        val outbox = Outbox(table)
        (1..150).forEach { outbox.enqueue(acct, it, "p$it") }
        var call = 0
        try {
            outbox.drain(acct, batchSize = 100) { if (++call == 2) throw IOException("corte") }
            fail()
        } catch (_: IOException) {}
        assertEquals(50, outbox.pending(acct))
    }

    @Test fun destinationsAreIndependentAndCapturedAtEnqueueTime() = runBlocking {
        val table = MemoryOutbox()
        val outbox = Outbox(table)
        outbox.enqueue("acct:A", 1, "a")
        outbox.enqueue("acct:B", 1, "b")
        outbox.drain("acct:B") { }
        assertEquals(1, outbox.pending("acct:A"))     // lo de A sigue pendiente aunque B ya envió
        assertEquals(0, outbox.pending("acct:B"))
    }

    @Test fun cancellationDoesNotCountAsAFailedAttempt() = runBlocking {
        val table = MemoryOutbox()
        val outbox = Outbox(table)
        outbox.enqueue(acct, 1, "x")
        try { outbox.drain(acct) { throw CancellationException("cancelado") }; fail() } catch (_: CancellationException) {}
        assertEquals(0, table.get(acct, 1)!!.attempts)
        assertEquals(1, outbox.pending(acct))
    }
}

class ContextGuardTest {
    @Test fun aLateResultFromAnOldContextIsDiscarded() {
        val guard = ContextGuard()
        val token = guard.token()
        assertTrue(guard.valid(token))
        guard.invalidate()          // cambió de amigo / salió de la sala / cerró sesión
        assertFalse(guard.valid(token))
        assertTrue(guard.valid(guard.token()))
    }
}

class PagesTest {
    private val all = (1..1501).toList()
    private suspend fun page(offset: Int, limit: Int) = all.drop(offset).take(limit)

    @Test fun fetchesEveryPage() = runBlocking {
        val calls = ArrayList<Int>()
        val result = Pages.fetchAll(500) { o, l -> calls += o; page(o, l) }
        assertEquals(all, result)
        assertEquals(listOf(0, 500, 1000, 1500), calls)
    }

    @Test fun exactMultipleOfThePageSizeEndsWithAnEmptyPage() = runBlocking {
        val result = Pages.fetchAll(500) { o, l -> (1..1000).toList().drop(o).take(l) }
        assertEquals(1000, result.size)
    }

    @Test fun aFailureInTheSecondPageReturnsNothingPartial() {
        var calls = 0
        try {
            runBlocking { Pages.fetchAll(500) { o, l -> if (++calls == 2) throw IOException("fallo") else page(o, l) } }
            fail("debía fallar")
        } catch (e: IOException) {
            assertEquals("fallo", e.message)
        }
    }

    @Test fun emptyListIsFine() = runBlocking {
        assertTrue(Pages.fetchAll<Int>(100) { _, _ -> emptyList() }.isEmpty())
    }
}

class ScopesTest {
    @Test fun accountReceivesMarksOnlyWhenItsOwnCollectionIsActive() {
        assertEquals(listOf("acct:u1"), Scopes.destinations("acct:u1", "u1", true, null))
        assertTrue(Scopes.destinations(Scopes.GUEST, "u1", true, null).isEmpty())      // a decidir si se sube lo de este teléfono
        assertTrue(Scopes.destinations("acct:u2", "u1", true, null).isEmpty())          // colección de otra cuenta
        assertTrue(Scopes.destinations("acct:u1", "u1", false, null).isEmpty())         // aún sin usuario
    }

    @Test fun legacyRoomsOnlyWorkWithoutAProfiledAccount() {
        assertEquals(listOf("room:ABC"), Scopes.destinations(Scopes.GUEST, null, false, "ABC"))
        assertTrue(Scopes.destinations("acct:u1", "u1", true, "ABC").none { it.startsWith("room:") })
        assertTrue(Scopes.destinations(Scopes.GUEST, null, false, null).isEmpty())
    }

    @Test fun loginNeverMergesTheGuestCollectionSilently() {
        // A marcó cosas, salió y entra B: no hay datos guardados de B y la colección activa es la del invitado
        assertEquals(Scopes.LoginPlan.AskClaim, Scopes.loginPlan(Scopes.GUEST, "acct:B", hasStash = false, meCount = 5))
        // B entra por primera vez sin películas: nada que decidir
        assertEquals(Scopes.LoginPlan.Adopt, Scopes.loginPlan(Scopes.GUEST, "acct:B", hasStash = false, meCount = 0))
        // A vuelve: se restauran sus datos y lo del invitado se guarda aparte
        assertEquals(Scopes.LoginPlan.Restore, Scopes.loginPlan(Scopes.GUEST, "acct:A", hasStash = true, meCount = 3))
        assertEquals(Scopes.LoginPlan.Same, Scopes.loginPlan("acct:A", "acct:A", hasStash = false, meCount = 3))
        // venía de otra cuenta sin cerrar sesión: se aparta, nunca se mezcla
        assertEquals(Scopes.LoginPlan.Restore, Scopes.loginPlan("acct:A", "acct:B", hasStash = false, meCount = 3))
    }

    @Test fun stashOwnersAreDistinctFromTheActiveAndPartnerOnes() {
        assertEquals("STASH|acct:u1", Scopes.stash("acct:u1"))
        assertFalse(Scopes.stash(Scopes.GUEST) == OWNER_ME || Scopes.stash(Scopes.GUEST) == OWNER_PARTNER)
    }
}
