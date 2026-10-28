package com.peli.app.data

import com.google.gson.annotations.SerializedName
import com.peli.app.BuildConfig
import com.peli.app.log.LogInterceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class OmdbRating(
    @SerializedName("Source") val source: String?,
    @SerializedName("Value") val value: String?,
)

/** Respuesta de OMDb por id de IMDb. Con error trae Response = "False". */
data class OmdbResponse(
    @SerializedName("Response") val response: String? = null,
    @SerializedName("imdbRating") val imdbRating: String? = null,
    @SerializedName("imdbVotes") val imdbVotes: String? = null,
    @SerializedName("Metascore") val metascore: String? = null,
    @SerializedName("Ratings") val ratings: List<OmdbRating>? = null,
    @SerializedName("Awards") val awards: String? = null,
    @SerializedName("Error") val error: String? = null,
)

interface OmdbApi {
    @GET("/")
    suspend fun byImdb(@Query("i") imdbId: String, @Query("apikey") apiKey: String): OmdbResponse
}

/** IMDb, Rotten Tomatoes y Metacritic salen de OMDb (clave gratuita en omdbapi.com). */
object Omdb {
    val configured get() = BuildConfig.OMDB_API_KEY.isNotBlank()

    val api: OmdbApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.omdbapi.com/")
            .client(OkHttpClient.Builder().addInterceptor(LogInterceptor("OMDb")).build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OmdbApi::class.java)
    }
}
