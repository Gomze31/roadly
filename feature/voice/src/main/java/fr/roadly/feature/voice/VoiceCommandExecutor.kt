package fr.roadly.feature.voice

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Formatters
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.community.CommunityRepository
import fr.roadly.core.data.repository.FavoritesRepository
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.geocoding.GeocodingProvider
import fr.roadly.core.geocoding.PoiCategory
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.Place
import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.RoutePreferences
import fr.roadly.core.navigation.AppNavigator
import fr.roadly.core.navigation.GuidanceActions
import fr.roadly.core.navigation.NavRequest
import fr.roadly.core.navigation.NavigationSession
import fr.roadly.core.navigation.NavigationState
import fr.roadly.core.navigation.SpeechPriority
import fr.roadly.core.navigation.SpeechService
import fr.roadly.core.navigation.VoiceChannel
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/** Réponse à une commande : texte affiché et prononcé, et succès de l'action. */
data class VoiceReply(val text: String, val ok: Boolean = true)

/**
 * Exécute une commande vocale avec les services de l'application (guidage, préférences, favoris, signalements, recherche)
 * et prononce la réponse sur le canal « assistant » (qui laisse passer les annonces de guidage). Rien ne quitte le téléphone
 * à part les requêtes que l'action elle-même demande (calcul d'itinéraire, géocodage, signalement).
 */
@Singleton
class VoiceCommandExecutor @Inject constructor(
    private val session: NavigationSession,
    private val guidance: GuidanceActions,
    private val prefs: UserPreferencesRepository,
    private val favorites: FavoritesRepository,
    private val community: CommunityRepository,
    private val geocoder: GeocodingProvider,
    private val location: LocationRepository,
    private val navigator: AppNavigator,
    private val speech: SpeechService,
) {
    suspend fun handle(text: String): VoiceReply {
        val reply = try { run(VoiceCommandParser.parse(text)) } catch (e: CancellationException) { throw e } catch (e: Exception) {
            VoiceReply(VoiceReplies.failure(), ok = false)
        }
        speech.speak(reply.text, flush = false, priority = SpeechPriority.NORMAL, channel = VoiceChannel.ASSISTANT)
        return reply
    }

    private val guiding get() = session.state.value as? NavigationState.Guiding

    private suspend fun run(intent: VoiceIntent): VoiceReply = when (intent) {
        is VoiceIntent.Navigate -> navigate(intent.target)
        VoiceIntent.StopGuidance -> if (guiding == null) VoiceReply(VoiceReplies.NOT_GUIDING, ok = false) else { guidance.stop(); VoiceReply(VoiceReplies.STOPPED) }
        is VoiceIntent.Avoid -> avoid(intent)
        VoiceIntent.ArrivalTime -> guiding?.let { VoiceReply(VoiceReplies.arrival(it.etaEpochMs, it.progress.remainingDurationS, ZoneId.systemDefault())) } ?: VoiceReply(VoiceReplies.NOT_GUIDING, ok = false)
        VoiceIntent.Remaining -> guiding?.let { VoiceReply(VoiceReplies.remaining(it.progress.remainingDistanceM, it.progress.remainingDurationS)) } ?: VoiceReply(VoiceReplies.NOT_GUIDING, ok = false)
        VoiceIntent.NextInstruction -> guiding?.let { g ->
            val m = g.route.maneuvers.getOrNull(g.progress.nextManeuverIndex)
            VoiceReply(if (m == null) VoiceReplies.NO_NEXT else VoiceReplies.next(g.progress.distanceToNextManeuverM, m.instruction))
        } ?: VoiceReply(VoiceReplies.NOT_GUIDING, ok = false)
        VoiceIntent.SpeedLimit -> VoiceReply(VoiceReplies.speedLimit(guiding?.speedLimitKmh))
        VoiceIntent.Recalculate -> if (guiding == null) VoiceReply(VoiceReplies.NOT_GUIDING, ok = false) else when (val r = guidance.recalculate()) {
            is Outcome.Success -> VoiceReply(VoiceReplies.recalculated(r.value.arrivalEpochMs, ZoneId.systemDefault()))
            is Outcome.Failure -> VoiceReply(VoiceReplies.routeFailed(r.error), ok = false)
        }
        is VoiceIntent.Report -> report(intent.kind)
        is VoiceIntent.Nearby -> { navigator.navigate(NavRequest.OpenSearch(intent.category.name)); VoiceReply(VoiceReplies.nearby(intent.category)) }
        is VoiceIntent.Mute -> { session.setMuted(intent.muted); VoiceReply(if (intent.muted) VoiceReplies.MUTED else VoiceReplies.UNMUTED) }
        VoiceIntent.Help -> VoiceReply(VoiceReplies.HELP)
        is VoiceIntent.Unknown -> VoiceReply(VoiceReplies.unknown(intent.text), ok = false)
    }

    private suspend fun navigate(target: VoiceIntent.Target): VoiceReply {
        val place: Place = when (target) {
            VoiceIntent.Target.Home -> favorites.observeHome().first() ?: return VoiceReply(VoiceReplies.HOME_UNDEFINED, ok = false)
            VoiceIntent.Target.Work -> favorites.observeWork().first() ?: return VoiceReply(VoiceReplies.WORK_UNDEFINED, ok = false)
            is VoiceIntent.Target.Query -> when (val r = geocoder.search(target.text, near = location.lastKnown?.point, limit = 1)) {
                is Outcome.Success -> r.value.firstOrNull() ?: return VoiceReply(VoiceReplies.notFound(target.text), ok = false)
                is Outcome.Failure -> return VoiceReply(VoiceReplies.routeFailed(r.error), ok = false)
            }
        }
        val label = when (target) {
            VoiceIntent.Target.Home -> VoiceReplies.HOME
            VoiceIntent.Target.Work -> VoiceReplies.WORK
            is VoiceIntent.Target.Query -> place.name
        }
        return when (val r = guidance.startTo(place.copy(name = label))) {
            is Outcome.Success -> {
                navigator.navigate(NavRequest.OpenGuidance)
                VoiceReply(VoiceReplies.started(label, r.value.arrivalEpochMs, ZoneId.systemDefault()))
            }
            is Outcome.Failure -> VoiceReply(VoiceReplies.routeFailed(r.error), ok = false)
        }
    }

    private suspend fun avoid(intent: VoiceIntent.Avoid): VoiceReply {
        val current = prefs.routePreferences.first()
        val next = VoiceReplies.apply(current, intent.option, intent.enabled)
        if (next == current) return VoiceReply(VoiceReplies.avoidAlready(intent.option, intent.enabled))
        return when (val r = guidance.applyPreferences(next)) {
            is Outcome.Success -> {
                val before = r.value.before; val after = r.value.after
                VoiceReply(VoiceReplies.avoidDone(intent.option, intent.enabled, if (before != null && after != null) (after.arrivalEpochMs - before.arrivalEpochMs) / 60_000 else null))
            }
            is Outcome.Failure -> VoiceReply(VoiceReplies.avoidSavedRouteFailed(intent.option, intent.enabled), ok = false)
        }
    }

    private suspend fun report(kind: ReportKind): VoiceReply {
        if (!community.isConfigured.first()) return VoiceReply(VoiceReplies.COMMUNITY_OFF, ok = false)
        val fix = location.lastKnown ?: return VoiceReply(VoiceReplies.NO_POSITION, ok = false)
        return when (val r = community.report(kind, fix.point, fix.bearingDeg)) {
            is Outcome.Success -> VoiceReply(VoiceReplies.reported(kind))
            is Outcome.Failure -> VoiceReply(if (r.error == AppError.RateLimited) VoiceReplies.REPORT_RATE_LIMITED else VoiceReplies.REPORT_FAILED, ok = false)
        }
    }
}

