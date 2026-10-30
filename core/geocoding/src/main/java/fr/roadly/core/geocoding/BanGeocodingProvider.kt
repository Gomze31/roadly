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

/** Base Adresse Nationale — https://adresse.data.gouv.fr/api-doc/adresse (Licence Ouverte 2.0). */
@Singleton
class BanGeocodingProvider @Inject constructor(
    private val api: ApiClient,
    private val config: ProviderConfigRepository,
    private val json: Json,
) : GeocodingProvider {
    override val id = "ban"

    override suspend fun search(query: String, near: GeoPoint?, limit: Int): Outcome<List<Place>> {
        val base = config.config.first().banBaseUrl
        val url = "$base/search/".toHttpUrlOrNull()?.newBuilder()
            ?: return Outcome.Failure(AppError.ProviderUnavailable(id))
        val request = url
            .addFormQueryParameter("q", query)
            .addQueryParameter("limit", limit.toString())
            .apply { if (near != null) { addQueryParameter("lat", near.lat.toQueryString()); addQueryParameter("lon", near.lon.toQueryString()) } }
            .build()
        return api.get(request).flatMap { parse(it) }
    }

    override suspend fun reverse(point: GeoPoint): Outcome<Place?> {
        val base = config.config.first().banBaseUrl
        val url = "$base/reverse/".toHttpUrlOrNull()?.newBuilder()
            ?: return Outcome.Failure(AppError.ProviderUnavailable(id))
        val request = url
            .addQueryParameter("lon", point.lon.toQueryString())
            .addQueryParameter("lat", point.lat.toQueryString())
            .build()
        return api.get(request).flatMap { parse(it) }.let { r -> when (r) { is Outcome.Success -> Outcome.Success(r.value.firstOrNull()); is Outcome.Failure -> r } }
    }

    /** Convertit le GeoJSON BAN en lieux ; les entités sans coordonnées exploitables sont ignorées. */
    private fun parse(body: String): Outcome<List<Place>> = try {
        val fc = json.decodeFromString(BanFeatureCollection.serializer(), body)
        Outcome.Success(fc.features.mapNotNull { f ->
            val coords = f.geometry?.coordinates ?: return@mapNotNull null
            if (coords.size < 2) return@mapNotNull null
            val p = f.properties
            val kind = when (p.type) { "housenumber" -> PlaceKind.ADDRESS; "street", "locality" -> PlaceKind.STREET; "municipality" -> PlaceKind.CITY; else -> PlaceKind.ADDRESS }
            val name = if (kind == PlaceKind.CITY) (p.city ?: p.label.orEmpty()) else (p.name ?: p.label.orEmpty())
            val address = listOfNotNull(p.postcode, p.city).joinToString(" ").ifBlank { null }
            Place(id = "ban:${p.id ?: "${coords[1]},${coords[0]}"}", name = name, address = address, point = GeoPoint(coords[1], coords[0]), kind = kind, source = id)
        })
    } catch (e: Exception) {
        Outcome.Failure(AppError.Parse(e.message ?: "ban"))
    }
}

@Serializable internal data class BanFeatureCollection(val features: List<BanFeature> = emptyList())
@Serializable internal data class BanFeature(val geometry: BanGeometry? = null, val properties: BanProperties = BanProperties())
@Serializable internal data class BanGeometry(val coordinates: List<Double> = emptyList())
@Serializable internal data class BanProperties(
    val id: String? = null,
    val label: String? = null,
    val name: String? = null,
    val type: String? = null,
    val postcode: String? = null,
    val city: String? = null,
    @SerialName("citycode") val cityCode: String? = null,
    val context: String? = null,
)
