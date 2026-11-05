package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.DispatcherProvider
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.DefaultRadarSettingsRepository
import fr.roadly.core.data.repository.RadarSettingsRepository
import fr.roadly.core.radar.DriveSample
import fr.roadly.core.radar.FreeRadarMatcher
import fr.roadly.core.radar.HeadingFilter
import fr.roadly.core.radar.RadarAlertEngine
import fr.roadly.core.radar.RadarAlertEvent
import fr.roadly.core.radar.RadarIndex
import fr.roadly.core.radar.RadarQuality
import fr.roadly.core.radar.ZoneStatus
import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.vigilance.VigilanceRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Conduite libre (1.0) : vitesse, limitation de la route et zones de vigilance, sans itinéraire. */
sealed interface FreeDriveState {
    data object Idle : FreeDriveState

    data class Active(
        val startedAtMs: Long,
        val speedKmh: Double? = null,
        /** Limitation de la route suivie (OSM via Valhalla) ; null = inconnue ou à confirmer après un virage. */
        val speedLimitKmh: Int? = null,
        val roadName: String? = null,
        val zone: FreeDriveZone? = null,
        /** Prochaine zone de danger devant (≤ 3 km). */
        val nextZone: ZoneStatus? = null,
        val distanceM: Double = 0.0,
        val gpsLost: Boolean = false,
        val lastFix: LocationFix? = null,
    ) : FreeDriveState

    data class Failed(val error: AppError) : FreeDriveState
}

/** Zone de danger traversée ; [distanceM] = distance restante jusqu'à la fin de zone (jamais l'emplacement de l'équipement). */
data class FreeDriveZone(val id: String, val limitKmh: Int?, val distanceM: Double, val confidence: Int = RadarQuality.MIN_VOICE)

/** Route sous la position : limitation et nom, tels que les donne la carte OSM. */
data class RoadSnapshot(val speedLimitKmh: Int?, val roadName: String?)

/**
 * Fréquence des requêtes de map matching sans itinéraire. Instance publique (fair-use) : au plus une requête par 20 s et 250 m
 * parcourus, ou 8 s après un virage ; à l'arrêt, aucune requête. Instance auto-hébergée : 5 s / 40 m, 3 s après un virage.
 */
data class RoadQueryPolicy(val minIntervalMs: Long, val minDistanceM: Double, val turnMinIntervalMs: Long) {
    fun shouldQuery(nowMs: Long, lastAtMs: Long?, movedM: Double, turned: Boolean): Boolean {
        if (lastAtMs == null) return true
        val elapsed = nowMs - lastAtMs
        return (elapsed >= minIntervalMs && movedM >= minDistanceM) || (turned && elapsed >= turnMinIntervalMs)
    }

    companion object {
        val PUBLIC = RoadQueryPolicy(20_000L, 250.0, 8_000L)
        val SELF_HOSTED = RoadQueryPolicy(5_000L, 40.0, 3_000L)
    }
}

interface RoadSnapshotProvider {
    suspend fun policy(): RoadQueryPolicy
    /** Map matching des derniers fixes ; Success(null) = aucune route reconnue. */
    suspend fun snapshot(recent: List<LocationFix>): Outcome<RoadSnapshot?>
}

object NoRoadSnapshotProvider : RoadSnapshotProvider {
    override suspend fun policy() = RoadQueryPolicy.PUBLIC
    override suspend fun snapshot(recent: List<LocationFix>): Outcome<RoadSnapshot?> = Outcome.Success(null)
}

/** Géométrie et règles pures de la conduite libre (testées sur JVM). */
object FreeDriveRules {
    /** Cap (°, 0 = nord) de [from] vers [to]. */
    fun bearing(from: GeoPoint, to: GeoPoint): Double {
        val k = cos(Math.toRadians(from.lat))
        val dx = (to.lon - from.lon) * k
        val dy = to.lat - from.lat
        return (Math.toDegrees(atan2(dx, dy)) + 360.0) % 360.0
    }

    fun angleDiff(a: Double, b: Double): Double = abs(((a - b + 540.0) % 360.0) - 180.0)

    /** Virage depuis la dernière requête : cap écarté de plus de 45° à plus de 3 m/s. */
    const val TURN_DEG = 45.0
    const val TURN_MIN_SPEED_MPS = 3f

    fun turned(headingAtQuery: Double?, fix: LocationFix): Boolean {
        val h = headingAtQuery ?: return false
        val b = fix.bearingDeg?.toDouble() ?: return false
        if ((fix.speedMps ?: 0f) < TURN_MIN_SPEED_MPS) return false
        return angleDiff(h, b) > TURN_DEG
    }

