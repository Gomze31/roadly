package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.data.repository.DriveMemoryRepository
import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.NoDriveMemoryRepository
import fr.roadly.core.data.repository.ParkedVehicle
import fr.roadly.core.data.repository.DefaultRadarSettingsRepository
import fr.roadly.core.data.repository.RadarSettingsRepository
import fr.roadly.core.radar.RadarAlertConfig
import fr.roadly.core.radar.RadarAlertEngine
import fr.roadly.core.radar.RadarAlertEvent
import fr.roadly.core.radar.RadarQuality
import fr.roadly.core.radar.ZoneObservation
import fr.roadly.core.radar.ZoneStatus
import fr.roadly.core.data.repository.TollReminder
import fr.roadly.core.data.repository.LastGuidance
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteHazard
import fr.roadly.core.model.HazardKind
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import fr.roadly.core.model.TravelMode
import fr.roadly.core.routing.NoScooterRouteAnalyzer
import fr.roadly.core.routing.ScooterRouteAnalyzer
import fr.roadly.core.scooter.analysis.ScooterRouteAnalysis
import fr.roadly.core.scooter.guidance.AnnouncementStage
import fr.roadly.core.scooter.guidance.ScooterNavigationCoordinator
import fr.roadly.core.scooter.guidance.ScooterNavigationUiState
import fr.roadly.core.scooter.range.ScooterRangeEstimator

/**
 * Session de guidage unique de l'application (spec §2, §4). Logique pure Kotlin testée sur JVM ;
 * la voix, le service Android, la limitation et l'horloge sont injectés derrière des interfaces.
 * Toute une course s'exécute sur un dispatcher à parallélisme 1 : aucun verrou n'est nécessaire.
 */
