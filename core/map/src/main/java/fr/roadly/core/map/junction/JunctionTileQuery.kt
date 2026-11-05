package fr.roadly.core.map.junction

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.junction.JunctionMapContext
import fr.roadly.core.model.junction.MapBuilding
import fr.roadly.core.model.junction.MapRoad
import fr.roadly.core.model.junction.RoadClass
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.VectorSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.LineString
import org.maplibre.geojson.MultiLineString
import org.maplibre.geojson.MultiPolygon
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos

/**
 * Lecture des routes et bâtiments autour d'un carrefour dans les tuiles vectorielles déjà chargées par MapLibre (OpenMapTiles,
 * source `openmaptiles` des styles Roadly). Aucune requête réseau : avec une carte téléchargée, la vue fonctionne hors ligne.
 * Doit être appelée sur le thread principal (API MapLibre).
 */
object JunctionTileQuery {
    const val SOURCE_ID = "openmaptiles"

    fun query(style: Style, center: GeoPoint, radiusM: Double): JunctionMapContext {
        val source = style.getSourceAs<VectorSource>(SOURCE_ID) ?: return JunctionMapContext.empty(center)
        val box = Box.around(center, radiusM)
        val roads = runCatching { source.querySourceFeatures(arrayOf("transportation"), null) }.getOrDefault(emptyList())
            .flatMap { roadsOf(it, box) }
        val buildings = runCatching { source.querySourceFeatures(arrayOf("building"), null) }.getOrDefault(emptyList())
            .flatMap { buildingsOf(it, box) }
        return JunctionMapContext(center, radiusM, dedupRoads(roads), dedupBuildings(buildings))
    }

    internal data class Box(val south: Double, val west: Double, val north: Double, val east: Double) {
        fun contains(p: Point) = p.latitude() in south..north && p.longitude() in west..east
        companion object {
            fun around(c: GeoPoint, r: Double): Box {
                val dLat = r / LocalFrame.M_PER_DEG
                val dLon = r / (LocalFrame.M_PER_DEG * cos(Math.toRadians(c.lat)))
                return Box(c.lat - dLat, c.lon - dLon, c.lat + dLat, c.lon + dLon)
            }
        }
    }

    private fun roadsOf(f: Feature, box: Box): List<MapRoad> {
        val cls = f.str("class")?.removeSuffix("_construction") ?: return emptyList()
        val roadClass = RoadClass.fromOpenMapTiles(cls) ?: return emptyList()
        val lines = when (val g = f.geometry()) {
            is LineString -> listOf(g.coordinates())
            is MultiLineString -> g.coordinates()
            else -> return emptyList()
        }
        val brunnel = f.str("brunnel")
        val ramp = f.num("ramp")?.toInt() == 1
        val oneway = f.num("oneway")?.toInt()?.let { it != 0 } ?: false
        val layer = f.num("layer")?.toInt() ?: 0
        return lines.filter { l -> l.size >= 2 && l.any { box.contains(it) } }.map { l ->
            MapRoad(l.map { GeoPoint(it.latitude(), it.longitude()) }, roadClass, ramp, oneway, brunnel == "bridge", brunnel == "tunnel", layer)
        }
    }

    private fun buildingsOf(f: Feature, box: Box): List<MapBuilding> {
        val h = f.num("render_height")?.toDouble() ?: DEFAULT_BUILDING_M
        val min = f.num("render_min_height")?.toDouble() ?: 0.0
        val rings = when (val g = f.geometry()) {
            is Polygon -> listOfNotNull(g.coordinates().firstOrNull())
            is MultiPolygon -> g.coordinates().mapNotNull { it.firstOrNull() }
            else -> return emptyList()
        }
        return rings.filter { r -> r.size >= 4 && r.any { box.contains(it) } }.map { r ->
            MapBuilding(r.dropLast(1).map { GeoPoint(it.latitude(), it.longitude()) }, h, min)
        }
    }

    /** Les tuiles voisines renvoient la même géométrie découpée avec une marge : on écarte les doublons exacts. */
    private fun dedupRoads(roads: List<MapRoad>): List<MapRoad> = roads.distinctBy { r -> Triple(r.points.first().key(), r.points.last().key(), r.points.size) }
    private fun dedupBuildings(b: List<MapBuilding>): List<MapBuilding> = b.distinctBy { it.ring.first().key() to it.ring.size }

    private fun GeoPoint.key() = (lat * 1e6).toLong() to (lon * 1e6).toLong()
    private fun Feature.str(k: String): String? = if (hasNonNullValueForProperty(k)) getStringProperty(k) else null
    private fun Feature.num(k: String): Number? = if (hasNonNullValueForProperty(k)) runCatching { getNumberProperty(k) }.getOrNull() else null

    private const val DEFAULT_BUILDING_M = 6.0
}
