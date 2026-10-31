package fr.roadly.core.map

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.roadly.core.model.GeoPoint
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

@Composable
fun rememberMapController(): MapController {
    val ctx = LocalContext.current
    return remember { MapController(ctx) }
}

@Composable
fun RoadlyMap(
    styleUrl: String,
    controller: MapController,
    modifier: Modifier = Modifier,
    onMapClick: ((GeoPoint) -> Unit)? = null,
    onMapLongClick: ((GeoPoint) -> Unit)? = null,
    onUserGesture: () -> Unit = {},
    onRouteClick: ((String) -> Unit)? = null,
    /** Caméra immobile : cap de la carte en degrés (0 = nord en haut) pour la boussole. */
    onCameraIdle: ((bearingDeg: Double) -> Unit)? = null,
    /** C3 : plafond d'images par seconde du rendu ([MapRendering.MAX_FPS] ; 30 en économie d'énergie). */
    maximumFps: Int = MapRendering.MAX_FPS,
    /** D5 : emplacement de l'icône d'attribution ; null = en bas à gauche, au-dessus de la barre de navigation (bord à bord). */
    attribution: AttributionPlacement? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { controllerContext(controller).let { ctx -> MapView(ctx, MapRendering.options(ctx)) } }
    // Le moteur de rendu est créé par le constructeur de MapView : le plafond peut être posé tout de suite.
    // Le marqueur animé du contrôleur suit le même plafond (pas d'écriture pour une image que la carte ne rendra pas).
    LaunchedEffect(mapView, maximumFps) {
        mapView.setMaximumFps(maximumFps)
        controller.setMaximumFps(maximumFps)
    }
    // D5 : sans emplacement imposé, l'icône d'attribution reste au-dessus de la barre de navigation (elle était dessous, inaccessible).
    val density = LocalDensity.current
    val navBars = WindowInsets.navigationBars
    val navBottom = navBars.getBottom(density)
    val navLeft = navBars.getLeft(density, LayoutDirection.Ltr)
    LaunchedEffect(attribution, navBottom, navLeft, density) {
        val p = attribution
        if (p == null) {
            val edge = with(density) { AttributionPlacement.DEFAULT_EDGE.roundToPx() }
            controller.setAttribution(top = false, end = false, verticalPx = navBottom + edge, sidePx = navLeft + edge)
        } else {
            controller.setAttribution(p.top, p.end, with(density) { p.vertical.roundToPx() }, with(density) { p.side.roundToPx() })
        }
    }
    val currentOnMapClick = rememberUpdatedState(onMapClick)
    val currentOnMapLongClick = rememberUpdatedState(onMapLongClick)
    val currentOnUserGesture = rememberUpdatedState(onUserGesture)
    val currentOnCameraIdle = rememberUpdatedState(onCameraIdle)
    SideEffect { controller.onRouteClick = onRouteClick }

    DisposableEffect(lifecycleOwner, mapView) {
        var destroyed = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> if (!destroyed) { destroyed = true; mapView.onDestroy() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            controller.captureCamera()
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (!destroyed) {
                // Le composable quitte la composition alors que le propriétaire est encore actif : descendre la vue étape par étape.
                val state = lifecycleOwner.lifecycle.currentState
                if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                destroyed = true
                mapView.onDestroy()
            }
            controller.detach()
        }
    }

    LaunchedEffect(styleUrl) { controller.setStyle(styleUrl) }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                onCreate(null)
                getMapAsync { map: MapLibreMap ->
                    controller.attach(map)
                    map.addOnMapClickListener { ll -> currentOnMapClick.value?.invoke(GeoPoint(ll.latitude, ll.longitude)); false }
                    map.addOnMapLongClickListener { ll ->
                        val handler = currentOnMapLongClick.value
                        handler?.invoke(GeoPoint(ll.latitude, ll.longitude))
                        handler != null
                    }
                    map.addOnCameraMoveStartedListener { reason ->
                        if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) currentOnUserGesture.value()
                    }
                    map.addOnCameraIdleListener { currentOnCameraIdle.value?.invoke(map.cameraPosition.bearing) }
                }
            }
        },
    )
}

// MapView a besoin du Context ; le contrôleur le détient déjà (le contexte d'activité est requis pour les thèmes).
private fun controllerContext(controller: MapController): android.content.Context = controller.context
