package fr.roadly.feature.route

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.DeparturePlanner
import kotlinx.coroutines.flow.map
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationMode
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.model.VehicleProfile
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.common.AppError
import fr.roadly.core.data.repository.BatteryReading
import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.model.ScooterRouteRequest
import fr.roadly.core.model.TravelMode
import fr.roadly.core.model.motorVehicle
import fr.roadly.core.navigation.ScooterGuidanceConfig
import fr.roadly.core.navigation.ScooterServices
import fr.roadly.core.navigation.VigilanceZoneProvider
import fr.roadly.core.navigation.RouteAttributesProvider
import fr.roadly.core.navigation.RouteIdentity
import kotlinx.coroutines.flow.collectLatest
import kotlin.coroutines.cancellation.CancellationException
import fr.roadly.core.routing.ScooterRouteOptionsUseCase
import fr.roadly.core.scooter.assistant.ScooterAssistantContext
import fr.roadly.core.scooter.guidance.CategoryAhead
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.routing.RouteAlternativesUseCase
import fr.roadly.core.routing.RouteOption
import fr.roadly.core.routing.RoutingProvider
import fr.roadly.core.routing.StopOrdering
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Clé du `savedStateHandle` de l'entrée de pile par laquelle la recherche (mode « choisir un arrêt ») renvoie un lieu (JSON). */
const val PICKED_STOP_KEY = "pickedStop"

