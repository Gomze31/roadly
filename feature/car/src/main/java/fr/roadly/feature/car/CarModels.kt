package fr.roadly.feature.car

import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver as CarManeuver
import fr.roadly.core.common.Formatters
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.navigation.FreeDriveState
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.radar.RadarQuality
import java.net.URLDecoder
import kotlin.math.roundToInt

/**
 * Ce que l'écran de la voiture dessine par-dessus la carte : vitesse, limitation, zone de danger.
 * Aucune position ni type d'équipement : en France, seule la zone de danger est signalée (R413-15).
 */
data class CarHudModel(
    val speedKmh: Int?,
    val limitKmh: Int?,
    val overLimit: Boolean,
    val zone: CarZoneModel?,
    val gpsLost: Boolean = false,
) {
    companion object {
        val EMPTY = CarHudModel(null, null, false, null)

        fun from(g: NavigationState.Guiding, toleranceKmh: Int): CarHudModel {
            val z = g.radarActive ?: g.radarNext
            return build(g.speedKmh, g.speedLimitKmh, z?.let { CarZoneModel(it.inside, it.distanceM, it.speedLimitKmh, it.confidence) }, toleranceKmh, g.gpsLost)
        }

        fun from(s: FreeDriveState.Active, toleranceKmh: Int): CarHudModel {
            val zone = s.zone?.let { CarZoneModel(true, it.distanceM, it.limitKmh, it.confidence) }
                ?: s.nextZone?.let { CarZoneModel(it.inside, it.distanceM, it.speedLimitKmh, it.confidence) }
            return build(s.speedKmh, s.speedLimitKmh, zone, toleranceKmh, s.gpsLost)
        }

        internal fun build(speedKmh: Double?, limitKmh: Int?, zone: CarZoneModel?, toleranceKmh: Int, gpsLost: Boolean): CarHudModel {
            val speed = if (gpsLost) null else speedKmh?.roundToInt()?.coerceAtLeast(0)
            val over = speed != null && limitKmh != null && speed > limitKmh + toleranceKmh
            return CarHudModel(speed, limitKmh, over, zone, gpsLost)
        }
    }
}

/** [distanceM] : avant le début de la zone si ![inside], reste à parcourir si [inside]. */
data class CarZoneModel(val inside: Boolean, val distanceM: Double, val limitKmh: Int?, val confidence: Int) {
    /** Confiance suffisante pour une annonce vocale ; en dessous, la zone est montrée comme peu fiable. */
    val reliable: Boolean get() = confidence >= RadarQuality.MIN_VOICE
}

/** Étape de guidage pour les gabarits de la voiture ; [maneuverCode] = constante `Maneuver.TYPE_*` de la bibliothèque Car App. */
data class CarStep(val type: ManeuverType, val maneuverCode: Int, val roundaboutExit: Int?, val cue: String, val road: String?)

data class CarGuidanceModel(
    val current: CarStep,
    val distanceToCurrentM: Double,
    val next: CarStep?,
    val remainingDistanceM: Double,
    val remainingDurationS: Double,
    val etaEpochMs: Long,
    val rerouting: Boolean,
    val gpsLost: Boolean,
)

object CarGuidanceMapper {
    fun map(g: NavigationState.Guiding): CarGuidanceModel? {
        val maneuvers = g.route.maneuvers
        if (maneuvers.isEmpty()) return null
        val index = g.progress.nextManeuverIndex.coerceIn(0, maneuvers.lastIndex)
        return CarGuidanceModel(
            current = step(maneuvers[index]),
            distanceToCurrentM = g.progress.distanceToNextManeuverM.coerceAtLeast(0.0),
            next = g.upcoming.firstOrNull()?.let(::step),
            remainingDistanceM = g.progress.remainingDistanceM.coerceAtLeast(0.0),
            remainingDurationS = g.progress.remainingDurationS.coerceAtLeast(0.0),
            etaEpochMs = g.etaEpochMs,
            rerouting = g.rerouting,
            gpsLost = g.gpsLost,
        )
    }

    fun step(m: Maneuver): CarStep {
        val exit = m.roundaboutExit?.takeIf { it > 0 }
        return CarStep(m.type, maneuverCode(m.type, exit), exit, m.instruction, m.streetName?.takeIf { it.isNotBlank() })
    }

