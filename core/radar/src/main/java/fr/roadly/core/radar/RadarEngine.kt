package fr.roadly.core.radar

import fr.roadly.core.model.GeoPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Ce que la réglementation du pays permet de montrer. */
enum class RadarDisplayMode {
    /** France (art. R413-15 du Code de la route, décret 2012-3) : zone de danger, jamais l'emplacement ni le type de contrôle. */
    DANGER_ZONE_ONLY,
    /** Pays où l'information détaillée est admise. */
    DETAILED,
    /** Pays où l'usage d'un avertisseur en conduite est interdit (Suisse, Allemagne) : aucune alerte. */
    DISABLED,
}

object RadarRegulation {
    fun modeFor(countryCode: String?): RadarDisplayMode = when (countryCode?.uppercase()) {
        "FR", null -> RadarDisplayMode.DANGER_ZONE_ONLY
        "CH", "DE" -> RadarDisplayMode.DISABLED
        else -> RadarDisplayMode.DETAILED
    }

    /**
     * Traîne après le point, entre [TAIL_MIN_M] et [TAIL_MAX_M] selon l'équipement : la zone ne finit jamais à une distance fixe
     * du radar. Avec une traîne fixe, « encore 300 m » ou la fin de zone tracée sur la carte donneraient sa position exacte.
     */
    const val TAIL_MIN_M = 150.0
    const val TAIL_MAX_M = 450.0

    /** Longueur avant le point : la longueur de référence multipliée par un facteur stable entre ces bornes. */
    const val LEAD_MIN_FACTOR = 0.85
    const val LEAD_MAX_FACTOR = 1.15

    /**
     * Longueur de référence avant le point, calée sur la pratique des zones de danger : 300 m en ville, 2 km sur route, 4 km sur
     * voie rapide. Feu rouge, passage à niveau et radar urbain : 300 m (milieu urbain ou carrefour, sans vitesse publiée).
     */
    fun baseLeadM(radar: Radar): Double = when {
        radar.kind == RadarKind.RED_LIGHT || radar.kind == RadarKind.LEVEL_CROSSING || radar.kind == RadarKind.URBAN -> 300.0
        radar.speedLimitKmh == null -> 2_000.0
        radar.speedLimitKmh >= 110 -> 4_000.0
        radar.speedLimitKmh >= 70 -> 2_000.0
        else -> 300.0
    }

    /** Début de zone avant le point : [baseLeadM] × facteur propre à l'équipement (même zone à chaque passage, sur tout téléphone). */
    fun leadM(radar: Radar): Double = baseLeadM(radar) * (LEAD_MIN_FACTOR + (LEAD_MAX_FACTOR - LEAD_MIN_FACTOR) * stableFraction(radar.id, SALT_LEAD))

    fun tailM(radar: Radar): Double = TAIL_MIN_M + (TAIL_MAX_M - TAIL_MIN_M) * stableFraction(radar.id, SALT_TAIL)

    /** Longueur contrôlée après le point (tronçon de vitesse moyenne), puis la traîne. */
    fun afterM(radar: Radar): Double = (radar.sectionLengthM ?: 0.0) + tailM(radar)

    /** Fraction dans [0, 1[ tirée de [id] (FNV-1a 32 bits) : déterministe, indépendante de la plateforme et du lancement. */
    fun stableFraction(id: String, salt: Int): Double {
        var h = FNV_OFFSET xor salt
        for (c in id) {
            h = h xor c.code
            h *= FNV_PRIME
        }
        // Brassage final : des identifiants voisins (« 01234 », « 01235 ») donnent des fractions éloignées.
        h = h xor (h ushr 16)
        h *= MIX
        h = h xor (h ushr 13)
        return (h.toLong() and 0xFFFFFFFFL).toDouble() / 4_294_967_296.0
    }

    private const val FNV_OFFSET = -0x7ee3623b // 0x811C9DC5
    private const val FNV_PRIME = 0x01000193
    private const val MIX = -0x7a143595 // 0x85EBCA6B
    private const val SALT_LEAD = 0x1EAD
    private const val SALT_TAIL = 0x7A11
}

/** Normalisation des numéros de route : « RN 12 » / « N12 » → « N12 », « A 7 » → « A7 ». Null si ce n'est pas un numéro. */
object RoadRef {
    private val re = Regex("^(A|N|RN|D|RD|M|RM|E|C|VC)\\s*-?\\s*(\\d+[A-Z]?)$")

