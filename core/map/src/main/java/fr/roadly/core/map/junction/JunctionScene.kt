package fr.roadly.core.map.junction

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.Route
import fr.roadly.core.model.junction.JunctionGuidance
import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.JunctionMapContext
import fr.roadly.core.model.junction.MapRoad
import fr.roadly.core.model.junction.RoadClass
import fr.roadly.core.model.junction.TopologyEdge
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Route de la scène : polyligne locale et, par sommet, largeur, élévation et pont ; [laneCount] null = voies inconnues (aucun marquage inventé). */
data class SceneRoad(
    val pts: List<V2>,
    val widths: List<Double>,
    val elev: List<Double>,
    val laneCounts: List<Int?>,
    val roadClass: RoadClass,
    val isRamp: Boolean,
    val tunnel: List<Boolean>,
    /** Glissières / parapets sur les deux bords. */
    val barriers: Boolean,
    /** Sous-tracé de l'itinéraire (chaussée sous la trajectoire). */
    val onRoute: Boolean,
    /** Ordre de dessin (les routes importantes au-dessus). */
    val order: Int,
)

/** Trajectoire recommandée : abscisse [s] locale (0 = début de la fenêtre), décalage latéral par sommet (voies conseillées). */
data class ScenePath(
    val pts: List<V2>,
    val elev: List<Double>,
    val lateral: List<Double>,
    /** Abscisse du tracé (`RouteProgress.distanceAlongM`) au premier point. */
    val alongOffsetM: Double,
    /** Abscisse locale du point de manœuvre et de la fin de la trajectoire (flèche). */
    val maneuverS: Double,
    val endS: Double,
) {
    val line by lazy { Polyline2(pts) }
}

enum class SignStyle { MOTORWAY, PRIMARY, LOCAL }

/** Panneau de direction réel, sur portique au-dessus de la chaussée (sorties) ou en accotement (ronds-points). */
data class SceneSign(
    val at: V2,
    val elev: Double,
    /** Sens de marche (unitaire) : le panneau fait face au conducteur. */
    val dir: V2,
    val roadWidth: Double,
    /** +1 = panneau au-dessus de la droite de la chaussée, −1 = à gauche, 0 = centré. */
    val side: Int,
    val exitNumber: String?,
    val lines: List<String>,
    val branch: String?,
    val arrowDeg: Double,
    val style: SignStyle,
    val gantry: Boolean,
)

/** Étiquette (numéro de sortie de rond-point) : toujours tournée vers la caméra. */
data class SceneLabel(val at: V2, val elev: Double, val text: String, val target: Boolean)

data class SceneRoundabout(val center: V2, val radius: Double, val width: Double, val lanes: Int?)

data class SceneBuilding(val ring: List<V2>, val height: Double, val minHeight: Double)

/** Zone hachurée de séparation (musoir) entre la trajectoire et la branche qui continue. */
data class SceneGore(val polygon: List<V2>, val elev: Double)

/** Flèches peintes sur les voies d'approche, quand Valhalla fournit les voies. */
data class SceneLaneArrow(val at: V2, val elev: Double, val dir: V2, val direction: LaneDirection, val active: Boolean, val valid: Boolean)

data class JunctionScene(
    /** Identifie le carrefour (tracé + manœuvre + topologie chargée). */
    val key: String,
    val kind: JunctionKind,
    val origin: GeoPoint,
    val radiusM: Double,
    val roads: List<SceneRoad>,
    val path: ScenePath,
    val gores: List<SceneGore>,
    val signs: List<SceneSign>,
    val labels: List<SceneLabel>,
    val roundabout: SceneRoundabout?,
    val buildings: List<SceneBuilding>,
    val laneArrows: List<SceneLaneArrow>,
    /** Piles de ponts (pied, hauteur). */
    val pillars: List<Pair<V2, Double>>,
)

/** Niveau de détail de la scène (économie d'énergie : sans bâtiments ni piles, rayon réduit). */
enum class JunctionDetail { FULL, REDUCED }

