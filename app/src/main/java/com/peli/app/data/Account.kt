package com.peli.app.data

import android.content.Context
import com.google.gson.GsonBuilder
import com.peli.app.BuildConfig
import com.peli.app.log.AppLog
import com.peli.app.log.LogInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Query
import java.io.IOException

/** Sesión de Supabase Auth (tras entrar con Google). */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Segundos desde 1970 en que caduca el accessToken. */
    val expiresAt: Long,
    val userId: String,
    val email: String?,
) {
    fun expiresSoon(nowSec: Long = System.currentTimeMillis() / 1000) = expiresAt - 60 <= nowSec
}

/** Mi perfil público: nombre de usuario y el ID que comparto para que me agreguen. */
data class Profile(val username: String, val friend_code: String)

/** Un amigo, o una solicitud (pending). [incoming] = me la enviaron a mí. */
data class Friend(val friend_id: String, val username: String, val status: String, val incoming: Boolean) {
    val accepted get() = status == "accepted"
}

/** Fila de `user_entries`: mis marcas, visibles solo para mí y mis amigos aceptados. */
data class UserEntry(
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
    val my_rating: Int = 0,
)

fun Entry.toUserEntry(userId: String) = UserEntry(
    user_id = userId, movie_id = movieId, title = title, overview = overview, poster_path = posterPath,
    year = year, release_date = releaseDate, liked = liked, seen = seen, disliked = disliked,
    watchlist = watchlist, genres = genres, rating = rating, updated_at = updatedAt, my_rating = myRating,
)

fun UserEntry.toEntry() = Entry(
    movieId = movie_id, owner = OWNER_PARTNER, title = title, overview = overview, posterPath = poster_path,
    year = year, liked = liked, seen = seen, disliked = disliked, releaseDate = release_date,
    watchlist = watchlist, genres = genres, rating = rating, updatedAt = updated_at, myRating = my_rating,
).normalized()

/** Qué pasó al pedir amistad (respuesta de la función request_friend). */
sealed interface FriendRequestResult {
    data class Sent(val username: String) : FriendRequestResult
    data class Accepted(val username: String) : FriendRequestResult
    data class AlreadyExists(val username: String) : FriendRequestResult
    data object NotFound : FriendRequestResult
    data object Self : FriendRequestResult

    companion object {
        fun parse(raw: String): FriendRequestResult? {
            val kind = raw.substringBefore(':')
            val name = raw.substringAfter(':', "")
            return when (kind) {
                "sent" -> Sent(name)
                "accepted" -> Accepted(name)
                "exists" -> AlreadyExists(name)
                "not_found" -> NotFound
                "self" -> Self
                else -> null
            }
        }
    }
}

/** Reglas del nombre de usuario (las mismas que valida la base de datos). */
object Usernames {
    private val RULE = Regex("^[A-Za-z0-9_]{3,20}$")
    fun isValid(name: String) = RULE.matches(name.trim())
    fun explain(name: String): String? = when {
        name.isBlank() -> null
        name.trim().length < 3 -> "Mínimo 3 caracteres."
        name.trim().length > 20 -> "Máximo 20 caracteres."
        !RULE.matches(name.trim()) -> "Solo letras, números y guion bajo (sin espacios ni tildes)."
        else -> null
    }
}

/** El ID de amigo se muestra como ABCD-EFGH. */
object FriendCodes {
    fun format(code: String) = code.chunked(4).joinToString("-")
}

/** Guarda la sesión, el perfil y el amigo elegido en el teléfono. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("peli_account", Context.MODE_PRIVATE)

    @Volatile private var cached: Session? = readSession()

    private fun readSession(): Session? {
        val access = prefs.getString("access", null) ?: return null
        val refresh = prefs.getString("refresh", null) ?: return null
        val uid = prefs.getString("uid", null) ?: return null
        return Session(access, refresh, prefs.getLong("expires", 0L), uid, prefs.getString("email", null))
    }

    val session: Session? get() = cached

    @Synchronized
    fun save(s: Session) {
        cached = s
        prefs.edit().putString("access", s.accessToken).putString("refresh", s.refreshToken)
            .putLong("expires", s.expiresAt).putString("uid", s.userId).putString("email", s.email).apply()
    }

    @Synchronized
    fun clear() {
        cached = null
        prefs.edit().clear().apply()
    }

    var profile: Profile?
        get() {
            val u = prefs.getString("username", null) ?: return null
            val c = prefs.getString("friend_code", null) ?: return null
            return Profile(u, c)
        }
        set(v) = prefs.edit().putString("username", v?.username).putString("friend_code", v?.friend_code).apply()

    /** Amigo con quien comparo listas ahora (id de cuenta). */
    var activeFriend: String?
        get() = prefs.getString("active_friend", null)
        set(v) = prefs.edit().putString("active_friend", v).apply()

    /** Amigo para el que ya se dejaron "sin avisar" los matches que existían al elegirlo. */
    var baselineFriend: String?
        get() = prefs.getString("baseline_friend", null)
        set(v) = prefs.edit().putString("baseline_friend", v).apply()

    /** Hora (ms) de la última subida completa de mis marcas. */
    var lastPush: Long
        get() = prefs.getLong("last_push", 0L)
        set(v) = prefs.edit().putLong("last_push", v).apply()
}