    fun normalize(name: String?): String? {
        val m = re.find(name?.trim()?.uppercase() ?: return null) ?: return null
        val prefix = when (val p = m.groupValues[1]) { "RN" -> "N"; "RD" -> "D"; "RM" -> "M"; else -> p }
        return prefix + m.groupValues[2]
    }

    /** Vrai si les deux jeux de numéros sont connus et n'ont rien en commun (route différente). */
    fun conflict(radarRoad: String?, current: Collection<String>): Boolean {
        val r = normalize(radarRoad) ?: return false
        val refs = current.mapNotNull { normalize(it) }
        return refs.isNotEmpty() && r !in refs
    }
}

/** Seuils du rapprochement géospatial. */
data class MatchParams(
    /** Écart latéral admis = base + précision du point (plafonnée). */
    val baseOffsetM: Double = 18.0,
    val maxPrecisionAllowanceM: Double = 42.0,
    /** Sens contrôlé connu : écart de cap admis (le sens publié est quantifié à 45°). */
    val maxDirectionDiffDeg: Double = 60.0,
    /** Sans itinéraire : marge latérale supplémentaire par mètre devant soi (incertitude du cap, ≈ 2,3°). */
    val crossPerAlongM: Double = 0.04,
    /**
     * Sans itinéraire, sens contrôlé inconnu : à plusieurs kilomètres, l'erreur de cap (1–2°) vaut 70–140 m latéraux et la
     * chaussée opposée est indiscernable. La zone n'est alors ouverte qu'à moins de [ambiguousConfirmM] mètres, avec un couloir
     * strict : une alerte plus tardive plutôt qu'une fausse alerte.
     */
    val ambiguousConfirmM: Double = 300.0,
    val ambiguousCrossPerAlongM: Double = 0.01,
    /** Sans itinéraire : précision GPS au-delà de laquelle aucune nouvelle zone n'est ouverte. */
    val maxGpsAccuracyM: Double = 50.0,
    val minConfidence: Int = RadarQuality.MIN_ALERT,
) {
    fun offsetLimit(r: Radar) = baseOffsetM + min(r.precisionM, maxPrecisionAllowanceM)
}

/** Zone de danger le long d'un itinéraire, en abscisses du tracé ; [radars] = équipements fusionnés dans la zone. */
data class RouteRadarZone(
    val key: String,
    val startAlongM: Double,
    val endAlongM: Double,
    val speedLimitKmh: Int?,
    val confidence: Int,
    val radars: List<Radar>,
)

/** Raisons d'écarter un équipement (diagnostic et mesure des faux positifs évités). */
enum class RejectReason { STATUS, CONFIDENCE, OFFSET, DIRECTION, ROAD, BEHIND, HEADING_UNKNOWN, GPS_ACCURACY, KIND_DISABLED, AMBIGUOUS_FAR }

/** Itinéraire connu : projection sur le tracé, écart latéral, sens contrôlé et numéro de route. */
object RouteRadarMatcher {
    data class Result(val zones: List<RouteRadarZone>, val rejected: Map<String, RejectReason>)

    /** [roadRefsAt] : noms de voie du tracé à une abscisse (manœuvres) ; [enabled] : catégories autorisées. */
    fun match(
        route: Polyline,
        candidates: List<Radar>,
        params: MatchParams = MatchParams(),
        enabled: Set<RadarKind> = RadarKind.entries.toSet(),
        roadRefsAt: (Double) -> Collection<String> = { emptyList() },
    ): Result {
        val rejected = HashMap<String, RejectReason>()
        val raw = ArrayList<RouteRadarZone>()
        for (r in candidates) {
            val reason = baseReject(r, params, enabled)
            if (reason != null) { rejected[r.id] = reason; continue }
            val p = route.project(r.point) ?: continue
            if (p.offsetM > params.offsetLimit(r)) { rejected[r.id] = RejectReason.OFFSET; continue }
            val dir = r.direction
            if (dir != null && dir.isKnownOneWay && GeoMath.angleDiff(p.segmentBearingDeg, dir.bearingDeg!!) > params.maxDirectionDiffDeg) {
                rejected[r.id] = RejectReason.DIRECTION; continue
            }
            if (RoadRef.conflict(r.road, roadRefsAt(p.alongM))) { rejected[r.id] = RejectReason.ROAD; continue }
            raw += RouteRadarZone(
                r.id, (p.alongM - RadarRegulation.leadM(r)).coerceAtLeast(0.0), (p.alongM + RadarRegulation.afterM(r)).coerceAtMost(route.totalM),
                r.speedLimitKmh, r.confidence, listOf(r),
            )
        }
        return Result(mergeOverlapping(raw.sortedBy { it.startAlongM }), rejected)
    }

