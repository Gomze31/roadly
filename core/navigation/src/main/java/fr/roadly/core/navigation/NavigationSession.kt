package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.model.RouteRequest
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.roundToLong

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
    dispatchers: DispatcherProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val state: StateFlow<NavigationState> = _state.asStateFlow()

    private val _muted = MutableStateFlow(false)

    /** Sourdine demandée par l'utilisateur pour la course (indépendante du réglage « voix »). */
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private val confined = dispatchers.default.limitedParallelism(1)
    private var job: Job? = null
    @Volatile private var generation = 0
    private var lastOption: RouteOption? = null
    private var lastPreferences = RoutePreferences()

    /** Démarre (ou remplace) la course sur [option] ; les [preferences] sont réutilisées lors des recalculs. */
    fun start(option: RouteOption, preferences: RoutePreferences) {
        cancelRun()
        lastOption = option
        lastPreferences = preferences
        if (location.permissionState.value != LocationPermissionState.GRANTED) {
            _state.value = NavigationState.Failed(AppError.PermissionDenied)
            return
        }
        val tracker = try {
            RouteTracker(option.route)
        } catch (e: IllegalArgumentException) {
            _state.value = NavigationState.Failed(AppError.NoResult)
            return
        }
        val run = Run(++generation, option.route, tracker, preferences)
        _state.value = run.guiding(tracker.initialProgress(), clock.nowMs(), gpsLost = false)
        location.setGuidanceActive(true)
        serviceController.start()
        job = scope.launch(confined) { run.execute() }
    }

    /** Arrête la course : `Idle`, GPS relâché, parole coupée et moteur de synthèse libéré. */
    fun stop() {
        cancelRun()
        _state.value = NavigationState.Idle
        speech.release()
    }

    /** Relance la dernière course demandée (après un `Failed`, par exemple). */
    fun retry() {
        lastOption?.let { start(it, lastPreferences) }
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        if (muted) speech.stop()
    }

    private fun cancelRun() {
        generation++
        job?.cancel()
        job = null
        location.setGuidanceActive(false)
        speech.stop()
        // La sourdine appartient à la course : le service de synthèse (singleton) est rendu tel quel.
        speech.setMuted(false)
    }

    /** Une course : son état n'est lu et écrit que sur [confined]. */
    private inner class Run(val gen: Int, var route: Route, var tracker: RouteTracker, val routePreferences: RoutePreferences) {
        private val destination: GeoPoint = route.geometry.last()
        private val costing = route.costing
        private val announced = mutableSetOf<Pair<Int, Tier>>()
        private val smoother = SpeedSmoother()
        private val recent = ArrayDeque<LocationFix>()
        private var speedKmh: Double? = null
        private var speedLimitKmh: Int? = null
        private var lastFixAtMs = clock.nowMs()
        private var lastAdvanceAtMs = lastFixAtMs
        private var hadFix = false
        private var lastRerouteAtMs: Long? = null
        private var rerouting = false
        private var overSpeedAlerted = false
        private var limitJob: Job? = null
        private lateinit var runScope: CoroutineScope

        suspend fun execute() = coroutineScope {
            runScope = this
            speech.setVolume(prefs.voiceVolume.first())
            launch { prefs.voiceVolume.collect { speech.setVolume(it) } }
            launch {
                combine(prefs.voiceEnabled, _muted) { enabled, muted -> muted || !enabled }
                    .distinctUntilChanged()
                    .collect { speech.setMuted(it) }
            }
            val depart = route.maneuvers.first()
            announced += 0 to Tier.NOW
            say(depart.verbalPre ?: depart.instruction)
            launch { watchdog() }
            location.fixes.collect { onFix(it) }
        }

        private suspend fun onFix(fix: LocationFix) {
            val now = clock.nowMs()
            // Premier fix après une longue coupure (tunnel) : le suivi peut le voir hors itinéraire à tort, on attend le suivant.
            val afterLongGap = hadFix && now - lastFixAtMs > GuidanceConstants.REROUTE_MIN_INTERVAL_MS
            hadFix = true
            lastFixAtMs = now
            lastAdvanceAtMs = now
            recent.addLast(fix)
            while (recent.size > GuidanceConstants.RECENT_FIXES) recent.removeFirst()
            speedKmh = smoother.update(fix.speedMps?.let { it * 3.6 })
            val progress = tracker.update(fix)
            if (tracker.hasArrived) { arrive(); return }
            if (tracker.isOffRoute && !afterLongGap) maybeReroute(fix, now)
            announce(progress)
            refreshSpeedLimit()
            checkOverSpeed()
            publish(guiding(progress, now, gpsLost = false))
        }

        private suspend fun announce(progress: RouteProgress) {
            if (rerouting) return
            val a = AnnouncementPolicy.next(progress, route.maneuvers, speedKmh, announced) ?: return
            announced += a.maneuverIndex to a.tier
            say(a.text)
        }

        private suspend fun arrive() {
            val last = route.maneuvers.lastIndex
            if ((last to Tier.NOW) !in announced) {
                announced += last to Tier.NOW
                val m = route.maneuvers[last]
                say(m.verbalPre ?: m.instruction)
            }
            publish(NavigationState.Arrived(route))
            if (gen == generation) {
                location.setGuidanceActive(false)
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
            say(SpokenPhrases.REROUTING)
            runScope.launch {
                val result = try {
                    routing.route(RouteRequest(origin = fix.point, destination = destination, costing = costing, preferences = routePreferences))
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
                    announced.clear()
                    announced += 0 to Tier.NOW
                    freshTracker.update(fix)
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
                publish(guiding(progress, now, gpsLost = silentMs > GuidanceConstants.GPS_LOST_BADGE_AFTER_MS))
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
                speedLimitKmh = when (result) {
                    is Outcome.Success -> result.value
                    is Outcome.Failure -> null
                }
            }
        }

        private suspend fun checkOverSpeed() {
            val limit = speedLimitKmh
            val v = speedKmh
            val over = limit != null && v != null && v > limit + prefs.speedAlertToleranceKmh.first()
            if (over && !overSpeedAlerted) {
                overSpeedAlerted = true
                if (prefs.speedAlertSound.first() && !_muted.value) speech.beep()
            } else if (!over) {
                overSpeedAlerted = false
            }
        }

        private suspend fun say(text: String) {
            if (text.isBlank() || gen != generation || _muted.value || !prefs.voiceEnabled.first()) return
            speech.speak(text)
        }

        private fun publish(s: NavigationState) {
            if (gen == generation) _state.value = s
        }

        fun guiding(progress: RouteProgress, now: Long, gpsLost: Boolean): NavigationState.Guiding {
            val ms = route.maneuvers
            val next = progress.nextManeuverIndex.coerceIn(0, ms.lastIndex)
            return NavigationState.Guiding(
                route = route,
                progress = progress,
                speedKmh = speedKmh,
                speedLimitKmh = speedLimitKmh,
                upcoming = ms.subList(min(next + 1, ms.size), min(next + 1 + GuidanceConstants.UPCOMING_COUNT, ms.size)),
                lanes = ms[next].lanes,
                etaEpochMs = now + (progress.remainingDurationS * 1000).roundToLong(),
                gpsLost = gpsLost,
                rerouting = rerouting,
            )
        }
    }
}
