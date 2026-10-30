package fr.roadly.core.data.config

data class ProviderConfig(
    val banBaseUrl: String,
    val photonBaseUrl: String,
    val valhallaBaseUrl: String,
    val mapStyleLightUrl: String,
    val mapStyleDarkUrl: String,
) {
    companion object {
        val DEFAULT = ProviderConfig(
            banBaseUrl = "https://api-adresse.data.gouv.fr",
            photonBaseUrl = "https://photon.komoot.io",
            valhallaBaseUrl = "https://valhalla1.openstreetmap.de",
            mapStyleLightUrl = "https://tiles.openfreemap.org/styles/positron",
            mapStyleDarkUrl = "https://tiles.openfreemap.org/styles/dark",
        )
    }
}
