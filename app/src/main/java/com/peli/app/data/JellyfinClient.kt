package com.peli.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

data class JfItem(val ProviderIds: Map<String, String>? = null)
data class JfPage(val Items: List<JfItem>? = null)

interface JellyfinApi {
    @GET
    suspend fun items(
        @Url url: String,
        @Header("X-Emby-Token") token: String,
        @Header("Authorization") auth: String,
    ): JfPage
}

/** Ninguna dirección respondió: cada una con la razón (para mostrarla). */
class JellyfinException(val details: List<Pair<String, String>>) : Exception(details.joinToString("; ") { "${it.first}: ${it.second}" })

/** Consulta a tu servidor Jellyfin (por ejemplo, por Tailscale): qué películas tienes. */
object JellyfinClient {
    private val api: JellyfinApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JellyfinApi::class.java)
    }

    private suspend fun fetchIds(base: String, apiKey: String): Set<Int> {
        val ids = HashSet<Int>()
        var start = 0
        repeat(60) {
            val page = api.items(Jellyfin.pageUrl(base, start), apiKey, "MediaBrowser Token=\"$apiKey\"").Items.orEmpty()
            ids += Jellyfin.tmdbIds(page.map { it.ProviderIds })
            if (page.size < Jellyfin.PAGE) return ids
            start += Jellyfin.PAGE
        }
        return ids
    }

    /**
     * Prueba todas las direcciones a la vez y se queda con la primera que responde: sirve la de casa, la de Tailscale
     * o la del hotspot según la red en la que estés. Devuelve la dirección que funcionó y los ids de TMDB de sus
     * películas. Lanza si ninguna responde.
     */
    suspend fun tmdbIds(bases: List<String>, apiKey: String): Pair<String, Set<Int>> {
        require(bases.isNotEmpty()) { "sin direcciones" }
        return coroutineScope {
            val result = CompletableDeferred<Pair<String, Set<Int>>>()
            val failures = AtomicInteger(0)
            val reasons = java.util.concurrent.ConcurrentHashMap<String, String>()
            val jobs = bases.map { base ->
                launch(Dispatchers.IO) {
                    try {
                        result.complete(base to fetchIds(base, apiKey))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val code = (e as? retrofit2.HttpException)?.code()
                        reasons[base] = Jellyfin.reason(e.javaClass.simpleName, code)
                        if (failures.incrementAndGet() == bases.size) {
                            result.completeExceptionally(JellyfinException(bases.map { it to (reasons[it] ?: "sin respuesta") }))
                        }
                    }
                }
            }
            try { result.await() } finally { jobs.forEach { it.cancel() } }
        }
    }
}
