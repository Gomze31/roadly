package fr.roadly.core.map

import android.app.Presentation
import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.view.Surface
import android.view.ViewGroup
import android.widget.FrameLayout
import fr.roadly.core.model.GeoPoint
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

/**
 * Carte MapLibre rendue sur une [Surface] fournie par un tiers (écran Android Auto) : écran virtuel + [Presentation] qui héberge
 * une [MapView] et, par-dessus, [overlay] (vues dessinées par l'appelant : vitesse, zone de danger). Le [controller] s'utilise comme
 * sur le téléphone (style, tracé, position, caméra). Tout se fait sur le fil principal ; [release] libère la vue et l'écran virtuel.
 */
class SurfaceMapHost(
    context: Context,
    surface: Surface,
    val width: Int,
    val height: Int,
    densityDpi: Int,
) {
    private val display: VirtualDisplay = context.getSystemService(DisplayManager::class.java)
        .createVirtualDisplay(DISPLAY_NAME, width, height, densityDpi, surface, DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
    private val presentation = Presentation(context, display.display)
    private val mapView: MapView
    val controller: MapController
    /** Calque de vues au-dessus de la carte, à la taille de la surface. */
    val overlay: FrameLayout
    private var map: MapLibreMap? = null
    private var released = false

    /** Zone de la surface non recouverte par les gabarits de l'hôte (px) ; toute la surface tant que l'hôte ne l'a pas donnée. */
    var visibleArea: SurfaceArea = SurfaceArea(0, 0, width, height)
        private set

    init {
        val ctx = presentation.context
        controller = MapController(ctx)
        mapView = MapView(ctx, MapRendering.options(ctx))
        overlay = FrameLayout(ctx)
        val root = FrameLayout(ctx).apply {
            addView(mapView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(this@SurfaceMapHost.overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        presentation.setContentView(root)
        presentation.show()
        mapView.onCreate(null)
        mapView.getMapAsync { m ->
            if (released) return@getMapAsync
            map = m
            controller.attach(m)
            // Pas de gestes tactiles directs : l'hôte transmet défilement et zoom (SurfaceCallback).
            m.uiSettings.setAllGesturesEnabled(false)
            applyAttributionPlacement()
        }
        mapView.onStart()
        mapView.onResume()
    }

    fun setVisibleArea(area: SurfaceArea) {
        visibleArea = area
        applyAttributionPlacement()
    }

    /** L'attribution OpenStreetMap reste visible, en bas à gauche de la zone libre. */
    private fun applyAttributionPlacement() {
        val a = visibleArea
        controller.setAttribution(top = false, end = false, verticalPx = (height - a.bottom).coerceAtLeast(0) + EDGE_PX, sidePx = a.left + EDGE_PX)
    }

    /** Suit [point] : centré horizontalement dans la zone libre, à [anchorY] (0 = haut, 1 = bas) de sa hauteur. */
    fun follow(point: GeoPoint, zoom: Double, bearing: Double, tilt: Double, anchorY: Float, durationMs: Int = 1000) {
        val padding = SurfacePadding.forVisibleArea(width, height, visibleArea, anchorY)
        controller.applyGuidanceCamera(point, GuidanceCamera(zoom, tilt, bearing, padding), durationMs)
    }

    /** Cadre [points] dans la zone libre. */
    fun fit(points: List<GeoPoint>, marginPx: Int) {
        val a = visibleArea
        controller.fitBounds(points, a.left + marginPx, a.top + marginPx, width - a.right + marginPx, height - a.bottom + marginPx)
    }

    /** Défilement transmis par l'hôte (convention `GestureDetector` : distance = position précédente − position courante). */
    fun scrollBy(distanceX: Float, distanceY: Float) {
        map?.scrollBy(-distanceX, -distanceY, 0)
    }

    /** Zoom relatif autour de [focus] (px de la surface) ; centre de la zone libre si null. */
    fun zoomBy(delta: Double, focus: Point? = null) {
        val m = map ?: return
        val f = focus ?: Point(visibleArea.centerX, visibleArea.centerY)
        m.animateCamera(CameraUpdateFactory.zoomBy(delta, f), ZOOM_ANIMATION_MS)
    }

    fun release() {
        if (released) return
        released = true
        controller.captureCamera()
        mapView.onPause()
        mapView.onStop()
        mapView.onDestroy()
        controller.detach()
        map = null
        presentation.dismiss()
        display.release()
    }

    private companion object {
        const val DISPLAY_NAME = "RoadlySurfaceMap"
        const val EDGE_PX = 8
        const val ZOOM_ANIMATION_MS = 300
    }
}

/** Rectangle en pixels de surface ([right] et [bottom] exclus). */
data class SurfaceArea(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
}

object SurfacePadding {
    /**
     * Marges caméra (fractions de la vue) qui placent la cible au centre horizontal de [visible] et à [anchorY] de sa hauteur.
     * MapLibre centre la cible dans la vue privée de ses marges : marge haute = haut + (2·anchorY − 1)·hauteur libre.
     */
    fun forVisibleArea(width: Int, height: Int, visible: SurfaceArea, anchorY: Float): CameraPadding {
        if (width <= 0 || height <= 0) return CameraPadding.NONE
        val v = SurfaceArea(
            visible.left.coerceIn(0, width), visible.top.coerceIn(0, height),
            visible.right.coerceIn(0, width), visible.bottom.coerceIn(0, height),
        )
        if (v.width <= 0 || v.height <= 0) return CameraPadding.NONE
        val a = anchorY.coerceIn(0f, 1f)
        val top = (v.top + (2f * a - 1f) * v.height).coerceIn(0f, height.toFloat())
        val bottom = (height - v.bottom).toFloat().coerceAtLeast(0f)
        val bottomPadding = if (a < 0.5f) bottom + (1f - 2f * a) * v.height else bottom
        val topPadding = if (a < 0.5f) v.top.toFloat() else top
        return CameraPadding(
            left = v.left.toFloat() / width,
            top = topPadding / height,
            right = (width - v.right).toFloat() / width,
            bottom = bottomPadding.coerceAtMost(height.toFloat()) / height,
        )
    }
}
