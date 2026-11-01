package fr.roadly.core.map

/** Position de caméra mémorisée pour être restaurée (accueil en mode libre, retour sur le guidage) ; [tilt] repris par le guidage seulement. */
data class CameraSnapshot(val lat: Double, val lon: Double, val zoom: Double, val bearing: Double, val tilt: Double = 0.0)