    /** Base radar rechargée quand on s'éloigne de plus de 2 km du centre du dernier chargement (boîte de ±0,1°). */
    const val ZONES_RELOAD_M = 2_000.0
    const val ZONES_BOX_HALF_DEG = 0.1
}

/** Valhalla `trace_attributes` (`map_snap`) sur les derniers fixes : limitation et nom de la dernière arête reconnue. */
@Singleton
class ValhallaRoadSnapshotProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
) : RoadSnapshotProvider {
    override suspend fun policy(): RoadQueryPolicy {
        val url = baseUrl()?.toHttpUrlOrNull() ?: return RoadQueryPolicy.PUBLIC
        return if (ValhallaTraceSpeedLimitProvider.isPublicInstance(url)) RoadQueryPolicy.PUBLIC else RoadQueryPolicy.SELF_HOSTED
    }

    private suspend fun baseUrl(): String? = try { config.config.first().valhallaBaseUrl } catch (e: CancellationException) { throw e } catch (e: Exception) { null }

    override suspend fun snapshot(recent: List<LocationFix>): Outcome<RoadSnapshot?> {
        if (recent.size < 2) return Outcome.Success(null)
        val url = baseUrl()?.let { "${it.trimEnd('/')}/trace_attributes".toHttpUrlOrNull() } ?: return Outcome.Failure(AppError.ProviderUnavailable("valhalla"))
        return when (val r = api.postJson(url, request(recent).toString())) {
            is Outcome.Success -> Outcome.Success(parse(json, r.value))
            is Outcome.Failure -> r
        }
    }

    companion object {
        fun request(recent: List<LocationFix>): JsonObject = buildJsonObject {
            putJsonArray("shape") { recent.forEach { f -> addJsonObject { put("lat", f.point.lat); put("lon", f.point.lon) } } }
            put("costing", "auto")
            put("shape_match", "map_snap")
            put("units", "kilometers")
            putJsonObject("filters") {
                putJsonArray("attributes") { add("edge.speed_limit"); add("edge.names"); add("matched.edge_index") }
                put("action", "include")
            }
        }

        /** Arête du dernier point reconnu (`matched_points[].edge_index`), sinon dernière arête ; null sans arête. */
        fun parse(json: Json, text: String): RoadSnapshot? = runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val edges = root["edges"] as? JsonArray ?: return null
            val matched = (root["matched_points"] as? JsonArray)?.lastOrNull { m ->
                ((m as? JsonObject)?.get("edge_index") as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull?.let { it in edges.indices } == true
            } as? JsonObject
            val index = (matched?.get("edge_index") as? JsonPrimitive)?.intOrNull
            val edge = (if (index != null) edges[index] else edges.lastOrNull()) as? JsonObject ?: return null
            val limit = (edge["speed_limit"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull?.toInt()?.takeIf { it in 1..254 }
            val name = (edge["names"] as? JsonArray)?.firstNotNullOfOrNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content?.takeIf { s -> s.isNotBlank() } }
            RoadSnapshot(limit, name)
        }.getOrNull()
    }
}

/**
 * Session de conduite libre (mode vigilance sans itinéraire), exclusive du guidage : un guidage qui démarre la termine sans
 * toucher au GPS ni à la voix, qui lui appartiennent alors. Même confinement que [NavigationSession] (parallélisme 1).
 * LIMITE : un guidage lancé pendant la conduite libre remplace le trajet en cours d'enregistrement.
 */
@Singleton
class FreeDriveSession @Inject constructor(
    private val location: LocationRepository,
    private val roads: RoadSnapshotProvider,
    private val vigilance: VigilanceRepository,
    private val speech: SpeechService,
    private val prefs: UserPreferencesRepository,
    private val serviceController: NavigationServiceController,
    private val clock: NavigationClock,
    private val tripRecorder: TripRecorder,
    private val navigation: NavigationSession,
    dispatchers: DispatcherProvider,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val radarSettings: RadarSettingsRepository = DefaultRadarSettingsRepository,
) {
    private val _state = MutableStateFlow<FreeDriveState>(FreeDriveState.Idle)
    val state: StateFlow<FreeDriveState> = _state.asStateFlow()

    private val confined = dispatchers.default.limitedParallelism(1)
    private val lock = Any()
    @Volatile private var generation = 0
    private var job: Job? = null

    /** Démarre la conduite libre ; refusé (faux) pendant un guidage ou sans permission de localisation. */
    fun start(): Boolean {
        if (navigation.state.value is NavigationState.Guiding) return false
        if (_state.value is FreeDriveState.Active) return true
        if (location.permissionState.value != LocationPermissionState.GRANTED) {
            synchronized(lock) { generation++; _state.value = FreeDriveState.Failed(AppError.PermissionDenied) }
            return false
        }
        val now = clock.nowMs()
        val gen = synchronized(lock) { (++generation).also { _state.value = FreeDriveState.Active(now) } }
        tripRecorder.begin(TRIP_LABEL, now)
        location.setGuidanceActive(true)
        serviceController.start()
        job = scope.launch(confined) { Drive(gen, now).run() }
        return true
    }

    /** Arrête la conduite libre : GPS relâché, trajet enregistré s'il est assez long. */
    fun stop() {
        if (!end()) return
        location.setGuidanceActive(false)
        speech.stop(VoiceChannel.GUIDANCE)
        speech.release()
        scope.launch { tripRecorder.end(arrived = false, nowMs = clock.nowMs()) }
    }

    /** Un guidage a démarré : la session s'efface sans relâcher le GPS ni la voix du guidage. */
    private fun endForGuidance() { end() }

    /** Vrai si une session était active. */
    private fun end(): Boolean {
        val wasActive = synchronized(lock) {
            generation++
            (_state.value is FreeDriveState.Active).also { _state.value = FreeDriveState.Idle }
        }
        job?.cancel()
        job = null
        return wasActive
    }

    private inner class Drive(val gen: Int, val startedAtMs: Long) {
        private var current = FreeDriveState.Active(startedAtMs)
        private val recent = ArrayDeque<LocationFix>()
        private var lastFixAtMs = startedAtMs
        private var policy = RoadQueryPolicy.PUBLIC
        private var lastQueryAtMs: Long? = null
        private var lastQueryPoint: GeoPoint? = null
        private var headingAtQuery: Double? = null
        private var queryJob: Job? = null
        private var radarIndex = RadarIndex(emptyList())
        private var zonesCenter: GeoPoint? = null
        private var zonesJob: Job? = null
        private val headingFilter = HeadingFilter()
        /** Sans itinéraire : 3 observations consécutives avant d'ouvrir une zone (voir `RadarAlertConfig.confirmTicks`). */
        private val radarEngine = RadarAlertEngine()
        private var overSpeedAlerted = false
        private lateinit var runScope: CoroutineScope

        suspend fun run() = coroutineScope {
            runScope = this
            policy = try { roads.policy() } catch (e: CancellationException) { throw e } catch (e: Exception) { RoadQueryPolicy.PUBLIC }
            speech.setVolume(prefs.voiceVolume.first())
            launch { navigation.state.collect { if (it is NavigationState.Guiding) endForGuidance() } }
            launch { watchdog() }
            location.fixes.collect { if (it.timestampMs >= startedAtMs - GuidanceConstants.STALE_FIX_TOLERANCE_MS) onFix(it) }
        }

        private suspend fun onFix(fix: LocationFix) {
            val now = clock.nowMs()
            lastFixAtMs = now
            val previous = recent.lastOrNull()
            val step = if (previous != null && fix.accuracyM <= GuidanceConstants.OFF_ROUTE_MAX_ACCURACY_M) previous.point.distanceTo(fix.point) else 0.0
            // Points espacés d'au moins 5 m pour le map matching : à l'arrêt, le bruit GPS n'est pas une trajectoire.
            if (previous == null || previous.point.distanceTo(fix.point) >= MATCH_MIN_SPACING_M) {
                recent.addLast(fix)
                while (recent.size > GuidanceConstants.RECENT_FIXES) recent.removeFirst()
            }
            tripRecorder.onFix(fix, now)
            val turned = FreeDriveRules.turned(headingAtQuery, fix)
            current = current.copy(
                speedKmh = fix.speedMps?.let { it * 3.6 },
                distanceM = current.distanceM + step,
                gpsLost = false,
                lastFix = fix,
                // Après un virage, la limitation de l'ancienne route n'est plus sûre : « — » jusqu'à la prochaine réponse.
                speedLimitKmh = if (turned) null else current.speedLimitKmh,
            )
            maybeQueryRoad(now, fix, turned)
            maybeReloadZones(fix.point)
            updateZone(fix, now)
            checkOverSpeed()
            publish()
        }

        private fun maybeQueryRoad(now: Long, fix: LocationFix, turned: Boolean) {
            if (queryJob?.isActive == true || recent.size < 2) return
            val moved = lastQueryPoint?.distanceTo(fix.point) ?: Double.MAX_VALUE
            if (!policy.shouldQuery(now, lastQueryAtMs, moved, turned)) return
            lastQueryAtMs = now
            lastQueryPoint = fix.point
            headingAtQuery = fix.bearingDeg?.toDouble()?.takeIf { (fix.speedMps ?: 0f) >= FreeDriveRules.TURN_MIN_SPEED_MPS }
            val snapshot = recent.toList()
            queryJob = runScope.launch {
                val r = try { roads.snapshot(snapshot) } catch (e: CancellationException) { throw e } catch (e: Exception) { Outcome.Failure(AppError.Unknown(e.message)) }
                val road = (r as? Outcome.Success)?.value
                // Échec : valeur inconnue plutôt qu'une limitation périmée.
                current = current.copy(speedLimitKmh = road?.speedLimitKmh, roadName = road?.roadName ?: current.roadName.takeIf { r is Outcome.Failure })
                publish()
            }
        }

        private fun maybeReloadZones(at: GeoPoint) {
            if (zonesJob?.isActive == true) return
            val center = zonesCenter
            if (center != null && center.distanceTo(at) < FreeDriveRules.ZONES_RELOAD_M) return
            zonesCenter = at
            zonesJob = runScope.launch {
                if (!prefs.vigilanceEnabled.first()) { radarIndex = RadarIndex(emptyList()); return@launch }
                val h = FreeDriveRules.ZONES_BOX_HALF_DEG
                val radars = try {
                    vigilance.radarsIn(BoundingBox(at.lat - h, at.lon - h, at.lat + h, at.lon + h))
                } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
                radarIndex = RadarIndex(radars)
            }
        }

        /**
         * Radar Intelligence sans itinéraire : cap filtré, trajectoire prolongée en arc, sens contrôlé, numéro de route courant
         * (map matching), puis machine à états. Tout est calculé localement, sans réseau.
         */
        private suspend fun updateZone(fix: LocationFix, now: Long) {
            headingFilter.update(fix.point, fix.bearingDeg, fix.speedMps, now)
            if (radarIndex.size == 0) { current = current.copy(zone = null, nextZone = null); return }
            val settings = radarSettings.settings.first()
            radarEngine.config = radarEngine.config.copy(prewarnM = settings.prewarnM.toDouble())
            val sample = DriveSample(fix.point, headingFilter.heading, headingFilter.curvaturePerM, fix.speedMps, fix.accuracyM, current.roadName)
            val observations = FreeRadarMatcher.match(radarIndex, sample, radarEngine.openKeys, enabled = settings.enabledKinds, lookAheadM = settings.prewarnM.toDouble()).observations
                .filter { it.key !in settings.hiddenZones }
            val out = radarEngine.update(now, observations, current.speedKmh, prefs.speedAlertToleranceKmh.first())
            current = current.copy(
                zone = out.active?.let { FreeDriveZone(it.key, it.speedLimitKmh, it.distanceM, it.confidence) },
                nextZone = out.next,
            )
            for (e in out.events) {
                val voice = e.voice && settings.voice
                when (e) {
                    is RadarAlertEvent.PreWarning -> if (voice) say(SpokenPhrases.dangerZoneAhead(e.distanceM))
                    is RadarAlertEvent.Entered -> {
                        if (voice) say(SpokenPhrases.vigilanceZone(e.speedLimitKmh))
                        if (prefs.vigilanceSound.first() && prefs.voiceEnabled.first()) speech.beep()
                    }
                    is RadarAlertEvent.Exited -> if (voice && settings.announceExit) say(SpokenPhrases.DANGER_ZONE_END)
                    is RadarAlertEvent.OverSpeed -> Unit
                }
            }
        }

        private suspend fun checkOverSpeed() {
            val limit = current.speedLimitKmh
            val v = current.speedKmh
            val over = limit != null && v != null && v > limit + prefs.speedAlertToleranceKmh.first()
            if (over && !overSpeedAlerted) {
                overSpeedAlerted = true
                if (prefs.speedAlertSound.first() && prefs.voiceEnabled.first()) speech.beep()
            } else if (!over) overSpeedAlerted = false
        }

        private suspend fun watchdog() {
            while (true) {
                delay(GuidanceConstants.WATCHDOG_TICK_MS)
                val lost = clock.nowMs() - lastFixAtMs > GuidanceConstants.GPS_LOST_BADGE_AFTER_MS
                if (lost != current.gpsLost) { current = current.copy(gpsLost = lost); publish() }
            }
        }

        private suspend fun say(text: String) {
            if (!prefs.voiceEnabled.first() || gen != generation) return
            speech.speak(text, priority = SpeechPriority.LOW, channel = VoiceChannel.GUIDANCE)
        }

        private fun publish() { synchronized(lock) { if (gen == generation) _state.value = current } }
    }

    companion object {
        /** Libellé du trajet enregistré dans l'historique. */
        const val TRIP_LABEL = "Conduite libre"
        const val MATCH_MIN_SPACING_M = 5.0
    }
}
