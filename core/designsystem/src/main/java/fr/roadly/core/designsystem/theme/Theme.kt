package fr.roadly.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** Agrandissement du texte en mode conduite. */
const val DRIVING_TEXT_SCALE = 1.15f

/**
 * Thème Roadly. [drivingMode] agrandit la typographie (×[DRIVING_TEXT_SCALE]) et les cibles tactiles (56 dp) ;
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
    // Écran étroit : interface réduite (≥ 0,85). Mode conduite : texte un peu plus grand (×1,15 ; la police arrondie est déjà très grasse).
    val uiScale = UiScale.forWidth(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp)
    CompositionLocalProvider(LocalRoadlyColors provides colors, LocalDrivingMode provides drivingMode, LocalUiScale provides uiScale) {
        MaterialTheme(colorScheme = material, typography = roadlyTypography((if (drivingMode) DRIVING_TEXT_SCALE else 1f) * uiScale), content = content)
    }
}
