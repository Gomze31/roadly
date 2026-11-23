package fr.roadly.core.navigation

import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.data.repository.TripRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Trip
import fr.roadly.core.model.TripPoint
import fr.roadly.core.model.TravelMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import fr.roadly.core.routing.FuelCostCalculator
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Contexte du trajet en cours (mode trottinette : profil et batterie saisie au départ). */
data class TripContext(val travelMode: TravelMode = TravelMode.CAR, val scooterProfileId: Long? = null, val batteryStartPct: Int? = null)

/** Valeurs estimées en fin de course par le guidage trottinette ; null = inconnues. */
data class TripExtras(val ascentM: Double?, val descentM: Double?, val energyModelWh: Double?)

/** Enregistreur de trajet piloté par la course : début, fixes, fin (arrivée ou arrêt). */
interface TripRecorder {
    fun begin(destinationName: String, startedAtMs: Long)
    fun onFix(fix: LocationFix, nowMs: Long)
    /** Clôt le trajet en cours ; ignoré s'il est trop court ([MIN_DISTANCE_M]) ou s'il n'y a pas de trajet. */
    suspend fun end(arrived: Boolean, nowMs: Long)
    /** Appelé juste après [begin]. */
    fun setContext(context: TripContext) {}
    /** Appelé juste avant [end]. */
    fun setExtras(extras: TripExtras) {}
    /** Dernier trajet enregistré (écran récapitulatif). */
    val lastSaved: StateFlow<Trip?> get() = NO_TRIP

    companion object {
        const val MIN_DISTANCE_M = 200.0; const val SAMPLE_M = 20.0
        val NO_TRIP: StateFlow<Trip?> = MutableStateFlow<Trip?>(null).asStateFlow()
    }
}

class NoopTripRecorder @Inject constructor() : TripRecorder {
    override fun begin(destinationName: String, startedAtMs: Long) = Unit
    override fun onFix(fix: LocationFix, nowMs: Long) = Unit
    override suspend fun end(arrived: Boolean, nowMs: Long) = Unit
}

/**
 * Stockage local uniquement (Room) ; points échantillonnés tous les ≥ 20 m ; coût d'après le profil véhicule du moment.
 *
 * NAV-1 : le tracé n'existe qu'en mémoire jusqu'à [end]. Pour qu'un trajet ne disparaisse pas avec le processus (arrêt de
 * l'application par le téléphone écran éteint), il est sauvegardé en roulant dans [checkpoint], au plus tard toutes les
 * [CHECKPOINT_INTERVAL_MS] ou tous les [CHECKPOINT_DISTANCE_M]. Une sauvegarde trouvée au démarrage du processus ([recoverLeftover],
 * lancé à la création) ou au [begin] suivant devient un trajet enregistré « non arrivé », arrêté à la dernière sauvegarde.
 */
