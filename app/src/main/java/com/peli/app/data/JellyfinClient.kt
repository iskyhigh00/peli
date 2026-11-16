package com.peli.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

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

/** Consulta a tu servidor Jellyfin (por ejemplo, por Tailscale): qué películas tienes. */
object JellyfinClient {
    private val api: JellyfinApi by lazy {
        Retrofit.Builder()
            .baseUrl("http://localhost/")
            .client(OkHttpClient.Builder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JellyfinApi::class.java)
    }

    /** Los ids de TMDB de todas las películas del servidor. Lanza si no se pudo conectar o la clave no sirve. */
    suspend fun tmdbIds(base: String, apiKey: String): Set<Int> {
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
}
