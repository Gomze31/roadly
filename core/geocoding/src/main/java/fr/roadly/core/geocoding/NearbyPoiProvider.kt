package fr.roadly.core.geocoding

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.flatMap
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Catégories de lieux « à proximité » (puces de la recherche et de l'accueil). Chaque catégorie correspond à des
 * étiquettes OSM ; les données de prix, de puissance ou de disponibilité ne viennent pas d'ici (LOT 5).
 */
enum class PoiCategory(val osmTags: List<String>, val radiusKm: Int) {
    FUEL(listOf("amenity:fuel"), 5),
    CHARGING(listOf("amenity:charging_station"), 5),
    PARKING(listOf("amenity:parking"), 2),
    REST_AREA(listOf("highway:rest_area", "highway:services"), 30),
    RESTAURANT(listOf("amenity:restaurant"), 2),
}

/** Lieux d'une catégorie autour d'un point, triés du plus proche au plus éloigné. */
interface NearbyPoiProvider {
    val id: String
    suspend fun nearby(category: PoiCategory, near: GeoPoint, limit: Int = 25): Outcome<List<Place>>
}

/** Photon `/reverse` avec `radius` (km) et filtres `osm_tag` : données © contributeurs OpenStreetMap (ODbL), fair-use. */
@Singleton
class PhotonNearbyPoiProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
) : NearbyPoiProvider {
    override val id = "photon"

    override suspend fun nearby(category: PoiCategory, near: GeoPoint, limit: Int): Outcome<List<Place>> {
        val base = config.config.first().photonBaseUrl
        val url = "$base/reverse".toHttpUrlOrNull()?.newBuilder()
            ?: return Outcome.Failure(AppError.ProviderUnavailable(id))
        val request = url
            .addQueryParameter("lat", near.lat.toQueryString())
            .addQueryParameter("lon", near.lon.toQueryString())
            .addQueryParameter("radius", category.radiusKm.toString())
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("lang", "fr")
            .apply { category.osmTags.forEach { addQueryParameter("osm_tag", it) } }
            .build()
        return api.get(request).flatMap { parse(it, near) }
    }

    private fun parse(body: String, near: GeoPoint): Outcome<List<Place>> = try {
        val fc = json.decodeFromString(PhotonFeatureCollection.serializer(), body)
        val places = fc.features.mapNotNull { f ->
            val c = f.geometry?.coordinates ?: return@mapNotNull null
            if (c.size < 2) return@mapNotNull null
            val p = f.properties
            val point = GeoPoint(c[1], c[0])
            val name = p.name ?: listOfNotNull(p.street, p.city).joinToString(", ").ifBlank { return@mapNotNull null }
            val address = listOfNotNull(
                listOfNotNull(p.houseNumber, p.street).joinToString(" ").ifBlank { null },
                listOfNotNull(p.postcode, p.city).joinToString(" ").ifBlank { null },
            ).joinToString(", ").ifBlank { null }
            Place(id = "photon:${p.osmType ?: "x"}${p.osmId ?: "${c[1]},${c[0]}"}", name = name, address = address, point = point, kind = PlaceKind.POI, source = id)
        }
        Outcome.Success(places.sortedBy { it.point.distanceTo(near) })
    } catch (e: Exception) {
        Outcome.Failure(AppError.Parse(e.message ?: "photon"))
    }
}
