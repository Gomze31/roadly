package fr.roadly.core.map.junction

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Lane
import fr.roadly.core.model.LaneDirection
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.junction.JunctionGuidance
import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.JunctionMapContext
import fr.roadly.core.model.junction.JunctionPhase
import fr.roadly.core.model.junction.JunctionSign
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.model.junction.MapBuilding
import fr.roadly.core.model.junction.MapRoad
import fr.roadly.core.model.junction.RoadClass
import fr.roadly.core.model.junction.TopologyEdge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Géométrie, scène et maillages de la vue 3D sur un tracé géométrique simple (Kotlin pur) : les scénarios sur données réelles sont
 * dans `feature/navigation` (`JunctionPipelineTest`).
 */
class JunctionSceneTest {
    private val origin = GeoPoint(43.6, 1.44)
    private val frame = LocalFrame(origin)

    /** Autoroute plein nord sur 1 km, puis bretelle qui part à droite (30°) au point de manœuvre (indice 10). */
    private fun exitRoute(): Route {
        val pts = ArrayList<GeoPoint>()
        for (k in -10..0) pts += frame.toGeo(V2(0.0, k * 100.0))
        val dir = V2.ofHeading(30.0)
        for (k in 1..5) pts += frame.toGeo(dir * (k * 80.0))
        val ms = listOf(
            Maneuver(ManeuverType.DEPART, "Départ", null, 1000.0, 40.0, 0, null),
            Maneuver(ManeuverType.EXIT_RIGHT, "Prenez la sortie", null, 400.0, 20.0, 10, null, exitNumber = "12", exitToward = "Blagnac · Aéroport", bearingBeforeDeg = 0, bearingAfterDeg = 30),
            Maneuver(ManeuverType.ARRIVE, "Arrivée", null, 0.0, 0.0, 15, null),
        )
        return Route("r", RouteCosting.FASTEST, 1400.0, 60.0, pts, ms, false, "test")
    }

    private fun guidance(route: Route, vehicleAlong: Double, lanes: List<Lane> = emptyList(), topology: JunctionTopology? = null) = JunctionGuidance(
        routeId = route.id, maneuverIndex = 1, kind = JunctionKind.MOTORWAY_EXIT, phase = JunctionPhase.REVEAL,
        distanceM = 1000.0 - vehicleAlong, maneuverAlongM = 1000.0, vehicleAlongM = vehicleAlong, maneuverIndexInShape = 10,
        reveal = 0.5f, complexity = 0, sign = JunctionSign("12", listOf("Blagnac", "Aéroport"), "A621"), lanes = lanes,
        roundabout = null, topology = topology, degraded = false, chainedNext = false, speedKmh = 110.0,
    )

    @Test fun `local frame round trip`() {
        val p = GeoPoint(43.61, 1.45)
        val back = frame.toGeo(frame.toLocal(p))
        assertEquals(p.lat, back.lat, 1e-9)
        assertEquals(p.lon, back.lon, 1e-9)
        assertEquals(p.distanceTo(origin), frame.toLocal(p).length(), 2.0)
    }

    @Test fun `polyline sampling and projection`() {
        val l = Polyline2(listOf(V2(0.0, 0.0), V2(0.0, 100.0), V2(100.0, 100.0)))
        assertEquals(200.0, l.length, 1e-9)
        assertEquals(V2(50.0, 100.0), l.pointAt(150.0))
        val (s, d) = l.project(V2(10.0, 50.0))
        assertEquals(50.0, s, 1e-6); assertEquals(10.0, d, 1e-6)
    }

    @Test fun `circle fit finds a roundabout`() {
        val pts = (0 until 12).map { k -> val a = k * 0.3; V2(20 + 25 * kotlin.math.cos(a), -5 + 25 * kotlin.math.sin(a)) }
        val (c, r) = fitCircle(pts)!!
        assertEquals(20.0, c.x, 1e-6); assertEquals(-5.0, c.y, 1e-6); assertEquals(25.0, r, 1e-6)
    }

