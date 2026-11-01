package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.ManeuverType
import kotlin.math.exp

/** C1 : mode de caméra du guidage, choisi pour la session (bouton 2D/3D, « nord en haut ») ; départ = réglage « Carte 3D ». */
enum class CameraMode3D {
    FOLLOW_3D, FOLLOW_2D, NORTH_UP;

    /** Bouton 2D/3D : 3D → 2D, sinon 3D (depuis « nord en haut », on repasse en 3D). */
    fun toggled3d(): CameraMode3D = if (this == FOLLOW_3D) FOLLOW_2D else FOLLOW_3D

    companion object {
        /** Mode de départ d'une session : réglage « Carte 3D en guidage » activé → 3D, sinon 2D. */
        fun default(map3dEnabled: Boolean): CameraMode3D = if (map3dEnabled) FOLLOW_3D else FOLLOW_2D
    }
}

/** Entrée de la caméra à chaque position (temps [nowMs] monotone, ex. `SystemClock.elapsedRealtime()`). */
data class NavigationCameraInput(
    val point: GeoPoint,
    val bearingDeg: Float?,
    val speedKmh: Double?,
    val distanceToManeuverM: Double,
    val maneuverType: ManeuverType,
    /** Approche décidée par l'écran (hystérésis B1) ; null = règle de distance. */
    val approaching: Boolean?,
    val mode: CameraMode3D,
    val powerSave: Boolean,
    val landscape: Boolean,
    val nowMs: Long,
    /** Hauteur du panneau inférieur / hauteur de la vue (portrait) ; null = marges par défaut. Mesure branchée à l'Étape D5. */
    val bottomPanelFraction: Float? = null,
    /** Bas de la pile du haut (bandeau, panneau de direction, voies) / hauteur de la vue (portrait) ; null = marges par défaut. */
    val topPanelFraction: Float? = null,
)

/** Commande caméra : cible, caméra, durée (0 = déplacement immédiat) et courbe ([eased] = accélération / décélération MapLibre, sinon linéaire). */
data class NavigationCameraFrame(val point: GeoPoint, val camera: GuidanceCamera, val durationMs: Int, val eased: Boolean)

/**
 * Caméra du guidage (C1) : enveloppe [GuidanceCameraPolicy] (zoom, inclinaison, marges) et y ajoute le cap filtré
 * (moyenne exponentielle circulaire, constante de temps [BEARING_TAU_S], gelée à l'arrêt), le mode et les transitions.
 */
class NavigationCameraController {
    private var filteredBearing: Double? = null
    private var lastNowMs: Long? = null
    private var previous: GuidanceCamera? = null
    private var lastMode: CameraMode3D? = null
    private var transitionRequested = false
    /** Identifiant du tracé suivi (voir [onRouteChanged]) ; null avant le premier tracé de la session. */
    private var routeId: String? = null

    /** Cap filtré courant (degrés), null avant le premier cap connu. */
    val bearing: Double? get() = filteredBearing

    fun update(input: NavigationCameraInput): NavigationCameraFrame {
        val bearing = filterBearing(input)
        val threeD = input.mode == CameraMode3D.FOLLOW_3D
        val headingUp = GuidanceCameraPolicy.compute(
            GuidanceCameraInput(
                speedKmh = input.speedKmh, bearingDeg = bearing?.toFloat(), distanceToManeuverM = input.distanceToManeuverM,
                maneuverType = input.maneuverType, threeD = threeD, powerSave = input.powerSave, landscape = input.landscape,
                approaching = input.approaching, buildingsVisible = threeD && !input.powerSave,
            ),
            previous,
        )
        // La caméra « cap en haut » est gardée comme référence : en sortant du nord en haut, cap et zoom repartent d'elle.
        previous = headingUp
        val padded = withPanels(headingUp, input)
        val camera = if (input.mode == CameraMode3D.NORTH_UP) padded.copy(bearing = 0.0) else padded
        val first = lastMode == null
        val transition = !first && (transitionRequested || lastMode != input.mode)
        lastMode = input.mode
        transitionRequested = false
        val duration = when {
            first -> 0
            transition -> TRANSITION_MS
            else -> TICK_MS
        }
        return NavigationCameraFrame(input.point, camera, duration, eased = transition)
    }