/** Texto para mostrar a partir de un error de red o de las funciones de Supabase. */
fun Throwable.accountMessage(): String {
    if (this is AccountException) return message ?: "Error de cuenta."
    if (this is retrofit2.HttpException) {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val msg = runCatching { JSONObject(body).optString("message") }.getOrDefault("")
        val pg = runCatching { JSONObject(body).optString("code") }.getOrDefault("")
        return when {
            msg.contains("username_taken") -> "Ese nombre de usuario ya está en uso."
            msg.contains("invalid_username") -> "Nombre no válido: 3 a 20 letras, números o guion bajo."
            msg.contains("already_exists") -> "Ya tienes un nombre de usuario."
            msg.contains("no_profile") -> "Primero elige tu nombre de usuario."
            msg.contains("no_session") || code() == 401 -> "Tu sesión caducó. Inicia sesión de nuevo."
            pg == "PGRST202" || pg == "42P01" || code() == 404 -> "Falta ejecutar accounts.sql en Supabase."
            else -> "Error del servidor (${code()}) $msg".trim()
        }
    }
    return message ?: javaClass.simpleName
}

/** Errores con un texto que se puede mostrar tal cual. */
class AccountException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Llamadas a Supabase Auth (iniciar con el token de Google y renovar la sesión). */
object SupabaseAuth {
    private val client by lazy { OkHttpClient.Builder().addInterceptor(LogInterceptor("Auth")).build() }
    private val JSON = "application/json".toMediaType()
    private val base get() = SupabaseUrl.base + "/auth/v1/token"

    fun signInWithGoogle(idToken: String, rawNonce: String): Session = post(
        "$base?grant_type=id_token",
        JSONObject().put("provider", "google").put("id_token", idToken).put("nonce", rawNonce),
    )

    fun refresh(refreshToken: String): Session = post(
        "$base?grant_type=refresh_token",
        JSONObject().put("refresh_token", refreshToken),
    )

    private fun post(url: String, body: JSONObject): Session {
        val req = Request.Builder().url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .post(body.toString().toRequestBody(JSON)).build()
        client.newCall(req).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                val detail = runCatching {
                    JSONObject(text).let { it.optString("msg").ifBlank { it.optString("error_description") }.ifBlank { it.optString("message") } }
                }.getOrDefault("")
                AppLog.w("Cuenta", "Supabase Auth respondió ${res.code}: $detail")
                throw AccountException("No se pudo iniciar sesión (${res.code}) $detail".trim())
            }
            val json = JSONObject(text)
            val user = json.getJSONObject("user")
            val expiresAt = json.optLong("expires_at", 0L).takeIf { it > 0 }
                ?: (System.currentTimeMillis() / 1000 + json.optLong("expires_in", 3600))
            return Session(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAt = expiresAt,
                userId = user.getString("id"),
                email = user.optString("email").takeIf { it.isNotBlank() },
            )
        }
    }
}

/** Funciones y tablas de Supabase que usan la sesión del usuario (RLS por cuenta). */
interface SocialApi {
    @POST("rest/v1/rpc/my_profile")
    suspend fun myProfile(@Body none: Map<String, String> = emptyMap()): List<Profile>

    @POST("rest/v1/rpc/create_profile")
    suspend fun createProfile(@Body body: Map<String, String>): List<Profile>

    @POST("rest/v1/rpc/request_friend")
    suspend fun requestFriend(@Body body: Map<String, String>): String

    @POST("rest/v1/rpc/accept_friend")
    suspend fun acceptFriend(@Body body: Map<String, String>)

    @POST("rest/v1/rpc/remove_friend")
    suspend fun removeFriend(@Body body: Map<String, String>)

    @POST("rest/v1/rpc/my_friends")
    suspend fun myFriends(@Body none: Map<String, String> = emptyMap()): List<Friend>

    @Headers("Prefer: resolution=merge-duplicates,return=minimal")
    @POST("rest/v1/user_entries")
    suspend fun upsert(@Body rows: List<UserEntry>, @Query("on_conflict") onConflict: String = "user_id,movie_id")

    /** Filas de una persona: la base solo las entrega si soy yo o un amigo aceptado. */
    @GET("rest/v1/user_entries")
    suspend fun entriesOf(@Query("user_id") userId: String, @Query("select") select: String = "*"): List<UserEntry>
}

object Social {
    /** Hay servidor y cliente de Google configurados en esta compilación. */
    val configured get() = Remote.configured && BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** La asigna PeliApp al iniciar. */
    lateinit var store: AccountStore

    private val refreshLock = Any()

    /** accessToken vigente; lo renueva si está por caducar. null = sin sesión. */
    private fun freshToken(force: Boolean = false): String? = synchronized(refreshLock) {
        val s = store.session ?: return null
        if (!force && !s.expiresSoon()) return s.accessToken
        try {
            val next = SupabaseAuth.refresh(s.refreshToken)
            store.save(next)
            AppLog.i("Cuenta", "sesión renovada")
            next.accessToken
        } catch (e: AccountException) {
            AppLog.w("Cuenta", "no se pudo renovar la sesión", e)
            // Si el servidor la rechazó, la sesión ya no sirve; si solo fue la red, se conserva.
            if (e.message?.contains("(400)") == true || e.message?.contains("(401)") == true) store.clear()
            null
        } catch (e: IOException) {
            AppLog.w("Cuenta", "sin red para renovar la sesión", e)
            if (force) null else s.accessToken
        }
    }

    val api: SocialApi by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                fun withAuth(token: String?) = chain.request().newBuilder()
                    .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                    .apply { if (token != null) header("Authorization", "Bearer $token") }
                    .build()
                var res = chain.proceed(withAuth(freshToken()))
                if (res.code == 401) {
                    res.close()
                    res = chain.proceed(withAuth(freshToken(force = true)))
                }
                res
            }
            .addInterceptor(LogInterceptor("Cuenta"))
            .build()
        Retrofit.Builder()
            .baseUrl(SupabaseUrl.base + "/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().serializeNulls().create()))
            .build()
            .create(SocialApi::class.java)
    }
}
