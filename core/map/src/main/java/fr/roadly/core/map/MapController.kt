package fr.roadly.core.map

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.CommunityReport
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.MapDetail
import fr.roadly.core.model.Place
import fr.roadly.core.model.Route
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.eq
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionBase
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionHeight
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionOpacity
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
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
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Locale

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
    private var lastReports: List<CommunityReport> = emptyList()
    private var lastTraversed: String = TraversedFeatureBuilder.EMPTY
    private var lastTraversedTail: String = TraversedFeatureBuilder.EMPTY
    private val traversed = TraversedLine()
    private var routeColorActive = "#B794FF"
    private var routeColorInactive = "#5A5470"
    private var routeColorCasing = "#0E0B14"
    private var arrowColorBody = "#FFFFFF"
    private var arrowColorCasing = "#8B5CF6"
    private var lastArrow: String = ManeuverArrowBuilder.EMPTY
    private var buildings3d = false
    private var buildingsColor = "#2A2236"

    /** Commande caméra reçue avant que la carte et son style soient prêts ; la dernière l'emporte, rejouée en fin de `replayState`. */
    private var pendingCamera: ((MapLibreMap) -> Unit)? = null

    private var lastSnapshot: CameraSnapshot? = null

    /** C1 : animation en cours du marqueur, et pose affichée (point de départ de la prochaine animation). */
    private var markerAnimator: ValueAnimator? = null
    private var shownPose: MarkerPose? = null
    /** C3 : écritures du marqueur animé calées sur le plafond de rendu de la carte (voir [setMaximumFps]). */
    private val markerGate = FrameGate(FrameGate.intervalFor(MapRendering.MAX_FPS))

    /** C2 : niveau de détail courant ; visibilité et filtres d'origine des calques touchés (style courant), pour revenir au niveau normal. */
    private var detail = MapDetail.NORMAL
    private val baseVisibility = mutableMapOf<String, String>()
    private val baseNameFilters = mutableMapOf<String, Expression?>()

    var onRouteClick: ((String) -> Unit)? = null

    /** Détache la carte (la vue est détruite) ; l'état mémorisé reste pour un futur `attach`. */
    internal fun detach() {
        markerAnimator?.cancel()
        markerAnimator = null
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
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            // C3 : mesure du rendu de la carte (SurfaceView, invisible pour `dumpsys gfxinfo`), builds débogables : adb logcat -s RoadlyFps
            val window = FpsWindow()
            map.setOnFpsChangedListener { fps ->
                window.add(fps, SystemClock.elapsedRealtime())?.let { Log.d(FPS_TAG, String.format(Locale.ROOT, "carte %.1f i/s (moyenne 5 s)", it)) }
            }
        }
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
        if (styleReady) map?.style?.let { applyRouteColors(it) }
    }

    /** C3 : plafond d'images par seconde du rendu (posé avec `MapView.setMaximumFps`) ; le marqueur animé n'écrit pas plus souvent. */
    fun setMaximumFps(fps: Int) {
        markerGate.minIntervalMs = FrameGate.intervalFor(fps)
    }

    fun setStyle(url: String) {
        if (url == currentStyleUrl && styleReady) return
        currentStyleUrl = url
        if (map != null) loadStyle(url)
    }

    private fun loadStyle(url: String) {
        styleReady = false
        baseVisibility.clear()
        baseNameFilters.clear()
        map?.setStyle(Style.Builder().fromUri(url)) { style -> installLayers(style); styleReady = true; replayState() }
    }

    private fun installLayers(style: Style) {
        style.addImage(MapIds.IMG_ARROW, drawableToBitmap(R.drawable.ic_user_arrow))
        style.addImage(MapIds.IMG_PIN, drawableToBitmap(R.drawable.ic_place_pin))
        style.addImage(MapIds.IMG_REPORT, drawableToBitmap(R.drawable.ic_report))
        style.addImage(MapIds.IMG_ARROW_HEAD, drawableToBitmap(R.drawable.ic_arrow_head))
        style.addSource(GeoJsonSource(MapIds.SRC_ARROW))
        style.addSource(GeoJsonSource(MapIds.SRC_REPORTS))
        style.addSource(GeoJsonSource(MapIds.SRC_ROUTES))
        style.addSource(GeoJsonSource(MapIds.SRC_TRAVERSED))
        style.addSource(GeoJsonSource(MapIds.SRC_TRAVERSED_TAIL))
        style.addSource(GeoJsonSource(MapIds.SRC_MARKERS))
        style.addSource(GeoJsonSource(MapIds.SRC_USER))
        // Bâtiments d'abord : leur ancrage ne voit que les calques du style, et le repli `addLayer` reste sous ceux de Roadly.
        installBuildings(style)

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
        style.addLayer(LineLayer(MapIds.LYR_ROUTE_TRAVERSED_TAIL, MapIds.SRC_TRAVERSED_TAIL).withProperties(
            lineColor(routeColorInactive), lineWidth(6.5f), lineOpacity(0.9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ))
        // Flèche de manœuvre (approche d'une intersection) : liseré, trait clair, tête orientée selon le dernier segment.
        style.addLayer(LineLayer(MapIds.LYR_ARROW_CASING, MapIds.SRC_ARROW).withProperties(
            lineColor(arrowColorCasing), lineWidth(14f), lineOpacity(0.9f), lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("kind"), literal("arrow"))))
        style.addLayer(LineLayer(MapIds.LYR_ARROW, MapIds.SRC_ARROW).withProperties(
            lineColor(arrowColorBody), lineWidth(8f), lineCap(Property.LINE_CAP_BUTT), lineJoin(Property.LINE_JOIN_ROUND),
        ).withFilter(eq(get("kind"), literal("arrow"))))
        style.addLayer(SymbolLayer(MapIds.LYR_ARROW_HEAD, MapIds.SRC_ARROW).withProperties(
            iconImage(MapIds.IMG_ARROW_HEAD), iconRotate(get("bearing")), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
            iconAnchor(Property.ICON_ANCHOR_CENTER), iconAllowOverlap(true), iconIgnorePlacement(true), iconSize(1f),
        ).withFilter(eq(get("kind"), literal("head"))))
        style.addLayer(SymbolLayer(MapIds.LYR_MARKERS, MapIds.SRC_MARKERS).withProperties(
            iconImage(MapIds.IMG_PIN), iconAnchor(Property.ICON_ANCHOR_BOTTOM), iconAllowOverlap(true), iconSize(1f),
        ))
        style.addLayer(SymbolLayer(MapIds.LYR_REPORTS, MapIds.SRC_REPORTS).withProperties(
            iconImage(MapIds.IMG_REPORT), iconAnchor(Property.ICON_ANCHOR_CENTER), iconAllowOverlap(true), iconSize(1f),
        ))
        style.addLayer(CircleLayer(MapIds.LYR_ACCURACY, MapIds.SRC_USER).withProperties(
            circleColor(routeColorActive), circleOpacity(0.15f), circleRadius(18f),
        ))
        style.addLayer(SymbolLayer(MapIds.LYR_USER, MapIds.SRC_USER).withProperties(
            iconImage(MapIds.IMG_ARROW), iconRotate(get("bearing")), iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP), iconAllowOverlap(true), iconSize(1f),
        ))
    }

    /** Bâtiments extrudés (guidage 3D) sous le premier calque d'étiquettes ; seulement si le style a la source OpenMapTiles. */
    private fun installBuildings(style: Style) {
        if (style.getSource(MapIds.STYLE_SRC_OPENMAPTILES) == null) return
        val layer = FillExtrusionLayer(MapIds.LYR_BUILDINGS_3D, MapIds.STYLE_SRC_OPENMAPTILES).withSourceLayer(MapIds.STYLE_LYR_BUILDING).withProperties(
            fillExtrusionHeight(coalesce(get("render_height"), literal(0f))),
            fillExtrusionBase(coalesce(get("render_min_height"), literal(0f))),
            fillExtrusionOpacity(0.85f),
            fillExtrusionColor(buildingsColor),
            visibility(if (buildings3d) Property.VISIBLE else Property.NONE),
        )
        layer.minZoom = 15f
        val firstSymbol = style.layers.firstOrNull { it is SymbolLayer }?.id
        if (firstSymbol != null) style.addLayerBelow(layer, firstSymbol) else style.addLayer(layer)
    }

    /**
     * Rejoue tout l'état mémorisé dans un style tout juste chargé (seul appelant : `loadStyle`). Les réglages de couleurs et de
     * bâtiments ne touchent ensuite que leurs calques ; le niveau de détail n'est réappliqué qu'ici ou quand il change.
     */
    private fun replayState() {
        val style = map?.style ?: return
        // Le marqueur reprend la pose affichée (pas la cible de l'animation en cours) : aucun saut en avant puis en arrière.
        markerAnimator?.cancel()
        markerAnimator = null
        val pose = shownPose ?: lastFix?.let { MarkerInterpolation.pose(it) }
        val fix = lastFix
        if (pose != null && fix != null) {
            shownPose = pose
            style.getSourceAs<GeoJsonSource>(MapIds.SRC_USER)?.setGeoJson(PointFeatureBuilder.user(pose, fix.accuracyM))
        }
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_ROUTES)?.setGeoJson(RouteFeatureBuilder.build(lastRoutes, lastActiveRouteId))
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(lastTraversed)
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED_TAIL)?.setGeoJson(lastTraversedTail)
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_MARKERS)?.setGeoJson(PointFeatureBuilder.markers(lastMarkers))
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_REPORTS)?.setGeoJson(PointFeatureBuilder.reports(lastReports))
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_ARROW)?.setGeoJson(lastArrow)
        applyRouteColors(style)
        applyArrowColors(style)
        applyBuildings(style)
        applyDetailTo(style)
        map?.let { m -> pendingCamera?.let { it(m); pendingCamera = null } }
    }

    private fun applyRouteColors(style: Style) {
        (style.getLayer(MapIds.LYR_ROUTE_ACTIVE) as? LineLayer)?.setProperties(lineColor(routeColorActive))
        (style.getLayer(MapIds.LYR_ROUTE_GLOW) as? LineLayer)?.setProperties(lineColor(routeColorActive))
        (style.getLayer(MapIds.LYR_ROUTE_CASING) as? LineLayer)?.setProperties(lineColor(routeColorCasing))
        (style.getLayer(MapIds.LYR_ROUTE_INACTIVE) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
        (style.getLayer(MapIds.LYR_ROUTE_TRAVERSED) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
        (style.getLayer(MapIds.LYR_ROUTE_TRAVERSED_TAIL) as? LineLayer)?.setProperties(lineColor(routeColorInactive))
    }

    private fun applyArrowColors(style: Style) {
        (style.getLayer(MapIds.LYR_ARROW_CASING) as? LineLayer)?.setProperties(lineColor(arrowColorCasing))
        (style.getLayer(MapIds.LYR_ARROW) as? LineLayer)?.setProperties(lineColor(arrowColorBody))
    }

    private fun applyBuildings(style: Style) {
        (style.getLayer(MapIds.LYR_BUILDINGS_3D) as? FillExtrusionLayer)?.setProperties(
            fillExtrusionColor(buildingsColor), visibility(if (buildings3d) Property.VISIBLE else Property.NONE),
        )
    }

    /**
     * Position de l'utilisateur. C1 : le marqueur glisse de la pose affichée vers [fix] en [animateMs] (linéaire, comme `easeCamera`)
     * pour avancer avec la caméra ; 0 = pose immédiate ; un saut de plus de 200 m est posé directement.
     */
    fun setUserLocation(fix: LocationFix?, animateMs: Long = USER_ANIMATION_MS) {
        lastFix = fix
        markerAnimator?.cancel()
        markerAnimator = null
        val target = fix?.let { MarkerInterpolation.pose(it) }
        if (!styleReady) { shownPose = target; return }
        val src = map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_USER) ?: return
        if (fix == null || target == null) {
            shownPose = null
            src.setGeoJson("""{"type":"FeatureCollection","features":[]}""")
            return
        }
        val from = shownPose
        if (from == null || !MarkerInterpolation.shouldAnimate(from, target, animateMs)) {
            shownPose = target
            src.setGeoJson(PointFeatureBuilder.user(target, fix.accuracyM))
            return
        }
        markerGate.reset()
        markerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = animateMs
            interpolator = LinearInterpolator()
            addUpdateListener { a ->
                // Écran à 90-120 Hz, carte rendue à 60 / 30 i/s : une image trop proche de la précédente n'est pas écrite (la dernière toujours).
                if (!markerGate.pass(a.currentPlayTime, last = a.animatedFraction >= 1f)) return@addUpdateListener
                val pose = MarkerInterpolation.lerp(from, target, (a.animatedValue as Float).toDouble())
                shownPose = pose
                map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_USER)?.setGeoJson(PointFeatureBuilder.user(pose, fix.accuracyM))
            }
            start()
        }
    }

    fun setRoutes(routes: List<Route>, activeId: String?) {
        lastRoutes = routes; lastActiveRouteId = activeId
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_ROUTES)?.setGeoJson(RouteFeatureBuilder.build(routes, activeId))
    }

    /** Signalements communautaires actifs autour de la position (couche dédiée, icône ambre). */
    fun setReports(reports: List<CommunityReport>) {
        lastReports = reports
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_REPORTS)?.setGeoJson(PointFeatureBuilder.reports(reports))
    }

    fun setMarkers(places: List<Place>) {
        lastMarkers = places
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_MARKERS)?.setGeoJson(PointFeatureBuilder.markers(places))
    }

    /** Atténue la portion parcourue de l'itinéraire actif (écran de guidage). C3 : préfixe renvoyé seulement s'il change, queue à chaque position. */
    fun setTraversed(geometry: List<GeoPoint>, segmentIndex: Int, snapped: GeoPoint?) {
        val u = traversed.update(geometry, segmentIndex, snapped)
        u.prefix?.let { lastTraversed = it }
        lastTraversedTail = u.tail
        if (!styleReady) return
        val style = map?.style ?: return
        u.prefix?.let { style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED)?.setGeoJson(it) }
        style.getSourceAs<GeoJsonSource>(MapIds.SRC_TRAVERSED_TAIL)?.setGeoJson(u.tail)
    }

    fun clearTraversed() = setTraversed(emptyList(), -1, null)

    /** Flèche de manœuvre (GeoJSON de `ManeuverArrowBuilder`) ; vide = aucune flèche. */
    fun setManeuverArrow(geoJson: String) {
        lastArrow = geoJson
        if (styleReady) map?.style?.getSourceAs<GeoJsonSource>(MapIds.SRC_ARROW)?.setGeoJson(geoJson)
    }

    fun clearManeuverArrow() = setManeuverArrow(ManeuverArrowBuilder.EMPTY)

    /** Couleurs CSS de la flèche : trait et liseré propres, contrastés quel que soit le thème du fond de carte. */
    fun setArrowColor(body: String, casing: String) {
        arrowColorBody = body; arrowColorCasing = casing
        if (styleReady) map?.style?.let { applyArrowColors(it) }
    }

    /**
     * Bâtiments extrudés : visibles ou non, couleur CSS des façades. Sans effet si le style n'a pas la source OpenMapTiles.
     * Ne touche que le calque des bâtiments : la bascule 2D / 3D ne renvoie ni le tracé ni le marqueur (pas de clignotement).
     */
    fun setBuildings3d(enabled: Boolean, colorHex: String) {
        buildings3d = enabled; buildingsColor = colorHex
        if (styleReady) map?.style?.let { applyBuildings(it) }
    }

    /**
     * C2 : niveau de détail. REDUCED masque les points d'intérêt et les noms des rues `minor` / `service` / `track` ; les calques
     * sont repérés par leur `source-layer` (pas par leur identifiant, propre à chaque style). Rejoué après un changement de style.
     */
    fun applyDetail(level: MapDetail) {
        if (level == detail) return
        detail = level
        if (styleReady) map?.style?.let { applyDetailTo(it) }
    }

    private fun applyDetailTo(style: Style) {
        val reduced = detail == MapDetail.REDUCED
        for (layer in style.layers) {
            if (layer !is SymbolLayer) continue
            when (layer.sourceLayer) {
                MapIds.STYLE_LYR_POI -> {
                    val base = baseVisibility.getOrPut(layer.id) { layer.visibility.value ?: Property.VISIBLE }
                    layer.setProperties(visibility(if (reduced) Property.NONE else base))
                }
                MapIds.STYLE_LYR_TRANSPORTATION_NAME -> {
                    if (layer.id !in baseNameFilters) baseNameFilters[layer.id] = layer.filter
                    val base = baseNameFilters[layer.id]
                    layer.setFilter(if (reduced) withoutMinorStreets(base) else base ?: literal(true))
                }
            }
        }
    }

    private fun withoutMinorStreets(base: Expression?): Expression {
        val notMinor = match(get("class"), literal(true), stop("minor", false), stop("service", false), stop("track", false))
        return if (base == null) notMinor else all(base, notMinor)
    }

    /**
     * Caméra de guidage : cible, zoom, cap, inclinaison et marges (fractions de la vue → pixels) ;
     * transition de [durationMs], linéaire pour suivre le rythme des positions (~1 Hz), adoucie si [eased] (bascule 2D/3D, recentrage).
     * Carte ou style pas encore prêts : la commande attend et sera rejouée sans transition (`moveCamera`), quelle que soit
     * [durationMs] — sinon, au retour sur l'écran, la caméra glisserait 1 s depuis la position par défaut de la vue.
     */
    fun applyGuidanceCamera(point: GeoPoint, camera: GuidanceCamera, durationMs: Int = 1000, eased: Boolean = false) {
        fun action(ms: Int): (MapLibreMap) -> Unit = { m ->
            val w = m.width.toDouble(); val h = m.height.toDouble()
            val p = camera.padding
            val pos = CameraPosition.Builder()
                .target(LatLng(point.lat, point.lon)).zoom(camera.zoom).bearing(camera.bearing).tilt(camera.pitch)
                .padding(p.left * w, p.top * h, p.right * w, p.bottom * h)
                .build()
            val update = CameraUpdateFactory.newCameraPosition(pos)
            // `easeCamera` refuse une durée nulle ou négative : déplacement immédiat dans ce cas.
            if (ms > 0) m.easeCamera(update, ms, eased) else m.moveCamera(update)
        }
        val m = map
        if (m == null || !styleReady) { pendingCamera = action(0); return }
        action(durationMs)(m)
    }

    fun moveTo(point: GeoPoint, zoom: Double, bearing: Double = 0.0, animate: Boolean = true, tilt: Double = 0.0) {
        val action: (MapLibreMap) -> Unit = { m ->
            val pos = CameraPosition.Builder().target(LatLng(point.lat, point.lon)).zoom(zoom).bearing(bearing).tilt(tilt).padding(0.0, 0.0, 0.0, 0.0).build()
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

    /** Restaure [snapshot] ; l'inclinaison n'est reprise que si [keepTilt] (écran de guidage) — l'accueil reste à plat. */
    fun restore(snapshot: CameraSnapshot, keepTilt: Boolean = false) =
        moveTo(GeoPoint(snapshot.lat, snapshot.lon), snapshot.zoom, snapshot.bearing, animate = false, tilt = if (keepTilt) snapshot.tilt else 0.0)

    private fun liveCamera(): CameraSnapshot? = map?.cameraPosition?.let { CameraSnapshot(it.target?.latitude ?: return null, it.target?.longitude ?: return null, it.zoom, it.bearing, it.tilt) }

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

    companion object {
        /** Durée (ms) du glissement du marqueur entre deux positions, égale à celle de la caméra (C1). */
        const val USER_ANIMATION_MS = 1_000L
        /** Étiquette logcat du journal des images par seconde de la carte (builds débogables). */
        const val FPS_TAG = "RoadlyFps"
    }
}
