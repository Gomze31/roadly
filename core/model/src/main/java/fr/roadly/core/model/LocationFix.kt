package fr.roadly.core.model

data class LocationFix(
    val point: GeoPoint,
    val bearingDeg: Float?,
    val speedMps: Float?,
    val accuracyM: Float,
    val timestampMs: Long,
)
