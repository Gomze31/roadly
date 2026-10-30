package fr.roadly.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Palette Roadly (identité propre, inspirée de l'organisation d'un cockpit GPS premium) :
 * - [brand] : violet de marque (tracé, sélection, boutons de navigation, tuiles d'icônes) ;
 * - [accent] : orange chaud réservé aux appels à l'action (démarrer, essayer, confirmer) ;
 * - [surface] : panneau opaque (listes, réglages, feuilles) ; [glass] : panneau translucide posé sur la carte.
 */
@Immutable
data class RoadlyColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val glass: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val brand: Color,
    val brandBright: Color,
    val onBrand: Color,
    val accent: Color,
    val onAccent: Color,
    val routeActive: Color,
    val routeInactive: Color,
    val routeCasing: Color,
    val danger: Color,
    val warning: Color,
    val success: Color,
    val speedRing: Color,
    val isDark: Boolean,
) {
    /** Fond doux teinté marque (tuiles d'icônes, lignes sélectionnées). */
    val brandSoft: Color get() = brand.copy(alpha = if (isDark) 0.22f else 0.14f)
    val accentSoft: Color get() = accent.copy(alpha = if (isDark) 0.20f else 0.14f)
    val dangerSoft: Color get() = danger.copy(alpha = if (isDark) 0.22f else 0.14f)
    val divider: Color get() = glassBorder

    /** Teinte du cockpit (personnalisation) : remplace la couleur de marque et ses dérivés. */
    fun withTint(tint: CockpitTint): RoadlyColorScheme {
        val (b, bright) = if (isDark) tint.night else tint.day
        return copy(brand = b, brandBright = bright, routeActive = bright)
    }
}

/** Teintes de cockpit proposées (L10 : personnalisation). La première est la teinte par défaut. */
enum class CockpitTint(val night: Pair<Color, Color>, val day: Pair<Color, Color>) {
    VIOLET(Color(0xFF8B5CF6) to Color(0xFFB794FF), Color(0xFF6D3FE0) to Color(0xFF8B5CF6)),
    OCEAN(Color(0xFF2F9BFF) to Color(0xFF6DC1FF), Color(0xFF1E6FD9) to Color(0xFF2F9BFF)),
    MINT(Color(0xFF22C58B) to Color(0xFF5EE3B0), Color(0xFF13A070) to Color(0xFF22C58B)),
    CORAL(Color(0xFFFF5C7A) to Color(0xFFFF8FA3), Color(0xFFE04366) to Color(0xFFFF5C7A)),
    AMBER(Color(0xFFF5A524) to Color(0xFFFFC65C), Color(0xFFD98B00) to Color(0xFFF5A524)),
}

object RoadlyColors {
    val Night = RoadlyColorScheme(
        background = Color(0xFF0E0B14),
        surface = Color(0xFF17121F),
        surfaceElevated = Color(0xFF1F1829),
        glass = Color(0xE6171221),
        glassBorder = Color(0x1AFFFFFF),
        glassHighlight = Color(0x0CFFFFFF),
        textPrimary = Color(0xFFF4F1F8),
        textSecondary = Color(0xFFA79FB5),
        textTertiary = Color(0xFF6E6680),
        brand = Color(0xFF8B5CF6),
        brandBright = Color(0xFFB794FF),
        onBrand = Color(0xFFFFFFFF),
        accent = Color(0xFFF5A524),
        onAccent = Color(0xFF1A1200),
        routeActive = Color(0xFFB794FF),
        routeInactive = Color(0xFF5A5470),
        routeCasing = Color(0xFF0E0B14),
        danger = Color(0xFFFF4D6D),
        warning = Color(0xFFFFC43D),
        success = Color(0xFF34D399),
        speedRing = Color(0xFFE0263C),
        isDark = true,
    )
    val Day = RoadlyColorScheme(
        background = Color(0xFFF5F3F8),
        surface = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFFFFFFF),
        glass = Color(0xE6FFFFFF),
        glassBorder = Color(0x1A1A1030),
        glassHighlight = Color(0x80FFFFFF),
        textPrimary = Color(0xFF14101C),
        textSecondary = Color(0xFF5E5870),
        textTertiary = Color(0xFF938CA3),
        brand = Color(0xFF6D3FE0),
        brandBright = Color(0xFF8B5CF6),
        onBrand = Color(0xFFFFFFFF),
        accent = Color(0xFFE08A00),
        onAccent = Color(0xFFFFFFFF),
        routeActive = Color(0xFF8B5CF6),
        routeInactive = Color(0xFFB9B3C8),
        routeCasing = Color(0xFFFFFFFF),
        danger = Color(0xFFE0304F),
        warning = Color(0xFFD99A00),
        success = Color(0xFF13A070),
        speedRing = Color(0xFFD7263D),
        isDark = false,
    )
}

val LocalRoadlyColors = staticCompositionLocalOf { RoadlyColors.Night }
val LocalDrivingMode = staticCompositionLocalOf { false }
