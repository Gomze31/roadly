package fr.roadly.feature.navigation

import fr.roadly.core.map.junction.JunctionDetail
import fr.roadly.core.map.junction.JunctionMeshBuilder
import fr.roadly.core.map.junction.JunctionPalette
import fr.roadly.core.map.junction.JunctionScene
import fr.roadly.core.map.junction.JunctionSceneBuilder
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.Route
import fr.roadly.core.model.RouteCosting
import fr.roadly.core.model.RouteProgress
import fr.roadly.core.model.junction.JunctionGuidance
import fr.roadly.core.model.junction.JunctionKind
import fr.roadly.core.model.junction.JunctionPhase
import fr.roadly.core.model.junction.JunctionTopology
import fr.roadly.core.navigation.RouteGeometry
import fr.roadly.core.navigation.junction.JunctionAnticipation
import fr.roadly.core.navigation.junction.JunctionInput
import fr.roadly.core.navigation.junction.ValhallaJunctionTopology
import fr.roadly.core.routing.Polyline6
import fr.roadly.core.routing.ValhallaManeuverTypes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vue 3D de carrefour de bout en bout sur des itinéraires Valhalla réels (fixtures de core:navigation) : anticipation → scène →
 * maillages. Scénarios du cahier des charges : sortie simple, sortie suivie d'une bifurcation, ronds-points à 3 et 6 sorties
 * (Nation, Étoile), échangeur à plusieurs niveaux, GPS imprécis, recalcul, hors ligne sans topologie ni tuiles.
 */
class JunctionPipelineTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun load(resource: String): JsonObject = json.parseToJsonElement(javaClass.classLoader!!.getResource(resource)!!.readText()).jsonObject

    private fun route(name: String): Route {
        val root = load(if (name == "motorway") "fixtures/valhalla_motorway.json" else "fixtures/junction_${name}_route.json")
        val leg = root["trip"]!!.jsonObject["legs"]!!.jsonArray[0].jsonObject
        val ms = leg["maneuvers"]!!.jsonArray.map { it.jsonObject }.map { m ->
            val sign = m["sign"] as? JsonObject
            fun texts(k: String) = (sign?.get(k) as? JsonArray)?.mapNotNull { ((it as? JsonObject)?.get("text") as? JsonPrimitive)?.contentOrNull }.orEmpty()
            Maneuver(
                type = ValhallaManeuverTypes.of(m["type"]!!.jsonPrimitive.int), instruction = (m["instruction"] as JsonPrimitive).content,
                streetName = (m["street_names"] as? JsonArray)?.firstOrNull()?.jsonPrimitive?.content,
                lengthMeters = m["length"]!!.jsonPrimitive.double * 1000, durationSeconds = m["time"]!!.jsonPrimitive.double,
                beginShapeIndex = m["begin_shape_index"]!!.jsonPrimitive.int, roundaboutExit = (m["roundabout_exit_count"] as? JsonPrimitive)?.intOrNull,
                exitNumber = texts("exit_number_elements").firstOrNull(), exitToward = texts("exit_toward_elements").take(2).joinToString(" · ").ifEmpty { null },
                exitBranch = texts("exit_branch_elements").firstOrNull(),
                bearingBeforeDeg = (m["bearing_before"] as? JsonPrimitive)?.intOrNull, bearingAfterDeg = (m["bearing_after"] as? JsonPrimitive)?.intOrNull,
                endShapeIndex = (m["end_shape_index"] as? JsonPrimitive)?.intOrNull,
            )
        }
        return Route(name, RouteCosting.FASTEST, 0.0, 0.0, Polyline6.decode(leg["shape"]!!.jsonPrimitive.content), ms, false, "valhalla")
    }

    private fun topology(name: String, r: Route): JunctionTopology =
        JunctionTopology(r.id, 0, ValhallaJunctionTopology.parse(json, javaClass.classLoader!!.getResource("fixtures/junction_${name}_trace.json")!!.readText(), 0)!!)

    private fun alongOf(r: Route, i: Int): Double = RouteGeometry(r.geometry).cumulative[r.maneuvers[i].beginShapeIndex]

    private fun progress(r: Route, along: Double, accuracy: Float = 5f): RouteProgress {
        val g = RouteGeometry(r.geometry)
        val next = r.maneuvers.indices.firstOrNull { it > 0 && alongOf(r, it) > along } ?: r.maneuvers.lastIndex
        val seg = (0 until g.cumulative.lastIndex).lastOrNull { g.cumulative[it] <= along } ?: 0
        return RouteProgress(r.id, seg, along, 0.0, next, (alongOf(r, next) - along).coerceAtLeast(0.0), g.totalM - along, 0.0, r.geometry[seg], null, accuracyM = accuracy)
    }

    /** Vue du carrefour [index] vue à [beforeM] m (première étape visible), via la vraie machine d'anticipation. */
    private fun guidanceFor(r: Route, t: JunctionTopology?, index: Int, kmh: Double, accuracy: Float = 5f): JunctionGuidance {
        val a = JunctionAnticipation()
        val target = alongOf(r, index)
        var s = (target - 1_700).coerceAtLeast(0.0)
        var now = 0L
        while (s < target) {
            now += 1000
            val g = a.update(JunctionInput(r, progress(r, s, accuracy), kmh, now, false, false, false) { t })
            if (g != null && g.maneuverIndex == index && g.phase.visible) return g
            s += kmh / 3.6
        }
        error("aucune vue pour la manœuvre $index")
    }

    private fun assertRenderable(scene: JunctionScene) {
        val m = JunctionMeshBuilder.build(scene, JunctionPalette.NIGHT)
        assertTrue(m.opaqueVertices > 100)
        assertTrue(m.transparentVertices > 30)
        assertFalse(m.opaque.any { it.isNaN() || it.isInfinite() })
        assertFalse(m.transparent.any { it.isNaN() || it.isInfinite() })
        assertFalse(m.pathSamples.any { it.isNaN() })
        // Trajectoire = géométrie réelle : le point de manœuvre est l'origine de la scène.
        assertTrue(scene.path.line.pointAt(scene.path.maneuverS).length() < 2.0)
    }

    @Test fun `simple motorway exit 31b - real sign on a gantry, ramp, renderable`() {
        val r = route("motorway"); val t = topology("motorway", r)
        val g = guidanceFor(r, t, 9, 90.0)
        val scene = JunctionSceneBuilder.build(r, g, null)!!
        assertEquals("31b", scene.signs.single().exitNumber)
        assertTrue(scene.signs.single().gantry)
        assertTrue(scene.signs.single().lines.isNotEmpty())
        // Périphérique à 4 voies (topologie) : séparations de voies dessinées sur la chaussée sous la trajectoire.
        assertTrue(4 in scene.roads.single { it.onRoute }.laneCounts)
        assertRenderable(scene)
    }

    @Test fun `exit 19 followed by the fork - both scenes built, fork chained`() {
        val r = route("motorway"); val t = topology("motorway", r)
        val exit = guidanceFor(r, t, 11, 110.0)
        assertEquals("19", JunctionSceneBuilder.build(r, exit, null)!!.signs.single().exitNumber)
        val a = JunctionAnticipation()
        var s = alongOf(r, 11) - 900; var now = 0L
        var fork: JunctionGuidance? = null
        while (s < alongOf(r, 12) && fork == null) {
            now += 1000
            fork = a.update(JunctionInput(r, progress(r, s), 110.0, now, false, false, false) { t })?.takeIf { it.maneuverIndex == 12 }
            s += 30.0
        }
        assertNotNull(fork)
        assertTrue(fork!!.chainedNext)
        assertRenderable(JunctionSceneBuilder.build(r, fork, null)!!)
    }

    @Test fun `multi level interchange on the A620 - elevated decks and pillars`() {
        val r = route("motorway"); val t = topology("motorway", r)
        val g = guidanceFor(r, t, 10, 100.0)
        assertEquals(JunctionKind.INTERCHANGE, g.kind)
        val scene = JunctionSceneBuilder.build(r, g, null)!!
        assertTrue(scene.roads.single { it.onRoute }.elev.max() > 5.0)
        assertTrue(scene.pillars.isNotEmpty())
        assertRenderable(scene)
    }

    @Test fun `roundabout with 3 exits (Muret) - ring, island and numbered exits 1 to 3`() {
        val r = route("muret"); val t = topology("muret", r)
        val enter = r.maneuvers.indices.first { r.maneuvers[it].type == ManeuverType.ROUNDABOUT_ENTER && r.maneuvers[it].roundaboutExit == 3 }
        val scene = JunctionSceneBuilder.build(r, guidanceFor(r, t, enter, 45.0), null)!!
        val rb = scene.roundabout!!
        assertTrue(rb.radius in 4.0..20.0)
        assertEquals(listOf("1", "2", "3"), scene.labels.map { it.text })
        assertEquals("3", scene.labels.single { it.target }.text)
        assertRenderable(scene)
    }

    @Test fun `complex roundabouts with 6 exits (Nation, Etoile) - exit numbers from topology`() {
        for (name in listOf("nation", "etoile")) {
            val r = route(name); val t = topology(name, r)
            val enter = r.maneuvers.indices.first { r.maneuvers[it].type == ManeuverType.ROUNDABOUT_ENTER }
            val g = guidanceFor(r, t, enter, 30.0)
            assertEquals(name, 6, g.roundabout!!.targetExit)
            val scene = JunctionSceneBuilder.build(r, g, null)!!
            assertEquals(name, (1..6).map { it.toString() }, scene.labels.map { it.text })
            assertTrue(name, scene.roundabout!!.radius > 40.0)
            assertRenderable(scene)
        }
    }

    @Test fun `imprecise GPS - degraded view still built`() {
        val r = route("motorway"); val t = topology("motorway", r)
        val g = guidanceFor(r, t, 11, 110.0, accuracy = 45f)
        assertTrue(g.degraded)
        assertRenderable(JunctionSceneBuilder.build(r, g, null)!!)
    }

    @Test fun `offline without topology nor tiles - scene from the route alone, only the Valhalla target numbered`() {
        val r = route("muret")
        val enter = r.maneuvers.indices.first { r.maneuvers[it].type == ManeuverType.ROUNDABOUT_ENTER && r.maneuvers[it].roundaboutExit == 3 }
        val g = guidanceFor(r, null, enter, 45.0)
        assertFalse(g.roundabout!!.consistent)
        val scene = JunctionSceneBuilder.build(r, g, null, JunctionDetail.REDUCED)!!
        assertTrue(scene.labels.size <= 1)
        assertTrue(scene.labels.all { it.text == "3" })
        assertTrue(scene.roads.flatMap { it.laneCounts }.all { it == null })
        assertRenderable(scene)
    }

    @Test fun `reroute - a new route gives a new scene key`() {
        val r = route("motorway"); val t = topology("motorway", r)
        val a = JunctionSceneBuilder.build(r, guidanceFor(r, t, 11, 110.0), null)!!
        val r2 = r.copy(id = "rerouted")
        val b = JunctionSceneBuilder.build(r2, guidanceFor(r2, t.copy(routeId = "rerouted"), 11, 110.0), null)!!
        assertTrue(a.key != b.key)
    }

    @Test fun `ui title only states real data`() {
        val r = route("motorway"); val t = topology("motorway", r)
        val g = guidanceFor(r, t, 9, 90.0)
        assertEquals("SORTIE 31b", junctionUi(g, r.maneuvers[9])!!.title)
        val rbRoute = route("nation")
        val enter = rbRoute.maneuvers.indices.first { rbRoute.maneuvers[it].type == ManeuverType.ROUNDABOUT_ENTER }
        assertEquals("6e SORTIE", junctionUi(guidanceFor(rbRoute, topology("nation", rbRoute), enter, 30.0), rbRoute.maneuvers[enter])!!.title)
        assertEquals(null, junctionUi(g.copy(phase = JunctionPhase.PREPARE), r.maneuvers[9]))
    }

    @Test fun `map camera goes wide only before the maneuver of an interchange`() {
        val r = route("motorway"); val t = topology("motorway", r)
        val g = guidanceFor(r, t, 10, 100.0)
        assertTrue(junctionWide(g))
        assertFalse(junctionWide(g.copy(phase = JunctionPhase.MANEUVER)))
        assertFalse(junctionWide(g.copy(phase = JunctionPhase.RECOVER)))
        assertFalse(junctionWide(null))
    }
}
