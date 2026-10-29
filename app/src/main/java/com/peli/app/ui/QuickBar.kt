package com.peli.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxScope
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
 * Barra de filtros que reaparece al instante en cuanto se desliza la lista hacia arriba (aunque se esté lejos del
 * principio) y se esconde al seguir bajando. Al estar en la parte superior no hace falta: ahí ya se ven los filtros.
 */
@Stable
class QuickBar internal constructor(private val listState: LazyListState) {
    private var goingUp by mutableStateOf(false)

    val visible: Boolean
        get() = goingUp && !(listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < NEAR_TOP_PX)

    /** Se engancha a la lista con `Modifier.nestedScroll(connection)`; solo observa la dirección, no consume nada. */
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y > DEAD_ZONE_PX) goingUp = true else if (available.y < -DEAD_ZONE_PX) goingUp = false
            return Offset.Zero
        }
    }

    private companion object {
        const val DEAD_ZONE_PX = 3f
        const val NEAR_TOP_PX = 24
    }
}

@Composable
fun rememberQuickBar(listState: LazyListState): QuickBar = remember(listState) { QuickBar(listState) }

/** Dibuja la barra fijada arriba del contenido mientras [QuickBar.visible]. */
@Composable
fun BoxScope.QuickBarOverlay(bar: QuickBar, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = bar.visible,
        modifier = Modifier.align(Alignment.TopCenter),
        enter = slideInVertically(tween(150)) { -it } + fadeIn(tween(150)),
        exit = slideOutVertically(tween(150)) { -it } + fadeOut(tween(150)),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = ScreenHorizontalPadding, vertical = 8.dp)) { content() }
        }
    }
}
