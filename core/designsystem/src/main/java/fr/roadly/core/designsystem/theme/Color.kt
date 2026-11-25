package fr.roadly.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Luminance relative maximale de [RoadlyColorScheme.brand] pour un texte blanc à 3:1 au moins (D3). */
private const val ON_BRAND_WHITE_MAX_LUMINANCE = 0.30f

/** Texte sombre sur les teintes claires du cockpit (ambre, menthe…) : même encre que le texte principal du jour. */
private val ON_BRAND_DARK = Color(0xFF0E1428)

/** Texte posé sur le magenta de nuit (teinte au choix) : le blanc n'y tient pas le contraste. */
private val ON_MAGENTA_NIGHT = Color(0xFF1B0820)

/**
 * Palette Roadly, « nuit bleue » : des panneaux bleu nuit posés sur la carte, un bleu vif pour tout ce qui guide.
 * - [brand] : bleu de marque (tracé, distance de manœuvre, sélection, boutons de navigation, tuiles d'icônes) ;
 * - [accent] : ambre réservé aux appels à l'action et au signalement (démarrer, essayer, confirmer) ;
 * - [danger] : rouge franc (arrêter, vigilance active) ;
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
    /** Façades des bâtiments extrudés (guidage 3D). */
    val buildings3d: Color,
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

    /**
     * Texte posé sur un aplat [brand] plein (panneau de direction D3) : [onBrand] (blanc) tant qu'il garde un contraste d'au moins 3:1
     * avec la teinte du cockpit, sinon un texte sombre ([ON_BRAND_DARK]). Suit [withTint] puisque calculé depuis [brand].
     * Seuil : blanc à 3:1 ⇔ (1 + 0,05) / (L + 0,05) ≥ 3 ⇔ luminance relative L ≤ 0,30 ; au-delà (ambre, menthe, océan, corail de nuit,
     * ambre de jour), le blanc tombe sous 3:1 et le texte sombre dépasse 6:1.
     */
    val onBrandAuto: Color get() = if (brand.luminance() <= ON_BRAND_WHITE_MAX_LUMINANCE) onBrand else ON_BRAND_DARK

    /** Teinte du cockpit (personnalisation) : remplace la couleur de marque et ses dérivés. */
    fun withTint(tint: CockpitTint): RoadlyColorScheme {
        val (b, bright) = if (isDark) tint.night else tint.day
        // Texte clair sur toutes les teintes, sauf le magenta de nuit, trop lumineux pour du blanc.
        return copy(brand = b, brandBright = bright, routeActive = bright, onBrand = if (tint == CockpitTint.MAGENTA && isDark) ON_MAGENTA_NIGHT else Color.White)
    }
}

/**
 * Teintes de cockpit proposées (L10 : personnalisation). [AZUR] est la teinte par défaut : c'est celle des palettes
 * [RoadlyColors.Night] et [RoadlyColors.Day], et le réglage ne l'enregistre pas (clé absente).
 */
enum class CockpitTint(val night: Pair<Color, Color>, val day: Pair<Color, Color>) {
    AZUR(Color(0xFF3A84F7) to Color(0xFF73AEFF), Color(0xFF1F63E0) to Color(0xFF3A84F7)),
    VIOLET(Color(0xFF8B5CF6) to Color(0xFFB794FF), Color(0xFF6D3FE0) to Color(0xFF8B5CF6)),
    MAGENTA(Color(0xFFF03CDC) to Color(0xFFFF79EE), Color(0xFFD01FC0) to Color(0xFFF03CDC)),
    MINT(Color(0xFF22C58B) to Color(0xFF5EE3B0), Color(0xFF13A070) to Color(0xFF22C58B)),
    CORAL(Color(0xFFFF5C7A) to Color(0xFFFF8FA3), Color(0xFFE04366) to Color(0xFFFF5C7A)),
    AMBER(Color(0xFFF5A524) to Color(0xFFFFC65C), Color(0xFFD98B00) to Color(0xFFF5A524)),
}

