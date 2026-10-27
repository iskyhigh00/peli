package com.peli.app.data

import android.content.Context

/**
 * Qué se le entregó al usuario y cuándo, para no repetir películas hasta que pase tiempo y rondas.
 * [round]/[at]: última entrega; [visible]: si llegó a verse en pantalla desde entonces; [skips]: veces omitida.
 */
data class Exposure(val id: Int, val round: Long, val at: Long, val visible: Boolean, val skips: Int)

object ExposureRules {
    /** Nada entregado en las últimas rondas vuelve a salir, aunque falte material. */
    const val HARD_FLOOR_ROUNDS = 5
    private const val DAY_MS = 86_400_000.0

    /** De lo más estricto a lo más permisivo: se usa el primero que deja suficiente material nuevo. */
    val RELAX_LEVELS = listOf(1.0, 0.5, 0.25, 0.1)

    /** Rondas y días que deben pasar (ambos) antes de volver a ofrecerla. */
    fun waits(x: Exposure): Pair<Int, Int> = when {
        x.skips >= 2 -> 200 to 180
        x.skips == 1 -> 150 to 60
        x.visible -> 100 to 30
        else -> 60 to 14
    }

    /** [round] es la ronda de la entrega que se está armando. */
    fun eligible(x: Exposure?, round: Long, now: Long, relax: Double = 1.0): Boolean {
        if (x == null) return true
        val since = round - x.round
        if (since < HARD_FLOOR_ROUNDS) return false
        val (rounds, days) = waits(x)
        return since >= rounds * relax && (now - x.at) / DAY_MS >= days * relax
    }

    fun encode(items: Collection<Exposure>): String =
        items.joinToString(";") { "${it.id},${it.round},${it.at},${if (it.visible) 1 else 0},${it.skips}" }

    fun decode(text: String): Map<Int, Exposure> {
        val out = LinkedHashMap<Int, Exposure>()
        for (part in text.split(';')) {
            val f = part.split(',')
            if (f.size != 5) continue
            val id = f[0].toIntOrNull() ?: continue
            val round = f[1].toLongOrNull() ?: continue
            val at = f[2].toLongOrNull() ?: continue
            out[id] = Exposure(id, round, at, f[3] == "1", f[4].toIntOrNull() ?: 0)
        }
        return out
    }

    /** Si hay más de [capacity], se olvidan primero las entregas más antiguas (conservando las omitidas). */
    fun trim(items: Map<Int, Exposure>, capacity: Int = 4000): Map<Int, Exposure> {
        if (items.size <= capacity) return items
        val keep = items.values.sortedWith(
            compareByDescending<Exposure> { it.skips > 0 }.thenByDescending { it.at }.thenBy { it.id },
        ).take(capacity)
        return keep.associateBy { it.id }
    }

    /** Convierte el historial anterior (solo ids, de más antiguo a más reciente) en entregas de hace unos días. */
    fun migrate(oldIds: List<Int>, round: Long, now: Long, perRound: Int = 40): Map<Int, Exposure> {
        val out = LinkedHashMap<Int, Exposure>()
        oldIds.forEachIndexed { index, id ->
            val fromEnd = oldIds.size - 1 - index
            out[id] = Exposure(id, round - fromEnd / perRound, now - 5L * 86_400_000L, visible = false, skips = 0)
        }
        return out
    }
}

/** Guarda las entregas en el teléfono (SharedPreferences). Las marcas explícitas no pasan por aquí. */
class ExposureStore(context: Context) {
    private val prefs = context.getSharedPreferences("peli_exposure", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("peli_feed", Context.MODE_PRIVATE)

    @Synchronized fun round(): Long = prefs.getLong("round", 0L)

    @Synchronized fun snapshot(): Map<Int, Exposure> {
        migrateIfNeeded()
        return ExposureRules.decode(prefs.getString("items", "").orEmpty())
    }

    private fun migrateIfNeeded() {
        if (prefs.getBoolean("migrated", false)) return
        val old = legacy.getString("shown", "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
        val migrated = ExposureRules.migrate(old, round(), System.currentTimeMillis())
        prefs.edit().putString("items", ExposureRules.encode(migrated.values)).putBoolean("migrated", true).apply()
    }

    private fun save(items: Map<Int, Exposure>, round: Long = round()) {
        prefs.edit().putString("items", ExposureRules.encode(ExposureRules.trim(items).values)).putLong("round", round).apply()
    }

    /** Registra una entrega completada: es una ronda nueva. */
    @Synchronized fun deliver(ids: List<Int>, round: Long, now: Long) {
        val items = LinkedHashMap(snapshot())
        for (id in ids) items[id] = Exposure(id, round, now, visible = false, skips = items[id]?.skips ?: 0)
        save(items, round)
    }

    /** Película que llegó a verse en pantalla (solo si ya estaba registrada como entregada). */
    @Synchronized fun markVisible(ids: Collection<Int>) {
        val items = LinkedHashMap(snapshot())
        var changed = false
        for (id in ids) {
            val x = items[id] ?: continue
            if (!x.visible) {
                items[id] = x.copy(visible = true)
                changed = true
            }
        }
        if (changed) save(items)
    }

    /** "Omitir" en Descubrir: no es rechazo, pero tarda mucho en volver. */
    @Synchronized fun markSkipped(id: Int, now: Long) {
        val items = LinkedHashMap(snapshot())
        val x = items[id]
        items[id] = Exposure(id, round(), now, visible = true, skips = (x?.skips ?: 0) + 1)
        save(items)
    }

    /** Descubrir muestra una tarjeta: queda registrada para no repetirse pronto. */
    @Synchronized fun markShown(id: Int, now: Long) {
        val items = LinkedHashMap(snapshot())
        val x = items[id]
        items[id] = Exposure(id, round(), now, visible = true, skips = x?.skips ?: 0)
        save(items)
    }

    /** Cada 20 tarjetas respondidas en Descubrir cuenta como una ronda. */
    @Synchronized fun countDiscoverAnswer() {
        val n = prefs.getInt("discover_answers", 0) + 1
        if (n >= 20) prefs.edit().putInt("discover_answers", 0).putLong("round", round() + 1).apply()
        else prefs.edit().putInt("discover_answers", n).apply()
    }
}
