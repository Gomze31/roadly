package fr.roadly.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class RoadlyColorScheme(
    val background: Color,
    val glass: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val routeActive: Color,
    val routeInactive: Color,
    val danger: Color,
    val success: Color,
    val isDark: Boolean,
)

object RoadlyColors {
    val Night = RoadlyColorScheme(
        background = Color(0xFF0B0F14),
        glass = Color(0xB8161C24),
        glassBorder = Color(0x14FFFFFF),
        glassHighlight = Color(0x0AFFFFFF),
        textPrimary = Color(0xFFF2F5F7),
        textSecondary = Color(0xFF9AA6B2),
        accent = Color(0xFFFFB547),
        onAccent = Color(0xFF1A1200),
        routeActive = Color(0xFF4FD1E0),
        routeInactive = Color(0xFF5B6774),
        danger = Color(0xFFFF6B6B),
        success = Color(0xFF5EDC9A),
        isDark = true,
    )
    val Day = RoadlyColorScheme(
        background = Color(0xFFF4F6F8),
        glass = Color(0xC7FFFFFF),
        glassBorder = Color(0x1A000000),
        glassHighlight = Color(0x66FFFFFF),
        textPrimary = Color(0xFF0F1620),
        textSecondary = Color(0xFF5B6774),
        accent = Color(0xFFE69B2E),
        onAccent = Color(0xFF1A1200),
        routeActive = Color(0xFF2FB4C4),
        routeInactive = Color(0xFF9AA6B2),
        danger = Color(0xFFE05252),
        success = Color(0xFF3FBF7F),
        isDark = false,
    )
}

val LocalRoadlyColors = staticCompositionLocalOf { RoadlyColors.Night }
val LocalDrivingMode = staticCompositionLocalOf { false }
