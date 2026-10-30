package com.peli.app.data

import android.content.Context

/**
 * Qué se le entregó al usuario y cuándo, para no repetir películas hasta que pase tiempo y rondas.
 * Tres eventos independientes: entrega ([deliveredRound]/[deliveredAt]), visibilidad ([visibleRound]/[visibleAt],
 * -1 = nunca se vio desde esa entrega) y omisión ([skipRound]/[skipAt], [skips] veces). Un evento tardío no
 * acorta el plazo de otro: para volver a ofrecerla deben haber vencido TODOS los que existan.
 */
data class Exposure(
    val id: Int,
    val deliveredRound: Long,
    val deliveredAt: Long,
    val visibleRound: Long = -1,
    val visibleAt: Long = -1,
    val skipRound: Long = -1,
    val skipAt: Long = -1,
    val skips: Int = 0,
) {
    val hasVisible get() = visibleRound >= 0
}

object ExposureRules {
    /** Nada entregado en las últimas rondas vuelve a salir. */
    const val HARD_FLOOR_ROUNDS = 5
    private const val DAY_MS = 86_400_000.0

    private fun due(round: Long, now: Long, atRound: Long, atTime: Long, rounds: Int, days: Int) =
        round - atRound >= rounds && (now - atTime) / DAY_MS >= days

    /**
     * [round] es la ronda de la entrega que se está armando. Plazos (rondas Y días, desde cada evento):
     * entregada y nunca vista 60/14; vista 100/30; omitida una vez 150/60; omitida dos o más 200/180.
     * No hay relajación automática: si falta material nuevo se buscan otras fuentes o la lista queda más corta.
     */
    fun eligible(x: Exposure?, round: Long, now: Long): Boolean {
        if (x == null) return true
        if (round - x.deliveredRound < HARD_FLOOR_ROUNDS) return false
        if (!x.hasVisible && !due(round, now, x.deliveredRound, x.deliveredAt, 60, 14)) return false
        if (x.hasVisible && !due(round, now, x.visibleRound, x.visibleAt, 100, 30)) return false
        if (x.skips >= 2 && !due(round, now, x.skipRound, x.skipAt, 200, 180)) return false
        if (x.skips == 1 && !due(round, now, x.skipRound, x.skipAt, 150, 60)) return false
        return true
    }

    /** Nivel de prioridad entre las elegibles: 0 nunca entregadas, 1 entregadas sin respuesta, 2 omitidas. */
    fun tier(x: Exposure?): Int = when {
        x == null -> 0
        x.skips > 0 -> 2
        else -> 1
    }

    fun encode(items: Collection<Exposure>): String = items.joinToString(";") {
        "${it.id},${it.deliveredRound},${it.deliveredAt},${it.visibleRound},${it.visibleAt},${it.skipRound},${it.skipAt},${it.skips}"
    }

    /** Lee el formato nuevo (8 campos) y el anterior (5 campos: id, ronda, hora, visible, omisiones). */
    fun decode(text: String): Map<Int, Exposure> {
        val out = LinkedHashMap<Int, Exposure>()
        for (part in text.split(';')) {
            val f = part.split(',')
            val id = f.getOrNull(0)?.toIntOrNull() ?: continue
            when (f.size) {
                8 -> {
                    val nums = f.drop(1).take(6).map { it.toLongOrNull() ?: return@map null }
                    if (nums.any { it == null }) continue
                    out[id] = Exposure(id, nums[0]!!, nums[1]!!, nums[2]!!, nums[3]!!, nums[4]!!, nums[5]!!, f[7].toIntOrNull() ?: 0)
                }
                5 -> {
                    val round = f[1].toLongOrNull() ?: continue
                    val at = f[2].toLongOrNull() ?: continue
                    val skips = f[4].toIntOrNull() ?: 0
                    out[id] = Exposure(
                        id, round, at,
                        visibleRound = if (f[3] == "1") round else -1, visibleAt = if (f[3] == "1") at else -1,
                        skipRound = if (skips > 0) round else -1, skipAt = if (skips > 0) at else -1, skips = skips,
                    )
                }
            }
        }
        return out
    }

    /**
     * Limpia solo lo que YA no protege nada (todos sus plazos vencieron: equivale a no haberla mostrado nunca).
     * Si pasa de [capacity] pero todo sigue protegido, no se olvida nada: el límite es blando.
     */
    fun trim(items: Map<Int, Exposure>, round: Long, now: Long, capacity: Int = 4000): Map<Int, Exposure> {
        if (items.size <= capacity) return items
        val expired = items.values.filter { eligible(it, round, now) }.sortedWith(compareBy({ it.deliveredAt }, { it.id }))
        var excess = items.size - capacity
        val drop = HashSet<Int>()
        for (e in expired) {
            if (excess <= 0) break
            drop += e.id
            excess--
        }
        return if (drop.isEmpty()) items else items.filterKeys { it !in drop }
    }

    /** Convierte el historial anterior (solo ids, de más antiguo a más reciente) en entregas de hace unos días. */
    fun migrate(oldIds: List<Int>, round: Long, now: Long, perRound: Int = 40): Map<Int, Exposure> {
        val out = LinkedHashMap<Int, Exposure>()
        oldIds.forEachIndexed { index, id ->
            val fromEnd = oldIds.size - 1 - index
            out[id] = Exposure(id, round - fromEnd / perRound, now - 5L * 86_400_000L)
        }
        return out
    }
}

