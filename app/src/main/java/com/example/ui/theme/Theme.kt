package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = RadarTealBright,
    onPrimary = RadarMidnight,
    primaryContainer = RadarTealContainerDark,
    onPrimaryContainer = RadarTealBright,
    secondary = RadarSky,
    onSecondary = RadarMidnight,
    secondaryContainer = RadarCobaltContainerDark,
    onSecondaryContainer = RadarSky,
    tertiary = RadarAmberBright,
    onTertiary = RadarMidnight,
    background = RadarMidnight,
    onBackground = DarkOnSurface,
    surface = RadarSurfaceDark,
    onSurface = DarkOnSurface,
    surfaceVariant = RadarSurfaceVariantDark,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = ErrorDark,
    onError = RadarMidnight,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = RadarTeal,
    onPrimary = Color.White,
    primaryContainer = RadarTealContainerLight,
    onPrimaryContainer = Color(0xFF003831),
    secondary = RadarCobalt,
    onSecondary = Color.White,
    secondaryContainer = RadarCobaltContainerLight,
    onSecondaryContainer = Color(0xFF102A6B),
    tertiary = RadarAmber,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = ErrorLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = ErrorLight
)

val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun WhoIsNearMeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
