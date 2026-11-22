package fr.roadly.feature.car

import androidx.car.app.model.Distance
import androidx.car.app.navigation.model.Maneuver as CarManeuver
import fr.roadly.core.common.Formatters
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Lane
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
    /** Prochaine limitation différente, annoncée par le profil du tracé ; [nextLimitDistanceM] arrondie comme à l'écran. */
    val nextLimitKmh: Int? = null,
    val nextLimitDistanceM: Int? = null,
    /** Nom de la voie suivie (conduite libre) ; null en guidage, où la carte de l'hôte porte déjà les noms. */
    val roadName: String? = null,
) {
    companion object {
        val EMPTY = CarHudModel(null, null, false, null)

        fun from(g: NavigationState.Guiding, toleranceKmh: Int): CarHudModel {
            val z = g.radarActive ?: g.radarNext
            val next = g.nextSpeedLimit
            return build(g.speedKmh, g.speedLimitKmh, z?.let { CarZoneModel(it.inside, it.distanceM, it.speedLimitKmh, it.confidence) }, toleranceKmh, g.gpsLost)
                .copy(nextLimitKmh = next?.kmh, nextLimitDistanceM = next?.let { Formatters.roundManeuverMeters(it.distanceM) })
        }

        /** Hors guidage (arrivée, échec, arrêt) : plus aucune mesure n'arrive, rien n'est affiché plutôt qu'une vitesse périmée. */
        fun forState(s: NavigationState, toleranceKmh: Int): CarHudModel = if (s is NavigationState.Guiding) from(s, toleranceKmh) else EMPTY

        fun from(s: FreeDriveState.Active, toleranceKmh: Int): CarHudModel {
            val zone = s.zone?.let { CarZoneModel(true, it.distanceM, it.limitKmh, it.confidence) }
                ?: s.nextZone?.let { CarZoneModel(it.inside, it.distanceM, it.speedLimitKmh, it.confidence) }
            return build(s.speedKmh, s.speedLimitKmh, zone, toleranceKmh, s.gpsLost).copy(roadName = s.roadName?.takeIf { it.isNotBlank() && !s.gpsLost })
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

/** Image de carrefour montrée dans la carte de guidage à l'approche : uniquement ce que les données de l'itinéraire disent. */
sealed interface CarJunctionVisual {
    /**
     * Rond-point vu depuis l'entrée (en bas) : [exitAngleDeg] = direction de la sortie (0 = en face, > 0 = à droite, ±180 = demi-tour),
     * [exitNumber] = rang de la sortie s'il est connu. Les autres sorties ne sont pas dessinées (non fournies).
     */
    data class Roundabout(
        val exitAngleDeg: Double,
        val exitNumber: Int?,
        /** Mode rond-point : sorties à laisser passer (décompte fiable seulement), dans le même repère que [exitAngleDeg]. */
        val otherExits: List<RoundaboutArm> = emptyList(),
        /** Mode rond-point : position du véhicule sur l'anneau (même repère), arrondie pour limiter les rafraîchissements ; null hors anneau. */
        val vehicleAngleDeg: Double? = null,
    ) : CarJunctionVisual

    /** Branche à laisser passer : direction (même repère que la sortie), éteinte une fois derrière soi. */
    data class RoundaboutArm(val angleDeg: Double, val passed: Boolean)

    /** Panneau de sortie : numéro, directions et route signalés (au moins un des trois), côté de la sortie. */
    data class ExitSign(val number: String?, val toward: String?, val branch: String?, val left: Boolean) : CarJunctionVisual
}

data class CarGuidanceModel(
    val current: CarStep,
    val distanceToCurrentM: Double,
    val next: CarStep?,
    val remainingDistanceM: Double,
    val remainingDurationS: Double,
    val etaEpochMs: Long,
    val rerouting: Boolean,
    val gpsLost: Boolean,
    /** Voies à l'approche de la manœuvre ; vide si inconnues ou trop loin ([CarGuidanceMapper.LANES_WITHIN_M]). */
    val lanes: List<Lane> = emptyList(),
    /** Image de carrefour à l'approche ; null loin de la manœuvre ou sans donnée. */
    val junction: CarJunctionVisual? = null,
)

/**
 * Textes courts de la carte de guidage de la voiture (exception assumée à la règle « strings en ressources », comme les phrases
 * prononcées : composés par la logique JVM et testés).
 *
 * Constat en voiture : la phrase complète de Valhalla (« Entrez dans le rond-point et prenez la 2e sortie dans… ») était coupée
 * par l'hôte. L'icône dit déjà le geste ; le texte ne garde que ce qu'elle ne dit pas : la sortie, la direction, la rue.
 */
object CarCues {
    private const val SEP = " · "

    fun ordinal(n: Int): String = if (n == 1) "1re" else "${n}e"

    /** [following] : manœuvre suivante (rue de sortie d'un rond-point). */
    fun cue(m: Maneuver, following: Maneuver? = null): String {
        val street = m.streetName?.takeIf { it.isNotBlank() }
        val toward = m.exitToward?.takeIf { it.isNotBlank() }
        val branch = m.exitBranch?.takeIf { it.isNotBlank() }
        return when (m.type) {
            ManeuverType.ROUNDABOUT_ENTER -> {
                val exit = m.roundaboutExit?.takeIf { it > 0 }
                val exitStreet = following?.takeIf { it.type == ManeuverType.ROUNDABOUT_EXIT }?.streetName?.takeIf { it.isNotBlank() }
                listOfNotNull(exit?.let { "${ordinal(it)} sortie" } ?: "Rond-point", exitStreet ?: toward).joinToString(SEP)
            }
            ManeuverType.ROUNDABOUT_EXIT -> street ?: "Sortez du rond-point"
            ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT ->
                listOfNotNull(m.exitNumber?.takeIf { it.isNotBlank() }?.let { "Sortie $it" } ?: "Sortie", toward ?: branch ?: street).joinToString(SEP)
            ManeuverType.RAMP, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT, ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT,
            ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT ->
                listOfNotNull(branch ?: street, toward).joinToString(SEP).ifEmpty { verb(m.type) }
            ManeuverType.DEPART, ManeuverType.ARRIVE, ManeuverType.FERRY, ManeuverType.OTHER -> m.instruction.trim().removeSuffix(".")
            else -> street ?: verb(m.type)
        }
    }

    /** Geste seul, quand ni rue ni direction ne sont connues. */
    fun verb(type: ManeuverType): String = when (type) {
        ManeuverType.CONTINUE, ManeuverType.RAMP -> "Continuez tout droit"
        ManeuverType.TURN_LEFT -> "Tournez à gauche"
        ManeuverType.TURN_RIGHT -> "Tournez à droite"
        ManeuverType.SLIGHT_LEFT -> "Légèrement à gauche"
        ManeuverType.SLIGHT_RIGHT -> "Légèrement à droite"
        ManeuverType.SHARP_LEFT -> "Serrez à gauche"
        ManeuverType.SHARP_RIGHT -> "Serrez à droite"
        ManeuverType.UTURN_LEFT, ManeuverType.UTURN_RIGHT -> "Faites demi-tour"
        ManeuverType.MERGE, ManeuverType.MERGE_LEFT, ManeuverType.MERGE_RIGHT -> "Insérez-vous"
        ManeuverType.RAMP_LEFT, ManeuverType.KEEP_LEFT -> "Restez à gauche"
        ManeuverType.RAMP_RIGHT, ManeuverType.KEEP_RIGHT -> "Restez à droite"
        ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT -> "Sortie"
        ManeuverType.ROUNDABOUT_ENTER -> "Rond-point"
        ManeuverType.ROUNDABOUT_EXIT -> "Sortez du rond-point"
        ManeuverType.DEPART -> "Départ"
        ManeuverType.ARRIVE -> "Arrivée"
        ManeuverType.FERRY -> "Ferry"
        ManeuverType.OTHER -> "Continuez"
    }
}

object CarGuidanceMapper {
    /** Les voies ne sont montrées qu'à cette distance de la manœuvre : avant, elles ne servent pas encore. */
    const val LANES_WITHIN_M = 1_500.0
    /** Distance d'affichage du panneau de sortie sur voie rapide, et de l'image de carrefour ailleurs. */
    const val EXIT_SIGN_WITHIN_M = 2_000.0
    const val JUNCTION_WITHIN_M = 400.0

    fun map(g: NavigationState.Guiding): CarGuidanceModel? {
        val maneuvers = g.route.maneuvers
        if (maneuvers.isEmpty()) return null
        val index = g.progress.nextManeuverIndex.coerceIn(0, maneuvers.lastIndex)
        val current = maneuvers[index]
        val distance = g.progress.distanceToNextManeuverM.coerceAtLeast(0.0)
        // Rond-point abordé : sa sortie est déjà dite par l'entrée ; « ensuite » montre ce qui vient après l'anneau.
        val nextIndex = if (current.type == ManeuverType.ROUNDABOUT_ENTER && maneuvers.getOrNull(index + 1)?.type == ManeuverType.ROUNDABOUT_EXIT) index + 2 else index + 1
        return CarGuidanceModel(
            current = step(current, maneuvers.getOrNull(index + 1)),
            distanceToCurrentM = distance,
            next = maneuvers.getOrNull(nextIndex)?.let { step(it, maneuvers.getOrNull(nextIndex + 1)) },
            remainingDistanceM = g.progress.remainingDistanceM.coerceAtLeast(0.0),
            remainingDurationS = g.progress.remainingDurationS.coerceAtLeast(0.0),
            etaEpochMs = g.etaEpochMs,
            rerouting = g.rerouting,
            gpsLost = g.gpsLost,
            lanes = if (distance <= LANES_WITHIN_M) CarLanes.displayable(g.lanes) else emptyList(),
            junction = junction(current, distance, g.speedKmh, g.roundabout?.exitAngleDeg, g.roundabout?.exitNumber, g.roundaboutLive),
        ).let { model -> roundaboutCue(g.roundaboutLive, current)?.let { model.copy(current = model.current.copy(cue = it)) } ?: model }
    }

    /**
     * Mode rond-point sur la carte de guidage de la voiture : le décompte du téléphone, en une ligne. Null sans décompte fiable
     * (le texte habituel reste), pour que la voiture ne dise jamais autre chose que le téléphone et la voix.
     */
    fun roundaboutCue(live: fr.roadly.core.navigation.RoundaboutLive?, current: Maneuver): String? {
        if (live == null || live.phase == fr.roadly.core.navigation.RoundaboutPhase.EXITED) return null
        if (current.type != ManeuverType.ROUNDABOUT_ENTER && current.type != ManeuverType.ROUNDABOUT_EXIT) return null
        val remaining = live.exitsRemaining ?: return null
        // Position douteuse dans l'anneau : le décompte est gelé (le téléphone écrit « Position imprécise »). La carte de l'hôte n'a
        // qu'une ligne : le décompte n'y est pas présenté comme sûr, sauf « prochaine sortie », qui ne peut plus reculer.
        if (unsureInside(live) && !live.exitIsNext) return null
        val street = live.layout.exitStreet?.takeIf { it.isNotBlank() }
        val head = when {
            remaining == 0 && live.phase == fr.roadly.core.navigation.RoundaboutPhase.INSIDE -> "Prochaine sortie"
            remaining == 0 -> "1re sortie"
            remaining == 1 -> "Encore 1 sortie à passer"
            else -> "Encore $remaining sorties à passer"
        }
        return listOfNotNull(head, street).joinToString(" · ")
    }

    /** Dans l'anneau avec une position douteuse ([fr.roadly.core.navigation.RoundaboutConfidence.LOW]) : même condition que le téléphone. */
    private fun unsureInside(live: fr.roadly.core.navigation.RoundaboutLive): Boolean =
        live.phase == fr.roadly.core.navigation.RoundaboutPhase.INSIDE && live.confidence == fr.roadly.core.navigation.RoundaboutConfidence.LOW

    /** Pas de l'angle du véhicule sur l'image (°) : une nouvelle image au plus tous les 20° d'anneau. */
    const val VEHICLE_ANGLE_STEP_DEG = 20.0

    /** Image de carrefour de [m] à [distanceM] ; null trop loin ou sans donnée (rien n'est inventé). */
    fun junction(
        m: Maneuver, distanceM: Double, speedKmh: Double?, roundaboutExitAngleDeg: Double?, roundaboutExitNumber: Int?,
        live: fr.roadly.core.navigation.RoundaboutLive? = null,
    ): CarJunctionVisual? = when (m.type) {
        ManeuverType.ROUNDABOUT_ENTER, ManeuverType.ROUNDABOUT_EXIT ->
            roundaboutExitAngleDeg?.takeIf { distanceM <= JUNCTION_WITHIN_M }?.let { roundabout(it, roundaboutExitNumber, live) }
        ManeuverType.EXIT_LEFT, ManeuverType.EXIT_RIGHT, ManeuverType.RAMP_LEFT, ManeuverType.RAMP_RIGHT, ManeuverType.KEEP_LEFT, ManeuverType.KEEP_RIGHT -> {
            val number = m.exitNumber?.takeIf { it.isNotBlank() }
            val toward = m.exitToward?.takeIf { it.isNotBlank() }
            val branch = m.exitBranch?.takeIf { it.isNotBlank() }
            val within = if ((speedKmh ?: 0.0) >= CarCameraPolicy.EXIT_FAR_MIN_KMH) EXIT_SIGN_WITHIN_M else JUNCTION_WITHIN_M
            if (distanceM > within || (number == null && toward == null && branch == null)) null
            else CarJunctionVisual.ExitSign(number, toward, branch, left = m.type == ManeuverType.EXIT_LEFT || m.type == ManeuverType.RAMP_LEFT || m.type == ManeuverType.KEEP_LEFT)
        }
        else -> null
    }

    /**
     * Image du rond-point. Avec le mode rond-point du téléphone ([live], décompte fiable) : branches à laisser passer et position du
     * véhicule, ramenées dans le repère de l'image (l'arc du schéma du téléphone est mis à l'échelle de l'arc dessiné ici).
     */
    internal fun roundabout(exitAngleDeg: Double, exitNumber: Int?, live: fr.roadly.core.navigation.RoundaboutLive?): CarJunctionVisual.Roundabout {
        val base = CarJunctionVisual.Roundabout(exitAngleDeg, exitNumber)
        val sweep = live?.layout?.sweepDeg?.takeIf { it > 0.0 } ?: return base
        if (live.phase == fr.roadly.core.navigation.RoundaboutPhase.EXITED) return base
        // Arc dessiné par l'image : de l'entrée (en bas) à la sortie, dans le sens antihoraire.
        val exit = if (exitAngleDeg >= 170.0 || exitAngleDeg <= -170.0) -180.0 else exitAngleDeg
        val drawn = 180.0 - exit
        fun toImage(layoutAngleDeg: Double) = 180.0 - layoutAngleDeg / sweep * drawn
        val arms = if (live.layout.counted) live.layout.branches.filter { !it.isTarget }.map { CarJunctionVisual.RoundaboutArm(Math.round(toImage(it.angleDeg) / 5.0) * 5.0, it.rank <= live.exitsPassed) } else emptyList()
        // Position douteuse : pas de repère de véhicule (il serait dessiné à la dernière position sûre, comme s'il était mesuré).
        val vehicle = live.vehicleAngleDeg?.takeIf { live.phase == fr.roadly.core.navigation.RoundaboutPhase.INSIDE && !unsureInside(live) }
            ?.let { toImage(Math.floor(it / VEHICLE_ANGLE_STEP_DEG) * VEHICLE_ANGLE_STEP_DEG) }
        return base.copy(otherExits = arms, vehicleAngleDeg = vehicle)
    }

    fun step(m: Maneuver, following: Maneuver? = null): CarStep {
        val exit = m.roundaboutExit?.takeIf { it > 0 }
        return CarStep(m.type, maneuverCode(m.type, exit), exit, CarCues.cue(m, following), m.streetName?.takeIf { it.isNotBlank() })
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

    /**
     * Types pour lesquels la bibliothèque accepte un numéro de sortie (`Maneuver.isValidTypeWithExitNumber`, Car App 1.7.0) ;
     * pour tout autre type, `setRoundaboutExitNumber` lève `IllegalArgumentException`.
     */
    fun acceptsExitNumber(maneuverCode: Int): Boolean = maneuverCode == CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW ||
        maneuverCode == CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW ||
        maneuverCode == CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CW_WITH_ANGLE ||
        maneuverCode == CarManeuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW_WITH_ANGLE

    /** Manœuvre de la bibliothèque pour [s], sans icône : le numéro de sortie n'est posé que sur un type qui l'accepte. */
    fun maneuverBuilder(s: CarStep): CarManeuver.Builder = CarManeuver.Builder(s.maneuverCode).apply {
        s.roundaboutExit?.takeIf { it >= 1 && acceptsExitNumber(s.maneuverCode) }?.let { setRoundaboutExitNumber(it) }
    }

    /**
     * Ce qui distingue deux résumés de trajet envoyés à l'hôte (`NavigationManager.updateTrip`) : étape, textes de distance tels
     * qu'affichés, minute d'arrivée, recalcul. Deux états de même clé donnent le même résumé à l'écran : il n'est pas renvoyé.
     */
    fun tripKey(routeId: String, stepIndex: Int, model: CarGuidanceModel?, destinationName: String?): Any =
        if (model == null || model.rerouting) listOf(routeId, "loading")
        else listOf(
            routeId, stepIndex, model.current, destinationName,
            CarDistances.maneuver(model.distanceToCurrentM), CarDistances.remaining(model.remainingDistanceM),
            model.etaEpochMs / 60_000, (model.remainingDurationS / 60).toLong(),
        )
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

    /** Nom de lieu ou recherche venant de l'hôte (assistant, autre application) : longueur bornée, comme sur le téléphone. */
    const val MAX_TEXT = 200
    /** Une URI plus longue n'est pas une adresse : ignorée (pas d'analyse d'une chaîne arbitraire), comme sur le téléphone. */
    const val MAX_URI = 2_000

    /** `geo:lat,lon`, `geo:lat,lon?q=lat,lon(nom)`, `geo:0,0?q=adresse` ; null si l'URI n'est pas exploitable. */
    fun parse(uri: String?): CarNavigateTarget? {
        if (uri == null || uri.length > MAX_URI || !uri.startsWith("geo:", ignoreCase = true)) return null
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
                return CarNavigateTarget.Coordinates(p, m.groupValues[3].trim().takeIf { it.isNotEmpty() }?.take(MAX_TEXT))
            }
            return CarNavigateTarget.Query(q.take(MAX_TEXT))
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
