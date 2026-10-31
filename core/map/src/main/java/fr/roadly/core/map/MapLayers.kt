package fr.roadly.core.map

import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.Route
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Identifiants des sources, calques et images ajoutés au style par Roadly. */
internal object MapIds {
    const val SRC_USER = "roadly-user"
    const val SRC_ROUTES = "roadly-routes"
    const val SRC_MARKERS = "roadly-markers"
    const val SRC_REPORTS = "roadly-reports"
    const val SRC_TRAVERSED = "roadly-traversed"
    const val LYR_ACCURACY = "roadly-accuracy"
    const val LYR_USER = "roadly-user-arrow"
    const val LYR_ROUTE_GLOW = "roadly-route-glow"
    const val LYR_ROUTE_CASING = "roadly-route-casing"
    const val LYR_ROUTE_INACTIVE = "roadly-route-inactive"
    const val LYR_ROUTE_ACTIVE = "roadly-route-active"
    const val LYR_ROUTE_TRAVERSED = "roadly-route-traversed"
    const val LYR_MARKERS = "roadly-markers"
    const val LYR_REPORTS = "roadly-reports"
    const val IMG_ARROW = "roadly-img-arrow"
    const val IMG_PIN = "roadly-img-pin"
    const val IMG_REPORT = "roadly-img-report"
    const val SRC_ARROW = "roadly-arrow"
    const val LYR_ARROW_CASING = "roadly-arrow-casing"
    const val LYR_ARROW = "roadly-arrow-line"
    const val LYR_ARROW_HEAD = "roadly-arrow-head"
    const val LYR_BUILDINGS_3D = "roadly-buildings-3d"
    const val IMG_ARROW_HEAD = "roadly-img-arrow-head"
    /** Source vecteur OpenMapTiles des styles OpenFreeMap ; absente = pas de bâtiments 3D. */
    const val STYLE_SRC_OPENMAPTILES = "openmaptiles"
    const val STYLE_LYR_BUILDING = "building"
    /** Calques de points d'intérêt et de noms de rues des tuiles OpenMapTiles (niveau de détail, C2). */
    const val STYLE_LYR_POI = "poi"
    const val STYLE_LYR_TRANSPORTATION_NAME = "transportation_name"
}

/** GeoJSON FeatureCollection des tracés ; l'itinéraire actif est placé en dernier (dessiné au-dessus). */
object RouteFeatureBuilder {
    fun build(routes: List<Route>, activeId: String?): String {
        val ordered = routes.filter { it.id != activeId } + routes.filter { it.id == activeId }
        return buildJsonObject {
            put("type", "FeatureCollection")
            putJsonArray("features") {
                ordered.forEach { r ->
                    add(buildJsonObject {
                        put("type", "Feature")
                        putJsonObject("properties") { put("id", r.id); put("active", r.id == activeId) }
                        putJsonObject("geometry") {
                            put("type", "LineString")
                            put("coordinates", JsonArray(r.geometry.map { p -> JsonArray(listOf(JsonPrimitive(p.lon), JsonPrimitive(p.lat))) }))
                        }
                    })
                }
            }
        }.toString()
    }
}

/** Builders GeoJSON des points : position de l'utilisateur et marqueurs de lieux. */
internal object PointFeatureBuilder {
    fun user(fix: LocationFix): String = user(MarkerInterpolation.pose(fix), fix.accuracyM)

    /** Marqueur à une pose interpolée (C1). */
    fun user(pose: MarkerPose, accuracyM: Float): String = buildJsonObject {
        put("type", "Feature")
        putJsonObject("properties") { put("bearing", pose.bearingDeg); put("accuracy", accuracyM.toDouble()) }
        putJsonObject("geometry") { put("type", "Point"); put("coordinates", JsonArray(listOf(JsonPrimitive(pose.lon), JsonPrimitive(pose.lat)))) }
    }.toString()

    fun reports(reports: List<CommunityReport>): String = buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            reports.forEach { r ->
                add(buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("id", r.id); put("kind", r.kind.name) }
                    putJsonObject("geometry") { put("type", "Point"); put("coordinates", JsonArray(listOf(JsonPrimitive(r.point.lon), JsonPrimitive(r.point.lat)))) }
                })
            }
        }
    }.toString()

    fun markers(places: List<Place>): String = buildJsonObject {
        put("type", "FeatureCollection")
        putJsonArray("features") {
            places.forEach { p ->
                add(buildJsonObject {
                    put("type", "Feature")
                    putJsonObject("properties") { put("id", p.id); put("name", p.name) }
                    putJsonObject("geometry") { put("type", "Point"); put("coordinates", JsonArray(listOf(JsonPrimitive(p.point.lon), JsonPrimitive(p.point.lat)))) }
                })
            }
        }
    }.toString()
}

/** Portion déjà parcourue de l'itinéraire actif (écran de guidage) : p_0 … p_segmentIndex puis la position projetée. */
object TraversedFeatureBuilder {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""

    fun build(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?): String {
        if (geometry.isEmpty() || segmentIndex < 0) return EMPTY
        val pts = geometry.subList(0, (segmentIndex + 1).coerceAtMost(geometry.size)) + listOfNotNull(snapped)
        if (pts.size < 2) return EMPTY
        return buildJsonObject {
            put("type", "Feature")
            putJsonObject("properties") {}
            putJsonObject("geometry") {
                put("type", "LineString")
                put("coordinates", JsonArray(pts.map { p -> JsonArray(listOf(JsonPrimitive(p.lon), JsonPrimitive(p.lat))) }))
            }
        }.toString()
    }
}
