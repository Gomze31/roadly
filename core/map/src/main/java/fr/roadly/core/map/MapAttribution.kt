package fr.roadly.core.map

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * D5 : coin de l'icône d'attribution ([top] haut / bas, [end] droite / gauche) et marges en dp depuis les bords de la carte,
 * barres système comprises. La carte reste sous les barres (bord à bord) : on déplace l'icône, pas la carte.
 */
data class AttributionPlacement(val top: Boolean, val end: Boolean, val vertical: Dp, val side: Dp) {
    companion object {
        /** Marge par défaut au-dessus de la barre de navigation et depuis le bord gauche. */
        val DEFAULT_EDGE = 4.dp
    }
}

/** Marges `UiSettings.setAttributionMargins(gauche, haut, droite, bas)` (px) pour un coin donné (Kotlin pur). */
internal object AttributionMargins {
    fun of(top: Boolean, end: Boolean, verticalPx: Int, sidePx: Int): IntArray = intArrayOf(
        if (end) 0 else sidePx,
        if (top) verticalPx else 0,
        if (end) sidePx else 0,
        if (top) 0 else verticalPx,
    )
}
