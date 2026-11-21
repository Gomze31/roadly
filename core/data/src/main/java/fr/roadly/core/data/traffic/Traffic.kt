package fr.roadly.core.data.traffic

import fr.roadly.core.model.GeoPoint
import java.time.OffsetDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.abs

/** Nature d'un événement de circulation officiel (regroupement des types DATEX II). */
enum class TrafficKind(val notable: Boolean) {
    ACCIDENT(true), JAM(true), SLOW(true), BROKEN_DOWN(true), CLOSURE(true), OBSTACLE(true), WEATHER(true),
    LANE_CLOSURE(false), ROADWORK(false),
}

/**
 * Événement publié par Bison Futé (DIR, réseau routier national). [points] : un point, ou début puis fin d'un tronçon ;
 * [bothWays] = valable dans les deux sens ; [queueLengthM] : longueur de bouchon annoncée.
 */
data class TrafficEvent(
    val id: String,
    val kind: TrafficKind,
    val points: List<GeoPoint>,
    val bothWays: Boolean,
    val road: String?,
    val direction: String?,
    val description: String?,
    val startEpochMs: Long?,
    val endEpochMs: Long?,
    val queueLengthM: Int?,
    val severity: String?,
)

/** Événement placé sur un itinéraire : [alongM] = abscisse depuis le départ, [offsetM] = écart au tracé. */
data class TrafficHit(val event: TrafficEvent, val alongM: Double, val offsetM: Double, val point: GeoPoint)

/**
 * Lecture du flux DATEX II 2.0 de Bison Futé (`Evenementiel-DIR/grt/RRN/content.xml`). Lecture par recherche de balises
 * (identique sur Android et en JVM, préfixe d'espace de noms quelconque) ; un enregistrement illisible est ignoré.
 */
object DatexParser {
    private val situationRe = Regex("""<(?:\w+:)?situation\s[^>]*id="([^"]+)"[^>]*>(.*?)</(?:\w+:)?situation>""", RegexOption.DOT_MATCHES_ALL)
    private val recordRe = Regex("""<(?:\w+:)?situationRecord\s([^>]*)>(.*?)</(?:\w+:)?situationRecord>""", RegexOption.DOT_MATCHES_ALL)
    /** Bloc `<name>` d'un point TPEG (un seul, sans déborder sur le suivant). */
    private val nameBlock = Regex("""<(?:\w+:)?name>((?:(?!</(?:\w+:)?name>).)*)</(?:\w+:)?name>""", RegexOption.DOT_MATCHES_ALL)
    private val coordRe = Regex("""<(?:\w+:)?latitude>([-0-9.]+)</(?:\w+:)?latitude>\s*<(?:\w+:)?longitude>([-0-9.]+)</(?:\w+:)?longitude>""")

    private fun tag(body: String, name: String): String? =
        Regex("""<(?:\w+:)?$name(?:\s[^>]*)?>([^<]*)</(?:\w+:)?$name>""").find(body)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

    fun parse(xml: String, nowMs: Long): List<TrafficEvent> {
        val out = ArrayList<TrafficEvent>()
        for (s in situationRe.findAll(xml)) {
            val severity = tag(s.groupValues[2], "overallSeverity")
            for (r in recordRe.findAll(s.groupValues[2])) {
                runCatching { record(r.groupValues[1], r.groupValues[2], severity, nowMs) }.getOrNull()?.let(out::add)
            }
        }
        return out
    }

