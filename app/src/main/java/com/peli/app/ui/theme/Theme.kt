package com.peli.app.ui.theme

import android.os.Build
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val DarkScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = PrimaryVariant,
    onPrimaryContainer = Color(0xFF1A0B66),
    secondary = Secondary,
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFF00513C),
    onSecondaryContainer = Color(0xFF7DFFD0),
    tertiary = Tertiary,
    onTertiary = Color(0xFF4A0021),
    tertiaryContainer = Color(0xFF7A2A4A),
    onTertiaryContainer = Color(0xFFFFD9E4),
    background = BackgroundDark,
    onBackground = Color(0xFFEDEBFA),
    surface = SurfaceDark,
    onSurface = Color(0xFFEDEBFA),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFB9B6CC),
    outline = OutlineDark,
    outlineVariant = Color(0xFF2A2A3A),
    error = ErrorDark,
    onError = Color(0xFF3A0000),
)

private val LightScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DEFF),
    onPrimaryContainer = Color(0xFF1F0A80),
    secondary = Color(0xFF00C48C),
    onSecondary = Color(0xFF00382A),
    secondaryContainer = Color(0xFFB8FFE5),
    onSecondaryContainer = Color(0xFF00513C),
    tertiary = Color(0xFFE0417B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9E4),
    onTertiaryContainer = Color(0xFF4A0021),
    background = BackgroundLight,
    onBackground = Color(0xFF16151F),
    surface = SurfaceLight,
    onSurface = Color(0xFF16151F),
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF4E4C63),
    outline = OutlineLight,
    outlineVariant = Color(0xFFD9D6EA),
    error = ErrorLight,
    onError = Color.White,
)

val ColorScheme.isDark: Boolean get() = background.luminance() < 0.5f

/**
 * @param dynamicColor Material You (Android 12+). Por defecto apagado para conservar la paleta de marca;
 * en Android < 12 siempre se usa la paleta manual.
 */
@Composable
fun PeliTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = PeliTypography, shapes = PeliShapes) {
        CompositionLocalProvider(
            LocalIndication provides ripple(color = MaterialTheme.colorScheme.primaryContainer, bounded = true),
            content = content,
        )
    }
}