@HiltViewModel
class RouteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alternatives: RouteAlternativesUseCase,
    private val vehicleRepo: VehicleProfileRepository,
    private val location: LocationRepository,
    private val config: ProviderConfigRepository,
    private val prefs: UserPreferencesRepository,
    private val guidance: GuidanceActions,
    private val routing: RoutingProvider,
    /** Services du mode trottinette ; null (tests existants) = seuls les modes voiture / moto existent. */
    private val scooter: ScooterServices? = null,
    /** Zones de danger le long de chaque variante ; null (tests existants) = pas de décompte. */
    private val dangerZoneProvider: VigilanceZoneProvider? = null,
    /** S5 : profil réel (limitations, portiques) de chaque variante ; null (tests existants) = pas de carte d'identité. */
    private val routeAttributes: RouteAttributesProvider? = null,
    /** Heure choisie sur la carte « Départ » de l'accueil ; null (tests existants) = départ immédiat. */
    private val departurePlanner: DeparturePlanner? = null,
    /** Étoile de l'en-tête ; null (tests existants) = pas d'étoile. */
    private val favorites: FavoritesRepository? = null,
    /** Base nationale des ZFE ; null (tests existants) = pas d'alerte ZFE. */
    private val zfe: fr.roadly.core.data.zfe.ZfeSource? = null,
) : ViewModel() {

    // Arguments lus par nom de champ de RouteRoute : c'est ce que fait toRoute(), mais sans
    // passer par Bundle (indisponible en test unitaire JVM sans Robolectric).
    private val destName: String = savedStateHandle.get<String>("destName").orEmpty()
    private val destination = GeoPoint(
        requireNotNull(savedStateHandle.get<Double>("destLat")) { "destLat manquant" },
        requireNotNull(savedStateHandle.get<Double>("destLon")) { "destLon manquant" },
    )
    /** Départ choisi par l'inversion ; null = position actuelle. */
    private val fixedOrigin: GeoPoint? = savedStateHandle.get<Double>("fromLat")?.let { lat -> savedStateHandle.get<Double>("fromLon")?.let { lon -> GeoPoint(lat, lon) } }
    /** Nom du départ affiché (« Partir de … ») ; null = « Ma position ». */
    val originName: String? = if (fixedOrigin != null) savedStateHandle.get<String>("fromName") ?: "" else null
    private val destIsMyPosition: Boolean = savedStateHandle.get<Boolean>("destIsMyPosition") ?: false
    private val origin = MutableStateFlow(fixedOrigin ?: location.lastKnown?.point)
    private val plannedAtOpen = departurePlanner?.active(System.currentTimeMillis())
    private val departAt = MutableStateFlow(plannedAtOpen?.takeIf { !it.arrive }?.epochMs ?: System.currentTimeMillis())
    /** Heure d'arrivée souhaitée ; null = mode « Partir à ». */
    private val arriveAt = MutableStateFlow<Long?>(plannedAtOpen?.takeIf { it.arrive }?.epochMs)
    private val stops = MutableStateFlow<List<Place>>(emptyList())
    private val optimizing = MutableStateFlow(false)
    private val activeId = MutableStateFlow<String?>(null)
    private val isDark = MutableStateFlow(true)
    private val computed = MutableStateFlow<RouteUiState>(RouteUiState.Loading)
    private val reload = MutableStateFlow(0)
    /** Préférences du dernier calcul affiché : transmises à la session (et donc au recalcul). */
    private var computedWith = RoutePreferences()
    private var computedVehicle: VehicleProfile? = null
    /** Course trottinette du dernier calcul affiché (transmise au guidage). */
    private var computedScooter: ScooterGuidanceConfig? = null
    private val assistantReply = MutableStateFlow<String?>(null)

    /** Mode actif : choix mémorisé, sinon déduit du profil véhicule (aucun changement pour les profils existants). */
    private val travelMode: StateFlow<TravelMode> = combine(scooter?.mobility?.travelMode ?: flowOf(null), vehicleRepo.profile) { saved, profile ->
        saved?.takeIf { it.available && (scooter != null || it != TravelMode.ELECTRIC_SCOOTER) } ?: TravelMode.derivedFrom(profile)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TravelMode.CAR)

    private data class ScooterInputs(
        val profile: ElectricScooterProfile?, val preferences: ScooterRoutePreferences, val battery: BatteryReading?, val osm: Boolean,
    )
    private val scooterInputs = scooter?.let { sc ->
        combine(sc.activeProfile, sc.mobility.scooterRoutePreferences, sc.mobility.scooterBattery, sc.mobility.scooterOsmEnrichment) { p, prefs, b, osm -> ScooterInputs(p, prefs, b, osm) }
    } ?: flowOf(null)

    private data class Params(val origin: GeoPoint?, val departAt: Long, val arriveAt: Long?, val vehicle: VehicleProfile?, val preferences: RoutePreferences, val stops: List<Place>)

    init {
        // Ouverture à froid (lien geo:, raccourci, Android Auto) : aucune position encore connue. Constaté sur le HONOR : l'écran restait
        // sur « Position inconnue ». On demande la position et on prend la première, sauf si un départ a été choisi entre-temps.
        if (origin.value == null && fixedOrigin == null) viewModelScope.launch {
            location.setMode(LocationMode.MAP)
            val first = location.fixes.first()
            if (origin.value == null) origin.value = first.point
        }
        scooter?.let { sc -> viewModelScope.launch { sc.ensureProfile() } }
        viewModelScope.launch {
            val base = combine(origin, departAt, arriveAt, vehicleRepo.profile, prefs.routePreferences, stops, reload) { values ->
                @Suppress("UNCHECKED_CAST")
                Params(values[0] as GeoPoint?, values[1] as Long, values[2] as Long?, values[3] as VehicleProfile?, values[4] as RoutePreferences, values[5] as List<Place>)
            }
            combine(base, travelMode, scooterInputs) { params, mode, sIn -> Triple(params, mode, sIn) }.collect { (params, mode, sIn) ->
                val (o, d, a, saved, p, s) = params
                if (o == null) { computed.value = RouteUiState.NoOrigin(destName); return@collect }
                computed.value = RouteUiState.Loading
                if (mode == TravelMode.ELECTRIC_SCOOTER && scooter != null) {
                    computeScooter(o, d, a, s, sIn)
                    return@collect
                }
                computedScooter = null
                val v = mode.motorVehicle(saved)
                when (val r = alternatives(o, destination, v, d, p, s.map { it.point })) {
                    is Outcome.Success -> {
                        val (options, effectiveDepart) = if (a != null) shiftToArrival(r.value, a) else r.value to d
                        // L'option active doit toujours exister dans la liste affichée.
                        if (activeId.value == null || options.none { it.route.id == activeId.value }) activeId.value = options.first().route.id
                        computedWith = p
                        computedVehicle = v
                        computed.value = RouteUiState.Content(o, destName, options, activeId.value!!, saved != null, effectiveDepart, a, p, s)
                    }
                    is Outcome.Failure -> computed.value = RouteUiState.Error(r.error)
                }
            }
        }
    }

    /** Variantes trottinette : profil actif, préférences trottinette, batterie saisie, température Open-Meteo. */
    private suspend fun computeScooter(o: GeoPoint, departAtMs: Long, arriveAtMs: Long?, stopsNow: List<Place>, inputs: ScooterInputs?) {
        val sc = scooter ?: return
        val inp = inputs ?: run { computed.value = RouteUiState.Error(AppError.NoResult); return }
        val profile = inp.profile ?: run { computed.value = RouteUiState.Error(AppError.NoResult); return }
        val prefsNow = inp.preferences
        val temperature = sc.temperatureAt(o)
        val calibration = sc.calibration(profile)
        val battery = inp.battery
        val params = ScooterRouteOptionsUseCase.Params(
            o, destination, profile, prefsNow, departAtMs, stopsNow.map { it.point },
            batteryPct = battery?.pct?.toDouble(), temperatureC = temperature, calibration = calibration, withOsmTags = inp.osm,
        )
        when (val r = sc.options(params)) {
            is Outcome.Success -> {
                val (options, effectiveDepart) = if (arriveAtMs != null) shiftToArrival(r.value, arriveAtMs) else r.value to departAtMs
                if (activeId.value == null || options.none { it.route.id == activeId.value }) activeId.value = options.first().route.id
                computedWith = RoutePreferences()
                computedVehicle = null
                computedScooter = ScooterGuidanceConfig(ScooterRouteRequest(profile, prefsNow), battery?.pct?.toDouble(), temperature, calibration, inp.osm)
                val ageMin = battery?.let { (System.currentTimeMillis() - it.atEpochMs).coerceAtLeast(0) / 60_000 }
                computed.value = RouteUiState.Content(
                    o, destName, options, activeId.value!!, hasVehicleProfile = true, departAtEpochMs = effectiveDepart, arriveAtEpochMs = arriveAtMs,
                    stops = stopsNow, scooter = ScooterRouteUi(profile.name, prefsNow, battery?.pct, ageMin, temperature, inp.osm),
                )
            }
            is Outcome.Failure -> computed.value = RouteUiState.Error(r.error)
        }
    }

    /** Radar Intelligence : zones de danger de chaque variante (même calcul que le guidage : sens, route, réglages, zones masquées). */
    private val dangerZoneCounts = MutableStateFlow<Map<String, Int>>(emptyMap())

    init {
        val provider = dangerZoneProvider
        if (provider != null) viewModelScope.launch {
            computed.collectLatest { s ->
                dangerZoneCounts.value = emptyMap()
                if (s !is RouteUiState.Content || s.scooter != null) return@collectLatest
                val counts = HashMap<String, Int>()
                for (o in s.options) {
                    try {
                        counts[o.route.id] = provider.zonesAlong(o.route).size
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Base locale illisible : pas de décompte pour cette variante, rien d'inventé.
                    }
                    dangerZoneCounts.value = counts.toMap()
                }
            }
        }
    }

    /** ZFE traversées par chaque variante, avec le verdict pour le véhicule (Crit'Air) à l'heure de départ ; vide si désactivé. */
    private val zfeCrossings = MutableStateFlow<Map<String, List<fr.roadly.core.data.zfe.ZfeCrossing>>>(emptyMap())

    init {
        val source = zfe
        if (source != null) viewModelScope.launch {
            combine(computed, prefs.zfeEnabled, vehicleRepo.profile) { s, on, v -> Triple(s, on, v) }.collectLatest { (s, on, v) ->
                zfeCrossings.value = emptyMap()
                if (!on || s !is RouteUiState.Content || s.scooter != null) return@collectLatest
                val rules = try { source.rules() } catch (e: CancellationException) { throw e } catch (e: Exception) { emptyList() }
                if (rules.isEmpty()) return@collectLatest
                val at = java.time.Instant.ofEpochMilli(s.departAtEpochMs).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
                val cls = fr.roadly.core.data.zfe.ZfeVehicleClass.of(v?.type)
                zfeCrossings.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    s.options.associate { o -> o.route.id to fr.roadly.core.data.zfe.ZfeRules.crossings(o.route.geometry, rules, cls, v?.critAir, at) }
                }
            }
        }
    }

    /**
     * S5 : carte d'identité de chaque variante (part à limitation connue, temps au-dessus de 90, portiques en flux libre). Une requête
     * `trace_attributes` par variante, mise en cache par le fournisseur (le guidage réutilise celle de la variante choisie).
     */
    private val identities = MutableStateFlow<Map<String, RouteIdentity>>(emptyMap())

    init {
        val provider = routeAttributes
        if (provider != null) viewModelScope.launch {
            computed.collectLatest { s ->
                identities.value = emptyMap()
                if (s !is RouteUiState.Content || s.scooter != null) return@collectLatest
                // Variante active d'abord : c'est celle que l'écran montre.
                val ordered = s.options.sortedByDescending { it.route.id == activeId.value }
                val found = HashMap<String, RouteIdentity>()
                for (o in ordered) {
                    val r = try { provider.attributes(o.route) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
                    // Échec (réseau, tracé non reconnu) : pas de carte pour cette variante, rien d'inventé.
                    if (r is Outcome.Success) { found[o.route.id] = RouteIdentity.from(r.value); identities.value = found.toMap() }
                }
            }
        }
    }

    val uiState: StateFlow<RouteScreenState> = combine(combine(computed, activeId, isDark, config.config, optimizing) { s, id, dark, cfg, opt ->
        val phase = if (s is RouteUiState.Content) s.copy(activeId = id ?: s.activeId, optimizing = opt) else s
        RouteScreenState(styleUrl = if (dark) cfg.mapStyleDarkUrl else cfg.mapStyleLightUrl, phase = phase)
    }, travelMode, assistantReply, combine(dangerZoneCounts, zfeCrossings) { d, z -> d to z }, identities) { st, mode, reply, zones, ids ->
        val phase = (st.phase as? RouteUiState.Content)?.copy(dangerZones = zones.first, zfe = zones.second, identities = ids) ?: st.phase
        st.copy(phase = phase, travelMode = mode, scooterAvailable = scooter != null, assistantReply = reply)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RouteScreenState())

    // ── Mode trottinette ──
    fun setTravelMode(mode: TravelMode) {
        val sc = scooter ?: return
        if (!mode.available) return
        viewModelScope.launch { sc.mobility.setTravelMode(mode) }
    }
    fun setScooterPreferences(value: ScooterRoutePreferences) { scooter?.let { sc -> viewModelScope.launch { sc.mobility.setScooterRoutePreferences(value) } } }
    /** Saisie manuelle du niveau de batterie (Roadly n'est pas connecté à la trottinette). */
    fun setBattery(pct: Int?) {
        val sc = scooter ?: return
        viewModelScope.launch { sc.mobility.setScooterBattery(pct?.let { BatteryReading(it.coerceIn(0, 100), System.currentTimeMillis()) }) }
    }

    /** Roadly AI (texte reconnu par la reconnaissance vocale du système) : réponse affichée et prononcée. */
    fun onAssistantText(text: String) {
        val sc = scooter ?: return
        val content = computed.value as? RouteUiState.Content
        val active = content?.options?.firstOrNull { it.route.id == activeId.value }
        viewModelScope.launch {
            val ctx = ScooterAssistantContext(
                preferences = sc.mobility.scooterRoutePreferences.first(),
                rangeToDestination = active?.scooter?.range,
                nextCycleInfrastructure = active?.scooter?.analysis?.let { a -> a.segments.firstOrNull { it.category.isCycleInfrastructure }?.let { CategoryAhead(it.category, it.startAlongM, it.lengthM) } },
                analysisAvailable = active?.scooter?.analysis != null,
            )
            val reply = sc.assistant(text, ctx, origin.value, null)
            sc.speech.speak(reply.speech, flush = true)
            assistantReply.value = reply.speech
            delay(8_000)
            if (assistantReply.value == reply.speech) assistantReply.value = null
        }
    }

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun selectRoute(id: String) { activeId.value = id }
    /** Mode « Partir à » : l'heure d'arrivée souhaitée est oubliée. */
    fun setDepartAt(epochMs: Long) { arriveAt.value = null; departAt.value = epochMs }
    /** Mode « Arriver à » : le départ est déduit de la durée du plus rapide (sans trafic prédictif). */
    fun setArriveAt(epochMs: Long) { arriveAt.value = epochMs }
    fun setPreferences(value: RoutePreferences) { viewModelScope.launch { prefs.setRoutePreferences(value) } }
    fun retry() { reload.value++ }
    /** Origine choisie par appui long sur la carte quand la position est inconnue. */
    fun setOrigin(point: GeoPoint) { origin.value = point }
    val destinationPoint: GeoPoint get() = destination

    /**
     * Inversion départ / arrivée : depuis « Ma position », on part de la destination pour revenir à la position actuelle ;
     * une nouvelle inversion repart de la position réelle. Null tant que le départ est inconnu.
     */
    fun swapped(myPositionLabel: String): RouteRoute? {
        val o = origin.value ?: return null
        val fromLabel = originName
        return if (destIsMyPosition) RouteRoute(o.lat, o.lon, fromLabel ?: destName)
        else RouteRoute(
            destLat = o.lat, destLon = o.lon, destName = fromLabel?.ifBlank { null } ?: myPositionLabel,
            fromLat = destination.lat, fromLon = destination.lon, fromName = destName, destIsMyPosition = fromLabel == null,
        )
    }

    /** Favori correspondant à la destination (même point à [FAVORITE_MATCH_M] près, l'écran ne reçoit pas l'identifiant du lieu). */
    private val matchingFavorite: StateFlow<Place?> = (favorites?.observeAll() ?: flowOf(emptyList()))
        .map { list -> list.firstOrNull { it.point.distanceTo(destination) <= FAVORITE_MATCH_M } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    /** null = favoris indisponibles (pas d'étoile). */
    val isFavorite: StateFlow<Boolean?> = matchingFavorite.map { fav -> if (favorites == null) null else fav != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, if (favorites == null) null else false)

    fun toggleFavorite() {
        val repo = favorites ?: return
        viewModelScope.launch {
            repo.toggle(matchingFavorite.value ?: Place(
                "fav:%.5f,%.5f".format(java.util.Locale.ROOT, destination.lat, destination.lon),
                destName.ifBlank { "%.5f, %.5f".format(java.util.Locale.ROOT, destination.lat, destination.lon) },
                null, destination, fr.roadly.core.model.PlaceKind.COORDINATES, "local",
            ))
        }
    }

    // ── Arrêts (≤ 10) ──
    fun addStop(place: Place) {
        val current = stops.value
        if (current.size >= StopOrdering.MAX_STOPS || current.any { it.id == place.id }) return
        stops.value = current + place
    }
    fun removeStop(index: Int) { stops.value = stops.value.filterIndexed { i, _ -> i != index } }
    fun moveStop(from: Int, to: Int) { stops.value = StopOrdering.move(stops.value, from, to) }

    /** Mode tournée : ordre optimal calculé par le fournisseur (repli local plus proche voisin). */
    fun optimizeStops() {
        val o = origin.value ?: return
        val current = stops.value
        if (current.size < 2 || optimizing.value) return
        viewModelScope.launch {
            optimizing.value = true
            val order = when (val r = routing.optimizeOrder(o, current.map { it.point }, destination, computedWith)) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> StopOrdering.nearestNeighbour(o, current.map { it.point })
            }
            if (stops.value == current) stops.value = order.map { current[it] }
            optimizing.value = false
        }
    }

    /** Démarre le guidage sur l'itinéraire sélectionné ; false si aucun itinéraire n'est affiché. */
    fun startGuidance(): Boolean {
        val content = computed.value as? RouteUiState.Content ?: return false
        val id = activeId.value ?: content.activeId
        val option = content.options.firstOrNull { it.route.id == id } ?: return false
        guidance.start(option, computedWith, computedVehicle, stops.value, destName.ifBlank { null }, scooter = computedScooter)
        departurePlanner?.clear()
        return true
    }

    companion object {
        /** Distance sous laquelle un favori est considéré comme la destination affichée. */
        const val FAVORITE_MATCH_M = 30.0

        /** Départ = arrivée souhaitée − durée du plus rapide ; chaque option reçoit l'arrivée correspondante. */
        internal fun shiftToArrival(options: List<RouteOption>, arriveAtMs: Long): Pair<List<RouteOption>, Long> {
            val reference = options.firstOrNull { it.route.costing == RouteCosting.FASTEST } ?: options.first()
            val depart = arriveAtMs - (reference.route.durationSeconds * 1000).toLong()
            return options.map { it.copy(arrivalEpochMs = depart + (it.route.durationSeconds * 1000).toLong()) } to depart
        }
    }
}
