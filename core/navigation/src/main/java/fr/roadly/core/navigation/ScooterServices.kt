package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.flatMap
import fr.roadly.core.data.network.ApiClient
import fr.roadly.core.data.repository.MobilityPreferencesRepository
import fr.roadly.core.data.repository.ScooterProfileRepository
import fr.roadly.core.data.repository.TripRepository
import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.routing.ScooterPoiProvider
import fr.roadly.core.routing.ScooterRouteOptionsUseCase
import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.assistant.AssistantAction
import fr.roadly.core.scooter.assistant.AssistantReply
import fr.roadly.core.scooter.assistant.ScooterAssistant
import fr.roadly.core.scooter.assistant.ScooterAssistantContext
import fr.roadly.core.scooter.assistant.ScooterCommandParser
import fr.roadly.core.scooter.range.RangeCalibration
import fr.roadly.core.scooter.range.TripCalibration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Température extérieure (°C) à une position ; null = inconnue. */
interface TemperatureProvider {
    suspend fun temperatureC(at: GeoPoint): Outcome<Double?>
}

class NoopTemperatureProvider @Inject constructor() : TemperatureProvider {
    override suspend fun temperatureC(at: GeoPoint): Outcome<Double?> = Outcome.Success(null)
}

/** Open-Meteo (CC BY 4.0, sans clé) : `current=temperature_2m`. */
@Singleton
class OpenMeteoTemperatureProvider @Inject constructor(private val api: ApiClient, private val json: Json) : TemperatureProvider {
    override suspend fun temperatureC(at: GeoPoint): Outcome<Double?> {
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("latitude", String.format(java.util.Locale.ROOT, "%.3f", at.lat))
            ?.addQueryParameter("longitude", String.format(java.util.Locale.ROOT, "%.3f", at.lon))
            ?.addQueryParameter("current", "temperature_2m")
            ?.build() ?: return Outcome.Failure(AppError.ProviderUnavailable("open-meteo"))
        return api.get(url).flatMap { body ->
            try {
                Outcome.Success(json.decodeFromString(OpenMeteoTemperature.serializer(), body).current?.temperature)
            } catch (e: Exception) {
                Outcome.Failure(AppError.Parse(e.message ?: "open-meteo"))
            }
        }
    }
}

@Serializable internal data class OpenMeteoTemperature(val current: OpenMeteoTemperatureCurrent? = null)
@Serializable internal data class OpenMeteoTemperatureCurrent(@SerialName("temperature_2m") val temperature: Double? = null)

/**
 * Services du mode trottinette regroupés pour les écrans (une seule dépendance injectée, nulle dans les tests
 * existants : le mode trottinette y est alors absent et les parcours voiture / moto restent strictement identiques).
 */
@Singleton
class ScooterServices @Inject constructor(
    val mobility: MobilityPreferencesRepository,
    val profiles: ScooterProfileRepository,
    val options: ScooterRouteOptionsUseCase,
    val trips: TripRepository,
    val temperature: TemperatureProvider,
    val pois: ScooterPoiProvider,
    val tripRecorder: TripRecorder,
    val speech: SpeechService,
    val favorites: fr.roadly.core.data.repository.FavoritesRepository,
) {
    /** Profil actif : celui mémorisé, sinon le premier ; crée la fiche MAX G2 préconfigurée si aucune n'existe. */
    val activeProfile: Flow<ElectricScooterProfile?> = combine(profiles.profiles, mobility.activeScooterProfileId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }

    suspend fun ensureProfile() {
        val id = profiles.seedIfEmpty(ScooterPresets.NINEBOT_MAX_G2)
        if (id != null) mobility.setActiveScooterProfileId(id)
    }

    suspend fun calibration(profile: ElectricScooterProfile): RangeCalibration = TripCalibration.of(trips.trips.first(), profile)

    /**
     * Roadly AI en mode trottinette : comprend la demande, applique les actions génériques (préférences, batterie,
     * autonomie jusqu'au domicile calculée sur un vrai itinéraire) et renvoie la réponse à prononcer. Les actions
     * propres à l'écran (recherche dans le rayon atteignable) sont renvoyées dans [AssistantReply.action].
     */
    suspend fun assistant(text: String, ctx: ScooterAssistantContext, origin: GeoPoint?, session: NavigationSession?): AssistantReply {
        val command = ScooterCommandParser.parse(text)
        val home = favorites.observeHome().first()
        val reply = ScooterAssistant.respond(command, ctx.copy(hasHome = home != null))
        when (val a = reply.action) {
            is AssistantAction.ApplyPreferences -> mobility.setScooterRoutePreferences(a.preferences)
            is AssistantAction.UpdateBattery -> {
                mobility.setScooterBattery(fr.roadly.core.data.repository.BatteryReading(a.pct, System.currentTimeMillis()))
                session?.setScooterBattery(a.pct.toDouble())
            }
            AssistantAction.EstimateRangeToHome -> return rangeToHome(home!!, origin, ctx)
            else -> Unit
        }
        return reply
    }

    private suspend fun rangeToHome(home: fr.roadly.core.model.Place, origin: GeoPoint?, ctx: ScooterAssistantContext): AssistantReply {
        origin ?: return AssistantReply("Je ne connais pas votre position actuelle.")
        val profile = activeProfile.first() ?: return AssistantReply("Aucune fiche trottinette n'est configurée.")
        val battery = ctx.rangeToDestination?.startBatteryPct ?: mobility.scooterBattery.first()?.pct?.toDouble()
        val params = ScooterRouteOptionsUseCase.Params(
            origin, home.point, profile, ctx.preferences, System.currentTimeMillis(), batteryPct = battery,
            temperatureC = temperatureAt(origin), calibration = calibration(profile), withOsmTags = false, variants = 1,
        )
        return when (val r = options(params)) {
            is Outcome.Success -> ScooterAssistant.rangeAnswer(r.value.firstOrNull()?.scooter?.range, "votre domicile")
            is Outcome.Failure -> AssistantReply("Je n'ai pas pu calculer le trajet jusqu'au domicile.")
        }
    }

    suspend fun temperatureAt(at: GeoPoint): Double? = try {
        (temperature.temperatureC(at) as? Outcome.Success)?.value
    } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { null }
}
