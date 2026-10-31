package fr.roadly.core.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** C2 : styles Roadly embarqués, générés par `tools/build-roadly-styles.ps1`, lus depuis le dossier du module. */
class RoadlyStyleAssetTest {
    private val names = listOf("roadly-dark.json", "roadly-light.json")
    private fun file(name: String) = File("src/main/assets/styles/$name")
    private fun style(name: String): JsonObject = Json.parseToJsonElement(file(name).readText()).jsonObject
    private fun JsonObject.layers(): List<JsonObject> = getValue("layers").jsonArray.map { it.jsonObject }
    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content
    private fun List<JsonObject>.byId(id: String): JsonObject = first { it.str("id") == id }

    private fun JsonElement.isZoomCall() = this is JsonArray && size == 1 && (this[0] as? JsonPrimitive)?.content == "zoom"
    private fun JsonElement.op(): String? = ((this as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** MapLibre n'accepte ["zoom"] que comme entrée d'un interpolate ou d'un step placé au premier niveau. */
    private fun zoomOnlyAtTop(value: JsonElement): Boolean {
        val input = when (value.op()) { "interpolate" -> 2; "step" -> 1; else -> -1 }
        if (input >= 0 && (value as JsonArray)[input].isZoomCall()) return value.withIndex().all { (i, e) -> i == input || noZoom(e) }
        return noZoom(value)
    }

    private fun noZoom(e: JsonElement): Boolean = when (e) {
        is JsonArray -> !e.isZoomCall() && e.all { noZoom(it) }
        is JsonObject -> e.values.all { noZoom(it) }
        else -> true
    }

    @Test fun `both styles keep the OpenFreeMap sources, glyphs and sprite`() = names.forEach { n ->
        val s = style(n)
        assertEquals(8, s.getValue("version").jsonPrimitive.content.toInt())
        val omt = s.getValue("sources").jsonObject.getValue("openmaptiles").jsonObject
        assertEquals("vector", omt.str("type"))
        assertTrue(n, omt.str("url")!!.startsWith("https://tiles.openfreemap.org/"))
        assertTrue(n, s.str("glyphs")!!.startsWith("https://tiles.openfreemap.org/fonts/"))
        assertTrue(n, s.str("sprite")!!.startsWith("https://tiles.openfreemap.org/sprites/"))
    }

    @Test fun `metadata carries the roadly style version`() = names.forEach { n ->
        assertEquals(n, "1", style(n).getValue("metadata").jsonObject.getValue("roadly").jsonObject.str("version"))
    }

    @Test fun `buildings come from the tiles`() = names.forEach { n ->
        assertTrue(n, style(n).layers().any { it.str("source-layer") == "building" && it.str("source") == "openmaptiles" })
    }

    @Test fun `road layers are stacked from tunnels to motorways and replace the original ones`() = names.forEach { n ->
        val ids = style(n).layers().map { it.str("id") }
        val stack = listOf(
            "roadly_road_tunnel", "roadly_road_minor", "roadly_road_bridge_casing", "roadly_road_major_casing",
            "roadly_road_major", "roadly_road_motorway_casing", "roadly_road_motorway",
        )
        val idx = stack.map { ids.indexOf(it) }
        assertTrue("$n : $idx", idx.none { it < 0 } && idx == idx.sorted())
        assertTrue(n, ids.none { it == "highway_minor" || it == "highway_motorway_inner" || it == "tunnel_motorway_inner" })
    }

    @Test fun `tunnels are dashed and translucent`() = names.forEach { n ->
        val t = style(n).layers().byId("roadly_road_tunnel")
        assertTrue(n, t.getValue("filter").toString().contains("\"tunnel\""))
        val paint = t.getValue("paint").jsonObject
        assertEquals(n, "[2,2]", paint.getValue("line-dasharray").toString())
        assertEquals(0.6, paint.getValue("line-opacity").jsonPrimitive.double, 1e-9)
    }

    @Test fun `ramps are narrower and motorways 30 percent wider than the original`() = names.forEach { n ->
        val width = style(n).layers().byId("roadly_road_motorway").getValue("paint").jsonObject.getValue("line-width").toString()
        assertTrue(n, width.contains("\"ramp\""))
        assertTrue(n, width.contains("39"))   // 30 × 1,3 au zoom 20
    }

    @Test fun `zoom is only used as the input of a top-level interpolate or step`() = names.forEach { n ->
        style(n).layers().forEach { l ->
            listOf("paint", "layout").forEach { group ->
                (l[group] as? JsonObject)?.forEach { (key, value) -> assertTrue("$n ${l.str("id")} $group.$key", zoomOnlyAtTop(value)) }
            }
        }
    }

    @Test fun `arrays survived the PowerShell conversion`() = names.forEach { n ->
        assertFalse(n, file(n).readText().contains("\"Count\":"))
        style(n).layers().forEach { l ->
            (l["layout"] as? JsonObject)?.get("text-font")?.let { assertTrue("$n ${l.str("id")}", it is JsonArray) }
            l["filter"]?.let { assertTrue("$n ${l.str("id")}", it is JsonArray) }
        }
    }

    @Test fun `layer ids are unique and every source exists`() = names.forEach { n ->
        val s = style(n)
        val ids = s.layers().map { it.str("id") }
        assertEquals(n, ids.size, ids.toSet().size)
        val sources = s.getValue("sources").jsonObject.keys
        s.layers().mapNotNull { it.str("source") }.forEach { assertTrue("$n $it", it in sources) }
    }

    @Test fun `street names are sparser and points of interest come from the tiles`() = names.forEach { n ->
        val layers = style(n).layers()
        val streetNames = layers.filter { it.str("source-layer") == "transportation_name" && it.str("id")!!.contains("name") }
        assertTrue(n, streetNames.isNotEmpty())
        streetNames.forEach { assertEquals("$n ${it.str("id")}", 400, it.getValue("layout").jsonObject.getValue("symbol-spacing").jsonPrimitive.content.toInt()) }
        val major = layers.byId("roadly_poi_major")
        assertEquals("poi", major.str("source-layer"))
        assertEquals(14.0, major.getValue("minzoom").jsonPrimitive.double, 1e-9)
        assertEquals(16.0, layers.byId("roadly_poi_other").getValue("minzoom").jsonPrimitive.double, 1e-9)
    }
}
