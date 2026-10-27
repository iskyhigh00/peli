package com.peli.app.data

/** Lógica pura (sin Room) de cómo cambia MI marca de una película. */
object Marks {
    /**
     * @param current mi marca actual guardada (null si no tengo ninguna).
     * @param base la película que se tocó en pantalla: puede venir de la pareja o de sugerencias, así que
     *  de ella solo se toman los metadatos, nunca sus marcas.
     */
    fun apply(current: Entry?, base: Entry, now: Long, change: (Entry) -> Entry): Entry {
        val start = current ?: base.asUnmarked(OWNER_ME)
        return change(start).copy(owner = OWNER_ME, updatedAt = now)
    }

    /** Estado al que vuelve un "deshacer": la marca anterior tal cual (con su fecha), o ninguna marca. */
    fun restored(base: Entry, previous: Entry?, now: Long): Entry =
        previous?.copy(owner = OWNER_ME) ?: base.asUnmarked(OWNER_ME).copy(updatedAt = now)
}