/** Reparto de las elegibles por niveles de prioridad y selección nivel por nivel. */
object Delivery {
    /** Elegibles (según su memoria) separadas en niveles: 0 nunca entregadas, 1 entregadas sin respuesta, 2 omitidas. */
    fun tiers(candidates: List<Entry>, snapshot: Map<Int, Exposure>, round: Long, now: Long): List<List<Entry>> {
        val eligible = candidates.filter { ExposureRules.eligible(snapshot[it.movieId], round, now) }
        return (0..2).map { t -> eligible.filter { ExposureRules.tier(snapshot[it.movieId]) == t } }
    }

    /**
     * Toma hasta [k] películas: el nivel siguiente solo se usa si el anterior no alcanza. [select] recibe
     * el nivel, cuántas faltan y lo ya elegido (para que la diversidad lo tenga en cuenta).
     */
    fun choose(tiers: List<List<Entry>>, k: Int, select: (List<Entry>, Int, List<Entry>) -> List<Entry>): List<Entry> {
        val out = ArrayList<Entry>()
        for (tier in tiers) {
            if (out.size >= k) break
            if (tier.isEmpty()) continue
            out += select(tier, k - out.size, out.toList())
        }
        return out
    }
}

/** Guarda las entregas en el teléfono (SharedPreferences). Las marcas explícitas no pasan por aquí. */
class ExposureStore(context: Context) {
    private val prefs = context.getSharedPreferences("peli_exposure", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("peli_feed", Context.MODE_PRIVATE)

    @Synchronized fun round(): Long = prefs.getLong("round", 0L)

    /** Copia en memoria de lo guardado: se lee del teléfono una sola vez (antes se decodificaba en cada consulta). */
    private var cache: Map<Int, Exposure>? = null

    @Synchronized fun snapshot(): Map<Int, Exposure> {
        cache?.let { return it }
        migrateIfNeeded()
        return ExposureRules.decode(prefs.getString("items", "").orEmpty()).also { cache = it }
    }

    private fun migrateIfNeeded() {
        if (prefs.getBoolean("migrated", false)) return
        val old = legacy.getString("shown", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
        val migrated = ExposureRules.migrate(old, round(), System.currentTimeMillis())
        prefs.edit().putString("items", ExposureRules.encode(migrated.values)).putBoolean("migrated", true).apply()
    }

    private fun save(items: Map<Int, Exposure>, round: Long = round()) {
        val kept = ExposureRules.trim(items, round + 1, System.currentTimeMillis())
        cache = kept
        prefs.edit().putString("items", ExposureRules.encode(kept.values)).putLong("round", round).apply()
    }

    /** Registra una entrega completada: es una ronda nueva (la visibilidad vuelve a empezar). */
    @Synchronized fun deliver(ids: List<Int>, round: Long, now: Long) {
        val items = LinkedHashMap(snapshot())
        for (id in ids) {
            val old = items[id]
            items[id] = Exposure(id, round, now, skipRound = old?.skipRound ?: -1, skipAt = old?.skipAt ?: -1, skips = old?.skips ?: 0)
        }
        save(items, round)
    }

    /** Película que llegó a verse en pantalla (solo si ya estaba registrada como entregada). */
    @Synchronized fun markVisible(ids: Collection<Int>, now: Long = System.currentTimeMillis()) {
        val items = LinkedHashMap(snapshot())
        var changed = false
        for (id in ids) {
            val x = items[id] ?: continue
            if (!x.hasVisible) {
                items[id] = x.copy(visibleRound = round(), visibleAt = now)
                changed = true
            }
        }
        if (changed) save(items)
    }

    /** Descubrir mostró esta tarjeta (entregada y vista a la vez); se anota una sola vez por presentación. */
    @Synchronized fun markShown(id: Int, now: Long) {
        val items = LinkedHashMap(snapshot())
        val old = items[id]
        items[id] = Exposure(
            id, round(), now, visibleRound = round(), visibleAt = now,
            skipRound = old?.skipRound ?: -1, skipAt = old?.skipAt ?: -1, skips = old?.skips ?: 0,
        )
        save(items)
    }

    /** "Omitir" en Descubrir: no es rechazo, pero tarda mucho en volver. */
    @Synchronized fun markSkipped(id: Int, now: Long) {
        val items = LinkedHashMap(snapshot())
        val old = items[id]
        items[id] = Exposure(
            id, old?.deliveredRound ?: round(), old?.deliveredAt ?: now,
            visibleRound = old?.visibleRound ?: round(), visibleAt = old?.visibleAt ?: now,
            skipRound = round(), skipAt = now, skips = (old?.skips ?: 0) + 1,
        )
        save(items)
    }

    /** Cada 20 tarjetas respondidas en Descubrir cuenta como una ronda; la precarga no avanza rondas. */
    @Synchronized fun countDiscoverAnswer() {
        val n = prefs.getInt("discover_answers", 0) + 1
        if (n >= 20) prefs.edit().putInt("discover_answers", 0).putLong("round", round() + 1).apply()
        else prefs.edit().putInt("discover_answers", n).apply()
    }
}

/** Cursores de página guardados en el teléfono (ver [PageCursors]). */
class PrefsCursors(context: Context) : PageCursors {
    private val prefs = context.getSharedPreferences("peli_cursors", Context.MODE_PRIVATE)
    @Synchronized override fun get(key: String): Int = prefs.getInt(key, 1)
    @Synchronized override fun set(key: String, page: Int) = prefs.edit().putInt(key, page).apply()
}
