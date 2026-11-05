package com.peli.app.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.peli.app.R
import com.peli.app.data.Pet
import kotlinx.coroutines.delay

/** Las calcomanías de Chuba y Pepa (dos series de 10). Se muestran de una en una y van cambiando. */
object Stickers {
    private val chubaAll = listOf(
        R.drawable.stk_a01, R.drawable.stk_a05, R.drawable.stk_a07, R.drawable.stk_a10,
        R.drawable.stk_b01, R.drawable.stk_b05, R.drawable.stk_b07, R.drawable.stk_b10,
        R.drawable.stk_c11, R.drawable.stk_c14, R.drawable.stk_c16, R.drawable.stk_c19,
        R.drawable.stk_c21, R.drawable.stk_c24, R.drawable.stk_c26, R.drawable.stk_c28,
    )
    private val pepaAll = listOf(
        R.drawable.stk_a02, R.drawable.stk_a04, R.drawable.stk_a06,
        R.drawable.stk_b02, R.drawable.stk_b04, R.drawable.stk_b06,
        R.drawable.stk_c12, R.drawable.stk_c15, R.drawable.stk_c17, R.drawable.stk_c20,
        R.drawable.stk_c22, R.drawable.stk_c25, R.drawable.stk_c27, R.drawable.stk_c29,
    )
    private val bothAll = listOf(
        R.drawable.stk_a03, R.drawable.stk_a08, R.drawable.stk_b03, R.drawable.stk_b08,
        R.drawable.stk_c13, R.drawable.stk_c18, R.drawable.stk_c23, R.drawable.stk_c30,
    )

    // En otro orden cada vez que se abre la app, para que no salgan siempre las mismas primero.
    val chuba = chubaAll.shuffled()
    val pepa = pepaAll.shuffled()
    val both = bothAll.shuffled()

    /** Todas, alternando a una, a la otra y a las dos juntas. */
    val all: List<Int> = buildList {
        for (i in 0 until maxOf(chuba.size, pepa.size)) {
            if (i < chuba.size) add(chuba[i])
            if (i < pepa.size) add(pepa[i])
            if (i % 2 == 1 && i / 2 < both.size) add(both[i / 2])
        }
    }

    fun of(pet: Pet) = if (pet == Pet.Chuba) chuba else pepa
}

/** Una calcomanía a la vez, cambiando cada [periodMs] con un fundido suave. [offset] reparte dónde empieza cada una. */
@Composable
fun RotatingSticker(ids: List<Int>, modifier: Modifier = Modifier, periodMs: Long = 14_000, offset: Int = 0) {
    var step by remember { mutableIntStateOf(offset) }
    LaunchedEffect(ids) {
        while (true) {
            delay(periodMs)
            step++
        }
    }
    Crossfade(targetState = ids[Math.floorMod(step, ids.size)], animationSpec = tween(700), label = "sticker") { res ->
        Image(painterResource(res), contentDescription = null, contentScale = ContentScale.Fit, modifier = modifier)
    }
}