/**
 * Construit la scène d'un carrefour à partir de données réelles uniquement (Kotlin pur, testé sur JVM) :
 * - trajectoire et chaussée sous la trajectoire = géométrie de l'itinéraire calculé ;
 * - voies, classes, ponts, branches = topologie Valhalla (si chargée) ;
 * - autres routes, ponts et bâtiments = tuiles vectorielles déjà chargées (hors ligne compris) ;
 * - panneaux = `sign` Valhalla ; numéros de sortie = comptage topologique cohérent avec Valhalla.
 * Sans donnée, l'élément n'est pas dessiné (pas de marquage de voie inventé, pas de panneau inventé).
 */
object JunctionSceneBuilder {
    const val LANE_WIDTH_M = 3.5
    const val SHOULDER_M = 1.0
    const val BRIDGE_DECK_M = 6.5
    const val BRIDGE_RAMP_M = 70.0
    const val PATH_BEFORE_MIN_M = 650.0
    const val PATH_AFTER_M = 320.0
    const val ARROW_AFTER_M = 70.0
    const val ROUNDABOUT_ARROW_AFTER_M = 45.0
    const val RADIUS_M = 480.0
    const val BUILDINGS_RADIUS_M = 320.0
    const val MAX_BUILDINGS = 450
    const val SIGN_BEFORE_M = 170.0
    const val GORE_LENGTH_M = 90.0
    private const val NEAR_ROUTE_M = 3.5
    private const val STUB_M = 70.0
    /** Sans topologie, les voies de Valhalla (`lanes`) valent pour les 300 derniers mètres avant la manœuvre. */
    private const val LANES_KNOWN_BEFORE_M = 300.0

    fun defaultWidth(c: RoadClass, ramp: Boolean, oneway: Boolean): Double = when {
        ramp -> 5.0
        c == RoadClass.MOTORWAY -> 8.5
        c == RoadClass.TRUNK -> if (oneway) 8.0 else 9.0
        c == RoadClass.PRIMARY -> 7.5
        c == RoadClass.SECONDARY -> 7.0
        c == RoadClass.TERTIARY -> 6.5
        c == RoadClass.UNCLASSIFIED || c == RoadClass.RESIDENTIAL -> 5.5
        c == RoadClass.SERVICE -> 4.0
        else -> 5.0
    }

    fun widthFor(lanes: Int?, c: RoadClass, ramp: Boolean, oneway: Boolean): Double =
        if (lanes != null) lanes * LANE_WIDTH_M + SHOULDER_M else defaultWidth(c, ramp, oneway)