    /** Zones qui se chevauchent : une seule zone (limitation la plus basse, confiance la plus haute) — une seule alerte. */
    fun mergeOverlapping(sorted: List<RouteRadarZone>): List<RouteRadarZone> {
        val out = ArrayList<RouteRadarZone>()
        for (z in sorted) {
            val last = out.lastOrNull()
            if (last != null && z.startAlongM <= last.endAlongM) {
                out[out.lastIndex] = last.copy(
                    endAlongM = maxOf(last.endAlongM, z.endAlongM),
                    speedLimitKmh = listOfNotNull(last.speedLimitKmh, z.speedLimitKmh).minOrNull(),
                    confidence = maxOf(last.confidence, z.confidence),
                    radars = last.radars + z.radars,
                )
            } else out += z
        }
        return out
    }

    internal fun baseReject(r: Radar, params: MatchParams, enabled: Set<RadarKind>): RejectReason? = when {
        r.status != RadarStatus.ACTIVE -> RejectReason.STATUS
        r.confidence < params.minConfidence -> RejectReason.CONFIDENCE
        r.kind !in enabled -> RejectReason.KIND_DISABLED
        else -> null
    }
}

/**
 * Cap filtré : moyenne circulaire des caps GPS des [windowMs] dernières ms à plus de [minSpeedMps] ; null si moins de 3
 * mesures ou dispersion > [maxSpreadDeg] (rond-point, manœuvre, bruit à basse vitesse). Courbure (rad/m) estimée sur la
 * fenêtre pour prolonger la trajectoire en arc plutôt qu'en ligne droite.
 */
class HeadingFilter(private val windowMs: Long = 6_000, private val minSpeedMps: Float = 3f, private val maxSpreadDeg: Double = 25.0) {
    private data class Sample(val t: Long, val heading: Double, val point: GeoPoint)
    private val samples = ArrayDeque<Sample>()

    var heading: Double? = null
        private set
    var curvaturePerM: Double = 0.0
        private set

    fun update(point: GeoPoint, headingDeg: Float?, speedMps: Float?, timeMs: Long) {
        while (samples.isNotEmpty() && timeMs - samples.first().t > windowMs) samples.removeFirst()
        if (headingDeg != null && (speedMps ?: 0f) >= minSpeedMps) samples.addLast(Sample(timeMs, headingDeg.toDouble(), point))
        if (samples.size < 3) { heading = null; curvaturePerM = 0.0; return }
        val sx = samples.sumOf { sin(Math.toRadians(it.heading)) }
        val sy = samples.sumOf { cos(Math.toRadians(it.heading)) }
        val mean = (Math.toDegrees(atan2(sx, sy)) + 360.0) % 360.0
        val spread = samples.maxOf { GeoMath.angleDiff(it.heading, mean) }
        // Virage régulier : la dispersion vient de la courbure, pas du bruit ; on garde le cap le plus récent.
        val first = samples.first(); val last = samples.last()
        val turn = signedDiff(first.heading, last.heading)
        val dist = (1 until samples.size).sumOf { samples[it - 1].point.distanceTo(samples[it].point) }
        curvaturePerM = if (dist > 20.0) (Math.toRadians(turn) / dist).coerceIn(-MAX_CURVATURE, MAX_CURVATURE) else 0.0
        val residual = samples.maxOf { s -> abs(signedDiff(expectedAt(s, first, last), s.heading)) }
        heading = when {
            spread <= maxSpreadDeg -> mean
            residual <= maxSpreadDeg -> last.heading
            else -> null
        }
    }

    fun reset() { samples.clear(); heading = null; curvaturePerM = 0.0 }

    private fun expectedAt(s: Sample, first: Sample, last: Sample): Double {
        val span = (last.t - first.t).coerceAtLeast(1)
        return first.heading + signedDiff(first.heading, last.heading) * (s.t - first.t) / span
    }

    private companion object {
        /** Rayon minimal pris en compte : 150 m. */
        const val MAX_CURVATURE = 1.0 / 150.0
        fun signedDiff(from: Double, to: Double): Double = ((to - from) % 360.0 + 540.0) % 360.0 - 180.0
    }
}

