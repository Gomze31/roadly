package fr.roadly.core.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.model.Route
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

enum class CameraMode { FREE, CENTERED, FOLLOW }

/**
 * Pont entre l'état Compose et la MapLibreMap. Toutes les commandes reçues avant que le style soit prêt
 * sont mémorisées et rejouées (`replayState`), y compris après un changement de style clair/sombre.
 */
class MapController(internal val context: Context) {
    private var map: MapLibreMap? = null
    private var styleReady = false
    private var currentStyleUrl: String? = null

    private var lastFix: LocationFix? = null
    private var lastRoutes: List<Route> = emptyList()
    private var lastActiveRouteId: String? = null
    private var lastMarkers: List<Place> = emptyList()
    private var lastTraversed: String = TraversedFeatureBuilder.EMPTY
    private var routeColorActive = "#B794FF"
    private var routeColorInactive = "#5A5470"
    private var routeColorCasing = "#0E0B14"

    /** Commande caméra reçue avant que la carte et son style soient prêts ; la dernière l'emporte, rejouée en fin de `replayState`. */
    private var pendingCamera: ((MapLibreMap) -> Unit)? = null

    private var lastSnapshot: CameraSnapshot? = null

    var onRouteClick: ((String) -> Unit)? = null

    /** Détache la carte (la vue est détruite) ; l'état mémorisé reste pour un futur `attach`. */
    internal fun detach() {
        map = null
        styleReady = false
        onRouteClick = null
    }

    internal fun attach(map: MapLibreMap) {
        this.map = map
        map.uiSettings.isCompassEnabled = false
        map.uiSettings.isAttributionEnabled = true
        map.uiSettings.isLogoEnabled = false
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isTiltGesturesEnabled = false
        map.addOnMapClickListener { latLng ->
            val m = this.map ?: return@addOnMapClickListener false
            val pt = m.projection.toScreenLocation(latLng)
            val hit = m.queryRenderedFeatures(pt, MapIds.LYR_ROUTE_INACTIVE, MapIds.LYR_ROUTE_ACTIVE).firstOrNull()
            val id = hit?.getStringProperty("id")
            if (id != null) { onRouteClick?.invoke(id); true } else false
        }
        currentStyleUrl?.let { loadStyle(it) }
    }

    /** Couleurs CSS `#RRGGBB` : tracé actif (halo + trait), tracés inactifs et parcourus, liseré sombre/clair sous le tracé actif. */
    fun setRouteColors(active: String, inactive: String, casing: String = routeColorCasing) {
        routeColorActive = active; routeColorInactive = inactive; routeColorCasing = casing
        if (styleReady) replayState()
    }

    fun setStyle(url: String) {
        if (url == currentStyleUrl && styleReady) return
        currentStyleUrl = url
        if (map != null) loadStyle(url)
    }

    private fun loadStyle(url: String) {
        styleReady = false
        map?.setStyle(Style.Builder().fromUri(url)) { style -> installLayers(style); styleReady = true; replayState() }
    }

