package com.peli.app.data

import com.google.gson.GsonBuilder
import com.peli.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query

/** Fila de la tabla `entries` de Supabase (ver supabase/schema.sql). */
data class RemoteEntry(
    val room: String,
    val user_id: String,
    val movie_id: Int,
    val title: String,
    val overview: String,
    val poster_path: String?,
    val year: String,
    val release_date: String?,
    val liked: Boolean,
    val seen: Boolean,
    val disliked: Boolean,
    val watchlist: Boolean,
    val genres: String?,
    val rating: Double?,
    val updated_at: Long,
)

/**
 * El header x-room es el que autoriza (RLS): solo se ven/escriben filas de esa sala.
 * Las claves de cada fila se mandan siempre (serializeNulls) para que el upsert por lotes sea uniforme.
 */
interface SupabaseApi {
    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/entries")
    suspend fun upsert(
        @Header("x-room") roomHeader: String,
        @Body rows: List<RemoteEntry>,
        @Query("on_conflict") onConflict: String = "room,user_id,movie_id",
    )

    /** Filas de los demás usuarios de la sala (filtros PostgREST: room=eq.X, user_id=neq.Y). */
    @GET("rest/v1/entries")
    suspend fun others(
        @Header("x-room") roomHeader: String,
        @Query("room") room: String,
        @Query("user_id") userId: String,
        @Query("select") select: String = "*",
    ): List<RemoteEntry>
}

object Remote {
    /** Sin URL/clave (secretos de CI o local.properties) la sincronización queda desactivada. */
    val configured get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

    val api: SupabaseApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                        .header("Authorization", "Bearer ${BuildConfig.SUPABASE_ANON_KEY}")
                        .build(),
                )
            }
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.SUPABASE_URL.trimEnd('/') + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
            .build()
            .create(SupabaseApi::class.java)
    }
}

fun Entry.toRemote(room: String, userId: String) = RemoteEntry(
    room = room, user_id = userId, movie_id = movieId, title = title, overview = overview,
    poster_path = posterPath, year = year, release_date = releaseDate, liked = liked, seen = seen,
    disliked = disliked, watchlist = watchlist, genres = genres, rating = rating, updated_at = updatedAt,
)

fun RemoteEntry.toEntry() = Entry(
    movieId = movie_id, owner = OWNER_PARTNER, title = title, overview = overview, posterPath = poster_path,
    year = year, liked = liked, seen = seen, disliked = disliked, releaseDate = release_date,
    watchlist = watchlist, genres = genres, rating = rating, updatedAt = updated_at,
)
