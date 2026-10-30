package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.flatMap
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.model.GeoPoint
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Pluie en cours à une position ; `null` = inconnue (jamais devinée). */
interface RainProvider {
    suspend fun isRaining(at: GeoPoint): Outcome<Boolean?>
}

class NoopRainProvider @Inject constructor() : RainProvider {
    override suspend fun isRaining(at: GeoPoint): Outcome<Boolean?> = Outcome.Success(null)
}

/** Open-Meteo (CC BY 4.0, sans clé) : `current=rain,precipitation`, pluie si ≥ 0,1 mm sur l'intervalle courant. */
@Singleton
class OpenMeteoRainProvider @Inject constructor(private val api: ApiClient, private val json: Json) : RainProvider {
    override suspend fun isRaining(at: GeoPoint): Outcome<Boolean?> {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("latitude", String.format(java.util.Locale.ROOT, "%.3f", at.lat))
            ?.addQueryParameter("longitude", String.format(java.util.Locale.ROOT, "%.3f", at.lon))
            ?.addQueryParameter("current", "rain,precipitation")
            ?.build() ?: return Outcome.Failure(AppError.ProviderUnavailable("open-meteo"))
        return api.get(url).flatMap { body ->
            try {
                val c = json.decodeFromString(OpenMeteoResponse.serializer(), body).current
                Outcome.Success(c?.let { (it.rain ?: 0.0) >= RAIN_MM || (it.precipitation ?: 0.0) >= RAIN_MM })
            } catch (e: Exception) {
                Outcome.Failure(AppError.Parse(e.message ?: "open-meteo"))
            }
        }
    }

    companion object { const val RAIN_MM = 0.1 }
}

@Serializable internal data class OpenMeteoResponse(val current: OpenMeteoCurrent? = null)
@Serializable internal data class OpenMeteoCurrent(val rain: Double? = null, val precipitation: Double? = null)

/** Règles de limitation : abaissement par temps de pluie (code de la route, art. R413-2). */
object SpeedLimitRules {
    fun rainReduced(limitKmh: Int): Int = when {
        limitKmh >= 130 -> 110
        limitKmh >= 110 -> 100
        limitKmh >= 90 -> 80
        else -> limitKmh
    }
}
