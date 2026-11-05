package com.peli.app.reminders

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.peli.app.data.Pet
import com.peli.app.ui.Stickers

/** Calcomanía para la imagen grande de un aviso: cambia en cada aviso (según [seed]) en vez de repetir siempre la misma. */
object NotifySticker {
    /** [pet] null = cualquiera de las dos o las dos juntas. */
    fun bitmap(context: Context, seed: Long, pet: Pet? = null): Bitmap? = runCatching {
        val pool = if (pet != null) Stickers.of(pet) + Stickers.both else Stickers.all
        val res = pool[Math.floorMod(seed, pool.size.toLong()).toInt()]
        val raw = BitmapFactory.decodeResource(context.resources, res) ?: return null
        val size = 256
        val scale = size.toFloat() / maxOf(raw.width, raw.height)
        if (scale >= 1f) raw else Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
    }.getOrNull()
}
