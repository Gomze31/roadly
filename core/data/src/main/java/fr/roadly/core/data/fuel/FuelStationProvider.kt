package fr.roadly.core.data.fuel

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.flatMap
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.FuelPrice
import fr.roadly.core.model.FuelStation
import fr.roadly.core.model.FuelType
import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.time.OffsetDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Stations-service avec prix autour d'un point, du plus proche au plus éloigné. */
interface FuelStationProvider {
    val id: String
    suspend fun nearby(near: GeoPoint, radiusKm: Int = 10, limit: Int = 40): Outcome<List<FuelStation>>
}

/** Source : flux instantané « Prix des carburants en France », data.economie.gouv.fr, Licence Ouverte 2.0 (mise à jour ~10 min). */
object FuelSource {
    const val BASE_URL = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records"
    const val DATASET_URL = "https://data.economie.gouv.fr/explore/dataset/prix-des-carburants-en-france-flux-instantane-v2/"
    const val NAME = "Prix des carburants · data.economie.gouv.fr"
}

@Singleton
class OpenDataFuelStationProvider @Inject constructor(private val api: ApiClient, private val json: Json) : FuelStationProvider {
    override val id = "prix-carburants"

    override suspend fun nearby(near: GeoPoint, radiusKm: Int, limit: Int): Outcome<List<FuelStation>> {
        val point = String.format(Locale.ROOT, "geom'POINT(%.5f %.5f)'", near.lon, near.lat)
        val url = FuelSource.BASE_URL.toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("select", SELECT)
            ?.addQueryParameter("where", "within_distance(geom,$point,${radiusKm}km)")
            ?.addQueryParameter("order_by", "distance(geom,$point)")
            ?.addQueryParameter("limit", limit.coerceIn(1, 100).toString())
            ?.build() ?: return Outcome.Failure(AppError.ProviderUnavailable(id))
        return api.get(url).flatMap { parse(it) }
    }

    fun parse(body: String): Outcome<List<FuelStation>> = try {
        val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: JsonArray(emptyList())
        Outcome.Success(results.mapNotNull { it.jsonObject.toStation() })
    } catch (e: Exception) {
        Outcome.Failure(AppError.Parse(e.message ?: id))
    }

    private fun JsonObject.toStation(): FuelStation? {
        val geom = this["geom"] as? JsonObject ?: return null
        val lat = geom["lat"]?.jsonPrimitive?.doubleOrNull ?: return null
        val lon = geom["lon"]?.jsonPrimitive?.doubleOrNull ?: return null
        val id = this["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val street = str("adresse")?.let { titleCase(it) }
        val city = str("ville")
        val cp = str("cp")
        val prices = FUEL_FIELDS.mapNotNull { (field, fuel) ->
            val price = this["${field}_prix"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            FuelPrice(fuel, price, str("${field}_maj")?.let { parseDate(it) })
        }
        val services = (this["services_service"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
        return FuelStation(
            id = id,
            name = street ?: city ?: "Station-service",
            address = listOfNotNull(listOfNotNull(cp, city).joinToString(" ").ifBlank { null }).joinToString(", ").ifBlank { null },
            point = GeoPoint(lat, lon),
            prices = prices,
            open24h = str("horaires_automate_24_24")?.equals("Oui", ignoreCase = true) == true,
            services = services,
            onMotorway = str("pop") == "A",
        )
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    companion object {
        /** Colonnes du flux → carburants Roadly (GPLc = GPL). */
        val FUEL_FIELDS = listOf("gazole" to FuelType.DIESEL, "sp95" to FuelType.SP95, "sp98" to FuelType.SP98, "e10" to FuelType.E10, "e85" to FuelType.E85, "gplc" to FuelType.LPG)
        private val SELECT = "id,adresse,ville,cp,geom,pop,horaires_automate_24_24,services_service," + FUEL_FIELDS.joinToString(",") { "${it.first}_prix,${it.first}_maj" }

        fun parseDate(iso: String): Long? = runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()

        /** « 104 AV GDE BRETAGNE » → « 104 Av Gde Bretagne » (le flux est en capitales). */
        fun titleCase(s: String): String = s.lowercase(Locale.FRANCE).split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.FRANCE) } }
    }
}