    fun build(
        route: Route,
        g: JunctionGuidance,
        context: JunctionMapContext?,
        detail: JunctionDetail = JunctionDetail.FULL,
    ): JunctionScene? {
        val geo = route.geometry
        if (geo.size < 2) return null
        val shapeIdx = g.maneuverIndexInShape.coerceIn(0, geo.lastIndex)
        val origin = geo[shapeIdx]
        val frame = LocalFrame(origin)
        val cum = fr.roadly.core.map.junction.cumulative(geo)
        val maneuverAlong = cum[shapeIdx]
        val roundabout = g.roundabout
        val endAlong = if (roundabout != null) cum[roundabout.exitIndex.coerceIn(0, geo.lastIndex)] else maneuverAlong
        // Fenêtre : depuis un peu derrière le véhicule (ou au moins 650 m avant), jusqu'à 320 m après la manœuvre.
        val startAlong = (min(g.vehicleAlongM, maneuverAlong - PATH_BEFORE_MIN_M) - 60.0).coerceAtLeast(0.0)
        val stopAlong = min(endAlong + PATH_AFTER_M, cum.last())
        val idx = (0..geo.lastIndex).filter { cum[it] in startAlong..stopAlong }.ifEmpty { return null }
        val first = idx.first(); val last = idx.last()
        val routePts = (first..last).map { frame.toLocal(geo[it]) }
        val routeCum = (first..last).map { cum[it] - cum[first] }
        val topology = g.topology
        val edgeFor = { i: Int -> topology?.edgeAt(i) ?: topology?.edges?.lastOrNull { it.endIndex == i } }
        val radius = if (detail == JunctionDetail.REDUCED) RADIUS_M * 0.75 else RADIUS_M

        // Routes des tuiles, en local, découpées au rayon.
        val tileRoads = context?.roads.orEmpty().mapNotNull { r -> localRoad(frame, r, radius) }
        // Chaussée sous la trajectoire : voies et classe par sommet, ponts par la topologie ou par les tuiles.
        val laneCounts = ArrayList<Int?>(); val classes = ArrayList<RoadClass>(); val ramps = ArrayList<Boolean>()
        val bridgeRaw = ArrayList<Boolean>(); val tunnels = ArrayList<Boolean>()
        for ((k, i) in (first..last).withIndex()) {
            val e: TopologyEdge? = edgeFor(i)
            val tileMatch = if (e == null) nearestTileRoad(routePts[k], tileRoads) else null
            laneCounts += e?.laneCount ?: g.lanes.size.takeIf { it > 0 && i <= shapeIdx && maneuverAlong - cum[i] < LANES_KNOWN_BEFORE_M }
            classes += e?.roadClass ?: tileMatch?.road?.roadClass ?: inferClass(g.kind, i <= shapeIdx)
            ramps += e?.isRamp ?: tileMatch?.road?.isRamp ?: (g.kind != JunctionKind.ROUNDABOUT && i > shapeIdx && g.kind != JunctionKind.COMPLEX_TURN)
            bridgeRaw += e?.bridge ?: (tileMatch?.road?.bridge == true)
            tunnels += e?.tunnel ?: (tileMatch?.road?.tunnel == true)
        }
        val routeElev = rampElevation(routeCum, bridgeRaw)
        val routeWidths = routePts.indices.map { k -> widthFor(laneCounts[k], classes[k], ramps[k], oneway = true) }
        val routeClass = classes.minByOrNull { it.rank } ?: RoadClass.OTHER
        val routeRoad = SceneRoad(
            routePts, routeWidths, routeElev, laneCounts, routeClass, ramps.any { it }, tunnels,
            barriers = classes.any { it.isHighway } || ramps.any { it }, onRoute = true, order = 100,
        )

        // Rond-point : anneau complet ajusté sur l'anneau parcouru (géométrie de l'itinéraire), îlot central.
        var sceneRoundabout: SceneRoundabout? = null
        if (roundabout != null) {
            val ringPts = (roundabout.entryIndex..roundabout.exitIndex).filter { it in first..last }.map { frame.toLocal(geo[it]) }
            val ringTile = tileRoads.flatMap { it.pts }.filter { p -> ringPts.isNotEmpty() && distanceToPolyline(p, ringPts) < 40.0 }
            val fit = fitCircle(ringPts.ifEmpty { ringTile }) ?: fitCircle(ringPts + ringTile)
            // Mini-giratoires compris (Muret : ≈ 6 m de rayon), jusqu'aux grandes places (Étoile ≈ 110 m).
            if (fit != null && fit.second in 4.0..150.0) {
                val ringLanes = topology?.edges?.filter { it.roundabout }?.mapNotNull { it.laneCount }?.maxOrNull()
                sceneRoundabout = SceneRoundabout(fit.first, fit.second, widthFor(ringLanes, RoadClass.TERTIARY, false, true).coerceAtLeast(6.0), ringLanes)
            }
        }

        // Autres routes : parties des tuiles hors de la chaussée de l'itinéraire et hors de l'anneau.
        val others = ArrayList<SceneRoad>()
        for (t in tileRoads) {
            for (run in splitAwayFrom(t, routePts, routeWidths, sceneRoundabout)) {
                if (run.size < 2) continue
                val line = Polyline2(run)
                val bridge = run.map { t.road.bridge }
                val elevBase = if (t.road.bridge) BRIDGE_DECK_M * max(1, t.road.layer) else 0.0
                val elev = if (t.road.bridge) bridgeEnds(line, elevBase, t) else run.map { 0.0 }
                val w = widthFor(null, t.road.roadClass, t.road.isRamp, t.road.oneway)
                others += SceneRoad(
                    run, run.map { w }, elev, run.map { null }, t.road.roadClass, t.road.isRamp, run.map { t.road.tunnel },
                    barriers = t.road.roadClass.isHighway || t.road.isRamp || bridge.any { it }, onRoute = false,
                    order = 50 - t.road.roadClass.rank * 5,
                )
            }
        }
        // Branches de la topologie sans route de tuile (hors ligne sans carte) : amorces droites réelles (cap Valhalla).
        if (topology != null) {
            for (e in topology.edges) {
                val node = e.endIndex
                if (node !in first..last || node == last) continue
                val at = frame.toLocal(geo[node])
                val k = node - first
                for (b in e.branches) {
                    val dir = V2.ofHeading(b.headingDeg.toDouble())
                    val probe = at + dir * 30.0
                    val covered = others.any { distanceToPolyline(probe, it.pts) < 12.0 } ||
                        (sceneRoundabout != null && abs(probe.dist(sceneRoundabout.center) - sceneRoundabout.radius) < sceneRoundabout.width)
                    if (covered) continue
                    val w = widthFor(null, b.roadClass, b.isRamp, oneway = b.outbound != b.inbound)
                    val pts = listOf(at, at + dir * STUB_M)
                    others += SceneRoad(
                        pts, pts.map { w }, pts.map { routeElev[k] }, pts.map { null }, b.roadClass, b.isRamp, pts.map { false },
                        barriers = b.roadClass.isHighway, onRoute = false, order = 50 - b.roadClass.rank * 5,
                    )
                }
            }
        }

        // Trajectoire : du début de la fenêtre jusqu'à la flèche après la manœuvre (sortie du rond-point).
        val maneuverS = maneuverAlong - cum[first]
        val arrowAfter = if (roundabout != null) ROUNDABOUT_ARROW_AFTER_M else ARROW_AFTER_M
        val endS = min((endAlong - cum[first]) + arrowAfter, routeCum.last())
        val laneInfo = laneOffsets(g.lanes, laneCounts.getOrNull((shapeIdx - first).coerceIn(0, laneCounts.lastIndex)))
        val lateral = routeCum.map { s ->
            if (laneInfo == null) 0.0 else {
                val start = maneuverS - 260.0
                val w = when {
                    s < start -> smoothstep((s - (start - 120.0)) / 120.0)
                    s <= maneuverS -> 1.0
                    else -> 1.0 - smoothstep((s - maneuverS) / 40.0)
                }
                laneInfo.activeOffset * w
            }
        }
        val path = ScenePath(routePts, routeElev.map { it + 0.0 }, lateral, cum[first], maneuverS, endS)

        val gores = if (g.kind == JunctionKind.ROUNDABOUT) emptyList() else gore(path, routeWidths, others)
        val signs = signsFor(g, path, routeWidths, routeElev)
        val labels = labelsFor(g, frame, geo, sceneRoundabout)
        val arrows = laneArrows(g.lanes, laneInfo, path, routeElev)
        val buildings = if (detail == JunctionDetail.REDUCED) emptyList() else buildingsFor(context, frame)
        val pillars = if (detail == JunctionDetail.REDUCED) emptyList() else pillarsFor(listOf(routeRoad) + others)
        return JunctionScene(
            key = "${g.routeId}#${g.maneuverIndex}#${topology != null}#${context?.roads?.size ?: 0}",
            kind = g.kind, origin = origin, radiusM = radius,
            roads = (others + routeRoad).sortedBy { it.order },
            path = path, gores = gores, signs = signs, labels = labels, roundabout = sceneRoundabout,
            buildings = buildings, laneArrows = arrows, pillars = pillars,
        )
    }

