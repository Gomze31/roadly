package fr.roadly.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Thème Roadly. [drivingMode] agrandit la typographie (×1,3) et les cibles tactiles (56 dp) ;
 * [tint] remplace la couleur de marque (personnalisation du cockpit).
 */
@Composable
fun RoadlyTheme(
    darkTheme: Boolean,
    drivingMode: Boolean = false,
    tint: CockpitTint = CockpitTint.MAGENTA,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) RoadlyColors.Night else RoadlyColors.Day
    val colors = if (tint == CockpitTint.MAGENTA) base else base.withTint(tint)
    val material: ColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand,
            secondary = colors.accent, onSecondary = colors.onAccent,
            tertiary = colors.brandBright,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated, onSurfaceVariant = colors.textSecondary,
            outline = colors.glassBorder, error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand,
            secondary = colors.accent, onSecondary = colors.onAccent,
            tertiary = colors.brandBright,
            background = colors.background, onBackground = colors.textPrimary,
            surface = colors.surface, onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated, onSurfaceVariant = colors.textSecondary,
            outline = colors.glassBorder, error = colors.danger,
        )
    }
    CompositionLocalProvider(LocalRoadlyColors provides colors, LocalDrivingMode provides drivingMode) {
        MaterialTheme(colorScheme = material, typography = roadlyTypography(if (drivingMode) 1.3f else 1f), content = content)
    }
}
