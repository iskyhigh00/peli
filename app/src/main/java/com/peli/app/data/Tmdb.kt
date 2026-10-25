package com.peli.app.data

import com.peli.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class TmdbMovie(
    val id: Int,
    val title: String?,
    val overview: String?,
    val poster_path: String?,
    val release_date: String?,
)

data class TmdbPage(val results: List<TmdbMovie> = emptyList())

interface TmdbApi {
    @GET("movie/popular")
    suspend fun popular(@Query("page") page: Int = 1): TmdbPage

    @GET("search/movie")
    suspend fun search(@Query("query") query: String): TmdbPage

    @GET("movie/{id}/recommendations")
    suspend fun recommendations(@Path("id") id: Int): TmdbPage

    companion object {
        const val POSTER_BASE = "https://image.tmdb.org/t/p/w342"

        fun create(): TmdbApi {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val url = chain.request().url.newBuilder()
                        .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                        .addQueryParameter("language", "es-ES")
                        .build()
                    chain.proceed(chain.request().newBuilder().url(url).build())
                }
                .build()
            return Retrofit.Builder()
                .baseUrl("https://api.themoviedb.org/3/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(TmdbApi::class.java)
        }
    }
}

fun TmdbMovie.toEntry(owner: String) = Entry(
    movieId = id,
    owner = owner,
    title = title.orEmpty(),
    overview = overview.orEmpty(),
    posterPath = poster_path,
    year = release_date.orEmpty().take(4),
    releaseDate = release_date?.takeIf { it.isNotBlank() },
)
