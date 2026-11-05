package fr.roadly.feature.car

import android.graphics.Point
import android.graphics.Rect
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import fr.roadly.core.map.ManeuverArrowBuilder
import fr.roadly.core.map.SurfaceArea
import fr.roadly.core.map.SurfaceMapHost
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Route
import kotlin.math.ln

/** Cadrages de la caméra qui suit le véhicule sur l'écran de la voiture. */
internal enum class FollowProfile(val zoom: Double, val tilt: Double, val anchorY: Float, val headingUp: Boolean) {
    /** Accueil, recherche : vue à plat, véhicule au centre. */
    BROWSE(15.0, 0.0, 0.5f, false),
    /** Guidage : perspective, véhicule bas pour montrer la route devant. */
    GUIDANCE(16.5, 45.0, 0.72f, true),
    /** Conduite libre : un peu plus large que le guidage. */
    FREE_DRIVE(16.0, 40.0, 0.66f, true),
}

/**
 * Carte de l'écran de la voiture (une seule par session, `AppManager.setSurfaceCallback`). L'état voulu (tracés, position, caméra,
 * indications) est gardé ici et rejoué à chaque nouvelle surface : l'hôte la détruit et la recrée (rotation, changement de taille).
 */
internal class CarMapRenderer(private val carContext: CarContext) : SurfaceCallback {
    private var host: SurfaceMapHost? = null
    private var hud: CarHudView? = null
    private var visible: SurfaceArea? = null

    private var styleUrl = ""
    private var routes: List<Route> = emptyList()
    private var activeRouteId: String? = null
    private var traversed: Pair<Int, GeoPoint?>? = null
    private var arrow: String = ManeuverArrowBuilder.EMPTY
    private var user: LocationFix? = null
    private var hudModel = CarHudModel.EMPTY

    private var follow: Triple<GeoPoint, Double?, FollowProfile>? = null
    private var overview: List<GeoPoint>? = null
    /** Défilement manuel : la caméra ne suit plus jusqu'à [recenter]. */
    private var panned = false
    private var zoomOffset = 0.0

    fun setStyle(url: String) {
        if (url.isEmpty() || url == styleUrl) return
        styleUrl = url
        host?.controller?.setStyle(url)
    }

    fun setRoutes(routes: List<Route>, activeId: String?) {
        this.routes = routes
        activeRouteId = activeId
        host?.controller?.setRoutes(routes, activeId)
        if (routes.isEmpty()) setTraversed(null, null)
    }

    /** Portion parcourue du tracé actif (segment courant et point projeté) ; null = rien d'atténué. */
    fun setTraversed(segmentIndex: Int?, snapped: GeoPoint?) {
        traversed = segmentIndex?.let { it to snapped }
        val c = host?.controller ?: return
        val geometry = routes.firstOrNull { it.id == activeRouteId }?.geometry
        if (segmentIndex == null || geometry == null) c.clearTraversed() else c.setTraversed(geometry, segmentIndex, snapped)
    }

    fun setManeuverArrow(geoJson: String) {
        if (geoJson == arrow) return
        arrow = geoJson
        host?.controller?.setManeuverArrow(geoJson)
    }

    fun clearRoutes() {
        setRoutes(emptyList(), null)
        setManeuverArrow(ManeuverArrowBuilder.EMPTY)
        overview = null
    }

    fun setUser(fix: LocationFix?) {
        user = fix
        host?.controller?.setUserLocation(fix)
    }

    fun setHud(model: CarHudModel) {
        hudModel = model
        hud?.update(model)
    }

    /** Suit [point] avec le cadrage [profile] ; [bearingDeg] = cap du véhicule (null = nord en haut ou cap précédent). */
    fun follow(point: GeoPoint, bearingDeg: Double?, profile: FollowProfile) {
        val previousBearing = follow?.second
        follow = Triple(point, bearingDeg ?: previousBearing, profile)
        overview = null
        applyCamera(durationMs = FOLLOW_ANIMATION_MS)
    }

