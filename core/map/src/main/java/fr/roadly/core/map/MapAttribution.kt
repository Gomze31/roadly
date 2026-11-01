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
        /** Côté de l'icône d'attribution MapLibre (zone tactile comprise), pour la centrer sur un bouton. */
        val ICON_SIZE = 24.dp

        /**
         * Guidage portrait : en haut à droite, sous le bouton « replier le bandeau » ([toggle], à [edge] du haut sous la barre
         * d'état [statusTop]), à [gap] dessous et à [edge] + [inset] du bord droit. Zone libre, panneau du bas replié ou déplié.
         */
        fun guidancePortrait(statusTop: Dp, edge: Dp, toggle: Dp, gap: Dp, inset: Dp) =
            AttributionPlacement(top = true, end = true, vertical = statusTop + edge + toggle + gap, side = edge + inset)

        /**
         * Guidage paysage : en haut à droite comme en portrait, mais à gauche du bouton « replier » et centrée sur sa hauteur.
         * Dessous, la colonne d'actions (Signaler à 12 dp) ne laisse pas la place de l'icône ; à gauche, c'est la carte.
         * [navEnd] : barre de navigation à droite (la carte passe dessous, l'overlay non).
         */
        fun guidanceLandscape(statusTop: Dp, navEnd: Dp, edge: Dp, toggle: Dp, gap: Dp) =
            AttributionPlacement(top = true, end = true, vertical = statusTop + edge + (toggle - ICON_SIZE) / 2, side = navEnd + edge + toggle + gap)
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