    /**
     * Correspondance avec les manœuvres Android Auto. Ronds-points : sens antihoraire (circulation à droite) ; l'entrée avec sortie
     * exige un numéro de sortie, sinon simple entrée.
     */
    fun maneuverCode(type: ManeuverType, roundaboutExit: Int?): Int = when (type) {
        ManeuverType.DEPART -> CarManeuver.TYPE_DEPART
        ManeuverType.ARRIVE -> CarManeuver.TYPE_DESTINATION
        ManeuverType.CONTINUE, ManeuverType.RAMP -> CarManeuver.TYPE_STRAIGHT
        ManeuverType.TURN_LEFT -> CarManeuver.TYPE_TURN_NORMAL_LEFT
        ManeuverType.TURN_RIGHT -> CarManeuver.TYPE_TURN_NORMAL_RIGHT
        ManeuverType.SLIGHT_LEFT -> CarManeuver.TYPE_TURN_SLIGHT_LEFT
        ManeuverType.SLIGHT_RIGHT -> CarManeuver.TYPE_TURN_SLIGHT_RIGHT
        ManeuverType.SHARP_LEFT -> CarManeuver.TYPE_TURN_SHARP_LEFT
        ManeuverType.SHARP_RIGHT -> CarManeuver.TYPE_TURN_SHARP_RIGHT
        ManeuverType.UTURN_LEFT -> CarManeuver.TYPE_U_TURN_LEFT
        ManeuverType.UTURN_RIGHT -> CarManeuver.TYPE_U_TURN_RIGHT
        ManeuverType.ROUNDABOUT_ENTER ->
            if (roundaboutExit != null) CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW else CarManeuver.TYPE_ROUNDABOUT_ENTER_CCW
        ManeuverType.ROUNDABOUT_EXIT -> CarManeuver.TYPE_ROUNDABOUT_EXIT_CCW
        ManeuverType.MERGE -> CarManeuver.TYPE_MERGE_SIDE_UNSPECIFIED
        ManeuverType.MERGE_LEFT -> CarManeuver.TYPE_MERGE_LEFT
        ManeuverType.MERGE_RIGHT -> CarManeuver.TYPE_MERGE_RIGHT
        ManeuverType.RAMP_LEFT -> CarManeuver.TYPE_ON_RAMP_NORMAL_LEFT
        ManeuverType.RAMP_RIGHT -> CarManeuver.TYPE_ON_RAMP_NORMAL_RIGHT
        ManeuverType.EXIT_LEFT -> CarManeuver.TYPE_OFF_RAMP_NORMAL_LEFT
        ManeuverType.EXIT_RIGHT -> CarManeuver.TYPE_OFF_RAMP_NORMAL_RIGHT
        ManeuverType.KEEP_LEFT -> CarManeuver.TYPE_KEEP_LEFT
        ManeuverType.KEEP_RIGHT -> CarManeuver.TYPE_KEEP_RIGHT
        ManeuverType.FERRY -> CarManeuver.TYPE_FERRY_BOAT
        ManeuverType.OTHER -> CarManeuver.TYPE_UNKNOWN
    }
}

/** Valeur et unité (`Distance.UNIT_*`) affichées par l'hôte ; mêmes arrondis que la voix et le téléphone. */
data class CarDistanceValue(val value: Double, val unit: Int)

object CarDistances {
    /** Distance à la manœuvre : « 450 m », « 1,2 km » (table d'arrondi commune à la voix). */
    fun maneuver(meters: Double): CarDistanceValue {
        val r = Formatters.roundManeuverMeters(meters)
        return if (r < 1000) CarDistanceValue(r.toDouble(), Distance.UNIT_METERS) else CarDistanceValue(r / 1000.0, Distance.UNIT_KILOMETERS_P1)
    }

    /** Distance restante : 10 m près sous 1 km, 0,1 km sous 10 km, puis km entiers. */
    fun remaining(meters: Double): CarDistanceValue = when {
        meters < 1000 -> CarDistanceValue(((meters / 10).roundToInt() * 10).toDouble(), Distance.UNIT_METERS)
        meters < 10_000 -> CarDistanceValue((meters / 100).roundToInt() / 10.0, Distance.UNIT_KILOMETERS_P1)
        else -> CarDistanceValue((meters / 1000).roundToInt().toDouble(), Distance.UNIT_KILOMETERS)
    }
}

/** Cible d'une demande « Navigue vers » reçue de la voiture (`androidx.car.app.action.NAVIGATE`, URI geo:). */
sealed interface CarNavigateTarget {
    data class Coordinates(val point: GeoPoint, val label: String?) : CarNavigateTarget
    data class Query(val text: String) : CarNavigateTarget
}

object CarNavigateIntent {
    private val COORDS_WITH_LABEL = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\((.*)\))?\s*$""")

    /** `geo:lat,lon`, `geo:lat,lon?q=lat,lon(nom)`, `geo:0,0?q=adresse` ; null si l'URI n'est pas exploitable. */
    fun parse(uri: String?): CarNavigateTarget? {
        if (uri == null || !uri.startsWith("geo:", ignoreCase = true)) return null
        val body = uri.substring(4)
        val coordsPart = body.substringBefore('?').substringBefore(';')
        val params = body.substringAfter('?', "").split('&').mapNotNull { kv ->
            val k = kv.substringBefore('=', "")
            if (k.isEmpty()) null else k to decode(kv.substringAfter('=', ""))
        }.toMap()
        val q = params["q"]?.trim()?.takeIf { it.isNotEmpty() }
        if (q != null) {
            COORDS_WITH_LABEL.find(q)?.let { m ->
                val p = point(m.groupValues[1], m.groupValues[2]) ?: return@let
                return CarNavigateTarget.Coordinates(p, m.groupValues[3].trim().takeIf { it.isNotEmpty() })
            }
            return CarNavigateTarget.Query(q)
        }
        val parts = coordsPart.split(',')
        if (parts.size < 2) return null
        val p = point(parts[0], parts[1]) ?: return null
        if (p.lat == 0.0 && p.lon == 0.0) return null
        return CarNavigateTarget.Coordinates(p, null)
    }

    private fun point(lat: String, lon: String): GeoPoint? {
        val la = lat.trim().toDoubleOrNull() ?: return null
        val lo = lon.trim().toDoubleOrNull() ?: return null
        if (la !in -90.0..90.0 || lo !in -180.0..180.0) return null
        return GeoPoint(la, lo)
    }

    private fun decode(s: String): String = try { URLDecoder.decode(s, "UTF-8") } catch (e: IllegalArgumentException) { s }
}
