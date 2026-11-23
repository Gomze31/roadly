package fr.roadly.core.navigation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.roadly.core.common.Outcome
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.data.repository.VehicleProfileRepository
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import fr.roadly.core.routing.RouteAlternativesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/** Issue d'une tentative de reprise automatique du guidage ([GuidanceRestorer.attempt]). */
enum class RestoreOutcome {
    /** Le guidage a repris vers la destination mémorisée. */
    RESUMED,
    /** Aucune course mémorisée : le dernier guidage s'était terminé normalement (arrêt, arrivée). */
    NOTHING_TO_RESUME,
    /** Course mémorisée trop ancienne ([GuidanceRestorer.MAX_AGE_MS]) ou datée du futur (heure du téléphone changée). */
    TOO_OLD,
    NO_PERMISSION,
    /** Trottinette ou vélo : la reprise calcule un itinéraire automobile, elle est refusée comme sur l'accueil. */
    LIGHT_VEHICLE,
    /** Trop de reprises automatiques récentes ([RestoreBudgetRule]) : arrêts du processus en boucle. */
    TOO_MANY_RESTORES,
    /** Pas de position récente dans le délai [GuidanceRestorer.POSITION_TIMEOUT_MS]. */
    NO_POSITION,
    ROUTE_FAILED,
    /** Un guidage ou une conduite libre a démarré entre-temps (l'utilisateur a rouvert l'application) : rien n'est remplacé. */
    SUPERSEDED,
}

/**
 * NAV-1 : reprise du guidage quand Android recrée [NavigationService] dans un nouveau processus (l'ancien a été arrêté par le
 * téléphone, écran éteint le plus souvent). Même calcul que la carte « Reprendre le guidage » de l'accueil et que
 * [GuidanceActions.startTo] : un seul itinéraire, le plus rapide, de la position actuelle vers la destination mémorisée
 * (`LastGuidance`), avec les préférences et le véhicule du moment. Le tracé précédent et ses arrêts ne sont pas repris.
 *
 * Tout refus laisse la course mémorisée en place : la carte de l'accueil reste proposée. La conduite libre n'est pas reprise (elle
 * n'a pas de destination).
 *
 * NON VÉRIFIÉ hors téléphone : qu'Android (12 à 15, MagicOS en particulier) recrée bien un service « sticky » après avoir arrêté
 * le processus, et qu'il laisse alors ce service de premier plan lire la position sans que l'application soit à l'écran. Si la
 * position est refusée, la tentative finit en [RestoreOutcome.NO_POSITION] et le service s'arrête.
 */
