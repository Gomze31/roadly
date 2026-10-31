package fr.roadly.core.model

/** Prix d'un carburant dans une station, avec la date de mise à jour publiée par la source. */
data class FuelPrice(val fuel: FuelType, val eurPerLitre: Double, val updatedAtEpochMs: Long?)

/**
 * Station-service issue du flux instantané « Prix des carburants » (ministère de l'Économie, Licence Ouverte).
 * [prices] ne contient que les carburants réellement publiés ; aucun prix n'est inventé.
 */
data class FuelStation(
    val id: String,
    val name: String,
    val address: String?,
    val point: GeoPoint,
    val prices: List<FuelPrice>,
    val open24h: Boolean,
    val services: List<String>,
) {
    fun price(fuel: FuelType): FuelPrice? = prices.firstOrNull { it.fuel == fuel }

    fun toPlace(): Place = Place(id = "fuel:$id", name = name, address = address, point = point, kind = PlaceKind.POI, source = "prix-carburants")
}
