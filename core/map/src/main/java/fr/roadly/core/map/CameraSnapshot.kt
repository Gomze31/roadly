package fr.roadly.core.map

/** Position de caméra mémorisée pour être restaurée (retour sur l'écran d'accueil en mode libre). */
data class CameraSnapshot(val lat: Double, val lon: Double, val zoom: Double, val bearing: Double, val tilt: Double = 0.0)
