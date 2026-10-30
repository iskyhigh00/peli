package com.peli.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity

const val OWNER_ME = "ME"
const val OWNER_PARTNER = "PARTNER"

/**
 * Una película marcada por una persona (yo o mi pareja).
 *
 * Estados:
 *  - [watchlist] "Quiero verla": es lo que se comparte y se compara con la pareja.
 *  - [liked]     "La vi y me gustó": señal de gusto para recomendar. Implica [seen].
 *  - [seen]      "Ya la vi" (con o sin opinión): deja de contar como pendiente.
 *  - [disliked]  "No me gusta / no me interesa": señal negativa.
 */
@Entity(tableName = "entries", primaryKeys = ["movieId", "owner"])
data class Entry(
    val movieId: Int,
    val owner: String,
    val title: String,
    val overview: String,
    val posterPath: String?,
    val year: String,
    /** La columna se llama "favorite" por compatibilidad con versiones anteriores. */
    @ColumnInfo(name = "favorite") val liked: Boolean = false,
    val seen: Boolean = false,
    val disliked: Boolean = false,
    /** yyyy-MM-dd de TMDB; null si se guardó antes de existir este campo o TMDB no la tiene. */
    val releaseDate: String? = null,
    val watchlist: Boolean = false,
    /** Ids de género de TMDB separados por coma (para aprender gustos). */
    val genres: String? = null,
    /** Nota promedio de TMDB (0..10). */
    val rating: Double? = null,
    /** Última vez que la marqué (ms); los gustos recientes pesan más. */
    val updatedAt: Long = 0,
    /** Mi nota de 1 a 10 (0 = sin calificar). Calificar implica que ya la vi. */
    val myRating: Int = 0,
    /** Título en el idioma original (de TMDB). null = aún no se sabe; "" = ya consultado y no hay otro. Solo local. */
    val originalTitle: String? = null,
) {
    val isEmpty get() = !liked && !seen && !disliked && !watchlist

    /** Aún no se estrena (fecha futura; sin fecha, año posterior al actual). */
    val isUpcoming get() = ReleaseInfo.isUpcoming(releaseDate, year)

    /** Fecha "12 nov 2026" si es próximo estreno; si no, solo el año. */
    val dateLabel get() = if (isUpcoming) ReleaseInfo.format(releaseDate) ?: year else year

    /** Cómo se muestra el título: «Original (Traducción)» cuando son distintos; si no, el título tal cual. */
    val displayTitle: String get() = TitleFormat.display(title, originalTitle)

    val genreIds: List<Int> get() = genres?.split(",")?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
}

/**
 * La misma película sin ninguna marca personal: solo metadatos. Es lo que se usa para empezar MI marca
 * desde una entrada que viene de otra persona (pareja/amigo) o de una lista de sugerencias, para no
 * heredar sus estados, su nota ni su fecha.
 */
fun Entry.asUnmarked(owner: String) = copy(
    owner = owner,
    watchlist = false,
    liked = false,
    seen = false,
    disliked = false,
    myRating = 0,
    updatedAt = 0,
)

/**
 * Repara estados incoherentes de forma conservadora (datos importados o antiguos):
 * la nota implica "la vi"; "me gustó" implica "la vi" y no es "no me gusta"; "no me gusta" no está en "quiero verla".
 */
fun Entry.normalized(): Entry {
    var e = copy(myRating = myRating.coerceIn(0, 10))
    if (e.myRating > 0) e = e.copy(seen = true, watchlist = false)
    if (e.liked) e = e.copy(seen = true, watchlist = false, disliked = false)
    if (e.disliked) e = e.copy(liked = false, watchlist = false)
    if (e.watchlist) e = e.copy(seen = false, liked = false, disliked = false, myRating = 0)
    return e
}

// Transiciones de estado: cada acción deja el estado consistente (p. ej. "me gustó" implica "la vi").
// "Quiero verla" es una película pendiente: no puede conservar una nota ni estar vista.
fun Entry.toggleWatch() =
    if (watchlist) copy(watchlist = false)
    else copy(watchlist = true, seen = false, liked = false, disliked = false, myRating = 0)

fun Entry.toggleLiked() =
    if (liked) copy(liked = false) else copy(liked = true, seen = true, watchlist = false, disliked = false)

fun Entry.toggleSeen() =
    if (seen) copy(seen = false, liked = false, myRating = 0) else copy(seen = true, watchlist = false)

/** Califica con 1..10 (0 quita la nota). Calificar implica que ya la vi y la saca de "quiero verla". */
fun Entry.withRating(score: Int): Entry {
    val n = score.coerceIn(0, 10)
    return if (n == 0) copy(myRating = 0) else copy(myRating = n, seen = true, watchlist = false)
}

fun Entry.toggleDisliked() =
    if (disliked) copy(disliked = false) else copy(disliked = true, liked = false, watchlist = false)

/** Texto corto del estado actual (para chips). Null si no hay marca. */
fun Entry.stateLabel(): String? = when {
    watchlist -> "Quiero verla"
    liked -> "La vi · me gustó"
    disliked && seen -> "La vi · no me gustó"
    disliked -> "No me gusta"
    seen -> "La vi"
    else -> null
}

/** Título en idioma original con la traducción entre paréntesis. */
object TitleFormat {
    private val accents = Regex("\\p{InCombiningDiacriticalMarks}+")

    /** Sin acentos, signos ni mayúsculas: «Amélie» y «Amelie» cuentan como el mismo título. */
    private fun key(text: String) = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        .replace(accents, "").lowercase().filter { it.isLetterOrDigit() }

    /** «El arte de viajar (The Art of Travel)» -> «El arte de viajar»: TMDB a veces ya trae el original entre paréntesis. */
    private val trailingParens = Regex("""^(.*?)\s*\(([^()]+)\)\s*$""")

    private fun withoutOriginalSuffix(title: String, originalKey: String): String {
        val m = trailingParens.matchEntire(title) ?: return title
        return if (key(m.groupValues[2]) == originalKey && m.groupValues[1].isNotBlank()) m.groupValues[1].trim() else title
    }

    fun display(title: String, original: String?): String {
        val o = original?.trim().orEmpty()
        val t = title.trim()
        if (o.isEmpty() || t.isEmpty()) return title.ifBlank { o }
        val ko = key(o)
        val translated = withoutOriginalSuffix(t, ko)
        val kt = key(translated)
        return if (ko.isNotEmpty() && ko == kt) { if (translated != t) o else title } else "$o ($translated)"
    }
}
