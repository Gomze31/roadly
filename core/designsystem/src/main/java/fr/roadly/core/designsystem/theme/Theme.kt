package fr.roadly.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun RoadlyTheme(darkTheme: Boolean, drivingMode: Boolean = false, content: @Composable () -> Unit) {
    val colors = if (darkTheme) RoadlyColors.Night else RoadlyColors.Day
    val material: ColorScheme = if (darkTheme) {
        darkColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.background, surface = colors.background, onBackground = colors.textPrimary, onSurface = colors.textPrimary, secondary = colors.routeActive, error = colors.danger)
    } else {
        lightColorScheme(primary = colors.accent, onPrimary = colors.onAccent, background = colors.background, surface = colors.background, onBackground = colors.textPrimary, onSurface = colors.textPrimary, secondary = colors.routeActive, error = colors.danger)
    }
    CompositionLocalProvider(LocalRoadlyColors provides colors, LocalDrivingMode provides drivingMode) {
        MaterialTheme(colorScheme = material, typography = roadlyTypography(if (drivingMode) 1.3f else 1f), content = content)
    }
}
