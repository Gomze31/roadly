package fr.roadly.core.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(controllerContext(controller)) }
    controller.onRouteClick = onRouteClick

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer); mapView.onDestroy() }
    }

    LaunchedEffect(styleUrl) { controller.setStyle(styleUrl) }

    AndroidView(
        modifier = modifier,
        factory = {
            mapView.apply {
                onCreate(null)
                getMapAsync { map: MapLibreMap ->
                    controller.attach(map)
                    map.addOnMapClickListener { ll -> onMapClick?.invoke(GeoPoint(ll.latitude, ll.longitude)); false }
                    map.addOnMapLongClickListener { ll -> onMapLongClick?.invoke(GeoPoint(ll.latitude, ll.longitude)); true }
                    map.addOnCameraMoveStartedListener { reason -> if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) onUserGesture() }
                }
            }
        },
    )
}

// MapView a besoin du Context ; le contrôleur le détient déjà (le contexte d'activité est requis pour les thèmes).
private fun controllerContext(controller: MapController): android.content.Context = controller.context
