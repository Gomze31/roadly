package fr.roadly.feature.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.DateTimeWithZone
import androidx.car.app.model.Distance
import androidx.car.app.model.Template
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.model.Destination
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.car.app.navigation.model.RoutingInfo
import androidx.car.app.navigation.model.Step
import androidx.car.app.navigation.model.TravelEstimate
import androidx.car.app.navigation.model.Trip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.map.DangerZoneSpans
import fr.roadly.core.map.ManeuverArrowBuilder
import fr.roadly.core.model.LocationFix
import fr.roadly.core.navigation.NavigationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * Guidage sur l'écran de la voiture : manœuvre et suivante, distance, arrivée estimée (gabarit de navigation de l'hôte),
 * tracé, flèche de manœuvre et caméra qui suit la position projetée ; vitesse, limitation et zone de danger dessinées sur la carte.
 * La voix reste celle du téléphone (diffusée par la voiture).
 */
internal class CarGuidanceScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val renderer: CarMapRenderer,
    private val navigationManager: NavigationManager,
) : Screen(carContext) {
    private var state: NavigationState = deps.navigation.state.value
    private var muted = deps.navigation.muted.value
    private var toleranceKmh = 0
    private var shownRouteId: String? = null
    private var shownArrowIndex = -1
    /** Contenu du gabarit au dernier rafraîchissement : l'hôte n'est sollicité que si l'affichage change. */
    private var shownKey: Any? = null
    /** Clé du dernier résumé de trajet accepté par l'hôte ([CarGuidanceMapper.tripKey]) : un résumé identique n'est pas renvoyé. */
    private var sentTripKey: Any? = null
    private val alerts = CarZoneAlerts(carContext, deps, lifecycleScope)
    /** Domicile enregistré ; le bouton n'est proposé que s'il diffère de la destination en cours. */
    private var home: fr.roadly.core.model.Place? = null
    private val homeKnown: Boolean
        get() {
            val h = home ?: return false
            val end = (state as? NavigationState.Guiding)?.route?.geometry?.lastOrNull() ?: return true
            return end.distanceTo(h.point) > HOME_SAME_DESTINATION_M
        }

    init {
        lifecycleScope.launch(Dispatchers.Main) {
            deps.favorites.observeHome().collect { home = it; update(state, muted, toleranceKmh) }
        }
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                shownRouteId = null
                shownArrowIndex = -1
                (state as? NavigationState.Guiding)?.let { drawMap(it, CarHudModel.from(it, toleranceKmh)) } ?: clearStale(state)
            }
            override fun onStop(owner: LifecycleOwner) {
                renderer.clearRoutes()
                renderer.setHud(CarHudModel.EMPTY)
            }
        })
        lifecycleScope.launch(Dispatchers.Main) {
            combine(deps.navigation.state, deps.navigation.muted, deps.prefs.speedAlertToleranceKmh) { s, m, t -> Triple(s, m, t) }
                .collect { (s, m, tolerance) -> update(s, m, tolerance) }
        }
    }

    private fun update(s: NavigationState, m: Boolean, toleranceKmh: Int) {
        state = s
        muted = m
        this.toleranceKmh = toleranceKmh
        if (s is NavigationState.Guiding) {
            val hud = CarHudModel.from(s, toleranceKmh)
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                drawMap(s, hud)
                alerts.onZone(s.radarActive?.key ?: s.radarNext?.key, hud.zone)
            }
            updateTrip(s)
        } else {
            // Arrivée, échec, arrêt : plus aucune mesure n'arrive. Rien de périmé ne reste affiché, et une reprise renvoie le résumé.
            sentTripKey = null
            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) clearStale(s)
        }
        val key = displayKey(s, m)
        if (key != shownKey) {
            shownKey = key
            invalidate()
        }
    }

    /**
     * Hors guidage : vitesse, limitation et zone effacées, repère du véhicule et flèche de manœuvre retirés (ils resteraient figés à
     * la dernière position du guidage). Le tracé et le repère d'arrivée restent. LIMITE : la position n'est plus suivie ici.
     */
    private fun clearStale(s: NavigationState) {
        renderer.setHud(CarHudModel.forState(s, toleranceKmh))
        renderer.setUser(null)
        renderer.setManeuverArrow(ManeuverArrowBuilder.EMPTY)
        shownArrowIndex = -1
    }

    private fun drawMap(g: NavigationState.Guiding, hud: CarHudModel) {
        val route = g.route
        if (route.id != shownRouteId) {
            shownRouteId = route.id
            shownArrowIndex = -1
            renderer.setRoutes(listOf(route), route.id)
        }
        val p = g.progress
        renderer.setTraversed(p.segmentIndex, p.snapped)
        renderer.setDangerZones(DangerZoneSpans.from(g.hazards, p.distanceAlongM, g.radarActive?.key))
        val next = p.nextManeuverIndex
        if (next != shownArrowIndex) {
            shownArrowIndex = next
            val m = route.maneuvers.getOrNull(next)
            renderer.setManeuverArrow(
                if (m == null || m.type == fr.roadly.core.model.ManeuverType.ARRIVE) ManeuverArrowBuilder.EMPTY
                else ManeuverArrowBuilder.build(route.geometry, m.beginShapeIndex),
            )
        }
        val bearing = p.bearingDeg?.toDouble()
        // Position projetée sur le tracé (celle du guidage), pas un fix inventé : même point que la caméra.
        renderer.setUser(LocationFix(point = p.snapped, bearingDeg = p.bearingDeg, speedMps = g.speedKmh?.let { (it / 3.6).toFloat() }, accuracyM = SNAPPED_ACCURACY_M, timestampMs = deps.clock.nowMs()))
        // Caméra : croisière selon la vitesse, puis glissement vers le cadrage de la manœuvre à son approche (rond-point, sortie).
        val type = route.maneuvers.getOrNull(next)?.type ?: fr.roadly.core.model.ManeuverType.CONTINUE
        val camera = CarCameraPolicy.guidance(g.speedKmh, p.distanceToNextManeuverM, type, renderer.currentCamera)
        renderer.setSpeed(g.speedKmh)
        renderer.follow(p.snapped, bearing, FollowProfile.GUIDANCE, camera)
        renderer.setHud(hud)
    }

    /** Ce qui change l'affichage du gabarit (distances arrondies comme à l'écran, minute d'arrivée). */
    private fun displayKey(s: NavigationState, m: Boolean): Any = when (s) {
        is NavigationState.Guiding -> {
            val model = CarGuidanceMapper.map(s)
            listOf(
                "g", m, model?.current, model?.next, model?.rerouting, model?.lanes, model?.junction, homeKnown,
                model?.let { CarDistances.maneuver(it.distanceToCurrentM) }, model?.let { CarDistances.remaining(it.remainingDistanceM) },
                model?.let { it.etaEpochMs / 60_000 }, model?.let { (it.remainingDurationS / 60).toLong() },
            )
        }
        else -> listOf(s::class, m)
    }

    override fun onGetTemplate(): Template {
        val builder = NavigationTemplate.Builder()
            .setBackgroundColor(CarColor.createCustom(CARD_DAY, CARD_NIGHT))
        // Bandeau de carte (défilement, zoom, recentrage) : niveau 2 ; au niveau 1 la carte suit le véhicule, sans boutons.
        if (carContext.capabilities().navigationMapStrip) builder.setMapActionStrip(CarActions.mapStrip(renderer)).setPanModeListener { }
        when (val s = state) {
            is NavigationState.Guiding -> {
                val model = CarGuidanceMapper.map(s)
                builder.setActionStrip(guidanceActions())
                if (model == null || model.rerouting) {
                    builder.setNavigationInfo(RoutingInfo.Builder().setLoading(true).build())
                } else {
                    builder.setNavigationInfo(routingInfo(model))
                    builder.setDestinationTravelEstimate(destinationEstimate(model))
                }
            }
            is NavigationState.Arrived -> {
                builder.setNavigationInfo(MessageInfo.Builder(carContext.getString(R.string.car_arrived)).apply {
                    deps.guidance.destinationName?.let { setText(it) }
                }.build())
                builder.setActionStrip(ActionStrip.Builder().addAction(CarActions.text(carContext, R.string.car_finish) { deps.guidance.stop() }).build())
            }
            is NavigationState.Failed -> {
                builder.setNavigationInfo(MessageInfo.Builder(carContext.getString(R.string.car_guidance_failed)).setText(s.error.userMessage()).build())
                builder.setActionStrip(
                    ActionStrip.Builder()
                        .addAction(CarActions.text(carContext, R.string.car_retry) { deps.navigation.retry() })
                        .addAction(CarActions.text(carContext, R.string.car_stop) { deps.guidance.stop() })
                        .build(),
                )
            }
            NavigationState.Idle -> {
                // Guidage arrêté (téléphone ou voiture) : la session retire cet écran.
                builder.setActionStrip(ActionStrip.Builder().addAction(CarActions.text(carContext, R.string.car_close) { screenManager.pop() }).build())
            }
        }
        return builder.build()
    }

    /** Voix, retour au domicile en un appui (s'il est défini et n'est pas déjà la destination), arrêt. */
    private fun guidanceActions(): ActionStrip = ActionStrip.Builder()
        .addAction(CarActions.icon(if (muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp) { deps.navigation.setMuted(!muted) })
        .apply { if (homeKnown) addAction(CarActions.icon(Icons.Default.Home) { goHome() }) }
        .addAction(CarActions.text(carContext, R.string.car_stop) { deps.guidance.stop() })
        .build()

    private fun goHome() {
        val place = home ?: return
        lifecycleScope.launch {
            val r = deps.guidance.startTo(place)
            if (r is fr.roadly.core.common.Outcome.Failure) androidx.car.app.CarToast.makeText(carContext, r.error.userMessage(), androidx.car.app.CarToast.LENGTH_LONG).show()
        }
    }

    /** [lanes] : voies à l'approche (étape courante seulement) — la bibliothèque exige les voies et leur image ensemble. */
    private fun step(s: CarStep, lanes: List<fr.roadly.core.model.Lane> = emptyList()): Step {
        // Numéro de sortie posé seulement sur un type qui l'accepte (sinon la bibliothèque lève une exception).
        val maneuver = CarGuidanceMapper.maneuverBuilder(s).setIcon(CarIcons.maneuver(s.type)).build()
        val cue = s.cue.ifBlank { s.road.orEmpty() }
        // Une voie sans direction connue : pas de bloc de voies du tout (ni voies, ni image).
        val carLanes = CarLanes.toCar(lanes)
        return Step.Builder(cue).setManeuver(maneuver).apply {
            s.road?.let { setRoad(it) }
            if (carLanes.isNotEmpty()) {
                carLanes.forEach(::addLane)
                setLanesImage(CarVisuals.lanes(lanes))
            }
        }.build()
    }

    private fun distance(v: CarDistanceValue): Distance = Distance.create(v.value, v.unit)

    private fun routingInfo(model: CarGuidanceModel): RoutingInfo = RoutingInfo.Builder()
        .setCurrentStep(step(model.current, model.lanes), distance(CarDistances.maneuver(model.distanceToCurrentM)))
        .apply {
            model.next?.let { setNextStep(step(it)) }
            // Rond-point avec la sortie à prendre, ou panneau de sortie : montré par l'hôte dans la carte de guidage.
            model.junction?.let { setJunctionImage(CarVisuals.junction(it)) }
        }
        .build()

    private fun destinationEstimate(model: CarGuidanceModel): TravelEstimate =
        TravelEstimate.Builder(distance(CarDistances.remaining(model.remainingDistanceM)), DateTimeWithZone.create(model.etaEpochMs, TimeZone.getDefault()))
            .setRemainingTimeSeconds(model.remainingDurationS.toLong())
            .build()

    /** Résumé pour le combiné d'instruments et les notifications de l'hôte. */
    private fun updateTrip(g: NavigationState.Guiding) {
        val model = CarGuidanceMapper.map(g)
        // Même étape, mêmes textes, même minute d'arrivée : l'hôte a déjà ce résumé (l'état est émis à chaque position).
        val key = CarGuidanceMapper.tripKey(g.route.id, g.progress.nextManeuverIndex, model, deps.guidance.destinationName)
        if (key == sentTripKey) return
        val trip = if (model == null || model.rerouting) {
            Trip.Builder().setLoading(true).build()
        } else {
            val now = deps.clock.nowMs()
            val speedMps = ((g.speedKmh ?: 0.0) / 3.6).coerceAtLeast(MIN_STEP_SPEED_MPS)
            val stepEta = now + (model.distanceToCurrentM / speedMps * 1000).toLong()
            val stepEstimate = TravelEstimate.Builder(distance(CarDistances.maneuver(model.distanceToCurrentM)), DateTimeWithZone.create(stepEta, TimeZone.getDefault())).build()
            val destination = Destination.Builder().setName(deps.guidance.destinationName ?: carContext.getString(R.string.car_destination)).build()
            Trip.Builder()
                .addStep(step(model.current), stepEstimate)
                .addDestination(destination, destinationEstimate(model))
                .apply { model.current.road?.let { setCurrentRoad(it) } }
                .build()
        }
        try {
            navigationManager.updateTrip(trip)
            sentTripKey = key
        } catch (e: IllegalStateException) {
            // Navigation pas (encore) déclarée à l'hôte : le prochain état la déclarera.
        }
    }

    private companion object {
        /** Fond des cartes de manœuvre : bleu nuit de l'app (interface sombre de jour comme de nuit). */
        const val CARD_DAY = 0xFF0E1326.toInt()
        const val CARD_NIGHT = 0xFF0E1326.toInt()
        const val SNAPPED_ACCURACY_M = 5f
        const val MIN_STEP_SPEED_MPS = 5.0
        const val HOME_SAME_DESTINATION_M = 150.0
    }
}
