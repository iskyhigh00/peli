package com.peli.app.data

import com.peli.app.entry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simulación de dos teléfonos conectados (cuenta o sala): cada uno marca en local, encola, envía al servidor
 * (una fila por persona y película, como `user_entries`), trae la lista de la otra persona y calcula los matches
 * y los avisos, con las mismas piezas que usa la app (Marks, Outbox, computeMatches, MatchAnnouncer).
 */
class TwoPeopleTest {
    /** Servidor compartido: (persona, película) -> marca. */
    private class Server {
        val rows = HashMap<Pair<String, Int>, Entry>()
        fun listOf(user: String) = rows.filterKeys { it.first == user }.values.toList()
    }

    private class Phone(val user: String, val other: String, val server: Server) {
        val mine = LinkedHashMap<Int, Entry>()
        var partner: List<Entry> = emptyList()
        val announced = HashSet<Int>()
        val alerts = ArrayList<Int>()
        var offline = false
        private var clock = 1L
        private val payloads = HashMap<String, Entry>()
        private val outbox = Outbox(MemoryOutbox()) { clock }
        private var baselineTaken = false

        fun mark(id: Int, change: (Entry) -> Entry) = runBlocking {
            clock++
            val base = entry(id, title = "Película $id")
            val next = Marks.apply(mine[id], base, clock, change)
            if (next.isEmpty) mine.remove(id) else mine[id] = next
            val key = "$user/$id/$clock"
            payloads[key] = next
            outbox.enqueue("acct:$user", id, key)
            announce()
        }

        /** Un ciclo de sincronización: enviar lo pendiente y traer la lista de la otra persona. */
        fun sync() = runBlocking {
            if (!offline) {
                outbox.drain("acct:$user") { items -> items.forEach { server.rows[user to it.movieId] = payloads.getValue(it.payload) } }
                val pulled = server.listOf(other).filter { !it.isEmpty }.map { it.copy(owner = OWNER_PARTNER) }
                if (!baselineTaken && pulled.isNotEmpty()) {
                    announced += MatchAnnouncer.baseline(mine.values.toList(), pulled)
                    baselineTaken = true
                }
                partner = pulled
            }
            announce()
        }

        fun pending() = runBlocking { outbox.pending("acct:$user") }
        fun matches() = computeMatches(mine.values.toList(), partner)
        fun both() = matches().filter { it.both }.map { it.entry.movieId }

        private fun announce() {
            val fresh = MatchAnnouncer.fresh(matches(), announced)
            announced += fresh.map { it.movieId }
            alerts += fresh.map { it.movieId }
        }
    }

    private fun pair(): Pair<Phone, Phone> {
        val server = Server()
        return Phone("ana", "beto", server) to Phone("beto", "ana", server)
    }

    @Test fun `los dos quieren la misma pelicula y ambos ven el match una sola vez`() {
        val (a, b) = pair()
        a.sync(); b.sync()
        a.mark(10) { it.toggleWatch() }
        a.sync(); b.sync()
        assertEquals(emptyList<Int>(), b.both())
        b.mark(10) { it.toggleWatch() }           // a Beto le salta el match al instante (ya tenía la lista de Ana)
        assertEquals(listOf(10), b.alerts)
        b.sync(); a.sync()                         // a Ana le llega en el siguiente ciclo
        assertEquals(listOf(10), a.alerts)
        repeat(3) { a.sync(); b.sync() }           // los ciclos siguientes no repiten el aviso
        assertEquals(listOf(10), a.alerts)
        assertEquals(listOf(10), b.alerts)
    }

    @Test fun `si uno la ve o la descarta el match desaparece para los dos`() {
        val (a, b) = pair()
        a.mark(1) { it.toggleWatch() }; b.mark(1) { it.toggleWatch() }
        a.mark(2) { it.toggleWatch() }; b.mark(2) { it.toggleWatch() }
        repeat(2) { a.sync(); b.sync() }
        assertEquals(setOf(1, 2), a.both().toSet())
        b.mark(1) { it.toggleSeen() }
        a.mark(2) { it.toggleDisliked() }
        repeat(2) { a.sync(); b.sync() }
        assertEquals(emptyList<Int>(), a.both())
        assertEquals(emptyList<Int>(), b.both())
    }