/** Phrases des réponses (pures, testées). Écrites pour être prononcées : pas d'abréviation. */
object VoiceReplies {
    const val HOME = "Domicile"
    const val WORK = "Travail"
    const val NOT_GUIDING = "Aucun guidage en cours."
    const val STOPPED = "Guidage arrêté."
    const val NO_NEXT = "Plus de manœuvre avant l'arrivée."
    const val MUTED = "Annonces coupées. Dites « remets la voix » pour les réentendre."
    const val UNMUTED = "Annonces réactivées."
    const val HOME_UNDEFINED = "Votre domicile n'est pas défini. Touchez « Domicile » sur l'écran d'accueil pour le choisir."
    const val WORK_UNDEFINED = "Votre travail n'est pas défini. Touchez « Travail » sur l'écran d'accueil pour le choisir."
    const val COMMUNITY_OFF = "Les signalements ne sont pas activés : le service communautaire n'est pas configuré."
    const val NO_POSITION = "Position inconnue pour l'instant."
    const val REPORT_RATE_LIMITED = "Un signalement par minute au plus. Réessayez dans un instant."
    const val REPORT_FAILED = "Signalement impossible pour le moment."
    const val HELP = "Vous pouvez dire : rentrer à la maison, emmène-moi à une adresse, évite les péages, signale un accident, " +
        "à quelle heure j'arrive, combien il reste, quelle est la limitation, trouve une station, coupe la voix, arrête le guidage."

    fun failure() = "Désolé, cette commande n'a pas abouti."
    fun unknown(text: String) = if (text.isBlank()) "Je n'ai rien entendu." else "Je n'ai pas compris « $text ». Dites « aide » pour connaître les commandes."
    fun notFound(query: String) = "Je n'ai pas trouvé « $query »."

