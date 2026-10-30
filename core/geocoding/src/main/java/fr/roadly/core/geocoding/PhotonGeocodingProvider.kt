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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Photon (komoot) — https://photon.komoot.io ; données © contributeurs OpenStreetMap (ODbL). Fair-use, pas de garantie de production. */
@Singleton
class PhotonGeocodingProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
) : GeocodingProvider {
    override val id = "photon"

    override suspend fun search(query: String, near: GeoPoint?, limit: Int): Outcome<List<Place>> {
        val base = config.config.first().photonBaseUrl
        val url = "$base/api/".toHttpUrlOrNull()?.newBuilder()
            ?: return Outcome.Failure(AppError.ProviderUnavailable(id))
        val request = url
            .addFormQueryParameter("q", query)
            .addQueryParameter("lang", "fr")
            .addQueryParameter("limit", limit.toString())
            .apply { if (near != null) { addQueryParameter("lat", near.lat.toQueryString()); addQueryParameter("lon", near.lon.toQueryString()) } }
            .build()
        return api.get(request).flatMap { parse(it) }
    }

    override suspend fun reverse(point: GeoPoint): Outcome<Place?> {
        val base = config.config.first().photonBaseUrl
        val url = "$base/reverse".toHttpUrlOrNull()?.newBuilder()
            ?: return Outcome.Failure(AppError.ProviderUnavailable(id))
        val request = url
            .addQueryParameter("lat", point.lat.toQueryString()).addQueryParameter("lon", point.lon.toQueryString()).addQueryParameter("lang", "fr").build()
        return when (val r = api.get(request).flatMap { parse(it) }) { is Outcome.Success -> Outcome.Success(r.value.firstOrNull()); is Outcome.Failure -> r }
    }

    /** Convertit le GeoJSON Photon en lieux ; le type (ville, rue, adresse, POI) est déduit des clés OSM. */
    private fun parse(body: String): Outcome<List<Place>> = try {
        val fc = json.decodeFromString(PhotonFeatureCollection.serializer(), body)
        Outcome.Success(fc.features.mapNotNull { f ->
            val c = f.geometry?.coordinates ?: return@mapNotNull null
            if (c.size < 2) return@mapNotNull null
            val p = f.properties
            val kind = when {
                p.osmKey == "place" && p.osmValue in setOf("city", "town", "village", "hamlet", "municipality") -> PlaceKind.CITY
                p.osmKey == "highway" -> PlaceKind.STREET
                p.houseNumber != null -> PlaceKind.ADDRESS
                else -> PlaceKind.POI
            }
            val name = p.name ?: listOfNotNull(p.houseNumber, p.street).joinToString(" ").ifBlank { p.city ?: "Lieu" }
            val address = listOfNotNull(
                if (p.name != null) listOfNotNull(p.houseNumber, p.street).joinToString(" ").ifBlank { null } else null,
                listOfNotNull(p.postcode, p.city).joinToString(" ").ifBlank { null },
                p.country,
            ).joinToString(", ").ifBlank { null }
            Place(id = "photon:${p.osmType ?: "x"}${p.osmId ?: "${c[1]},${c[0]}"}", name = name, address = address, point = GeoPoint(c[1], c[0]), kind = kind, source = id)
        })
    } catch (e: Exception) {
        Outcome.Failure(AppError.Parse(e.message ?: "photon"))
    }
}

@Serializable internal data class PhotonFeatureCollection(val features: List<PhotonFeature> = emptyList())
@Serializable internal data class PhotonFeature(val geometry: PhotonGeometry? = null, val properties: PhotonProperties = PhotonProperties())
@Serializable internal data class PhotonGeometry(val coordinates: List<Double> = emptyList())
@Serializable internal data class PhotonProperties(
    val name: String? = null,
    val street: String? = null,
    @SerialName("housenumber") val houseNumber: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    val country: String? = null,
    @SerialName("osm_key") val osmKey: String? = null,
    @SerialName("osm_value") val osmValue: String? = null,
    @SerialName("osm_id") val osmId: Long? = null,
    @SerialName("osm_type") val osmType: String? = null,
)
