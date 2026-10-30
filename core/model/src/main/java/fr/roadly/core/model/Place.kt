package fr.roadly.core.model

import kotlinx.serialization.Serializable

enum class PlaceKind { ADDRESS, STREET, CITY, POI, COORDINATES }

@Serializable
data class Place(
    val id: String,
    val name: String,
    val address: String?,
    val point: GeoPoint,
    val kind: PlaceKind,
    val source: String,
)
