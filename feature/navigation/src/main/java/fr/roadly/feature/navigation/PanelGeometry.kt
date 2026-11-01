package fr.roadly.feature.navigation

import kotlin.math.ceil

/**
 * D5 : part de la hauteur de l'écran couverte par un bloc de l'overlay, pour les marges de la caméra :
 * le bloc du bas (Horizon + panneau, [fraction]) et la pile du haut (bandeau, panneau de direction, voies, « puis », [topFraction]).
 */
internal object PanelGeometry {
    /** Pas de quantification : la caméra n'est recadrée que pour un écart de 2 % de la hauteur (≈ 20 dp), pas à chaque image d'animation. */
    const val STEP = 0.02f
    /** Plafond : au-delà, la voiture n'aurait plus de place entre les blocs. */
    const val MAX_FRACTION = 0.6f

    /** Fraction couverte sous [panelTopPx] (haut du bloc dans la racine), arrondie au-dessus ; null si la hauteur est inconnue. */
    fun fraction(panelTopPx: Float, rootHeightPx: Float): Float? {
        if (rootHeightPx.isNaN() || rootHeightPx <= 0f || panelTopPx.isNaN()) return null
        val raw = ((rootHeightPx - panelTopPx) / rootHeightPx).coerceIn(0f, MAX_FRACTION)
        // `maxOf` ramène −0 (plafond d'un petit négatif) à +0 ; `coerceIn` le laisserait passer, car −0 n'est pas < 0.
        return maxOf(0f, ceil(raw / STEP - 1e-3f) * STEP).coerceAtMost(MAX_FRACTION)
    }

    /** Fraction couverte au-dessus de [stackBottomPx] (bas de la pile dans la racine), même arrondi que [fraction]. */
    fun topFraction(stackBottomPx: Float, rootHeightPx: Float): Float? =
        if (stackBottomPx.isNaN()) null else fraction(rootHeightPx - stackBottomPx, rootHeightPx)
}