    private fun installLayers(style: Style) {
        style.addImage(MapIds.IMG_ARROW, drawableToBitmap(R.drawable.ic_user_arrow))
        style.addImage(MapIds.IMG_PIN, drawableToBitmap(R.drawable.ic_place_pin))
        style.addSource(GeoJsonSource(MapIds.SRC_ROUTES))
        style.addSource(GeoJsonSource(MapIds.SRC_TRAVERSED))
        style.addSource(GeoJsonSource(MapIds.SRC_MARKERS))
        style.addSource(GeoJsonSource(MapIds.SRC_USER))

        // Halo lumineux large et translucide sous le tracé actif (lueur), puis liseré, puis trait.
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_GLOW, MapIds.SRC_ROUTES).withProperties(
            lineColor(routeColorActive), lineWidth(18f), lineOpacity(0.22f), lineBlur(6f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("active"), literal(true))))
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_CASING, MapIds.SRC_ROUTES).withProperties(
            lineColor(routeColorCasing), lineWidth(10f), lineOpacity(0.7f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("active"), literal(true))))
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_INACTIVE, MapIds.SRC_ROUTES).withProperties(
            lineColor(routeColorInactive), lineWidth(5f), lineOpacity(0.85f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("active"), literal(false))))
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_ACTIVE, MapIds.SRC_ROUTES).withProperties(
            lineColor(routeColorActive), lineWidth(6f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("active"), literal(true))))
        // Tronçon déjà parcouru, dessiné par-dessus le tracé actif (spec §5 : « tracé parcouru atténué »).
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_TRAVERSED, MapIds.SRC_TRAVERSED).withProperties(
            lineColor(routeColorInactive), lineWidth(6.5f), lineOpacity(0.9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(SymbolLayer(MapIds.LYR_MARKERS, MapIds.SRC_MARKERS).withProperties(
            iconImage(MapIds.IMG_PIN), iconAnchor(Property.ICON_ANCHOR_BOTTOM), iconAllowOverlap(true), iconSize(1f),
        ))
        style.addLayer(CircleLayer(MapIds.LYR_ACCURACY, MapIds.SRC_USER).withProperties(
            circleColor(routeColorActive), circleOpacity(0.15f), circleRadius(18f),
        ))
        style.addLayer(SymbolLayer(MapIds.LYR_USER, MapIds.SRC_USER).withProperties(
            iconImage(MapIds.IMG_ARROW), iconRotate(get("bearing")), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP), iconAllowOverlap(true), iconSize(1f),
        ))
    }

    private fun replayState() {
        val style = map?.style ?: return
        lastFix?.let { style.getSourceAs<GeoJsonSource>(MapIds.SRC_USER)?.setGeoJson(PointFeatureBuilder.user(it)) }
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_ROUTES)?.setGeoJson(RouteFeatureBuilder.build(lastRoutes, lastActiveRouteId))
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(lastTraversed)
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_MARKERS)?.setGeoJson(PointFeatureBuilder.markers(lastMarkers))
        (style.getLayer(MapIds.LYR_ROUTE_ACTIVE) as? LineLayer)?.setProperties(lineColor(routeColorActive))
        (style.getLayer(MapIds.LYR_ROUTE_GLOW) as? LineLayer)?.setProperties(lineColor(routeColorActive))
        (style.getLayer(MapIds.LYR_ROUTE_CASING) as? LineLayer)?.setProperties(lineColor(routeColorCasing))
        (style.getLayer(MapIds.LYR_ROUTE_INACTIVE) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
        (style.getLayer(MapIds.LYR_ROUTE_TRAVERSED) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
        map?.let { m -> pendingCamera?.let { it(m); pendingCamera = null } }
    }

    fun setUserLocation(fix: LocationFix?) {
        lastFix = fix
        if (!styleReady) return
        val src = map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_USER) ?: return
        if (fix == null) src.setGeoJson("""{"type":"FeatureCollection","features":[]}""") else src.setGeoJson(PointFeatureBuilder.user(fix))
    }

    fun setRoutes(routes: List<Route>, activeId: String?) {
        lastRoutes = routes; lastActiveRouteId = activeId
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_ROUTES)?.setGeoJson(RouteFeatureBuilder.build(routes, activeId))
    }

    fun setMarkers(places: List<Place>) {
        lastMarkers = places
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_MARKERS)?.setGeoJson(PointFeatureBuilder.markers(places))
    }

    /** Atténue la portion parcourue de l'itinéraire actif (écran de guidage). */
    fun setTraversed(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?) {
        lastTraversed = TraversedFeatureBuilder.build(geometry, segmentIndex, snapped)
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(lastTraversed)
    }

    fun clearTraversed() = setTraversed(emptyList(), -1, null)

    fun moveTo(point: GeoPoint, zoom: Double, bearing: Double = 0.0, animate: Boolean = true) {
        val action: (MapLibreMap) -> Unit = { m ->
            val pos = CameraPosition.Builder().target(LatLng(point.lat, point.lon)).zoom(zoom).bearing(bearing).tilt(0.0).build()
            val update = CameraUpdateFactory.newCameraPosition(pos)
            if (animate) m.animateCamera(update, 600) else m.moveCamera(update)
        }
        val m = map
        if (m == null || !styleReady) { pendingCamera = action; return }
        action(m)
    }

    /** Boussole : remet le nord en haut sans changer le centre ni le zoom. */
    fun resetBearing() {
        val m = map ?: return
        val pos = m.cameraPosition
        m.animateCamera(CameraUpdateFactory.newCameraPosition(CameraPosition.Builder(pos).bearing(0.0).build()), 400)
    }

    /** Caméra courante, ou le dernier instantané pris au détachement ; null si aucune carte n'a jamais existé. */
    fun cameraPosition(): CameraSnapshot? = liveCamera() ?: lastSnapshot

    fun restore(snapshot: CameraSnapshot) = moveTo(GeoPoint(snapshot.lat, snapshot.lon), snapshot.zoom, snapshot.bearing, animate = false)

    private fun liveCamera(): CameraSnapshot? = map?.cameraPosition?.let { CameraSnapshot(it.target?.latitude ?: return null, it.target?.longitude ?: return null, it.zoom, it.bearing) }

    /** Mémorise la caméra juste avant la destruction de la vue (l'ordre de sortie de composition n'est pas garanti). */
    internal fun captureCamera() { liveCamera()?.let { lastSnapshot = it } }

    fun fitBounds(points: List<GeoPoint>, paddingPx: Int) = fitBounds(points, paddingPx, paddingPx, paddingPx, paddingPx)

    /** Marges distinctes (px) pour laisser le tracé visible sous les panneaux du haut et du bas. */
    fun fitBounds(points: List<GeoPoint>, left: Int, top: Int, right: Int, bottom: Int) {
        val bbox = BoundingBox.of(points) ?: return
        if (bbox.north == bbox.south && bbox.east == bbox.west) { moveTo(points.first(), 15.0); return }
        val action: (MapLibreMap) -> Unit = { m ->
            val bounds = LatLngBounds.Builder().include(LatLng(bbox.south, bbox.west)).include(LatLng(bbox.north, bbox.east)).build()
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, left, top, right, bottom), 600)
        }
        val m = map
        if (m == null || !styleReady) { pendingCamera = action; return }
        action(m)
    }

    fun applyCameraMode(mode: CameraMode, fix: LocationFix?) {
        if (fix == null) return
        when (mode) {
            CameraMode.FREE -> Unit
            CameraMode.CENTERED -> moveTo(fix.point, 15.5, bearing = 0.0)
            CameraMode.FOLLOW -> moveTo(fix.point, 17.0, bearing = (fix.bearingDeg ?: 0f).toDouble())
        }
    }

    private fun drawableToBitmap(resId: Int): Bitmap {
        val d = ContextCompat.getDrawable(context, resId)!!
        val bmp = Bitmap.createBitmap(d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp); d.setBounds(0, 0, c.width, c.height); d.draw(c)
        return bmp
    }
}