    @Test fun `quitar la marca tambien se envia y la otra persona deja de verla`() {
        val (a, b) = pair()
        a.mark(5) { it.toggleWatch() }
        a.sync(); b.sync()
        assertTrue(b.partner.any { it.movieId == 5 })
        a.mark(5) { it.toggleWatch() }            // la desmarca: queda vacía en local
        a.sync(); b.sync()
        assertFalse(b.partner.any { it.movieId == 5 })
        assertEquals(0, a.pending())
    }

    @Test fun `lo marcado sin conexion se envia al volver y el match llega igual`() {
        val (a, b) = pair()
        b.mark(7) { it.toggleWatch() }
        b.sync()
        a.sync()                                   // ya conectados
        a.offline = true
        a.sync()
        a.mark(7) { it.toggleWatch() }
        a.sync()
        assertEquals(1, a.pending())
        b.sync()
        assertTrue(b.both().isEmpty())
        a.offline = false
        a.sync(); b.sync()
        assertEquals(0, a.pending())
        assertEquals(listOf(7), b.both())
        assertEquals(listOf(7), b.alerts)
        assertEquals(listOf(7), a.alerts)
    }

    @Test fun `cambios rapidos antes de sincronizar dejan solo el estado final`() {
        val (a, b) = pair()
        a.mark(3) { it.toggleWatch() }
        a.mark(3) { it.toggleWatch() }
        a.mark(3) { it.toggleWatch() }
        assertEquals(1, a.pending())               // una sola fila pendiente por película
        a.sync(); b.sync()
        assertTrue(b.partner.single { it.movieId == 3 }.watchlist)
    }

    @Test fun `al conectar no se celebran de golpe muchas coincidencias que ya existian`() {
        val (a, b) = pair()
        // Antes de conectarse, cada uno ya tenía su lista (por ejemplo, sin red).
        a.offline = true; b.offline = true
        listOf(1, 2, 3, 11, 12).forEach { id -> a.mark(id) { it.toggleWatch() }; b.mark(id) { it.toggleWatch() } }
        a.offline = false; b.offline = false
        a.sync(); b.sync(); a.sync()               // Ana sube primero: su primera traída llega vacía
        assertEquals(setOf(1, 2, 3, 11, 12), a.both().toSet())
        assertEquals(emptyList<Int>(), a.alerts)  // se ven en «Juntos», sin animación por cada una
        assertEquals(emptyList<Int>(), b.alerts)
        a.mark(4) { it.toggleWatch() }; b.mark(4) { it.toggleWatch() }
        a.sync(); b.sync(); a.sync()
        assertEquals(listOf(4), a.alerts)          // lo nuevo sí se avisa
        assertEquals(listOf(4), b.alerts)
    }

    @Test fun `pocas coincidencias al conectar si se celebran`() {
        val (a, b) = pair()
        a.offline = true; b.offline = true
        listOf(1, 2).forEach { id -> a.mark(id) { it.toggleWatch() }; b.mark(id) { it.toggleWatch() } }
        a.offline = false; b.offline = false
        a.sync(); b.sync(); a.sync()
        assertEquals(setOf(1, 2), a.alerts.toSet())
        assertEquals(setOf(1, 2), b.alerts.toSet())
    }

    @Test fun `la nota y el me gusto de uno no crean matches ni los rompen`() {
        val (a, b) = pair()
        a.mark(9) { it.toggleWatch() }; b.mark(9) { it.toggleWatch() }
        a.sync(); b.sync(); a.sync()
        b.mark(8) { it.withRating(9) }             // calificar otra película (implica vista) no afecta el match
        b.sync(); a.sync()
        assertEquals(listOf(9), a.both())
        assertFalse(a.matches().any { it.entry.movieId == 8 })
    }
}
