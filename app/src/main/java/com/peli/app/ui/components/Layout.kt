package com.peli.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxSize

val ScreenHorizontalPadding = 20.dp
val SectionSpacing = 24.dp
val ItemSpacing = 16.dp
val CompactSpacing = 8.dp

/** 20dp horizontal; abajo 80dp para librar la barra flotante + inset de navegación del sistema. */
@Composable
fun screenContentPadding(): PaddingValues {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return PaddingValues(
        start = ScreenHorizontalPadding,
        end = ScreenHorizontalPadding,
        top = CompactSpacing,
        bottom = 80.dp + navInset,
    )
}

/** Scrims de barra de estado y de navegación; su opacidad depende del scroll. */
@Composable
fun SystemBarScrims(topAlpha: Float, bottomAlpha: Float, modifier: Modifier = Modifier) {
    val bg = MaterialTheme.colorScheme.background
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Brush.verticalGradient(listOf(bg.copy(alpha = topAlpha), Color.Transparent))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(Brush.verticalGradient(listOf(Color.Transparent, bg.copy(alpha = bottomAlpha)))),
        )
    }
}
