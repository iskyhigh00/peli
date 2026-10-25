package com.peli.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * extraLarge (28) = cards grandes y barra flotante, large (20) = cards medianas,
 * medium (16) = botones y campos de texto.
 */
val PeliShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