class GuidanceRestorer @Inject constructor(
    private val session: NavigationSession,
    private val freeDrive: FreeDriveSession,
    private val actions: GuidanceActions,
    private val alternatives: RouteAlternativesUseCase,
    private val location: LocationRepository,
    private val prefs: UserPreferencesRepository,
    private val vehicles: VehicleProfileRepository,
    private val clock: NavigationClock,
    private val budget: RestoreBudget,
) {
    /** Vrai si rien n'est en cours : ni guidage (ou son écran d'arrivée / d'échec), ni conduite libre. */
    fun idle(): Boolean = session.state.value is NavigationState.Idle && freeDrive.state.value !is FreeDriveState.Active

    /** Une tentative, jamais relancée d'elle-même. Ne lève pas d'exception (hors annulation). */
    suspend fun attempt(): RestoreOutcome {
        var outcome = RestoreOutcome.ROUTE_FAILED
        var positionRequested = false
        try {
            outcome = run {
                if (!idle()) return@run RestoreOutcome.SUPERSEDED
                val last = prefs.lastGuidance.first() ?: return@run RestoreOutcome.NOTHING_TO_RESUME
                val now = clock.nowMs()
                if (now - last.startedAtEpochMs !in 0..MAX_AGE_MS) return@run RestoreOutcome.TOO_OLD
                if (location.permissionState.value != LocationPermissionState.GRANTED) return@run RestoreOutcome.NO_PERMISSION
                if (actions.lightVehicleMode()) return@run RestoreOutcome.LIGHT_VEHICLE
                if (!budget.tryTake(now)) return@run RestoreOutcome.TOO_MANY_RESTORES
                // Au repos, le flux de positions n'émet rien (mode OFF) : la position est demandée comme pendant un guidage.
                positionRequested = true
                location.setGuidanceActive(true)
                val origin = withTimeoutOrNull(POSITION_TIMEOUT_MS) { location.fixes.first { recent(it) } }?.point
                    ?: return@run RestoreOutcome.NO_POSITION
                if (!idle()) return@run RestoreOutcome.SUPERSEDED
                val p = prefs.routePreferences.first()
                val v = vehicles.profile.first()
                when (val r = alternatives.fastest(origin, last.destination, v, clock.nowMs(), p)) {
                    is Outcome.Failure -> RestoreOutcome.ROUTE_FAILED
                    is Outcome.Success -> {
                        if (!idle()) return@run RestoreOutcome.SUPERSEDED
                        actions.start(r.value, p, v, emptyList(), last.destinationName)
                        if (session.state.value is NavigationState.Guiding) RestoreOutcome.RESUMED else RestoreOutcome.ROUTE_FAILED
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Réglages illisibles, erreur inattendue du calcul : pas de reprise, la carte de l'accueil reste.
            outcome = RestoreOutcome.ROUTE_FAILED
        } finally {
            // Position relâchée si la reprise n'a pas eu lieu, sauf si quelqu'un d'autre s'en sert désormais.
            if (positionRequested && outcome != RestoreOutcome.RESUMED && idle()) location.setGuidanceActive(false)
        }
        return outcome
    }

    /** Position mesurée il y a moins de [MAX_FIX_AGE_MS] ; une source sans instant monotone (rejeu) est acceptée telle quelle. */
    /** Position utilisable comme départ : récente, et pas grossière (NAV-3 : le guidage ignorerait ensuite toute position de ce genre). */
    private fun recent(fix: LocationFix): Boolean =
        fix.accuracyM <= GuidanceConstants.UNUSABLE_FIX_ACCURACY_M && (fix.elapsedRealtimeMs?.let { clock.elapsedMs() - it <= MAX_FIX_AGE_MS } ?: true)

    companion object {
        /**
         * Âge maximal de la course mémorisée (compté depuis son départ ou son dernier changement d'itinéraire). Six heures couvrent
         * un long trajet d'une traite ; au-delà, un guidage qui se remettrait à parler seul surprendrait plus qu'il n'aiderait
         * (guidage oublié la veille). La carte de l'accueil, elle, reste proposée sans limite d'âge.
         */
        const val MAX_AGE_MS = 6 * 60 * 60 * 1000L
        /** Attente d'une position : au-delà (tunnel, parking, position refusée en arrière-plan), la reprise est abandonnée. */
        const val POSITION_TIMEOUT_MS = 20_000L
        /** Une position plus ancienne (dernière position connue du téléphone) donnerait un itinéraire depuis un endroit déjà quitté. */
        const val MAX_FIX_AGE_MS = 30_000L
    }
}

/** Compte les reprises automatiques pour qu'un processus arrêté en boucle ne relance pas un calcul d'itinéraire à chaque fois. */
interface RestoreBudget {
    /** Vrai si une reprise est permise à [nowMs] ; elle est alors comptée, avant même d'être tentée. */
    fun tryTake(nowMs: Long): Boolean
}

/** Règle du budget (Kotlin pur) : au plus [MAX_RESTORES] reprises par [WINDOW_MS]. */
object RestoreBudgetRule {
    const val MAX_RESTORES = 3
    const val WINDOW_MS = 60 * 60 * 1000L

    /**
     * Instants à garder après une reprise à [nowMs], ou null si elle est refusée. Un instant daté du futur (heure du téléphone
     * reculée) compte comme récent : dans le doute, on ne reprend pas.
     */
    fun take(previous: List<Long>, nowMs: Long): List<Long>? {
        val recent = previous.filter { it > nowMs - WINDOW_MS }
        return if (recent.size >= MAX_RESTORES) null else recent + nowMs
    }
}

/** Budget conservé dans un petit fichier de préférences privé : il doit survivre à l'arrêt du processus. */
class PrefsRestoreBudget @Inject constructor(@param:ApplicationContext private val ctx: Context) : RestoreBudget {
    @Synchronized override fun tryTake(nowMs: Long): Boolean = try {
        val store = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val previous = store.getString(KEY, "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }
        val next = RestoreBudgetRule.take(previous, nowMs)
        // `commit` : écrit avant la tentative, pour qu'une reprise qui fait tomber le processus soit comptée.
        next != null && store.edit().putString(KEY, next.joinToString(",")).commit()
    } catch (e: RuntimeException) {
        false
    }

    private companion object {
        const val FILE = "roadly_guidance_restore"
        const val KEY = "restored_at"
    }
}