    @Test fun `ear clipping triangulates a concave footprint`() {
        val l = listOf(V2(0.0, 0.0), V2(20.0, 0.0), V2(20.0, 10.0), V2(10.0, 10.0), V2(10.0, 20.0), V2(0.0, 20.0))
        val tris = JunctionMeshBuilder.triangulate(l)
        assertEquals(12, tris.size)
        val area = tris.chunked(3).sumOf { (a, b, c) -> abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) / 2 }
        assertEquals(300.0, area, 1e-6)
    }

    @Test fun `scene follows the real route and keeps the vehicle inside the path`() {
        val route = exitRoute()
        val scene = JunctionSceneBuilder.build(route, guidance(route, 300.0), context = null)!!
        assertEquals(1000.0, scene.path.alongOffsetM + scene.path.maneuverS, 2.0)
        assertTrue(scene.path.alongOffsetM <= 300.0)
        assertTrue(scene.path.endS > scene.path.maneuverS)
        // Point de manœuvre à l'origine de la scène.
        assertTrue(scene.path.line.pointAt(scene.path.maneuverS).length() < 1.0)
        // Panneau réel posé avant la sortie, côté droit (la bretelle part à droite).
        assertEquals(1, scene.signs.size)
        assertEquals(1, scene.signs.first().side)
        assertEquals("12", scene.signs.first().exitNumber)
    }

    @Test fun `no lane markings and no lane arrows without lane data`() {
        val route = exitRoute()
        val scene = JunctionSceneBuilder.build(route, guidance(route, 300.0), null)!!
        assertTrue(scene.roads.flatMap { it.laneCounts }.all { it == null })
        assertTrue(scene.laneArrows.isEmpty())
        assertTrue(scene.path.lateral.all { it == 0.0 })
    }

    @Test fun `lane data shifts the path to the advised lanes and paints arrows`() {
        val route = exitRoute()
        val lanes = listOf(
            Lane(setOf(LaneDirection.STRAIGHT), valid = false), Lane(setOf(LaneDirection.STRAIGHT), valid = false),
            Lane(setOf(LaneDirection.STRAIGHT, LaneDirection.SLIGHT_RIGHT), valid = true), Lane(setOf(LaneDirection.SLIGHT_RIGHT), valid = true, active = true),
        )
        val scene = JunctionSceneBuilder.build(route, guidance(route, 300.0, lanes), null)!!
        assertTrue("trajectoire décalée à droite avant la sortie", scene.path.lateral.max() > 3.0)
        assertEquals(8, scene.laneArrows.size)
        assertEquals(2, scene.laneArrows.count { it.active })
    }

    @Test fun `topology gives lane counts and bridges, tile roads give the continuing motorway`() {
        val route = exitRoute()
        val topology = JunctionTopology(
            route.id, 1,
            listOf(
                TopologyEdge(0, 10, 1000.0, RoadClass.MOTORWAY, false, 3, false, bridge = false, tunnel = false, branches = emptyList()),
                TopologyEdge(10, 12, 160.0, RoadClass.MOTORWAY, true, 1, false, bridge = true, tunnel = false, branches = emptyList()),
                TopologyEdge(12, 15, 240.0, RoadClass.PRIMARY, false, 2, false, bridge = false, tunnel = false, branches = emptyList()),
            ),
        )
        val continuing = MapRoad((-10..6).map { frame.toGeo(V2(0.0, it * 100.0)) }, RoadClass.MOTORWAY, false, true, false, false, 0)
        val building = MapBuilding(listOf(V2(60.0, 60.0), V2(90.0, 60.0), V2(90.0, 90.0), V2(60.0, 90.0)).map { frame.toGeo(it) }, 18.0, 0.0)
        val ctx = JunctionMapContext(origin, 600.0, listOf(continuing), listOf(building))
        val scene = JunctionSceneBuilder.build(route, guidance(route, 300.0, topology = topology), ctx)!!
        val onRoute = scene.roads.single { it.onRoute }
        assertTrue(3 in onRoute.laneCounts)
        assertTrue("pont de la bretelle", onRoute.elev.max() > JunctionSceneBuilder.BRIDGE_DECK_M - 0.5)
        // L'autoroute qui continue n'est gardée qu'après la séparation (et derrière le début de la fenêtre), jamais en double sous la trajectoire.
        val other = scene.roads.filterNot { it.onRoute }
        assertTrue(other.isNotEmpty())
        val windowStart = onRoute.pts.first().y
        assertTrue(other.flatMap { it.pts }.none { it.y in (windowStart + 20)..-20.0 })
        assertTrue(other.flatMap { it.pts }.any { it.y > 100.0 })
        assertEquals(1, scene.buildings.size)
        assertTrue("musoir entre bretelle et autoroute", scene.gores.isNotEmpty())
        val meshes = JunctionMeshBuilder.build(scene, JunctionPalette.NIGHT)
        assertTrue(meshes.opaqueVertices > 500)
        assertTrue(meshes.transparentVertices > 50)
        assertFalse(meshes.opaque.any { it.isNaN() || it.isInfinite() })
        assertFalse(meshes.transparent.any { it.isNaN() || it.isInfinite() })
        assertEquals(0, meshes.opaqueVertices % 3)
    }

    @Test fun `reduced detail drops buildings and pillars`() {
        val route = exitRoute()
        val building = MapBuilding(listOf(V2(60.0, 60.0), V2(90.0, 60.0), V2(90.0, 90.0)).map { frame.toGeo(it) }, 18.0, 0.0)
        val scene = JunctionSceneBuilder.build(route, guidance(route, 300.0), JunctionMapContext(origin, 600.0, emptyList(), listOf(building)), JunctionDetail.REDUCED)!!
        assertTrue(scene.buildings.isEmpty())
        assertTrue(scene.pillars.isEmpty())
    }

    @Test fun `no sign drawn without Valhalla sign data`() {
        val route = exitRoute()
        val scene = JunctionSceneBuilder.build(route, guidance(route, 300.0).copy(sign = null), null)!!
        assertTrue(scene.signs.isEmpty())
    }

    @Test fun `camera descends smoothly from overview to chase, wider for roundabouts`() {
        val route = exitRoute()
        val m = JunctionMeshBuilder.build(JunctionSceneBuilder.build(route, guidance(route, 300.0), null)!!, JunctionPalette.DAY)
        val cam = JunctionCamera(m.pathSamples, m.pathStep, m.maneuverS, null)
        val far = cam.rigFor(JunctionCameraInput(JunctionPhase.REVEAL, JunctionKind.MOTORWAY_EXIT, 0, m.maneuverS - 500, 110.0, 1f, 0.016))
        val near = cam.rigFor(JunctionCameraInput(JunctionPhase.PRECISE, JunctionKind.MOTORWAY_EXIT, 0, m.maneuverS - 80, 110.0, 1f, 0.016))
        val at = cam.rigFor(JunctionCameraInput(JunctionPhase.MANEUVER, JunctionKind.MOTORWAY_EXIT, 0, m.maneuverS, 110.0, 1f, 0.016))
        assertTrue(far.height > near.height && near.height > at.height)
        val rb = cam.rigFor(JunctionCameraInput(JunctionPhase.REVEAL, JunctionKind.ROUNDABOUT, 0, m.maneuverS - 500, 110.0, 1f, 0.016))
        assertTrue(rb.height > far.height)
        val complex = cam.rigFor(JunctionCameraInput(JunctionPhase.REVEAL, JunctionKind.INTERCHANGE, 2, m.maneuverS - 500, 110.0, 1f, 0.016))
        assertTrue(complex.height > far.height)
        // Changement d'étape : l'œil glisse, sans saut d'une image à l'autre.
        var s = m.maneuverS - 300
        var last = cam.update(JunctionCameraInput(JunctionPhase.REINFORCE, JunctionKind.MOTORWAY_EXIT, 0, s, 110.0, 1f, 1 / 60.0))
        repeat(120) {
            s += 30.0 / 60
            val ph = if (it < 60) JunctionPhase.REINFORCE else JunctionPhase.PRECISE
            val p = cam.update(JunctionCameraInput(ph, JunctionKind.MOTORWAY_EXIT, 0, s, 110.0, 1f, 1 / 60.0))
            val jump = kotlin.math.sqrt((0..2).sumOf { k -> ((p.eye[k] - last.eye[k]) * (p.eye[k] - last.eye[k])).toDouble() })
            assertTrue("saut de caméra $jump m", jump < 2.0)
            last = p
        }
    }

    @Test fun `vehicle interpolation never jumps back for a small backward fix`() {
        val v = VehicleInterpolator()
        v.onFix(100.0, 72.0, 0.0)
        var t = 0.0; var s = v.current(t, 0.016)
        repeat(60) { t += 1 / 60.0; val n = v.current(t, 1 / 60.0); assertTrue(n >= s - 1e-9); s = n }
        v.onFix(s - 3.0, 72.0, t)
        repeat(30) { t += 1 / 60.0; val n = v.current(t, 1 / 60.0); assertTrue(n >= s - 1e-9); s = n }
        assertNotNull(s)
    }
}