/** Position, cap filtré et route courante d'un véhicule sans itinéraire. */
data class DriveSample(
    val point: GeoPoint,
    val headingDeg: Double?,
    val curvaturePerM: Double,
    val speedMps: Float?,
    val accuracyM: Float,
    /** Numéro ou nom de la route suivie (map matching), null si inconnu. */
    val roadName: String?,
)

/** Zone observée : distances (m) au début et à la fin de la zone dans le sens de marche ; négatif = dépassé. */
data class ZoneObservation(
    val key: String,
    val distanceToStartM: Double,
    val distanceToEndM: Double,
    val speedLimitKmh: Int?,
    val confidence: Int,
)

/**
 * Sans itinéraire : trajectoire prolongée en arc depuis la position, le cap filtré et la courbure ; un équipement est retenu
 * s'il est devant, dans le couloir latéral, dans le bon sens et sur la même route (quand ces données existent). Une zone déjà
 * ouverte ([openKeys]) reste suivie à la distance seule, même si le cap devient incertain.
 */
object FreeRadarMatcher {
    /** Rayon de recherche : la plus longue zone (4 km) plus une marge. */
    const val SEARCH_RADIUS_M = 4_500.0

    data class Result(val observations: List<ZoneObservation>, val rejected: Map<String, RejectReason>)

    fun match(
        index: RadarIndex,
        s: DriveSample,
        openKeys: Set<String>,
        params: MatchParams = MatchParams(),
        enabled: Set<RadarKind> = RadarKind.entries.toSet(),
        /** Distance au-delà du début de zone où l'on observe déjà (préavis), en plus de la zone elle-même. */
        lookAheadM: Double = 0.0,
    ): Result {
        val rejected = HashMap<String, RejectReason>()
        val out = ArrayList<ZoneObservation>()
        // Zones ouvertes suivies jusqu'au bout, même au-delà du rayon (tronçons de plusieurs kilomètres).
        val candidates = (index.near(s.point, SEARCH_RADIUS_M + lookAheadM) + openKeys.mapNotNull(index::byId)).distinctBy { it.id }
        for (r in candidates) {
            val open = r.id in openKeys
            val base = RouteRadarMatcher.baseReject(r, params, enabled)
            if (base != null) { rejected[r.id] = base; continue }
            val heading = s.headingDeg
            if (heading == null) {
                // Cap inconnu (arrêt, rond-point) : une zone ouverte reste active tant qu'on est près du point, rien de nouveau.
                if (open) {
                    // Côté du point inconnu : on reste « dedans » sans rien décider ; la sortie attend un cap fiable.
                    val d = s.point.distanceTo(r.point)
                    val after = RadarRegulation.afterM(r)
                    if (d <= RadarRegulation.leadM(r) + after) out += ZoneObservation(r.id, -1.0, d + after, r.speedLimitKmh, r.confidence)
                } else rejected[r.id] = RejectReason.HEADING_UNKNOWN
                continue
            }
            if (!open && s.accuracyM > params.maxGpsAccuracyM) { rejected[r.id] = RejectReason.GPS_ACCURACY; continue }
            val (along, cross) = GeoMath.alongCross(s.point, heading, r.point)
            val lead = RadarRegulation.leadM(r); val after = RadarRegulation.afterM(r)
            if (along > lead + lookAheadM + 1.0 || along < -after - 100.0) { if (!open) rejected[r.id] = RejectReason.BEHIND; continue }
            if (!open && along <= 0.0) { rejected[r.id] = RejectReason.BEHIND; continue }
            // Arc : décalage latéral attendu à `along` mètres pour la courbure mesurée (positif = vers la droite).
            val expectedCross = if (along > 0) s.curvaturePerM * along * along / 2.0 else 0.0
            val dir = r.direction
            val ambiguous = !open && (dir == null || (!dir.bothWays && dir.bearingDeg == null))
            if (ambiguous && along > params.ambiguousConfirmM) { rejected[r.id] = RejectReason.AMBIGUOUS_FAR; continue }
            val perAlong = if (ambiguous) params.ambiguousCrossPerAlongM else params.crossPerAlongM
            val allowed = params.offsetLimit(r) + perAlong * along.coerceAtLeast(0.0)
            if (abs(cross - expectedCross) > allowed) { rejected[r.id] = RejectReason.OFFSET; continue }
            if (dir != null && dir.isKnownOneWay && GeoMath.angleDiff(heading, dir.bearingDeg!!) > params.maxDirectionDiffDeg) {
                rejected[r.id] = RejectReason.DIRECTION; continue
            }
            if (RoadRef.conflict(r.road, listOfNotNull(s.roadName))) { rejected[r.id] = RejectReason.ROAD; continue }
            out += ZoneObservation(r.id, along - lead, along + after, r.speedLimitKmh, r.confidence)
        }
        return Result(mergeSameZone(out), rejected)
    }

