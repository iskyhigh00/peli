package com.peli.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Busca servidores Jellyfin en la red en que está el teléfono (la misma forma que usan las apps oficiales): manda un
 * aviso por UDP al puerto 7359 y el servidor responde con su nombre e id. Sirve en wifi, hotspot y red local; no
 * atraviesa VPN como Tailscale.
 */
object JellyfinDiscovery {
    data class Found(val name: String?, val id: String?, val address: String)

    private const val PORT = 7359

    private fun targets(): List<InetAddress> {
        val out = LinkedHashSet<InetAddress>()
        runCatching { out += InetAddress.getByName("255.255.255.255") }
        runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.interfaceAddresses }
                .mapNotNullTo(out) { it.broadcast }
        }
        return out.toList()
    }

    suspend fun find(timeoutMs: Int = 1500): List<Found> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, Found>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 250
                val msg = "who is JellyfinServer?".toByteArray()
                targets().forEach { t -> runCatching { socket.send(DatagramPacket(msg, msg.size, t, PORT)) } }
                var end = System.currentTimeMillis() + timeoutMs
                val buf = ByteArray(2048)
                while (System.currentTimeMillis() < end) {
                    val p = DatagramPacket(buf, buf.size)
                    try { socket.receive(p) } catch (e: SocketTimeoutException) { continue }
                    val d = Jellyfin.parseDiscovery(String(p.data, 0, p.length)) ?: continue
                    val addr = Jellyfin.addressFrom(p.address.hostAddress.orEmpty(), d.address)
                    if (found.isEmpty()) end = minOf(end, System.currentTimeMillis() + 500)   // ya respondió: se espera un poco por otros
                    found[d.id ?: addr] = Found(d.name, d.id, addr)
                }
            }
        }
        found.values.toList()
    }
}
