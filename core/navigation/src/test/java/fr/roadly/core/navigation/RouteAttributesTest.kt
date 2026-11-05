package fr.roadly.core.navigation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Profil de tracé `trace_attributes` (1.0) : décodage, construction des tronçons, lecture. */
class RouteAttributesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun edge(m: Double, kmh: Int?, node: String? = null, name: String? = null, cc: String? = "FR") =
        RouteEdge(m, kmh, node, listOfNotNull(name), cc)

    @Test fun `parse reads lengths, limits, end node types and country from admins`() {
        // Extrait d'une réponse réelle FOSSGIS (A79, 2026-11-05), réduit à trois arêtes.
        val body = """
            {"units":"kilometers","admins":[{"country_code":"FR","state_code":"ARA"}],
             "edges":[
               {"length":0.023},
               {"length":1.768,"speed_limit":130,"names":["A 79","E 62"],"end_node":{"admin_index":0,"type":"toll_gantry"}},
               {"length":0.5,"speed_limit":"unlimited","end_node":{"admin_index":0,"type":"street_intersection"}}
             ]}
        """.trimIndent()
        val edges = ValhallaRouteAttributes.parse(json, body)!!
        assertEquals(3, edges.size)
        assertEquals(23.0, edges[0].lengthM, 1e-6)
        assertNull(edges[0].speedLimitKmh)
        assertEquals(130, edges[1].speedLimitKmh)
        assertEquals("toll_gantry", edges[1].endNodeType)
        assertEquals("A 79", edges[1].names.first())
        assertEquals("FR", edges[1].countryCode)
        assertNull("une valeur textuelle n'est pas une limitation", edges[2].speedLimitKmh)
    }

    @Test fun `parse returns null on garbage`() {
        assertNull(ValhallaRouteAttributes.parse(json, "<html>"))
        assertNull(ValhallaRouteAttributes.parse(json, """{"error":"x"}"""))
    }

    @Test fun `request walks the exact route edges with the needed attributes`() {
        val r = ValhallaRouteAttributes.request("abc")
        assertEquals("edge_walk", r["shape_match"]!!.jsonPrimitive.content)
        val attrs = r["filters"]!!.let { (it as kotlinx.serialization.json.JsonObject)["attributes"]!!.jsonArray.map { a -> a.jsonPrimitive.content } }
        assertTrue(attrs.containsAll(listOf("edge.speed_limit", "node.type", "edge.length")))
    }

    @Test fun `consecutive equal limits merge and short unknown edges are absorbed`() {
        val a = RouteAttributesBuilder.build("r", 1_000.0, listOf(
            edge(300.0, 50), edge(200.0, 50), edge(20.0, null), edge(280.0, 30), edge(200.0, null),
        ))!!
        assertEquals(listOf(SpeedLimitSpan(0.0, 520.0, 50), SpeedLimitSpan(520.0, 800.0, 30)), a.speedLimits)
        assertEquals(50, a.limitAt(510.0))
        assertEquals(30, a.limitAt(799.0))
        assertNull("une longue arête inconnue reste inconnue", a.limitAt(900.0))
        assertEquals(0.8, a.coverage, 1e-9)
    }

    @Test fun `edge lengths are rescaled onto the geometry length`() {
        val a = RouteAttributesBuilder.build("r", 1_020.0, listOf(edge(500.0, 50), edge(500.0, 90)))!!
        assertEquals(510.0, a.speedLimits[0].endAlongM, 1e-9)
        assertEquals(1_020.0, a.speedLimits[1].endAlongM, 1e-9)
    }

    @Test fun `mismatching lengths give no profile`() {
        assertNull(RouteAttributesBuilder.build("r", 2_000.0, listOf(edge(500.0, 50), edge(500.0, 90))))
        assertNull(RouteAttributesBuilder.build("r", 1_000.0, emptyList()))
    }

    @Test fun `toll gantries sit at the end of their edge`() {
        val a = RouteAttributesBuilder.build("r", 3_000.0, listOf(
            edge(1_000.0, 130), edge(1_000.0, 130, node = "toll_gantry", name = "A 79"), edge(1_000.0, 130, node = "toll_booth"),
        ))!!
        assertEquals(listOf(TollGantry(2_000.0, "A 79", "FR")), a.tollGantries)
    }

    @Test fun `next change is the first different known limit within the horizon`() {
        val a = RouteAttributesBuilder.build("r", 1_000.0, listOf(edge(400.0, 90), edge(200.0, null), edge(400.0, 50)))!!
        assertEquals(SpeedLimitChange(50, 300.0), a.nextChange(300.0, 500.0))
        assertNull("au-delà de l'horizon", a.nextChange(0.0, 500.0))
        assertNull("rien après la dernière limitation", a.nextChange(700.0, 500.0))
        val same = RouteAttributesBuilder.build("r", 1_000.0, listOf(edge(400.0, 50), edge(200.0, null), edge(400.0, 50)))!!
        assertNull("même limitation après une portion inconnue : pas un changement", same.nextChange(300.0, 1_000.0))
        assertNotNull(same.nextChange(450.0, 1_000.0)) // depuis l'inconnu, la limitation à venir est annoncée
    }
}
