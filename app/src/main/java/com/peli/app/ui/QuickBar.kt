package com.peli.app.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.dp
import com.peli.app.ui.components.ScreenHorizontalPadding

/**
 * Barra de filtros que va apareciendo a medida que se desliza la lista hacia arriba (aunque se esté lejos del
 * principio): un roce mínimo solo asoma un pedazo, y para verla completa hay que seguir deslizando. Se esconde igual
 * de gradual al volver a bajar. Al estar en la parte superior no hace falta: ahí ya se ven los filtros.
 */
@Stable
class QuickBar internal constructor(private val listState: LazyListState) {
    /** Alto de la barra ya medido (px) y cuánto de ella se ve ahora (0..alto). */
    internal var heightPx by mutableFloatStateOf(0f)
    private var shownPx by mutableFloatStateOf(0f)

    private val nearTop: Boolean
        get() = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < NEAR_TOP_PX

    /** Píxeles visibles de la barra. */
    val shown: Float get() = if (nearTop) 0f else shownPx.coerceIn(0f, heightPx)

    /** Se engancha a la lista con `Modifier.nestedScroll(connection)`; solo observa el desplazamiento, no consume nada. */
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val dy = available.y
            if (dy == 0f) return Offset.Zero
            // Solo el dedo hace aparecer la barra; un impulso (fling) tras un roce no la abre de golpe, pero sí la esconde.
            if (dy > 0f && source != NestedScrollSource.UserInput) return Offset.Zero
            shownPx = (shownPx + dy).coerceIn(0f, heightPx)
            return Offset.Zero
        }
    }

    private companion object {
        const val NEAR_TOP_PX = 24
    }
}

@Composable
fun rememberQuickBar(listState: LazyListState): QuickBar = remember(listState) { QuickBar(listState) }

/** Dibuja la barra fijada arriba del contenido, asomando según [QuickBar.shown]. */
@Composable
fun BoxScope.QuickBarOverlay(bar: QuickBar, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(with(density) { bar.shown.toDp() })
            .clipToBounds(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Bottom, unbounded = true)
                .onSizeChanged { bar.heightPx = it.height.toFloat() },
        ) {
            Column(Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp)) { content() }
        }
    }
}