@Singleton
class RoomTripRecorder @Inject constructor(
    private val trips: TripRepository,
    private val vehicles: VehicleProfileRepository,
    private val checkpoint: TripCheckpointStore,
    @param:ApplicationScope private val scope: CoroutineScope,
) : TripRecorder {
    private var lastCheckpointAtMs = 0L
    private var lastCheckpointDistanceM = 0.0
    /** Un trajet terminé est en cours d'écriture en base : sa sauvegarde n'est pas un reste d'un processus précédent. */
    private var saving = false

    /**
     * Enregistre le trajet d'une sauvegarde laissée par un processus précédent, puis l'efface. Sans effet pendant un trajet (la
     * sauvegarde est alors la sienne), sans sauvegarde, ou si elle est illisible ou trop courte ([TripRecorder.MIN_DISTANCE_M]).
     * La sauvegarde est effacée avant l'écriture en base : un trajet récupéré ne peut pas être enregistré deux fois.
     */
    suspend fun recoverLeftover() {
        val leftover = synchronized(this) { if (destination != null || saving) null else checkpoint.take() } ?: return
        saveRecovered(leftover)
    }

    private suspend fun saveRecovered(s: TripSnapshot) {
        var distance = 0.0
        for (i in 1 until s.points.size) distance += s.points[i - 1].point.distanceTo(s.points[i].point)
        if (distance < TripRecorder.MIN_DISTANCE_M) return
        val endedAt = s.startedAtMs + s.points.last().tMs
        try {
            trips.save(withCost(trip(s.destinationName, s.startedAtMs, endedAt, distance, s.points.maxOf { it.speedKmh ?: 0.0 }, false, s.points, s.context, null)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Base indisponible : le trajet récupéré est perdu, rien d'autre n'est touché.
        }
    }

    private fun trip(
        dest: String, startedAtMs: Long, endedAtMs: Long, distance: Double, maxSpeedKmh: Double, arrived: Boolean,
        points: List<TripPoint>, ctx: TripContext, x: TripExtras?,
    ) = Trip(
        0, startedAtMs, endedAtMs, dest, distance, (endedAtMs - startedAtMs) / 1000, maxSpeedKmh, null, arrived, points,
        travelMode = ctx.travelMode, ascentM = x?.ascentM, descentM = x?.descentM, energyModelWh = x?.energyModelWh,
        batteryStartPct = ctx.batteryStartPct, scooterProfileId = ctx.scooterProfileId,
    )

    /** Trottinette, vélo : jamais de coût carburant calculé avec le profil voiture. */
    private suspend fun withCost(trip: Trip): Trip {
        if (trip.travelMode == TravelMode.ELECTRIC_SCOOTER || trip.travelMode == TravelMode.BICYCLE) return trip
        val profile = runCatching { vehicles.profile }.getOrNull()?.first()
        return trip.copy(costEur = profile?.let { FuelCostCalculator.estimate(trip.distanceM, it)?.fuelEur })
    }

    private var destination: String? = null
    private var startedAt = 0L
    private val points = ArrayList<TripPoint>()
    private var distanceM = 0.0
    private var maxSpeed = 0.0
    private var last: GeoPoint? = null
    private var context = TripContext()
    private var extras: TripExtras? = null
    private val _lastSaved = MutableStateFlow<Trip?>(null)
    override val lastSaved: StateFlow<Trip?> = _lastSaved.asStateFlow()

    // Après tous les champs : la récupération peut s'exécuter aussitôt, sur un autre fil.
    init {
        scope.launch { recoverLeftover() }
    }

    @Synchronized override fun begin(destinationName: String, startedAtMs: Long) {
        // Un trajet était en cours dans ce processus : il est remplacé, comme avant, et sa sauvegarde avec lui. Sinon la sauvegarde
        // présente vient d'un processus précédent (récupération du démarrage pas encore passée) : elle devient un trajet enregistré.
        val leftover = if (destination != null || saving) { checkpoint.delete(); null } else checkpoint.take()
        destination = destinationName; startedAt = startedAtMs
        points.clear(); distanceM = 0.0; maxSpeed = 0.0; last = null
        context = TripContext(); extras = null
        lastCheckpointAtMs = startedAtMs; lastCheckpointDistanceM = 0.0
        if (leftover != null) scope.launch { saveRecovered(leftover) }
    }

    @Synchronized override fun setContext(context: TripContext) { this.context = context }
    @Synchronized override fun setExtras(extras: TripExtras) { this.extras = extras }

    @Synchronized override fun onFix(fix: LocationFix, nowMs: Long) {
        if (destination == null) return
        val prev = last
        val d = prev?.distanceTo(fix.point) ?: Double.MAX_VALUE
        if (prev != null && d < TripRecorder.SAMPLE_M) return
        if (prev != null) distanceM += d
        last = fix.point
        val v = fix.speedMps?.let { it * 3.6 }
        if (v != null && v > maxSpeed) maxSpeed = v
        points += TripPoint(nowMs - startedAt, fix.point, v)
        // Sauvegarde périodique (NAV-1), seulement quand un point vient d'être ajouté : à l'arrêt, la précédente reste exacte.
        // Écrite ici, sur le fil du guidage : quelques millisecondes toutes les 30 s au plus, et l'ordre des écritures est garanti.
        if (nowMs - lastCheckpointAtMs >= CHECKPOINT_INTERVAL_MS || distanceM - lastCheckpointDistanceM >= CHECKPOINT_DISTANCE_M) {
            lastCheckpointAtMs = nowMs; lastCheckpointDistanceM = distanceM
            checkpoint.write(TripSnapshot(destination!!, startedAt, context, points.toList()))
        }
    }

    override suspend fun end(arrived: Boolean, nowMs: Long) {
        val trip = synchronized(this) {
            val dest = destination ?: return
            destination = null
            if (distanceM < TripRecorder.MIN_DISTANCE_M) { checkpoint.delete(); return }
            saving = true
            trip(dest, startedAt, nowMs, distanceM, maxSpeed, arrived, points.toList(), context, extras)
        }
        // La sauvegarde n'est effacée qu'une fois le trajet en base. Base indisponible : elle reste, et le trajet (arrêté à la
        // dernière sauvegarde) sera enregistré au prochain démarrage. L'échec ne remonte pas : la fin du guidage (arrivée
        // affichée, course oubliée, stationnement mémorisé) ne dépend pas de l'historique.
        var stored = false
        try {
            val saved = withCost(trip)
            val id = trips.save(saved)
            _lastSaved.value = saved.copy(id = id)
            stored = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Rien d'autre à faire : voir ci-dessus.
        } finally {
            synchronized(this) {
                saving = false
                // Un nouveau trajet a pu commencer entre-temps : la sauvegarde présente serait alors la sienne.
                if (stored && destination == null) checkpoint.delete()
            }
        }
    }

    companion object {
        /** Sauvegarde du trajet en cours au plus tard toutes les 30 s… */
        const val CHECKPOINT_INTERVAL_MS = 30_000L
        /** … ou tous les 200 m, selon ce qui arrive en premier (à 130 km/h, 30 s font plus d'un kilomètre). */
        const val CHECKPOINT_DISTANCE_M = 200.0
    }
}
