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
    val genre_ids: List<Int>? = null,
    val vote_average: Double? = null,
)

data class TmdbPage(val results: List<TmdbMovie> = emptyList())

data class TmdbExternalIds(val imdb_id: String? = null)

data class TmdbDetails(
    val id: Int,
    val vote_average: Double? = null,
    val vote_count: Int? = null,
    val external_ids: TmdbExternalIds? = null,
)

data class TmdbAuthorDetails(val rating: Double? = null)

data class TmdbReview(
    val author: String? = null,
    val content: String? = null,
    val created_at: String? = null,
    val author_details: TmdbAuthorDetails? = null,
)

data class TmdbReviewsPage(val results: List<TmdbReview> = emptyList())

interface TmdbApi {
    @GET("movie/popular")
    suspend fun popular(@Query("page") page: Int = 1): TmdbPage

    @GET("search/movie")
    suspend fun search(@Query("query") query: String, @Query("page") page: Int = 1): TmdbPage

    /**
     * Descubrir por géneros (ids separados por "|" = cualquiera de ellos) y/o rango de fechas de estreno.
     * Los parámetros nulos no se envían.
     */
    @GET("discover/movie")
    suspend fun discover(
        @Query("with_genres") genres: String? = null,
        @Query("page") page: Int = 1,
        @Query("sort_by") sortBy: String = "vote_average.desc",
        @Query("vote_count.gte") minVotes: Int = 500,
        @Query("primary_release_date.gte") releasedFrom: String? = null,
        @Query("primary_release_date.lte") releasedTo: String? = null,
    ): TmdbPage

    @GET("movie/{id}")
    suspend fun details(@Path("id") id: Int, @Query("append_to_response") append: String = "external_ids"): TmdbDetails

    /** Las reseñas de TMDB casi siempre están en inglés: se piden sin forzar el idioma. */
    @GET("movie/{id}/reviews")
    suspend fun reviews(@Path("id") id: Int, @Query("language") language: String = "en-US"): TmdbReviewsPage

    @GET("movie/{id}/recommendations")
    suspend fun recommendations(@Path("id") id: Int): TmdbPage

    companion object {
        const val POSTER_BASE = "https://image.tmdb.org/t/p/w342"
        const val POSTER_LARGE = "https://image.tmdb.org/t/p/w780"

        fun create(): TmdbApi {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val original = chain.request().url
                    val url = original.newBuilder()
                        .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
                        .apply { if (original.queryParameter("language") == null) addQueryParameter("language", "es-ES") }
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
    genres = genre_ids?.takeIf { it.isNotEmpty() }?.joinToString(","),
    rating = vote_average,
)
