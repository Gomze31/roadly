package fr.roadly.feature.navigation

import kotlin.math.ceil

/** D5 : part de la hauteur de l'écran couverte par le bloc du bas (Horizon + panneau), pour la marge basse de la caméra. */
internal object BottomPanelGeometry {
    /** Pas de quantification : la caméra n'est recadrée que pour un écart de 2 % de la hauteur (≈ 20 dp), pas à chaque image d'animation. */
    const val STEP = 0.02f
    /** Plafond : au-delà, la voiture n'aurait plus de place au-dessus du panneau. */
    const val MAX_FRACTION = 0.6f

    /** Fraction couverte sous [panelTopPx] (haut du bloc dans la racine), arrondie au-dessus ; null si la hauteur est inconnue. */
    fun fraction(panelTopPx: Float, rootHeightPx: Float): Float? {
        if (rootHeightPx <= 0f || panelTopPx.isNaN()) return null
        val raw = ((rootHeightPx - panelTopPx) / rootHeightPx).coerceIn(0f, MAX_FRACTION)
        return (ceil(raw / STEP - 1e-3f) * STEP).coerceIn(0f, MAX_FRACTION)
    }
}