    private fun record(attrs: String, body: String, severity: String?, nowMs: Long): TrafficEvent? {
        val type = Regex("""type="(?:\w+:)?(\w+)"""").find(attrs)?.groupValues?.get(1) ?: return null
        val id = Regex("""\bid="([^"]+)"""").find(attrs)?.groupValues?.get(1) ?: return null
        val kind = kind(type, body) ?: return null
        val start = tag(body, "overallStartTime")?.let(::epoch)
        val end = tag(body, "overallEndTime")?.let(::epoch)
        if (end != null && end < nowMs) return null
        if (start != null && start > nowMs + UPCOMING_MS) return null
        val loc = body.substringAfter("groupOfLocations", "")
        val from = Regex("""<(?:\w+:)?from\b.*?</(?:\w+:)?from>""", RegexOption.DOT_MATCHES_ALL).find(loc)?.value?.let { coordRe.find(it) }
        val to = Regex("""<(?:\w+:)?to\b.*?</(?:\w+:)?to>""", RegexOption.DOT_MATCHES_ALL).find(loc)?.value?.let { coordRe.find(it) }
        val points = if (from != null && to != null) listOf(point(from), point(to))
        else listOfNotNull(coordRe.find(loc)?.let(::point))
        if (points.isEmpty()) return null
        val comments = Regex("""<(?:\w+:)?generalPublicComment>(.*?)</(?:\w+:)?generalPublicComment>""", RegexOption.DOT_MATCHES_ALL).findAll(body).map { c ->
            (tag(c.groupValues[1], "value") ?: "") to (tag(c.groupValues[1], "commentType") ?: "")
        }.toList()
        val direction = comments.firstOrNull { it.first.startsWith("De ") && " vers " in it.first }?.first
        val description = comments.firstOrNull { it.second == "description" && !it.first.startsWith("Arrêté") }?.first
        val roadName = nameBlock.findAll(loc).map { it.groupValues[1] }
            .firstOrNull { tag(it, "tpegOtherPointDescriptorType") == "linkName" }?.let { tag(it, "value") }
        val road = roadName ?: tag(loc, "roadNumber")?.let { normalizeRoad(it) }
        val both = tag(loc, "tpegDirection") == "bothWays" || tag(loc, "directionRelativeAtPoint") == "both"
        return TrafficEvent(
            id = id, kind = kind, points = points, bothWays = both, road = road, direction = direction,
            description = description, startEpochMs = start, endEpochMs = end, queueLengthM = tag(body, "queueLength")?.toDoubleOrNull()?.toInt(),
            severity = severity,
        )
    }

    /** « A0047 » → « A47 », « N0020 » → « N20 ». */
    internal fun normalizeRoad(s: String): String = Regex("""^([A-Z]+)0*(\d+.*)$""").find(s)?.let { it.groupValues[1] + it.groupValues[2] } ?: s

    private fun point(m: MatchResult) = GeoPoint(m.groupValues[1].toDouble(), m.groupValues[2].toDouble())
    private fun epoch(s: String): Long? = runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()

    /** Type DATEX II → nature affichée ; null = pas une gêne pour l'automobiliste (gestion du réseau, consignes, déviations). */
    internal fun kind(type: String, body: String): TrafficKind? = when (type) {
        "Accident" -> TrafficKind.ACCIDENT
        "AbnormalTraffic" -> if (tag(body, "abnormalTrafficType") == "slowTraffic") TrafficKind.SLOW else TrafficKind.JAM
        "VehicleObstruction" -> TrafficKind.BROKEN_DOWN
        "GeneralObstruction", "AnimalPresenceObstruction", "EnvironmentalObstruction", "InfrastructureDamageObstruction" -> TrafficKind.OBSTACLE
        "WeatherRelatedRoadConditions", "PoorEnvironmentConditions" -> TrafficKind.WEATHER
        "MaintenanceWorks", "ConstructionWorks" -> TrafficKind.ROADWORK
        "RoadOrCarriagewayOrLaneManagement" -> when (tag(body, "roadOrCarriagewayOrLaneManagementType")) {
            "roadClosed", "closedPermanentlyForTheWinter", "carriagewayClosures" -> TrafficKind.CLOSURE
            null -> null
            else -> TrafficKind.LANE_CLOSURE
        }
        else -> null
    }

    /** Événement annoncé pour bientôt (dans l'heure) gardé ; au-delà, ignoré. */
    const val UPCOMING_MS = 60 * 60 * 1000L
}

/** Placement des événements sur un tracé : écart, abscisse, et sens de circulation pour les tronçons. */
object TrafficOnRoute {
    /** Écart maximal au tracé (m) pour qu'un événement concerne l'itinéraire. */
    const val MAX_OFFSET_M = 60.0

    fun along(route: List<GeoPoint>, events: List<TrafficEvent>): List<TrafficHit> {
        if (route.size < 2 || events.isEmpty()) return emptyList()
        val cum = DoubleArray(route.size)
        for (i in 1 until route.size) cum[i] = cum[i - 1] + route[i - 1].distanceTo(route[i])
        val minLat = route.minOf { it.lat } - 0.01; val maxLat = route.maxOf { it.lat } + 0.01
        val minLon = route.minOf { it.lon } - 0.01; val maxLon = route.maxOf { it.lon } + 0.01
        return events.mapNotNull { e ->
            if (e.points.none { it.lat in minLat..maxLat && it.lon in minLon..maxLon }) return@mapNotNull null
            val projections = e.points.map { project(route, cum, it) }
            val best = projections.filter { it.offsetM <= MAX_OFFSET_M }.minByOrNull { it.alongM } ?: return@mapNotNull null
            // Tronçon orienté : si son sens (début → fin) est opposé au sens du trajet, il concerne l'autre chaussée.
            if (!e.bothWays && e.points.size == 2 && e.points[0].distanceTo(e.points[1]) > 100) {
                val eventBearing = bearing(e.points[0], e.points[1])
                val seg = best.segment
                val routeBearing = bearing(route[seg], route[seg + 1])
                if (angleDiff(eventBearing, routeBearing) > 100) return@mapNotNull null
            }
            TrafficHit(e, best.alongM, best.offsetM, best.point)
        }.sortedBy { it.alongM }
    }

    private data class Projection(val alongM: Double, val offsetM: Double, val point: GeoPoint, val segment: Int)

    private fun project(route: List<GeoPoint>, cum: DoubleArray, p: GeoPoint): Projection {
        var best = Projection(0.0, Double.MAX_VALUE, route[0], 0)
        val k = cos(Math.toRadians(p.lat))
        for (i in 0 until route.size - 1) {
            val a = route[i]; val b = route[i + 1]
            val ax = a.lon * k; val ay = a.lat; val bx = b.lon * k; val by = b.lat; val px = p.lon * k; val py = p.lat
            val dx = bx - ax; val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
            val q = GeoPoint(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t)
            val d = q.distanceTo(p)
            if (d < best.offsetM) best = Projection(cum[i] + a.distanceTo(q), d, q, i)
        }
        return best
    }

    private fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val k = cos(Math.toRadians((a.lat + b.lat) / 2))
        return (Math.toDegrees(atan2((b.lon - a.lon) * k, b.lat - a.lat)) + 360) % 360
    }

    private fun angleDiff(a: Double, b: Double): Double { val d = abs(a - b) % 360; return if (d > 180) 360 - d else d }
}

/** Équivalent « signalement » d'un événement officiel (même icône, même Horizon) ; identifiant préfixé `bf:`. */
fun TrafficKind.reportKind(): fr.roadly.core.model.ReportKind = when (this) {
    TrafficKind.ACCIDENT -> fr.roadly.core.model.ReportKind.ACCIDENT
    TrafficKind.JAM, TrafficKind.SLOW -> fr.roadly.core.model.ReportKind.JAM
    TrafficKind.BROKEN_DOWN, TrafficKind.OBSTACLE -> fr.roadly.core.model.ReportKind.OBSTACLE
    TrafficKind.CLOSURE -> fr.roadly.core.model.ReportKind.CLOSED
    TrafficKind.LANE_CLOSURE, TrafficKind.ROADWORK -> fr.roadly.core.model.ReportKind.ROADWORK
    TrafficKind.WEATHER -> fr.roadly.core.model.ReportKind.WEATHER
}

fun TrafficHit.asReport(nowMs: Long): fr.roadly.core.model.CommunityReport = fr.roadly.core.model.CommunityReport(
    id = "bf:" + event.id, kind = event.kind.reportKind(), point = point,
    createdAtEpochMs = event.startEpochMs ?: nowMs, expiresAtEpochMs = event.endEpochMs ?: (nowMs + TrafficRepository.MAX_AGE_MS),
    confirmations = 0, denials = 0, mine = false,
)