    /** Prochaine image en transition douce (recentrage après un geste). */
    fun requestTransition() { transitionRequested = true }

    /**
     * Tracé suivi, signalé à chaque image (sans effet tant que l'identifiant ne change pas). Premier tracé de la session :
     * remise à zéro, image immédiate. Tracé suivant (recalcul, bascule sur une alternative) : cap filtré et zoom gardés,
     * transition douce de [TRANSITION_MS] — pas de coupure de caméra pendant que le marqueur glisse.
     */
    fun onRouteChanged(routeId: String) {
        val previousId = this.routeId
        if (routeId == previousId) return
        this.routeId = routeId
        if (previousId == null) reset() else requestTransition()
    }

    /** Cap, zoom et mode oubliés ; la prochaine image est immédiate. */
    fun reset() {
        filteredBearing = null; lastNowMs = null; previous = null; lastMode = null; transitionRequested = false
    }

    private fun filterBearing(input: NavigationCameraInput): Double? {
        val raw = input.bearingDeg?.toDouble()
        val last = lastNowMs
        lastNowMs = input.nowMs
        val current = filteredBearing
        val stopped = (input.speedKmh ?: 0.0) < GuidanceCameraPolicy.STOPPED_KMH
        if (raw == null || (stopped && current != null)) return current
        if (current == null || last == null) return Bearings.normalize(raw).also { filteredBearing = it }
        val dtS = ((input.nowMs - last) / 1000.0).coerceIn(0.0, MAX_DT_S)
        val alpha = 1.0 - exp(-dtS / BEARING_TAU_S)
        return Bearings.normalize(current + alpha * Bearings.delta(current, raw)).also { filteredBearing = it }
    }

    /**
     * Portrait : marges haute et basse étendues aux blocs de l'overlay mesurés par l'écran. En bas, au moins le bloc du bas ;
     * en haut, au moins la pile du haut + [TOP_STACK_MARGIN] (la pointe de la flèche de manœuvre reste sous la pile, pas dessous).
     * Somme plafonnée à [MAX_VERTICAL_PADDING] : la marge haute cède d'abord, la voiture reste au-dessus du panneau du bas.
     * Paysage : marges de la règle, inchangées.
     */
    private fun withPanels(cam: GuidanceCamera, input: NavigationCameraInput): GuidanceCamera {
        if (input.landscape) return cam
        val bottomFraction = input.bottomPanelFraction
        val topFraction = input.topPanelFraction
        if (bottomFraction == null && topFraction == null) return cam
        val bottom = maxOf(cam.padding.bottom, bottomFraction ?: 0f)
        val wantedTop = if (topFraction == null) cam.padding.top else maxOf(cam.padding.top, topFraction + TOP_STACK_MARGIN)
        val top = wantedTop.coerceAtMost(MAX_VERTICAL_PADDING - bottom).coerceAtLeast(0f)
        return cam.copy(padding = cam.padding.copy(top = top, bottom = bottom))
    }

    companion object {
        /** Constante de temps (s) du filtre de cap : environ 63 % d'un virage absorbé en 1,5 s. */
        const val BEARING_TAU_S = 1.5
        /** Intervalle maximal (s) pris en compte entre deux positions (après un trou GPS, le cap rattrape sans dépasser). */
        const val MAX_DT_S = 5.0
        /** Durée (ms) d'un glissement de caméra entre deux positions (~1 Hz), linéaire, comme l'animation du marqueur. */
        const val TICK_MS = 1_000
        /** Durée (ms) d'une transition 2D ↔ 3D, nord en haut ou recentrage. */
        const val TRANSITION_MS = 600
        /** Somme maximale des marges haute et basse (fraction de la hauteur de la vue). */
        const val MAX_VERTICAL_PADDING = 0.85f
        /** Écart (fraction de la hauteur de la vue) ajouté sous la pile du haut pour la marge haute. */
        const val TOP_STACK_MARGIN = 0.04f
    }
}