    fun arrival(etaMs: Long, remainingS: Double, zone: ZoneId) = "Arrivée prévue à ${spokenClock(etaMs, zone)}, dans ${spokenDuration(remainingS)}."
    fun remaining(distanceM: Double, durationS: Double) = "Il reste ${spokenDistance(distanceM)}, environ ${spokenDuration(durationS)}."
    fun next(distanceM: Double, instruction: String) = "Dans ${spokenDistance(distanceM)} : ${instruction.trimEnd('.')}."
    fun speedLimit(kmh: Int?) = if (kmh == null) "La limitation n'est pas connue ici." else "Limitation à $kmh kilomètres heure."
    fun started(label: String, etaMs: Long, zone: ZoneId) = "C'est parti pour $label. Arrivée prévue à ${spokenClock(etaMs, zone)}."
    fun recalculated(etaMs: Long, zone: ZoneId) = "Itinéraire recalculé. Arrivée prévue à ${spokenClock(etaMs, zone)}."
    fun routeFailed(e: AppError) = when (e) {
        AppError.Network, AppError.Timeout -> "Pas de connexion pour calculer l'itinéraire."
        AppError.PermissionDenied -> "Position inconnue : autorisez la localisation."
        AppError.NoResult -> "Aucun itinéraire trouvé."
        else -> "Le calcul de l'itinéraire a échoué."
    }

    fun nearby(c: PoiCategory) = when (c) {
        PoiCategory.FUEL -> "Voici les stations-service autour de vous."
        PoiCategory.CHARGING -> "Voici les bornes de recharge autour de vous."
        PoiCategory.PARKING -> "Voici les parkings autour de vous."
        PoiCategory.REST_AREA -> "Voici les aires de repos autour de vous."
        PoiCategory.RESTAURANT -> "Voici les restaurants autour de vous."
    }

    /** Tournure neutre : « route fermée » est féminin, « bouchon » masculin. */
    fun reported(k: ReportKind) = "Signalement envoyé : ${kindLabel(k)}. Merci !"
    fun kindLabel(k: ReportKind) = when (k) {
        ReportKind.ACCIDENT -> "accident"
        ReportKind.JAM -> "bouchon"
        ReportKind.OBSTACLE -> "obstacle"
        ReportKind.CLOSED -> "route fermée"
        ReportKind.ROADWORK -> "travaux"
        ReportKind.WEATHER -> "danger météo"
        ReportKind.POTHOLE -> "nid-de-poule"
        ReportKind.WORK_LIGHT -> "feu de chantier"
        ReportKind.HAZARD -> "danger"
    }

    fun optionLabel(o: AvoidOption) = when (o) {
        AvoidOption.TOLLS -> "péages"
        AvoidOption.HIGHWAYS -> "autoroutes"
        AvoidOption.TUNNELS -> "tunnels"
        AvoidOption.FERRIES -> "ferries"
    }

    fun apply(p: RoutePreferences, o: AvoidOption, on: Boolean) = when (o) {
        AvoidOption.TOLLS -> p.copy(avoidTolls = on)
        AvoidOption.HIGHWAYS -> p.copy(avoidHighways = on)
        AvoidOption.TUNNELS -> p.copy(avoidTunnels = on)
        AvoidOption.FERRIES -> p.copy(avoidFerries = on)
    }

    fun avoidAlready(o: AvoidOption, on: Boolean) = if (on) "Les ${optionLabel(o)} sont déjà évités." else "Les ${optionLabel(o)} sont déjà autorisés."

    /** [deltaMin] : écart d'heure d'arrivée après recalcul (minutes), null hors guidage. */
    fun avoidDone(o: AvoidOption, on: Boolean, deltaMin: Long?): String {
        val head = if (on) "J'évite les ${optionLabel(o)}." else "Les ${optionLabel(o)} sont de nouveau autorisés."
        return when {
            deltaMin == null -> "$head Ce sera appliqué à vos prochains itinéraires."
            deltaMin == 0L -> "$head Même heure d'arrivée."
            deltaMin > 0 -> "$head $deltaMin ${if (deltaMin == 1L) "minute" else "minutes"} de plus."
            else -> "$head ${-deltaMin} ${if (deltaMin == -1L) "minute" else "minutes"} de moins."
        }
    }

    fun avoidSavedRouteFailed(o: AvoidOption, on: Boolean) =
        (if (on) "J'évite désormais les ${optionLabel(o)}" else "Les ${optionLabel(o)} sont de nouveau autorisés") + ", mais le recalcul a échoué."

    fun spokenClock(epochMs: Long, zone: ZoneId): String {
        val t = java.time.Instant.ofEpochMilli(epochMs).atZone(zone)
        return if (t.minute == 0) "${t.hour} heures" else "${t.hour} heures ${t.minute}"
    }

    fun spokenDuration(seconds: Double): String {
        val min = (seconds / 60).roundToInt().coerceAtLeast(1)
        if (min < 60) return if (min == 1) "1 minute" else "$min minutes"
        val h = min / 60; val m = min % 60
        val hours = if (h == 1) "1 heure" else "$h heures"
        return if (m == 0) hours else "$hours $m"
    }

    fun spokenDistance(meters: Double): String = when {
        meters < 1000 -> "${((meters / 10).roundToInt() * 10).coerceAtLeast(10)} mètres"
        meters < 10_000 -> Formatters.distance(meters).replace(" km", " kilomètres")
        else -> "${(meters / 1000).roundToInt()} kilomètres"
    }
}
