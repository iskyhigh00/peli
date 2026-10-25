@file:OptIn(ExperimentalTextApi::class)

package com.peli.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.peli.app.R

private fun inter(weight: FontWeight) = Font(
    resId = R.font.inter,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Inter Variable embebida en res/font (no usa fuentes del sistema). */
val InterFamily = FontFamily(
    inter(FontWeight.Normal),
    inter(FontWeight.Medium),
    inter(FontWeight.SemiBold),
    inter(FontWeight.Bold),
    inter(FontWeight.ExtraBold),
)

private fun style(
    weight: FontWeight,
    size: Int,
    line: Int,
    tracking: TextUnit = 0.sp,
) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking,
)

val PeliTypography = Typography(
    displayLarge = style(FontWeight.ExtraBold, 57, 64, (-0.03).em),
    displayMedium = style(FontWeight.ExtraBold, 45, 52, (-0.025).em),
    displaySmall = style(FontWeight.Bold, 36, 44, (-0.02).em),
    headlineLarge = style(FontWeight.Bold, 32, 40, (-0.02).em),
    headlineMedium = style(FontWeight.Bold, 28, 36, (-0.015).em),
    headlineSmall = style(FontWeight.Bold, 24, 32, (-0.01).em),
    titleLarge = style(FontWeight.SemiBold, 22, 28),
    titleMedium = style(FontWeight.SemiBold, 16, 24, 0.01.em),
    titleSmall = style(FontWeight.SemiBold, 14, 20, 0.01.em),
    bodyLarge = style(FontWeight.Normal, 16, 24, 0.01.em),
    bodyMedium = style(FontWeight.Normal, 14, 20, 0.01.em),
    bodySmall = style(FontWeight.Normal, 12, 16, 0.02.em),
    labelLarge = style(FontWeight.SemiBold, 14, 20, 0.02.em),
    labelMedium = style(FontWeight.Medium, 12, 16, 0.04.em),
    labelSmall = style(FontWeight.Medium, 11, 16, 0.04.em),
)