    private fun inferClass(kind: JunctionKind, before: Boolean): RoadClass = when (kind) {
        JunctionKind.MOTORWAY_EXIT, JunctionKind.MOTORWAY_FORK, JunctionKind.INTERCHANGE, JunctionKind.MERGE -> if (before || kind == JunctionKind.MOTORWAY_FORK) RoadClass.MOTORWAY else RoadClass.PRIMARY
        JunctionKind.ROUNDABOUT -> RoadClass.TERTIARY
        JunctionKind.COMPLEX_TURN -> RoadClass.PRIMARY
    }

    internal class LocalTileRoad(val road: MapRoad, val pts: List<V2>)

    private fun localRoad(frame: LocalFrame, r: MapRoad, radius: Double): LocalTileRoad? {
        val pts = r.points.map { frame.toLocal(it) }
        if (pts.size < 2 || pts.none { it.length() < radius }) return null
        return LocalTileRoad(r, pts)
    }

    private class TileMatch(val road: MapRoad)

    private fun nearestTileRoad(p: V2, roads: List<LocalTileRoad>): TileMatch? =
        roads.asSequence().map { it to distanceToPolyline(p, it.pts) }.filter { it.second < 4.0 }.minByOrNull { it.second }?.let { TileMatch(it.first.road) }