object RoadlyColors {
    val Night = RoadlyColorScheme(
        background = Color(0xFF070A18),
        surface = Color(0xFF0E1326),
        surfaceElevated = Color(0xFF182040),
        glass = Color(0xF20F142B),
        glassBorder = Color(0x385F86D6),
        glassHighlight = Color(0x147FB2FF),
        textPrimary = Color(0xFFF5F8FF),
        textSecondary = Color(0xFFA9B4CC),
        textTertiary = Color(0xFF7C88A3),
        brand = Color(0xFF3A84F7),
        brandBright = Color(0xFF73AEFF),
        onBrand = Color(0xFFFFFFFF),
        accent = Color(0xFFF5A524),
        onAccent = Color(0xFF1A1200),
        routeActive = Color(0xFF4D9BFF),
        routeInactive = Color(0xFF55627F),
        routeCasing = Color(0xFF050814),
        buildings3d = Color(0xFF2A3A68),
        danger = Color(0xFFFF4B4B),
        warning = Color(0xFFFFC43D),
        success = Color(0xFF34D399),
        speedRing = Color(0xFFE0262E),
        isDark = true,
    )
    val Day = RoadlyColorScheme(
        background = Color(0xFFF3F5FA),
        surface = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFFFFFFF),
        glass = Color(0xE6FFFFFF),
        glassBorder = Color(0x1A101A30),
        glassHighlight = Color(0x80FFFFFF),
        textPrimary = Color(0xFF0E1428),
        textSecondary = Color(0xFF55607A),
        textTertiary = Color(0xFF8892A8),
        brand = Color(0xFF1F63E0),
        brandBright = Color(0xFF3A84F7),
        onBrand = Color(0xFFFFFFFF),
        accent = Color(0xFFE08A00),
        onAccent = Color(0xFFFFFFFF),
        routeActive = Color(0xFF2F7BF6),
        routeInactive = Color(0xFFB3BACB),
        routeCasing = Color(0xFFFFFFFF),
        buildings3d = Color(0xFFD5DAE6),
        danger = Color(0xFFE0303A),
        // Ambre foncé : texte et icônes sur fond clair (contraste 5,1:1 sur blanc ; 0xFFD99A00 ne donnait que 2,4:1).
        warning = Color(0xFF9A6200),
        success = Color(0xFF13A070),
        speedRing = Color(0xFFD7263D),
        isDark = false,
    )
}

val LocalRoadlyColors = staticCompositionLocalOf { RoadlyColors.Night }
val LocalDrivingMode = staticCompositionLocalOf { false }
/**
 * Échelle de l'interface selon la largeur de l'écran : 1 à partir de [UiScale.REFERENCE_WIDTH_DP], réduite en dessous
 * (jusqu'à [UiScale.MIN]) pour que tout tienne sur les téléphones étroits. La taille de police choisie par l'utilisateur
 * (sp) s'y ajoute : on ne la neutralise jamais.
 */
val LocalUiScale = staticCompositionLocalOf { 1f }

object UiScale {
    /** Largeur de référence (dp) de la maquette : téléphone Android courant. */
    const val REFERENCE_WIDTH_DP = 400f
    const val MIN = 0.85f
    fun forWidth(widthDp: Int): Float = (widthDp / REFERENCE_WIDTH_DP).coerceIn(MIN, 1f)
}

/**
 * Place réduite : écran étroit (< 380 dp) ou grande police système (> 115 %). Les écrans masquent alors les libellés
 * secondaires plutôt que de couper le texte principal.
 */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun isCompactSpace(): Boolean = androidx.compose.ui.platform.LocalConfiguration.current.let { it.screenWidthDp < 380 || it.fontScale > 1.15f }

/** Dimension mise à l'échelle de l'écran ([LocalUiScale]). */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun androidx.compose.ui.unit.Dp.scaled(): androidx.compose.ui.unit.Dp = this * LocalUiScale.current

/** L'activité est en mode image dans l'image (petite fenêtre) : les écrans affichent une version réduite. */
val LocalPictureInPicture = androidx.compose.runtime.compositionLocalOf { false }
