package fr.roadly.feature.navigation

/** Boutons de la colonne d'actions portrait, de haut en bas. */
internal enum class ActionButton { REPORT, VOICE, SEARCH, ALTERNATIVES, CAMERA }

/** Côté d'un bouton de conduite (`RoadlySpacing.touchTargetDriving`). */
private const val BUTTON_DP = 56
/** Écart entre deux rangées de la colonne (`RoadlySpacing.sm`). */
private const val GAP_DP = 12
/** Écart entre la colonne et le bloc du bas, entre la pile et le panneau en paysage (`RoadlySpacing.s`). */
private const val BLOCK_GAP_DP = 8
/**
 * Haut réservé sous la barre d'état, au-dessus de la colonne portrait : marge 12 + bouton « replier » 44 + écart 8
 * + icône d'attribution 24 + écart 8 (voir `AttributionPlacement.guidancePortrait`).
 */
private const val TOP_RESERVED_DP = 12 + 44 + 8 + 24 + 8

/** Hauteur (dp) d'une colonne de [count] rangées de 56 dp espacées de 12 dp. */
internal fun actionColumnHeightDp(count: Int): Int = if (count <= 0) 0 else count * BUTTON_DP + (count - 1) * GAP_DP

/**
 * Hauteur (dp) laissée à la colonne d'actions portrait, posée 8 dp au-dessus du bloc du bas ([bottomBlockDp], mesuré depuis le bas
 * de l'écran) : de là jusqu'au-dessous de l'icône d'attribution, sous le bouton « replier ».
 */
internal fun portraitActionColumnAvailableDp(screenHeightDp: Int, statusTopDp: Int, bottomBlockDp: Int): Int =
    (screenHeightDp - statusTopDp - TOP_RESERVED_DP - bottomBlockDp - BLOCK_GAP_DP).coerceAtLeast(0)

/**
 * Passe appareil (portrait) : boutons montrés dans [availableDp]. Tout tient panneau replié ; sinon, dans l'ordre, cèdent
 * « Itinéraires » si le panneau déplié le montre, la voix si la ligne repliée la montre ([voiceInPanel]), puis « Changer de
 * destination », « Itinéraires » et la voix. Signaler et 2D/3D + Nord restent toujours.
 */
internal fun portraitActionButtons(availableDp: Int, guiding: Boolean, panelExpanded: Boolean, voiceInPanel: Boolean): List<ActionButton> {
    val shown = ActionButton.entries.filter { it != ActionButton.ALTERNATIVES || guiding }.toMutableList()
    val dropOrder = buildList {
        if (panelExpanded) add(ActionButton.ALTERNATIVES)
        if (voiceInPanel) add(ActionButton.VOICE)
        add(ActionButton.SEARCH); add(ActionButton.ALTERNATIVES); add(ActionButton.VOICE)
    }
    for (button in dropOrder) {
        if (actionColumnHeightDp(shown.size) <= availableDp) break
        shown.remove(button)
    }
    return shown
}

/**
 * Passe appareil (paysage) : le panneau déplié tient dans la colonne gauche ([columnHeightDp]) sous la pile du bandeau
 * ([stackHeightDp]), avec 8 dp d'écart ; sinon l'overlay le replie.
 */
internal fun landscapePanelFits(columnHeightDp: Int, stackHeightDp: Int, panelExpandedHeightDp: Int): Boolean =
    stackHeightDp + BLOCK_GAP_DP + panelExpandedHeightDp <= columnHeightDp