    /** Élévation des sommets : tablier à [BRIDGE_DECK_M] sur les ponts, rampes douces de [BRIDGE_RAMP_M] de part et d'autre. */
    internal fun rampElevation(s: List<Double>, bridge: List<Boolean>): List<Double> {
        val bridgeS = s.indices.filter { bridge[it] }.map { s[it] }
        if (bridgeS.isEmpty()) return s.map { 0.0 }
        return s.map { si ->
            val d = bridgeS.minOf { abs(it - si) }
            BRIDGE_DECK_M * (1.0 - smoothstep(d / BRIDGE_RAMP_M))
        }
    }

    private fun bridgeEnds(line: Polyline2, deck: Double, t: LocalTileRoad): List<Double> {
        // Pont d'une tuile : le tablier remonte depuis le sol si l'extrémité n'est pas aussi sur un pont (rampe de 40 m).
        val len = line.length
        return line.pts.indices.map { i ->
            val si = line.s[i]
            val fromEnd = min(si, len - si)
            if (len < 80.0) deck else deck * smoothstep(fromEnd / 40.0).coerceAtLeast(if (t.road.layer >= 1) 0.5 else 0.0)
        }
    }

    /** Parties d'une route de tuile éloignées de la chaussée de l'itinéraire (et de l'anneau), avec un point de raccord. */
    private fun splitAwayFrom(t: LocalTileRoad, route: List<V2>, widths: List<Double>, rb: SceneRoundabout?): List<List<V2>> {
        val dense = Polyline2(t.pts).densified(8.0)
        val half = (widths.maxOrNull() ?: 8.0) / 2.0
        val myHalf = defaultWidth(t.road.roadClass, t.road.isRamp, t.road.oneway) / 2.0
        val keep = dense.map { p ->
            val dRoute = distanceToPolyline(p, route)
            val onRoute = dRoute < max(NEAR_ROUTE_M, min(half, myHalf) * 0.8)
            val onRing = rb != null && abs(p.dist(rb.center) - rb.radius) < rb.width * 0.6
            !(onRoute || onRing)
        }
        val runs = ArrayList<List<V2>>()
        var cur = ArrayList<V2>()
        for (i in dense.indices) {
            if (keep[i]) {
                if (cur.isEmpty() && i > 0) cur += dense[i - 1]
                cur += dense[i]
            } else if (cur.isNotEmpty()) {
                cur += dense[i]
                runs += cur; cur = ArrayList()
            }
        }
        if (cur.size >= 2) runs += cur
        return runs.filter { Polyline2(it).length > 6.0 }
    }

    private data class LaneLayout(val count: Int, val activeOffset: Double, val offsets: List<Double>)

    /** Décalage (m, > 0 = à droite) du centre des voies conseillées, si Valhalla fournit les voies. */
    private fun laneOffsets(lanes: List<Lane>, laneCount: Int?): LaneLayout? {
        if (lanes.isEmpty()) return null
        val n = lanes.size
        if (laneCount != null && laneCount != n && laneCount > 0 && abs(laneCount - n) > 1) return null
        val width = n * LANE_WIDTH_M
        val offsets = (0 until n).map { -width / 2 + (it + 0.5) * LANE_WIDTH_M }
        val chosen = lanes.indices.filter { lanes[it].active }.ifEmpty { lanes.indices.filter { lanes[it].valid } }
        if (chosen.isEmpty()) return null
        return LaneLayout(n, chosen.map { offsets[it] }.average(), offsets)
    }