    /** Équipements voisins dont les zones se chevauchent : une seule observation (clé du premier). */
    private fun mergeSameZone(obs: List<ZoneObservation>): List<ZoneObservation> {
        val out = ArrayList<ZoneObservation>()
        for (o in obs.sortedBy { it.distanceToStartM }) {
            val last = out.lastOrNull()
            if (last != null && o.distanceToStartM <= last.distanceToEndM) {
                out[out.lastIndex] = last.copy(
                    distanceToEndM = maxOf(last.distanceToEndM, o.distanceToEndM),
                    speedLimitKmh = listOfNotNull(last.speedLimitKmh, o.speedLimitKmh).minOrNull(),
                    confidence = maxOf(last.confidence, o.confidence),
                )
            } else out += o
        }
        return out
    }
}

/** Réglages d'alerte. */
data class RadarAlertConfig(
    /** Préavis avant l'entrée en zone (0 = aucun). */
    val prewarnM: Double = 0.0,
    /** Après la sortie, une même zone n'est pas réannoncée pendant ce délai (demi-tour, aller-retour du GPS). */
    val cooldownMs: Long = 180_000,
    /** Sortie confirmée [exitHysteresisM] mètres après la fin de zone. */
    val exitHysteresisM: Double = 50.0,
    /** Zone perdue de vue pendant ce nombre de mises à jour consécutives : refermée sans annonce. */
    val missingTicksToClose: Int = 3,
    /** Horizon de la « prochaine zone » affichée. */
    val displayHorizonM: Double = 3_000.0,
    val voiceMinConfidence: Int = RadarQuality.MIN_VOICE,
    /**
     * Une zone n'est ouverte (préavis ou entrée) qu'après ce nombre d'observations consécutives : un fix isolé tiré vers la
     * chaussée opposée ou une route voisine ne déclenche rien (≈ 3 s, 90 m à 110 km/h).
     */
    val confirmTicks: Int = 3,
)

enum class ZonePhase { PREWARN, INSIDE, COOLDOWN }

sealed interface RadarAlertEvent {
    val key: String
    /** Vrai si l'annonce vocale est justifiée (confiance suffisante) ; sinon alerte visuelle seulement. */
    val voice: Boolean
    data class PreWarning(override val key: String, val distanceM: Double, val speedLimitKmh: Int?, override val voice: Boolean) : RadarAlertEvent
    data class Entered(override val key: String, val speedLimitKmh: Int?, override val voice: Boolean) : RadarAlertEvent
    data class Exited(override val key: String, override val voice: Boolean) : RadarAlertEvent
    data class OverSpeed(override val key: String, val speedLimitKmh: Int, override val voice: Boolean) : RadarAlertEvent
}

/** État affichable d'une zone. */
data class ZoneStatus(val key: String, val distanceM: Double, val speedLimitKmh: Int?, val confidence: Int, val inside: Boolean)

/**
 * Machine à états des alertes, une entrée par zone : (rien) → PREWARN → INSIDE → COOLDOWN → (rien).
 * Une zone n'est annoncée qu'une fois par passage ; une rentrée dans le délai de [RadarAlertConfig.cooldownMs] (bruit GPS,
 * arrêt au milieu de la zone, demi-tour) est silencieuse. Pure : l'horloge est passée à [update].
 */
class RadarAlertEngine(var config: RadarAlertConfig = RadarAlertConfig()) {
    private data class Entry(var phase: ZonePhase, var since: Long, var missing: Int = 0, var overSpeedAlerted: Boolean = false)
    private val entries = HashMap<String, Entry>()
    /** Observations consécutives d'une zone pas encore ouverte. */
    private val pending = HashMap<String, Int>()

    data class Output(val events: List<RadarAlertEvent>, val active: ZoneStatus?, val next: ZoneStatus?)

