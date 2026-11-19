package com.peli.app.ui.features

import com.peli.app.data.Jellyfin
import com.peli.app.data.JellyfinClient
import com.peli.app.data.JellyfinDiscovery
import com.peli.app.data.JellyfinException
import com.peli.app.data.Repository
import com.peli.app.data.accountMessage
import com.peli.app.data.attempt
import com.peli.app.log.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow

/** Tu servidor Jellyfin: lo que ya tienes ahí cuenta como «disponible». Se busca solo en la red y se actualiza al cambiar de red. */
class ServerFeature(
    private val env: VmEnv,
    private val repo: Repository,
    private val activeFriend: () -> String?,
    private val hasSession: () -> Boolean,
    private val friendName: (String) -> String?,
) {
    private val prefs get() = env.peli.jellyfinPrefs

    val url = MutableStateFlow(prefs.url)
    val key = MutableStateFlow(prefs.key)
    val ids = MutableStateFlow(prefs.ids)
    val status = MutableStateFlow<String?>(null)

    /** Cuándo se actualizó con el servidor por última vez (milisegundos; 0 = nunca). */
    val lastSync = MutableStateFlow(prefs.syncedAt)

    /** Lo que comparte mi amigo (su servidor): me sirve aunque yo no tenga acceso. */
    val shared = MutableStateFlow(prefs.sharedIds)
    val sharedAt = MutableStateFlow(prefs.sharedAt)
    val sharedStatus = MutableStateFlow<String?>(null)

    /** Resultado de subir mi lista (solo si tengo acceso al servidor). */
    val uploadStatus = MutableStateFlow<String?>(null)

    /** Sube mi lista a mi amigo (solo ids de TMDB). */
    private suspend fun pushShared(mine: Set<Int>) {
        if (!hasSession()) return
        try {
            repo.setLibrary(mine.toList())
            val hora = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale("es")).format(java.util.Date())
            uploadStatus.value = "Tu lista (${mine.size} películas) está compartida con tus amigos. Subida $hora."
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Jellyfin", "no se pudo compartir la lista", e)
            uploadStatus.value = "No se pudo compartir tu lista: ${e.accountMessage()}"
        }
    }

    /** Baja la lista del servidor de mi amigo activo. */
    private suspend fun pullShared(quietIfEmpty: Boolean = false) {
        val friend = activeFriend() ?: return
        if (!hasSession()) return
        try {
            val row = repo.library(friend)
            val name = friendName(friend)?.let { "@$it" } ?: "tu amigo"
            if (row != null && row.tmdb_ids.isNotEmpty()) {
                val ids = row.tmdb_ids.toSet()
                shared.value = ids
                prefs.sharedIds = ids
                val now = System.currentTimeMillis()
                sharedAt.value = now
                prefs.sharedAt = now
                sharedStatus.value = "Biblioteca de $name: ${ids.size} películas."
            } else {
                sharedStatus.value = if (quietIfEmpty) null else "$name todavía no comparte su biblioteca."
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w("Jellyfin", "no se pudo traer la lista compartida", e)
            sharedStatus.value = "No se pudo traer la biblioteca compartida: ${e.accountMessage()}"
        }
    }

    fun setUrl(v: String) { url.value = v; prefs.url = v }
    fun setKey(v: String) { key.value = v; prefs.key = v }

    private var busy = false
    private var lastTry = 0L

    /**
     * Busca el servidor solo en la red actual (aviso UDP de Jellyfin) y lo consulta, probando también las direcciones
     * escritas a mano (Tailscale, otras redes). Con [manual] avisa del resultado; si no hay conexión deja lo último que supo.
     */
    fun refresh(manual: Boolean) {
        val typed = Jellyfin.baseUrls(url.value)
        val apiKey = key.value.trim()
        if (busy) {
            if (manual) env.message.value = "Ya está conectando…"
            return
        }
        busy = true
        lastTry = System.currentTimeMillis()
        status.value = "Buscando en la red…"
        env.launch(Dispatchers.IO) {
            try {
                // 1) ¿Está en esta red? Si el servidor cambió de IP, se actualiza sola.
                val knownId = prefs.serverId
                val seen = JellyfinDiscovery.find().firstOrNull { knownId.isEmpty() || it.id == null || it.id == knownId }
                if (seen != null) {
                    prefs.found = seen.address
                    if (!seen.id.isNullOrEmpty()) prefs.serverId = seen.id
                    AppLog.i("Jellyfin", "servidor detectado en la red: ${seen.name} ${seen.address}")
                }
                if (apiKey.isEmpty()) {
                    pullShared()
                    if (shared.value.isNotEmpty()) {
                        status.value = "Sin clave de API: se usa la biblioteca que comparte tu amigo."
                        if (manual) env.message.value = "Biblioteca compartida actualizada: ${shared.value.size} películas."
                        return@launch
                    }
                    status.value = if (seen != null) "Servidor detectado: ${seen.name ?: "Jellyfin"} (${seen.address.substringAfter("://")}). Falta la clave de API." else "Falta la clave de API."
                    if (manual) env.message.value = if (seen != null) "Encontré tu servidor. Falta la clave de API." else "Falta la clave de API."
                    return@launch
                }
                val bases = (listOf(prefs.found).filter { it.isNotEmpty() } + typed).distinct()
                if (bases.isEmpty()) {
                    pullShared()
                    status.value = "No encontré el servidor en esta red y no hay direcciones guardadas. Se usa lo último que se supo."
                    if (manual) env.message.value = "No encontré tu servidor en esta red."
                    return@launch
                }
                // 2) Se consultan todas las direcciones a la vez y se usa la que responda.
                status.value = "Conectando…"
                val r = attempt { JellyfinClient.tmdbIds(bases, apiKey) }
                val ok = r.getOrNull()
                if (ok != null) {
                    val (base, found) = ok
                    ids.value = found
                    prefs.ids = found
                    val now = System.currentTimeMillis()
                    prefs.syncedAt = now
                    lastSync.value = now
                    val via = if (base == prefs.found && seen != null) " (detectado en la red)" else ""
                    status.value = "Conectado por ${base.substringAfter("://")}$via: ${found.size} películas con ID de TMDB."
                    if (manual) env.message.value = "Servidor conectado: ${found.size} películas."
                    pushShared(found)
                    pullShared(quietIfEmpty = true)
                } else {
                    pullShared(quietIfEmpty = true)
                    AppLog.w("Jellyfin", "no se pudo consultar el servidor", r.exceptionOrNull())
                    val why = (r.exceptionOrNull() as? JellyfinException)?.details
                        ?.joinToString("\n") { (b, reason) -> "• ${b.substringAfter("://")}: $reason" }
                    status.value = "No se pudo conectar. Se usa lo último que se supo." + (why?.let { "\n$it" } ?: "")
                    if (manual) env.message.value = "No se pudo conectar con tu servidor."
                }
            } finally {
                busy = false
            }
        }
    }

    /** Intento automático (al abrir, al volver a la app, cada pocos minutos o al cambiar de red); no insiste si hubo uno hace poco. */
    fun refreshAuto(minGapMs: Long = 20_000) {
        if (System.currentTimeMillis() - lastTry < minGapMs) return
        refresh(false)
    }

    private var watching = false
    private val callback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) { refreshAuto(5_000) }
    }

    /** Cuando el teléfono cambia de red (wifi de casa, hotspot, datos…) vuelve a buscar el servidor. */
    fun watchNetwork() {
        if (watching) return
        watching = true
        runCatching {
            env.app.getSystemService(android.net.ConnectivityManager::class.java).registerDefaultNetworkCallback(callback)
        }
    }

    fun release() {
        runCatching {
            if (watching) env.app.getSystemService(android.net.ConnectivityManager::class.java).unregisterNetworkCallback(callback)
        }
    }
}