    private fun laneArrows(lanes: List<Lane>, layout: LaneLayout?, path: ScenePath, elev: List<Double>): List<SceneLaneArrow> {
        if (layout == null) return emptyList()
        val out = ArrayList<SceneLaneArrow>()
        for (back in listOf(140.0, 70.0)) {
            val s = path.maneuverS - back
            if (s < 0) continue
            val c = path.line.pointAt(s); val dir = path.line.tangentAt(s); val right = dir.rightNormal()
            val e = elevAt(path, elev, s)
            for ((i, lane) in lanes.withIndex()) {
                val d = lane.directions.firstOrNull() ?: continue
                out += SceneLaneArrow(c + right * layout.offsets[i], e, dir, d, lane.active, lane.valid)
            }
        }
        return out
    }

    internal fun elevAt(path: ScenePath, elev: List<Double>, s: Double): Double {
        val line = path.line
        val i = line.s.indexOfFirst { it >= s }.let { if (it < 0) line.s.lastIndex else it }
        if (i <= 0) return elev.first()
        val t = ((s - line.s[i - 1]) / (line.s[i] - line.s[i - 1]).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
        return elev[i - 1] + (elev[i] - elev[i - 1]) * t
    }

    /** Musoir : entre le bord intérieur de la trajectoire et la branche qui continue, sur [GORE_LENGTH_M] après la séparation. */
    private fun gore(path: ScenePath, widths: List<Double>, others: List<SceneRoad>): List<SceneGore> {
        val line = path.line
        val node = line.pointAt(path.maneuverS)
        val dirR = line.tangentAt(path.maneuverS + 20.0, 10.0)
        // Branche qui continue : route voisine qui part du nœud dans un cap proche (< 50°) de la trajectoire.
        val other = others.filter { it.roadClass.rank <= RoadClass.PRIMARY.rank || it.isRamp }.mapNotNull { r ->
            val pl = Polyline2(r.pts)
            val (s0, d0) = pl.project(node)
            if (d0 > 18.0) return@mapNotNull null
            val forward = pl.tangentAt(s0 + 20.0, 10.0)
            val sign = if (forward.dot(dirR) >= 0) 1.0 else -1.0
            val dir = forward * sign
            if (dir.dot(dirR) < kotlin.math.cos(Math.toRadians(50.0))) return@mapNotNull null
            Triple(pl, s0, sign) to r
        }.minByOrNull { (it.first.first.project(node)).second } ?: return emptyList()
        val (pl, s0, sign) = other.first
        val otherHalf = other.second.widths.first() / 2
        val left = ArrayList<V2>(); val right = ArrayList<V2>()
        val side = run {
            val probe = pl.pointAt(s0 + sign * 40.0)
            val rel = probe - line.pointAt(path.maneuverS + 40.0)
            if (rel.dot(line.tangentAt(path.maneuverS + 40.0).rightNormal()) > 0) 1.0 else -1.0
        }
        var d = 0.0
        while (d <= GORE_LENGTH_M) {
            val sR = path.maneuverS + d
            val pR = line.pointAt(sR); val nR = line.tangentAt(sR).rightNormal()
            val wR = widths.getOrNull(line.s.indexOfFirst { it >= sR }.coerceAtLeast(0)) ?: 5.0
            val edgeR = pR + nR * (side * wR / 2)
            val pO = pl.pointAt(s0 + sign * d)
            val nO = (pR - pO).normalized()
            val edgeO = pO + nO * otherHalf
            val gap = (edgeO - edgeR).dot(nR) * side
            if (gap > 0.4) { left += edgeR; right += edgeO }
            d += 6.0
        }
        if (left.size < 3) return emptyList()
        val elev = elevAt(path, path.elev, path.maneuverS + GORE_LENGTH_M / 2)
        return listOf(SceneGore(left + right.reversed(), elev))
    }

    private fun signsFor(g: JunctionGuidance, path: ScenePath, widths: List<Double>, elev: List<Double>): List<SceneSign> {
        val sign = g.sign ?: return emptyList()
        val line = path.line
        val s = (path.maneuverS - SIGN_BEFORE_M).coerceAtLeast(10.0)
        val at = line.pointAt(s); val dir = line.tangentAt(s, 15.0)
        val w = widths.getOrNull(line.s.indexOfFirst { it >= s }.coerceAtLeast(0)) ?: 8.0
        val after = line.tangentAt(path.maneuverS + 40.0, 20.0)
        val turn = fr.roadly.core.model.normalizeSignedDeg(headingOf(after) - headingOf(dir))
        val side = when {
            g.kind == JunctionKind.ROUNDABOUT -> 1
            turn > 4 -> 1
            turn < -4 -> -1
            else -> 0
        }
        // Signalisation française : bleu sur autoroute (et pour une destination autoroutière), vert ailleurs.
        val toMotorway = sign.branch?.trim()?.let { it.startsWith("A") || it.startsWith("E") } == true
        val style = when (g.kind) {
            JunctionKind.ROUNDABOUT, JunctionKind.COMPLEX_TURN -> if (toMotorway) SignStyle.MOTORWAY else SignStyle.PRIMARY
            else -> SignStyle.MOTORWAY
        }
        val lines = sign.toward.take(3)
        return listOf(
            SceneSign(at, elevAt(path, elev, s), dir, w, side, sign.exitNumber, lines, sign.branch, turn, style, gantry = g.kind != JunctionKind.ROUNDABOUT),
        )
    }

    private fun labelsFor(g: JunctionGuidance, frame: LocalFrame, geo: List<GeoPoint>, rb: SceneRoundabout?): List<SceneLabel> {
        val r = g.roundabout ?: return emptyList()
        if (rb == null) return emptyList()
        return r.exits.map { e ->
            val node = frame.toLocal(geo[e.nodeIndex.coerceIn(0, geo.lastIndex)])
            val dir = V2.ofHeading(e.headingDeg.toDouble())
            val radial = (node - rb.center).normalized()
            val out = (radial + dir).normalized().let { if (it.length() < 0.5) radial else it }
            SceneLabel(node + out * (rb.width * 0.5 + 9.0), 4.5, e.rank.toString(), e.isTarget)
        }
    }

    private fun buildingsFor(context: JunctionMapContext?, frame: LocalFrame): List<SceneBuilding> =
        context?.buildings.orEmpty().asSequence()
            .map { b -> SceneBuilding(b.ring.map { frame.toLocal(it) }, b.heightM.coerceIn(3.0, 220.0), b.minHeightM.coerceAtLeast(0.0)) }
            .filter { it.ring.size >= 3 }
            .map { it to it.ring.minOf { p -> p.length() } }
            .filter { it.second < BUILDINGS_RADIUS_M }
            .sortedBy { it.second }
            .take(MAX_BUILDINGS)
            .map { it.first }
            .toList()

    private fun pillarsFor(roads: List<SceneRoad>): List<Pair<V2, Double>> {
        val out = ArrayList<Pair<V2, Double>>()
        for (r in roads) {
            val line = Polyline2(r.pts)
            var s = 15.0
            while (s < line.length) {
                val i = line.s.indexOfFirst { it >= s }.coerceAtLeast(1)
                val t = ((s - line.s[i - 1]) / (line.s[i] - line.s[i - 1]).coerceAtLeast(1e-6)).coerceIn(0.0, 1.0)
                val h = r.elev[i - 1] + (r.elev[i] - r.elev[i - 1]) * t
                if (h > 3.0) out += line.pointAt(s) to h
                s += 30.0
            }
        }
        return out
    }
}

/** Abscisses cumulées (m) d'une géométrie. */
internal fun cumulative(geo: List<GeoPoint>): DoubleArray = DoubleArray(geo.size).also { for (i in 1 until geo.size) it[i] = it[i - 1] + geo[i - 1].distanceTo(geo[i]) }
