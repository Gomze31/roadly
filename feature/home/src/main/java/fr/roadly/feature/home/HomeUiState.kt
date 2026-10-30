package fr.roadly.feature.home

import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.map.CameraMode
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place

/** État affiché par l'écran d'accueil (carte + panneau inférieur). */
data class HomeUiState(
    val fix: LocationFix? = null,
    val address: String? = null,
    val home: Place? = null,
    val work: Place? = null,
    val cameraMode: CameraMode = CameraMode.CENTERED,
    val permission: LocationPermissionState = LocationPermissionState.UNKNOWN,
    val isOnline: Boolean = true,
    val styleUrl: String = "",
    val pickedPlace: Place? = null,
    /** Cap de la carte (0 = nord en haut) : la boussole n'apparaît que si la carte est tournée. */
    val mapBearingDeg: Double = 0.0,
) {
    val isRotated: Boolean get() = kotlin.math.abs(((mapBearingDeg % 360) + 360) % 360).let { it > 2.0 && it < 358.0 }

    /** Vitesse en km/h, `null` tant qu'aucune vitesse n'est connue. */
    val speedKmh: Double? get() = fix?.speedMps?.let { it * 3.6 }
}
