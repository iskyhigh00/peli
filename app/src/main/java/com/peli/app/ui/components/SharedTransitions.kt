package com.peli.app.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

@OptIn(ExperimentalSharedTransitionApi::class)
class SharedScopes(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

/** Null en previews y fuera de un SharedTransitionLayout: los modificadores compartidos no hacen nada. */
val LocalShared = compositionLocalOf<SharedScopes?> { null }

/** Elemento compartido (carátula) entre la lista y el detalle. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedPoster(movieId: Int): Modifier {
    val s = LocalShared.current ?: return this
    return with(s.transition) {
        this@sharedPoster.sharedElement(rememberSharedContentState("poster-$movieId"), s.visibility)
    }
}

/** Límites compartidos (título) entre la lista y el detalle. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedTitle(movieId: Int): Modifier {
    val s = LocalShared.current ?: return this
    return with(s.transition) {
        this@sharedTitle.sharedBounds(rememberSharedContentState("title-$movieId"), s.visibility)
    }
}