    /** Cadre [points] (aperçu d'itinéraire) ; annule le suivi jusqu'au prochain [follow]. */
    fun overview(points: List<GeoPoint>) {
        overview = points
        panned = false
        applyCamera(durationMs = 0)
    }

    fun recenter() {
        panned = false
        zoomOffset = 0.0
        applyCamera(durationMs = FOLLOW_ANIMATION_MS)
    }

    fun zoom(delta: Double) {
        if (panned || overview != null) { host?.zoomBy(delta); return }
        zoomOffset = (zoomOffset + delta).coerceIn(MIN_ZOOM_OFFSET, MAX_ZOOM_OFFSET)
        applyCamera(durationMs = ZOOM_ANIMATION_MS)
    }

    private fun applyCamera(durationMs: Int) {
        val h = host ?: return
        if (panned) return
        overview?.let { pts -> if (pts.isNotEmpty()) h.fit(pts, OVERVIEW_MARGIN_PX); return }
        val (point, bearing, profile) = follow ?: return
        val heading = if (profile.headingUp) bearing ?: 0.0 else 0.0
        h.follow(point, profile.zoom + zoomOffset, heading, profile.tilt, profile.anchorY, durationMs)
    }

    override fun onSurfaceAvailable(surfaceContainer: SurfaceContainer) {
        val surface = surfaceContainer.surface ?: return
        releaseHost()
        val h = SurfaceMapHost(carContext, surface, surfaceContainer.width, surfaceContainer.height, surfaceContainer.dpi)
        host = h
        val view = CarHudView(h.overlay.context)
        h.overlay.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        hud = view
        visible?.let { h.setVisibleArea(it); view.setArea(it) }
        view.update(hudModel)
        if (styleUrl.isNotEmpty()) h.controller.setStyle(styleUrl)
        h.controller.setRoutes(routes, activeRouteId)
        setTraversed(traversed?.first, traversed?.second)
        h.controller.setManeuverArrow(arrow)
        h.controller.setUserLocation(user, 0)
        applyCamera(durationMs = 0)
    }

    override fun onVisibleAreaChanged(visibleArea: Rect) {
        val area = SurfaceArea(visibleArea.left, visibleArea.top, visibleArea.right, visibleArea.bottom)
        if (area == visible) return
        visible = area
        host?.setVisibleArea(area)
        hud?.setArea(area)
        applyCamera(durationMs = 0)
    }

    override fun onStableAreaChanged(stableArea: Rect) = Unit

    override fun onSurfaceDestroyed(surfaceContainer: SurfaceContainer) = releaseHost()

    override fun onScroll(distanceX: Float, distanceY: Float) {
        panned = true
        host?.scrollBy(distanceX, distanceY)
    }

    override fun onFling(velocityX: Float, velocityY: Float) = Unit

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) {
        if (scaleFactor <= 0f) return
        val delta = ln(scaleFactor.toDouble()) / ln(2.0)
        // Carte libre ou aperçu : zoom autour du point touché ; en suivi, le zoom s'ajoute au cadrage (le véhicule reste en place).
        if (panned || overview != null || follow == null) {
            host?.zoomBy(delta, if (focusX >= 0f && focusY >= 0f) Point(focusX.toInt(), focusY.toInt()) else null)
        } else {
            zoom(delta)
        }
    }

    fun release() {
        releaseHost()
    }

    private fun releaseHost() {
        hud = null
        host?.release()
        host = null
    }

    private companion object {
        const val FOLLOW_ANIMATION_MS = 1000
        const val ZOOM_ANIMATION_MS = 300
        const val OVERVIEW_MARGIN_PX = 48
        const val MIN_ZOOM_OFFSET = -5.0
        const val MAX_ZOOM_OFFSET = 3.0
    }
}