@Singleton
class NavigationSession @Inject constructor(
    private val location: LocationRepository,
    private val routing: RoutingProvider,
    private val speedLimits: SpeedLimitProvider,
    private val speech: SpeechService,
    private val prefs: UserPreferencesRepository,
    private val serviceController: NavigationServiceController,
    private val clock: NavigationClock,
    private val vigilance: VigilanceZoneProvider,
    private val rain: RainProvider,
    private val tripRecorder: TripRecorder,
    dispatchers: DispatcherProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
    /** Analyse des voies après un recalcul en mode trottinette ; sans elle, le guidage reste générique. */
    private val scooterAnalyzer: ScooterRouteAnalyzer = NoScooterRouteAnalyzer,
    /** 1.0 : profil du tracé (limitations, portiques de péage en flux libre), une requête par tracé. */
    private val routeAttributes: RouteAttributesProvider = NoRouteAttributesProvider,
    /** 1.0 : rappels de péage en flux libre et position du véhicule à l'arrivée. */
    private val driveMemory: DriveMemoryRepository = NoDriveMemoryRepository,
    /** Radar Intelligence : voix, préavis, sortie de zone. */
    private val radarSettings: RadarSettingsRepository = DefaultRadarSettingsRepository,
) {
    private val _state = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _muted = MutableStateFlow(false)

    /** Sourdine demandée par l'utilisateur pour la course (indépendante du réglage « voix »). */
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /**
     * E3 : la reprise n'a de sens que pour une course héritée d'un processus précédent. Ouverte au démarrage du processus, fermée
     * (définitivement, de façon synchrone) au premier départ, arrêt ou « Ignorer » : l'effacement DataStore étant asynchrone, la course
     * qu'on vient d'arrêter n'est ainsi jamais proposée, même quelques images.
     */
    private val resumeOfferOpen = MutableStateFlow(true)

    /**
     * E3 : course mémorisée à proposer à la reprise (« Reprendre le guidage vers … »), seulement au repos et seulement si elle vient d'un
     * processus précédent (mort du processus, redémarrage) — un arrêt ou une arrivée l'ont effacée. Null pendant un guidage.
     * Les valeurs courantes de [resumeOfferOpen] et de l'état sont relues à chaque émission (pas celles, peut-être en retard, du `combine`).
     * LIMITE : aucune limite d'âge.
     */
    val resumableGuidance: Flow<LastGuidance?> = combine(prefs.lastGuidance, resumeOfferOpen, state) { last, _, _ ->
        last.takeIf { resumeOfferOpen.value && _state.value is NavigationState.Idle }
    }.distinctUntilChanged()

    /** E3 « Ignorer » : oublie la course mémorisée ; sans effet pendant un guidage, dont la course est celle qui est mémorisée. */
    fun forgetLastGuidance() {
        if (_state.value !is NavigationState.Idle) return
        resumeOfferOpen.value = false
        persistLastGuidance(null)
    }

    private val confined = dispatchers.default.limitedParallelism(1)
    private var job: Job? = null
    /** Rend atomiques « incrémenter la génération » et « écrire l'état » : plus aucune publication périmée après un arrêt. */
    private val stateLock = Any()
    @Volatile private var generation = 0
    private var lastOption: RouteOption? = null
    private var lastPreferences = RoutePreferences()
    private var lastVehicle: VehicleProfile? = null
    /** Nom de destination de la dernière course demandée : repris par [retry] pour ne pas l'effacer de `lastGuidance`. */
    private var lastDestinationName: String? = null
    private var lastScooter: ScooterGuidanceConfig? = null
    /** Course en cours (lue et modifiée uniquement sur [confined]). */
    private var current: Run? = null
    /**
     * 1.0 : portiques de péage en flux libre franchis depuis le départ. Conservé quand un départ remplace une course en cours
     * (alternative, étape ajoutée) ; enregistré comme rappel à l'arrivée ou à l'arrêt.
     */
    @Volatile private var tollTrip: FreeFlowTollTrip? = null

    /** Enregistre le rappel de péage de la course (s'il y en a un) et l'oublie ; une erreur de stockage n'a aucun effet sur le guidage. */
    private fun persistTollReminder() {
        val t = tollTrip ?: return
        tollTrip = null
        scope.launch {
            try {
                driveMemory.addTollReminder(TollReminder(t.firstPassedAtEpochMs, t.firstPassedAtEpochMs, t.roads, t.countryCode, t.gantries))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { /* rappel perdu, guidage intact */ }
        }
    }

    /**
     * Démarre (ou remplace) la course sur [option] ; les [preferences] sont réutilisées lors des recalculs.
     * [vehicle] : profil transmis au recalcul (costing poids lourd / moto / taxi) ; null = auto.
     * [scooter] non nul = guidage trottinette (costing vélo au recalcul, annonces et autonomie trottinette). [destinationName] : nom mémorisé (B6) ;
     * absent (alternative, étape ajoutée), le nom précédent est gardé si la destination est la même (à moins de 25 m).
     * Un démarrage en échec efface la course mémorisée.
     */
    fun start(
        option: RouteOption,
        preferences: RoutePreferences,
        vehicle: VehicleProfile? = null,
        scooter: ScooterGuidanceConfig? = null,
        destinationName: String? = null,
    ) {
        // Un départ qui ne prolonge pas une course en cours repart sans portique franchi (le précédent rappel a déjà été enregistré).
        if (_state.value !is NavigationState.Guiding) persistTollReminder()
        cancelRun(null)
        val previousDestination = lastOption?.route?.geometry?.lastOrNull()
        val sameDestination = previousDestination != null &&
            option.route.geometry.lastOrNull()?.let { it.distanceTo(previousDestination) <= GuidanceConstants.ARRIVAL_RADIUS_M } == true
        val name = destinationName ?: lastDestinationName.takeIf { sameDestination }
        lastOption = option
        lastPreferences = preferences
        lastVehicle = vehicle
        lastScooter = scooter
        lastDestinationName = name
        resumeOfferOpen.value = false // E3 : avant tout changement d'état ; la course mémorisée sera remplacée ou effacée.
        if (location.permissionState.value != LocationPermissionState.GRANTED) {
            publishFailure(NavigationState.Failed(AppError.PermissionDenied))
            persistLastGuidance(null)
            return
        }
        val tracker = try {
            RouteTracker(option.route)
        } catch (e: IllegalArgumentException) {
            publishFailure(NavigationState.Failed(AppError.NoResult))
            persistLastGuidance(null)
            return
        }
        val run = synchronized(stateLock) {
            Run(++generation, option.route, tracker, preferences, vehicle, scooter, option.scooter?.analysis).also {
                _state.value = it.guiding(tracker.initialProgress(), it.startedAtMs, gpsLost = false)
            }
        }
        tripRecorder.begin(option.label, clock.nowMs())
        if (scooter != null) {
            tripRecorder.setContext(TripContext(TravelMode.ELECTRIC_SCOOTER, scooter.request.profile.id.takeIf { it != 0L }, scooter.batteryPct?.roundToInt()))
        }
        location.setGuidanceActive(true)
        serviceController.start()
        job = scope.launch(confined) { current = run; run.execute() }
        // B6 : course mémorisée pour être proposée à la reprise après la mort du processus (bouton : Étape E3).
        persistLastGuidance(LastGuidance(option.route.geometry.last(), name, option.route.id, clock.nowMs()))
    }

    /** Réglages trottinette de la course en cours (alternatives, ajout d'arrêt) ; null en voiture / moto. */
    val scooterConfig: ScooterGuidanceConfig? get() = lastScooter

    /** Niveau de batterie saisi en cours de route (trottinette) : devient la référence de l'estimation. */
    fun setScooterBattery(pct: Double) {
        lastScooter = lastScooter?.copy(batteryPct = pct)
        scope.launch(confined) { current?.setBattery(pct) }
    }

    /** Arrête la course : `Idle`, GPS relâché, parole coupée et moteur de synthèse libéré. */
    fun stop() {
        val wasGuiding = _state.value is NavigationState.Guiding
        resumeOfferOpen.value = false // E3 : avant `Idle`, sinon la course arrêtée serait proposée le temps de l'effacement.
        cancelRun(NavigationState.Idle)
        speech.release()
        persistLastGuidance(null)
        persistTollReminder()
        if (wasGuiding) scope.launch { tripRecorder.end(arrived = false, nowMs = clock.nowMs()) }
    }

    /** Relance la dernière course demandée (après un `Failed`, par exemple). */
    fun retry() {
        lastOption?.let { start(it, lastPreferences, lastVehicle, lastScooter, lastDestinationName) }
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        // E1 : seul le guidage se tait ; un message de l'assistant en cours ou en file n'est ni coupé ni effacé.
        if (muted) speech.stop(VoiceChannel.GUIDANCE)
    }

    private fun publishFailure(failed: NavigationState.Failed) {
        synchronized(stateLock) { _state.value = failed }
    }

    /** Écrit ou efface ([value] null) la dernière course ; l'ordre est garanti par [confined], une erreur DataStore ne touche pas au guidage. */
    private fun persistLastGuidance(value: LastGuidance?) {
        scope.launch(confined) {
            try {
                prefs.setLastGuidance(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Mémoire non disponible : la reprise ne sera simplement pas proposée.
            }
        }
    }

    /** Invalide la course en cours ([generation]) et, si [state] n'est pas nul, l'écrit dans le même verrou. */
    private fun cancelRun(state: NavigationState?) {
        synchronized(stateLock) {
            generation++
            if (state != null) _state.value = state
        }
        job?.cancel()
        job = null
        location.setGuidanceActive(false)
        speech.stop(VoiceChannel.GUIDANCE)
        // La sourdine appartient à la course : le canal du guidage est rendu tel quel, celui de l'assistant n'est pas touché (E1).
        speech.setMuted(false, VoiceChannel.GUIDANCE)
    }

    /** Une course : son état n'est lu et écrit que sur [confined]. */
    private inner class Run(
        val gen: Int,
        var route: Route,
        var tracker: RouteTracker,
        val routePreferences: RoutePreferences,
        val vehicle: VehicleProfile?,
        val scooter: ScooterGuidanceConfig?,
        initialAnalysis: ScooterRouteAnalysis?,
    ) {
        private val estimator = scooter?.let { ScooterRangeEstimator(it.request.profile, it.calibration) }
        /** Coordinateur trottinette du tracé courant (remplacé après un recalcul). */
        private var coordinator: ScooterNavigationCoordinator? = scooter?.let { newCoordinator(route, initialAnalysis, it.batteryPct, null) }
        private var scooterUi: ScooterNavigationUiState? = null
        // État trottinette affiché dès le départ, avant le premier fix (sans annonce : position non mesurée).
        init { scooterUi = coordinator?.update(0.0, null, extrapolated = true)?.ui }
        private val destination: GeoPoint = route.geometry.last()
        private val costing = route.costing
        private val announced = mutableSetOf<Pair<Int, Tier>>()
        private val smoother = SpeedSmoother()
        private val recent = ArrayDeque<LocationFix>()
        /** Vitesse lissée : référence des paliers d'annonce et de l'alerte de dépassement. */
        private var speedKmh: Double? = null
        /** Vitesse GPS brute du dernier fix : c'est elle qu'affiche le badge (le lissage ne sert qu'à la politique d'annonce). */
        private var rawSpeedKmh: Double? = null
        /** Limitation du fournisseur fix par fix (Valhalla auto-hébergé uniquement) : repli quand le profil du tracé ne la connaît pas. */
        private var providerLimitKmh: Int? = null
        /** 1.0 : limitation lue dans le profil du tracé à la position courante ; null hors itinéraire ou inconnue. */
        private var routeLimitKmh: Int? = null
        private val speedLimitKmh: Int? get() = routeLimitKmh ?: providerLimitKmh
        /** 1.0 : profil du tracé courant (null tant qu'il n'est pas chargé, ou indisponible). */
        private var attributes: RouteAttributes? = null
        private var nextLimit: SpeedLimitChange? = null
        /** Abscisse au-delà de laquelle les portiques du tracé courant n'ont pas encore été comptés. */
        private var gantryCursorM = 0.0
        val startedAtMs = clock.nowMs()
        private var lastFixAtMs = startedAtMs
        private var lastAdvanceAtMs = lastFixAtMs
        private var hadFix = false
        private var lastRerouteAtMs: Long? = null
        private var rerouting = false
        /** « Recalcul de l'itinéraire. » n'est dit qu'une fois par épisode hors itinéraire. */
        private var rerouteAnnounced = false
        private var overSpeedAlerted = false
        private var limitJob: Job? = null
        /** Virages + zones de vigilance du tracé courant (recalculés après un recalcul d'itinéraire). */
        private var hazards: List<RouteHazard> = emptyList()
        private var activeZone: RouteHazard? = null
        /** Radar Intelligence : une machine à états par course (une zone n'est annoncée qu'une fois par passage). */
        private val radarEngine = RadarAlertEngine(RadarAlertConfig(confirmTicks = 1))
        private var radarActive: ZoneStatus? = null
        private var radarNext: ZoneStatus? = null
        /** B4 : ronds-points du tracé courant, calculés une fois par tracé. */
        private var roundabouts: Map<Int, RoundaboutInfo> = RoundaboutResolver.resolveAll(route)
        /** B5 : dernier indice de manœuvre vu et instant de son changement (passage d'une manœuvre). */
        private var lastNextIndex: Int? = null
        private var indexChangedAtMs: Long? = null
        private var vigilanceOn = GuidanceDefaults.VIGILANCE_ENABLED
        private var raining: Boolean? = null
        private var rainLowersLimit = GuidanceDefaults.RAIN_LOWERS_LIMIT
        private lateinit var runScope: CoroutineScope

        /** Garde de dernier recours : une exception inattendue publie `Failed` et relâche le GPS (le service s'arrête alors). */
        suspend fun execute() {
            try {
                body()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                synchronized(stateLock) {
                    if (gen == generation) {
                        _state.value = NavigationState.Failed(AppError.Unknown(e.message))
                        location.setGuidanceActive(false)
                    }
                }
            }
        }

        private suspend fun body() = coroutineScope {
            runScope = this
            speech.setVolume(prefs.voiceVolume.first())
            launch { prefs.voiceVolume.collect { speech.setVolume(it) } }
            launch {
                combine(prefs.voiceEnabled, _muted) { enabled, muted -> muted || !enabled }
                    .distinctUntilChanged()
                    .collect { speech.setMuted(it, VoiceChannel.GUIDANCE) }
            }
            launch { prefs.rainLowersLimit.collect { rainLowersLimit = it } }
            launch { prefs.vigilanceEnabled.collect { vigilanceOn = it; loadHazards() } }
            launch { rainWatch() }
            launch { loadAttributes(route) }
            val depart = route.maneuvers.first()
            announced += 0 to Tier.NOW
            say(depart.verbalPre ?: depart.instruction)
            launch { watchdog() }
            // Le flux de localisation rejoue son dernier fix : un fix antérieur au démarrage de la course est périmé.
            location.fixes.collect { if (it.timestampMs >= startedAtMs - GuidanceConstants.STALE_FIX_TOLERANCE_MS) onFix(it) }
        }

        private suspend fun onFix(fix: LocationFix) {
            val now = clock.nowMs()
            // Premier fix après une longue coupure (tunnel) : le suivi peut le voir hors itinéraire à tort, on attend le suivant.
            val afterLongGap = hadFix && now - lastFixAtMs > GuidanceConstants.REROUTE_MIN_INTERVAL_MS
            hadFix = true
            tripRecorder.onFix(fix, now)
            lastFixAtMs = now
            lastAdvanceAtMs = now
            recent.addLast(fix)
            while (recent.size > GuidanceConstants.RECENT_FIXES) recent.removeFirst()
            rawSpeedKmh = fix.speedMps?.let { it * 3.6 }
            speedKmh = smoother.update(rawSpeedKmh)
            val progress = tracker.update(fix)
            if (tracker.hasArrived) { arrive(); return }
            if (!tracker.isOffRoute) rerouteAnnounced = false
            if (tracker.isOffRoute && !afterLongGap) maybeReroute(fix, now)
            announce(progress)
            updateRouteProfile(progress)
            if (routeLimitKmh == null) refreshSpeedLimit()
            checkOverSpeed()
            updateZone(progress)
            // Un recalcul a pu aboutir pendant une suspension : ne jamais associer l'ancienne progression au nouveau tracé.
            if (progress.routeId == route.id) publish(guiding(progress, now, gpsLost = false))
        }

        /** Virages (géométrie) et zones de vigilance (base locale) du tracé courant ; vide si l'option est désactivée. */
        private suspend fun loadHazards() {
            val r = route
            val curves = CurveDetector.detect(r)
            val zones = if (vigilanceOn) {
                try { vigilance.zonesAlong(r) } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
            } else emptyList()
            if (r.id == route.id) hazards = (curves + zones).sortedBy { it.startAlongM }
        }

        /**
         * Radar Intelligence : zones de danger du tracé passées à la machine à états (préavis, entrée, sortie, une seule annonce
         * par passage, voix seulement au-delà du seuil de confiance). Hors itinéraire, aucune zone n'est observée.
         */
        private suspend fun updateZone(progress: RouteProgress) {
            if (progress.routeId != route.id) return
            val settings = radarSettings.settings.first()
            radarEngine.config = radarEngine.config.copy(prewarnM = settings.prewarnM.toDouble())
            // Zone signalée comme fausse alerte pendant le trajet : retirée du tracé tout de suite (pastille, carte, voix).
            if (settings.hiddenZones.isNotEmpty() && hazards.any { it.kind == HazardKind.VIGILANCE_ZONE && it.key in settings.hiddenZones }) {
                hazards = hazards.filterNot { it.kind == HazardKind.VIGILANCE_ZONE && it.key in settings.hiddenZones }
            }
            val along = progress.distanceAlongM
            val onRoute = !tracker.isOffRoute
            val observations = if (!onRoute) emptyList() else hazards.asSequence()
                .filter { it.kind == HazardKind.VIGILANCE_ZONE && it.endAlongM - along > -RADAR_BEHIND_M && it.startAlongM - along < RADAR_AHEAD_M }
                .map { ZoneObservation(zoneKey(it), it.startAlongM - along, it.endAlongM - along, it.speedLimitKmh, it.confidence ?: RadarQuality.MIN_VOICE) }
                .toList()
            val out = radarEngine.update(clock.nowMs(), observations, speedKmh, prefs.speedAlertToleranceKmh.first())
            activeZone = out.active?.let { a -> hazards.firstOrNull { it.kind == HazardKind.VIGILANCE_ZONE && zoneKey(it) == a.key } }
            radarActive = out.active
            radarNext = out.next
            for (e in out.events) {
                val voice = e.voice && settings.voice
                when (e) {
                    is RadarAlertEvent.PreWarning -> if (voice) say(SpokenPhrases.dangerZoneAhead(e.distanceM), routeId = progress.routeId, priority = SpeechPriority.LOW)
                    is RadarAlertEvent.Entered -> {
                        if (voice) say(SpokenPhrases.vigilanceZone(e.speedLimitKmh), routeId = progress.routeId, priority = SpeechPriority.LOW)
                        if (prefs.vigilanceSound.first() && !_muted.value && prefs.voiceEnabled.first()) speech.beep()
                    }
                    is RadarAlertEvent.Exited -> if (voice && settings.announceExit) say(SpokenPhrases.DANGER_ZONE_END, routeId = progress.routeId, priority = SpeechPriority.LOW)
                    // Le dépassement est déjà signalé par le bip général (checkOverSpeed) : pas de second son.
                    is RadarAlertEvent.OverSpeed -> Unit
                }
            }
        }

        private fun zoneKey(h: RouteHazard) = h.key ?: "zone@${h.startAlongM.toLong()}"

        /** 1.0 : profil du tracé [r] (une requête) ; ignoré s'il arrive après un recalcul vers un autre tracé. */
        private suspend fun loadAttributes(r: Route) {
            val result = try {
                routeAttributes.attributes(r)
            } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            val a = (result as? Outcome.Success)?.value ?: return
            if (r.id != route.id || a.routeId != r.id) return
            attributes = a
            // Un tracé recalculé commence à la position du recalcul : aucun de ses portiques n'est derrière nous.
            gantryCursorM = 0.0
        }

        /** Horizon d'affichage de la prochaine limitation : 20 s de trajet, entre 250 m et 1,5 km. */
        private fun limitHorizonM(): Double = ((speedKmh ?: 0.0) / 3.6 * LIMIT_HORIZON_S).coerceIn(LIMIT_HORIZON_MIN_M, LIMIT_HORIZON_MAX_M)

        /**
         * 1.0 : limitation courante et prochaine limitation lues dans le profil du tracé, et portiques franchis. Hors itinéraire,
         * la position projetée ne dit rien de la route réellement suivie : rien n'est lu dans le profil.
         */
        private suspend fun updateRouteProfile(progress: RouteProgress) {
            val a = attributes
            val onRoute = !tracker.isOffRoute && progress.offRouteM <= GuidanceConstants.OFF_ROUTE_SOFT_M
            if (a == null || a.routeId != route.id || progress.routeId != route.id || !onRoute) {
                routeLimitKmh = null
                nextLimit = null
                return
            }
            routeLimitKmh = a.limitAt(progress.distanceAlongM)
            nextLimit = a.nextChange(progress.distanceAlongM, limitHorizonM())
            val passed = a.tollGantries.filter { it.alongM > gantryCursorM && it.alongM <= progress.distanceAlongM }
            gantryCursorM = maxOf(gantryCursorM, progress.distanceAlongM)
            for (g in passed) passGantry(g, progress.routeId)
        }

        private suspend fun passGantry(g: TollGantry, routeId: String) {
            val previous = tollTrip
            val base = previous ?: FreeFlowTollTrip(0, emptyList(), g.countryCode, clock.nowMs())
            tollTrip = base.copy(gantries = base.gantries + 1, roads = (base.roads + listOfNotNull(g.roadName)).distinct())
            if (previous == null) say(SpokenPhrases.freeFlowToll(g.countryCode), routeId = routeId, priority = SpeechPriority.LOW)
        }

        /** Pluie : au plus une requête toutes les 10 min, sur la dernière position connue ; échec = inconnue. */
        private suspend fun rainWatch() {
            while (true) {
                val at = recent.lastOrNull()?.point
                if (at != null) {
                    raining = try {
                        (rain.isRaining(at) as? Outcome.Success)?.value
                    } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                }
                delay(GuidanceConstants.RAIN_CHECK_INTERVAL_MS)
            }
        }

        /** Limitation affichée et utilisée pour l'alerte : abaissée par temps de pluie si l'option est active. */
        private fun effectiveLimit(): Pair<Int?, Boolean> {
            val base = speedLimitKmh ?: return null to false
            if (raining == true && rainLowersLimit) {
                val reduced = SpeedLimitRules.rainReduced(base)
                if (reduced != base) return reduced to true
            }
            return base to false
        }

        private suspend fun announce(progress: RouteProgress) {
            if (rerouting || progress.routeId != route.id) return
            val now = clock.nowMs()
            if (progress.nextManeuverIndex != lastNextIndex) {
                if (lastNextIndex != null) indexChangedAtMs = now
                lastNextIndex = progress.nextManeuverIndex
            }
            val c = coordinator
            val override: ((Int, Tier, Double) -> String?)? = c?.let { co -> { i, tier, d -> co.maneuverText(i, tier.toStage(), d) } }
            val a = AnnouncementPolicy.next(
                progress, route.maneuvers, speedKmh, announced,
                scooter = scooter != null, textOverride = override, sincePreviousManeuverMs = indexChangedAtMs?.let { now - it },
            )
            if (a != null) {
                announced += a.maneuverIndex to a.tier
                say(a.text, routeId = progress.routeId, priority = a.tier.speechPriority())
            }
            if (c != null) scooterTick(c, progress)
        }

        /** Transitions de voie, avertissements anticipés et autonomie (trottinette). */
        private suspend fun scooterTick(c: ScooterNavigationCoordinator, progress: RouteProgress) {
            val u = c.update(progress.distanceAlongM, speedKmh, extrapolated = progress.extrapolated, nextManeuverIndex = progress.nextManeuverIndex)
            scooterUi = u.ui
            tripRecorder.setExtras(TripExtras(c.ascentDone.takeIf { c.hasGradeData }, c.descentDone.takeIf { c.hasGradeData }, c.modelEnergyUsedWh.takeIf { estimator != null && it > 0 }))
            u.speech.forEach { say(it, routeId = progress.routeId) }
        }

        fun setBattery(pct: Double) { coordinator?.setBattery(pct) }

        private fun newCoordinator(r: Route, analysis: ScooterRouteAnalysis?, batteryPct: Double?, previous: ScooterNavigationCoordinator?): ScooterNavigationCoordinator {
            val cfg = scooter!!
            return ScooterNavigationCoordinator(
                r, analysis, estimator, batteryPct, cfg.temperatureC, cfg.request.profile.effectiveCruiseKmh,
                carriedEnergyWh = previous?.energyUsedWh ?: 0.0,
                carriedModelEnergyWh = previous?.modelEnergyUsedWh ?: 0.0,
                carriedAscentM = previous?.ascentDone ?: 0.0,
            )
        }

        /** Après un recalcul : coordinateur sans analyse tout de suite, puis analyse des voies du nouveau tracé. */
        private fun refreshScooterAfterReroute(fresh: Route) {
            val cfg = scooter ?: return
            val previous = coordinator
            coordinator = newCoordinator(fresh, null, previous?.currentBatteryPct() ?: cfg.batteryPct, previous)
            runScope.launch {
                val analysis = try {
                    (scooterAnalyzer.analyze(fresh, cfg.request, cfg.withOsmTags) as? Outcome.Success)?.value
                } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                if (analysis != null && route.id == fresh.id) {
                    val interim = coordinator
                    coordinator = newCoordinator(fresh, analysis, interim?.currentBatteryPct() ?: cfg.batteryPct, interim)
                }
            }
        }

        private suspend fun arrive() {
            val last = route.maneuvers.lastIndex
            if ((last to Tier.NOW) !in announced) {
                announced += last to Tier.NOW
                val m = route.maneuvers[last]
                say(m.verbalPre ?: m.instruction, priority = SpeechPriority.URGENT)
            }
            val tolls = tollTrip
            publish(NavigationState.Arrived(route, tolls))
            tripRecorder.end(arrived = true, nowMs = clock.nowMs())
            if (gen == generation) {
                persistTollReminder()
                // Véhicule motorisé : sa position à l'arrivée est mémorisée (« Mon véhicule » sur l'accueil).
                if (scooter == null) rememberParking(recent.lastOrNull()?.point ?: destination)
            }
            val current = synchronized(stateLock) {
                // Course encore courante seulement : un nouveau départ survenu entre-temps a mémorisé sa propre course.
                (gen == generation).also { if (it) { location.setGuidanceActive(false); persistLastGuidance(null) } }
            }
            if (current) {
                // Le moteur vocal n'est libéré qu'après un délai : la phrase d'arrivée doit pouvoir se terminer.
                scope.launch {
                    delay(GuidanceConstants.SPEECH_RELEASE_DELAY_MS)
                    if (gen == generation) speech.release()
                }
            }
            runScope.cancel()
        }

        private suspend fun maybeReroute(fix: LocationFix, now: Long) {
            if (rerouting) return
            val last = lastRerouteAtMs
            if (last != null && now - last < GuidanceConstants.REROUTE_MIN_INTERVAL_MS) return
            lastRerouteAtMs = now
            rerouting = true
            if (!rerouteAnnounced) {
                rerouteAnnounced = true
                say(SpokenPhrases.REROUTING)
            }
            // Cap transmis seulement en mouvement : à l'arrêt le cap GPS est bruité et imposerait un sens à tort.
            val speed = fix.speedMps
            val heading = if (speed != null && speed >= GuidanceConstants.HEADING_FILTER_MIN_SPEED_MPS) fix.bearingDeg else null
            runScope.launch {
                val result = try {
                    withTimeout(GuidanceConstants.REROUTE_TIMEOUT_MS) {
                        routing.route(
                            RouteRequest(
                                origin = fix.point, destination = destination, costing = costing,
                                preferences = routePreferences, originHeadingDeg = heading, vehicle = vehicle,
                                scooter = scooter?.request,
                            ),
                        )
                    }
                } catch (e: TimeoutCancellationException) {
                    // Sans réponse : traité comme un échec (ancien tracé conservé, nouvel essai après l'intervalle).
                    Outcome.Failure(AppError.Timeout)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Outcome.Failure(AppError.Unknown(e.message))
                }
                val fresh = (result as? Outcome.Success)?.value?.firstOrNull()?.copy(costing = costing)
                val freshTracker = fresh?.let { r -> try { RouteTracker(r) } catch (e: IllegalArgumentException) { null } }
                if (fresh != null && freshTracker != null) {
                    route = fresh
                    tracker = freshTracker
                    roundabouts = RoundaboutResolver.resolveAll(fresh)
                    announced.clear()
                    announced += 0 to Tier.NOW
                    lastNextIndex = null; indexChangedAtMs = null
                    freshTracker.update(fix)
                    rerouteAnnounced = false
                    // Nouveau tracé : nouvelles abscisses, virages et zones à recalculer.
                    // Nouvelles abscisses : les zones déjà annoncées gardent leur clé (équipement), donc pas de seconde annonce.
                    hazards = emptyList(); activeZone = null; radarActive = null; radarNext = null
                    attributes = null; routeLimitKmh = null; nextLimit = null
                    refreshScooterAfterReroute(fresh)
                    runScope.launch { loadAttributes(fresh) }
                    loadHazards()
                }
                // Échec : on continue sur l'ancien tracé ; un nouveau recalcul sera tenté après 10 s.
                rerouting = false
                publish(guiding(tracker.lastProgress ?: tracker.initialProgress(), clock.nowMs(), gpsLost = false))
            }
        }

        /** Tic d'1 s : sans fix depuis > 3 s, avance la position le long du tracé (plafonnée par le suivi) ; badge après 10 s. */
        private suspend fun watchdog() {
            while (true) {
                delay(GuidanceConstants.WATCHDOG_TICK_MS)
                val now = clock.nowMs()
                val silentMs = now - lastFixAtMs
                if (silentMs <= GuidanceConstants.GPS_EXTRAPOLATE_AFTER_MS) continue
                val mps = (speedKmh ?: 0.0) / 3.6
                val progress = tracker.extrapolate(mps * (now - lastAdvanceAtMs) / 1000.0, silentMs)
                lastAdvanceAtMs = now
                announce(progress)
                updateRouteProfile(progress)
                updateZone(progress)
                // Un recalcul a pu aboutir pendant la suspension de l'annonce : on abandonne la progression périmée.
                if (progress.routeId == route.id) publish(guiding(progress, now, gpsLost = silentMs > GuidanceConstants.GPS_LOST_BADGE_AFTER_MS))
            }
        }

        private fun refreshSpeedLimit() {
            if (limitJob?.isActive == true) return
            val snapshot = recent.toList()
            limitJob = runScope.launch {
                val result = try {
                    speedLimits.speedLimitKmh(snapshot)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Outcome.Failure(AppError.Unknown(e.message))
                }
                providerLimitKmh = when (result) {
                    is Outcome.Success -> result.value
                    is Outcome.Failure -> null
                }
            }
        }

        private suspend fun checkOverSpeed() {
            val (limit, _) = effectiveLimit()
            val v = speedKmh
            val over = limit != null && v != null && v > limit + prefs.speedAlertToleranceKmh.first()
            if (over && !overSpeedAlerted) {
                overSpeedAlerted = true
                if (prefs.speedAlertSound.first() && !_muted.value && prefs.voiceEnabled.first()) speech.beep()
            } else if (!over) {
                overSpeedAlerted = false
            }
        }

        /** [priority] : NOW et arrivée URGENT, NEAR / départ / recalcul NORMAL, FAR / vigilance LOW (B5). */
        private suspend fun say(text: String, routeId: String? = null, priority: SpeechPriority = SpeechPriority.NORMAL) {
            if (text.isBlank() || _muted.value || !prefs.voiceEnabled.first()) return
            // Revérifié après la suspension : un arrêt ou un nouveau départ a pu survenir entre-temps.
            if (gen != generation) return
            // Idem pour un recalcul abouti entre-temps : une annonce issue de l'ancien tracé serait fausse.
            if (routeId != null && routeId != route.id) return
            speech.speak(text, priority = priority, channel = VoiceChannel.GUIDANCE)
        }

        private fun publish(s: NavigationState) {
            synchronized(stateLock) { if (gen == generation) _state.value = s }
        }

        fun guiding(progress: RouteProgress, now: Long, gpsLost: Boolean): NavigationState.Guiding {
            val ms = route.maneuvers
            val next = progress.nextManeuverIndex.coerceIn(0, ms.lastIndex)
            val (limit, lowered) = effectiveLimit()
            return NavigationState.Guiding(
                route = route,
                progress = progress,
                speedKmh = rawSpeedKmh,
                speedLimitKmh = limit,
                hazards = hazards,
                activeZone = activeZone,
                radarActive = radarActive,
                radarNext = radarNext,
                raining = raining,
                limitLoweredByRain = lowered,
                upcoming = ms.subList(min(next + 1, ms.size), min(next + 1 + GuidanceConstants.UPCOMING_COUNT, ms.size)),
                lanes = ms[next].lanes,
                etaEpochMs = now + (progress.remainingDurationS * 1000).roundToLong(),
                gpsLost = gpsLost,
                rerouting = rerouting,
                scooter = scooterUi,
                roundabout = RoundaboutResolver.current(roundabouts, next),
                nextSpeedLimit = nextLimit,
                freeFlowToll = tollTrip,
            )
        }
    }

    /** Mémorise la position du véhicule à l'arrivée ; une erreur de stockage est sans effet sur le guidage. */
    private fun rememberParking(point: GeoPoint) {
        val label = lastDestinationName
        val at = clock.nowMs()
        scope.launch {
            try {
                driveMemory.setParkedVehicle(ParkedVehicle(point, at, label))
            } catch (e: CancellationException) { throw e } catch (e: Exception) { /* position non mémorisée */ }
        }
    }

    private companion object {
        const val LIMIT_HORIZON_S = 20.0
        const val LIMIT_HORIZON_MIN_M = 250.0
        const val LIMIT_HORIZON_MAX_M = 1_500.0
        /** Zones observées : de 500 m derrière (sortie) à 6 km devant (préavis et affichage). */
        const val RADAR_BEHIND_M = 500.0
        const val RADAR_AHEAD_M = 6_000.0
    }
}

/** Réglages d'une course trottinette transmis par l'écran d'itinéraires. */
data class ScooterGuidanceConfig(
    val request: fr.roadly.core.model.ScooterRouteRequest,
    /** Batterie saisie par l'utilisateur (%), null = inconnue : aucune autonomie n'est alors affichée. */
    val batteryPct: Double?,
    val temperatureC: Double?,
    val calibration: fr.roadly.core.scooter.range.RangeCalibration,
    val withOsmTags: Boolean,
)

internal fun Tier.toStage(): AnnouncementStage = when (this) {
    Tier.FAR -> AnnouncementStage.FAR
    Tier.NEAR -> AnnouncementStage.NEAR
    Tier.NOW -> AnnouncementStage.NOW
}
