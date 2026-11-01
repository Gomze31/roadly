package fr.roadly.core.map

import android.content.Context
import fr.roadly.core.model.BoundingBox
import fr.roadly.core.model.GeoPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.cos

sealed interface OfflineDownloadState {
    data object Idle : OfflineDownloadState
    data class Downloading(val completed: Long, val required: Long) : OfflineDownloadState
    data class Done(val bytes: Long) : OfflineDownloadState
    data class Failed(val reason: String) : OfflineDownloadState
}

/**
 * Téléchargement des tuiles de carte d'un trajet (emprise du tracé + 500 m, zooms 12 à 16) via le cache hors
 * connexion de MapLibre. Seule la carte est disponible hors connexion : le calcul et le recalcul d'itinéraire
 * restent faits par le serveur Valhalla et demandent une connexion.
 */
class OfflineMapDownloader(private val context: Context) {
    private val _state = MutableStateFlow<OfflineDownloadState>(OfflineDownloadState.Idle)
    val state: StateFlow<OfflineDownloadState> = _state.asStateFlow()

    fun download(styleUrl: String, route: List<GeoPoint>, name: String, minZoom: Double = 12.0, maxZoom: Double = 16.0) {
        val box = BoundingBox.of(route) ?: return
        val padLat = 500.0 / 111_320.0
        val padLon = 500.0 / (111_320.0 * cos(Math.toRadians((box.north + box.south) / 2)))
        val bounds = LatLngBounds.Builder()
            .include(LatLng(box.south - padLat, box.west - padLon))
            .include(LatLng(box.north + padLat, box.east + padLon))
            .build()
        val definition = OfflineTilePyramidRegionDefinition(styleUrl, bounds, minZoom, maxZoom, context.resources.displayMetrics.density)
        _state.value = OfflineDownloadState.Downloading(0, 0)
        OfflineManager.getInstance(context).createOfflineRegion(definition, name.toByteArray(Charsets.UTF_8), object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        if (status.isComplete) {
                            _state.value = OfflineDownloadState.Done(status.completedResourceSize)
                            offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        } else {
                            _state.value = OfflineDownloadState.Downloading(status.completedResourceCount, status.requiredResourceCount)
                        }
                    }
                    override fun onError(error: OfflineRegionError) { _state.value = OfflineDownloadState.Failed(error.message) }
                    override fun mapboxTileCountLimitExceeded(limit: Long) { _state.value = OfflineDownloadState.Failed("Limite de $limit tuiles atteinte") }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
            override fun onError(error: String) { _state.value = OfflineDownloadState.Failed(error) }
        })
    }
}