    /** Clés des zones ouvertes (préavis ou dedans) : suivies même quand le cap devient incertain. */
    val openKeys: Set<String> get() = entries.filterValues { it.phase != ZonePhase.COOLDOWN }.keys

    fun phaseOf(key: String): ZonePhase? = entries[key]?.phase

    fun reset() { entries.clear(); pending.clear() }

    fun update(nowMs: Long, observations: List<ZoneObservation>, speedKmh: Double?, toleranceKmh: Int): Output {
        val events = ArrayList<RadarAlertEvent>()
        val seen = HashSet<String>()
        for (o in observations) {
            if (o.confidence < RadarQuality.MIN_ALERT) continue
            seen += o.key
            val voice = o.confidence >= config.voiceMinConfidence
            val e = entries[o.key]
            if (e != null) e.missing = 0
            val inside = o.distanceToStartM <= 0.0 && o.distanceToEndM >= -config.exitHysteresisM
            val wantsOpen = e == null && (inside || (config.prewarnM > 0.0 && o.distanceToStartM > 0.0 && o.distanceToStartM <= config.prewarnM))
            if (wantsOpen) {
                val n = (pending[o.key] ?: 0) + 1
                pending[o.key] = n
                if (n < config.confirmTicks) continue
            }
            pending.remove(o.key)
            when {
                inside -> when (e?.phase) {
                    ZonePhase.INSIDE -> Unit
                    ZonePhase.COOLDOWN -> if (nowMs - e.since < config.cooldownMs) { e.phase = ZonePhase.INSIDE } else {
                        entries[o.key] = Entry(ZonePhase.INSIDE, nowMs); events += RadarAlertEvent.Entered(o.key, o.speedLimitKmh, voice)
                    }
                    else -> {
                        // Préavis fait ou non : l'entrée est toujours annoncée une fois.
                        entries[o.key] = Entry(ZonePhase.INSIDE, nowMs); events += RadarAlertEvent.Entered(o.key, o.speedLimitKmh, voice)
                    }
                }
                o.distanceToEndM < -config.exitHysteresisM -> if (e?.phase == ZonePhase.INSIDE) {
                    e.phase = ZonePhase.COOLDOWN; e.since = nowMs; events += RadarAlertEvent.Exited(o.key, voice)
                }
                o.distanceToStartM > 0.0 && config.prewarnM > 0.0 && o.distanceToStartM <= config.prewarnM && e == null -> {
                    entries[o.key] = Entry(ZonePhase.PREWARN, nowMs); events += RadarAlertEvent.PreWarning(o.key, o.distanceToStartM, o.speedLimitKmh, voice)
                }
            }
            val cur = entries[o.key]
            val limit = o.speedLimitKmh
            if (cur != null && cur.phase == ZonePhase.INSIDE && limit != null && speedKmh != null) {
                if (speedKmh > limit + toleranceKmh && !cur.overSpeedAlerted) { cur.overSpeedAlerted = true; events += RadarAlertEvent.OverSpeed(o.key, limit, voice) }
                else if (speedKmh <= limit) cur.overSpeedAlerted = false
            }
        }
        pending.keys.retainAll(seen)
        // Zones disparues : refermées après quelques mises à jour ; délai de silence expiré : oubliées.
        val it = entries.entries.iterator()
        while (it.hasNext()) {
            val (key, e) = it.next()
            if (key in seen) continue
            when (e.phase) {
                ZonePhase.COOLDOWN -> if (nowMs - e.since >= config.cooldownMs) it.remove()
                ZonePhase.PREWARN -> if (++e.missing >= config.missingTicksToClose) it.remove()
                ZonePhase.INSIDE -> if (++e.missing >= config.missingTicksToClose) { e.phase = ZonePhase.COOLDOWN; e.since = nowMs }
            }
        }
        val visible = observations.filter { it.confidence >= RadarQuality.MIN_ALERT }
        val active = visible.filter { entries[it.key]?.phase == ZonePhase.INSIDE }.minByOrNull { it.distanceToEndM }
            ?.let { ZoneStatus(it.key, it.distanceToEndM.coerceAtLeast(0.0), it.speedLimitKmh, it.confidence, inside = true) }
        val next = visible.filter { it.distanceToStartM > 0.0 && it.distanceToStartM <= config.displayHorizonM }.minByOrNull { it.distanceToStartM }
            ?.let { ZoneStatus(it.key, it.distanceToStartM, it.speedLimitKmh, it.confidence, inside = false) }
        return Output(events, active, next)
    }
}
