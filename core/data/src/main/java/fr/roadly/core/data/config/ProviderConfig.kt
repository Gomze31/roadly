package fr.roadly.core.data.config

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ProviderConfig(
    val banBaseUrl: String,
    val photonBaseUrl: String,
    val valhallaBaseUrl: String,
    val mapStyleLightUrl: String,
    val mapStyleDarkUrl: String,
    /** Overpass API (tags OSM des voies d'un itinéraire trottinette, parkings et bornes) ; instance publique en fair-use. */
    val overpassBaseUrl: String = OVERPASS_DEFAULT,
) {
    companion object {
        const val OVERPASS_DEFAULT = "https://overpass-api.de/api"
        val DEFAULT = ProviderConfig(
            banBaseUrl = "https://api-adresse.data.gouv.fr",
            photonBaseUrl = "https://photon.komoot.io",
            valhallaBaseUrl = "https://valhalla1.openstreetmap.de",
            mapStyleLightUrl = "https://tiles.openfreemap.org/styles/positron",
            mapStyleDarkUrl = "https://tiles.openfreemap.org/styles/dark",
        )
    }
}

/** Normalise chaque URL (espaces et « / » finaux retirés) ; lève [IllegalArgumentException] (message = URL fautive) si l'une n'est pas une URL http(s) valide. */
fun ProviderConfig.normalized(): ProviderConfig {
    fun String.norm(): String = trim().trimEnd('/').also { require(it.toHttpUrlOrNull() != null) { it } }
    return ProviderConfig(banBaseUrl.norm(), photonBaseUrl.norm(), valhallaBaseUrl.norm(), mapStyleLightUrl.norm(), mapStyleDarkUrl.norm(), overpassBaseUrl.norm())
}
