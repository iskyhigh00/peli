package com.peli.app.data

import com.google.gson.annotations.SerializedName
import com.peli.app.APP_VERSION
import com.peli.app.log.LogInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class WdValue(@SerializedName("value") val value: String? = null)
data class WdResults(@SerializedName("bindings") val bindings: List<Map<String, WdValue>>? = null)
data class WdResponse(@SerializedName("results") val results: WdResults? = null)

interface WikidataApi {
    @GET("sparql")
    suspend fun query(@Query("query") query: String, @Query("format") format: String = "json"): WdResponse
}

/** Wikidata (consulta pública) para el detalle de premios y nominaciones. */
object Wikidata {
    private val api: WikidataApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Peli/$APP_VERSION (app personal de películas)")
                        .header("Accept", "application/sparql-results+json")
                        .build(),
                )
            }
            .addInterceptor(LogInterceptor("Wikidata"))
            .build()
        Retrofit.Builder()
            .baseUrl("https://query.wikidata.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WikidataApi::class.java)
    }

    /** Premios y nominaciones de la película con ese id de IMDb. Lanza si la red falla. */
    suspend fun awards(imdbId: String, filmTitles: Set<String>): List<AwardRow> {
        val q = AwardDetails.query(imdbId) ?: return emptyList()
        val rows = api.query(q).results?.bindings.orEmpty().map { b ->
            AwardRow(
                award = b["awardLabel"]?.value.orEmpty(),
                won = b["won"]?.value == "true",
                who = b["whoLabel"]?.value,
                year = b["year"]?.value?.toDoubleOrNull()?.toInt(),
            )
        }
        return AwardDetails.sorted(AwardDetails.clean(rows, filmTitles))
    }
}
