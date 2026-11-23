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
        /**
         * Géocodage de la Base Adresse Nationale sur la Géoplateforme de l'IGN (`/search/` et `/reverse/`, mêmes réponses).
         * Vérifié le 2026-11-22 : documenté par l'IGN, gratuit, sans clé, 50 requêtes par seconde et par adresse IP.
         */
        const val BAN_DEFAULT = "https://data.geopf.fr/geocodage"
        /** Ancienne adresse de la BAN, annoncée hors service depuis fin janvier 2026 : une valeur enregistrée égale est lue comme le défaut. */
        const val BAN_LEGACY = "https://api-adresse.data.gouv.fr"
        /** Schéma des styles embarqués (assets de `core/map`), lu par MapLibre (`Style.Builder().fromUri`). */
        const val ASSET_SCHEME = "asset://"

        /** C2 : styles Roadly embarqués ; une URL http(s) de style externe reste possible (Réglages → Fournisseurs). */
        val DEFAULT = ProviderConfig(
            banBaseUrl = BAN_DEFAULT,
            photonBaseUrl = "https://photon.komoot.io",
            valhallaBaseUrl = "https://valhalla1.openstreetmap.de",
            mapStyleLightUrl = "asset://styles/roadly-light.json",
            mapStyleDarkUrl = "asset://styles/roadly-dark.json",
        )
    }
}

/**
 * Normalise chaque URL (espaces et « / » finaux retirés) ; lève [IllegalArgumentException] (message = URL fautive) si l'une n'est pas valide :
 * http(s) pour les serveurs, http(s) ou `asset://<chemin>` pour les deux styles de carte.
 */
fun ProviderConfig.normalized(): ProviderConfig {
    fun String.norm(): String = trim().trimEnd('/').also { require(it.toHttpUrlOrNull() != null) { it } }
    fun String.normStyle(): String {
        val s = trim()
        if (!s.startsWith(ProviderConfig.ASSET_SCHEME)) return s.norm()
        require(s.length > ProviderConfig.ASSET_SCHEME.length && !s.endsWith("/")) { s }
        return s
    }
    return ProviderConfig(banBaseUrl.norm(), photonBaseUrl.norm(), valhallaBaseUrl.norm(), mapStyleLightUrl.normStyle(), mapStyleDarkUrl.normStyle(), overpassBaseUrl.norm())
}
