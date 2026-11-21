package fr.roadly.core.data.network

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Priorité d'une requête dans la file du [ValhallaRateLimiter] : à l'ouverture d'un créneau, la requête la plus prioritaire
 * passe d'abord, puis la plus ancienne. Sans effet sur un hôte non limité.
 */
enum class ApiPriority {
    /** Recalcul en cours de guidage : ne doit pas attendre derrière les requêtes d'arrière-plan. */
    GUIDANCE,
    /** Demande directe de l'utilisateur (recherche d'itinéraire, ordre des étapes). */
    INTERACTIVE,
    /** Enrichissements (profil du tracé, carrefours, limitations, analyse trottinette) : valeur par défaut. */
    BACKGROUND,
}

/**
 * Limiteur de débit unique du processus pour les instances publiques FOSSGIS (`*.openstreetmap.de`, dont
 * `valhalla1.openstreetmap.de`). Conditions d'usage (https://fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen,
 * relues le 2026-11-21) : « Maximal eine Anfrage pro Sekunde », aucune garantie de disponibilité.
 *
 * Garanties :
 * - une seule requête en vol à la fois (sérialisation) ;
 * - au moins [minIntervalMs] entre deux débuts de requête ;
 * - [pause] (en-tête `Retry-After`, HTTP 429 / 503) repousse le prochain créneau ; si l'attente imposée dépasse
 *   [maxPauseWaitMs], les requêtes sont refusées tout de suite au lieu de rester en file ;
 * - une attente annulée libère sa place sans consommer de créneau.
 *
 * Limite connue : la priorité joue entre requêtes en attente ; une requête déjà partie n'est pas interrompue, un recalcul
 * attend donc au plus la fin de la requête en vol et le reste de l'intervalle.
 *
 * Kotlin pur : l'horloge [nowMs] (monotone) et l'attente (`delay` des coroutines) se pilotent en test par le temps virtuel.
 * Un hôte auto-hébergé n'est pas concerné ([appliesTo] faux) : [ApiClient] ne passe alors pas par le limiteur.
 */
class ValhallaRateLimiter(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val maxPauseWaitMs: Long = DEFAULT_MAX_PAUSE_WAIT_MS,
    private val throttledHost: (String) -> Boolean = ::isPublicFossgisHost,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    /** Résultat de [withPermit]. */
    sealed interface Permit<out T> {
        data class Ran<T>(val value: T) : Permit<T>
        /** Pause imposée par le serveur trop longue pour attendre : [httpCode] (429 ou 503) est le code qui l'a déclenchée. */
        data class Refused(val httpCode: Int, val remainingMs: Long) : Permit<Nothing>
    }

    private class Waiter(val priority: ApiPriority, val seq: Long) {
        val wake = Channel<Unit>(Channel.CONFLATED)
    }

    private val lock = Any()
    private val waiters = ArrayList<Waiter>()
    private var seq = 0L
    private var busy = false
    private var nextStartMs = NEVER
    private var pausedUntilMs = NEVER
    private var pauseHttpCode = 429

    /** Vrai si l'hôte est une instance publique à débit limité. */
    fun appliesTo(host: String): Boolean = throttledHost(host)

    /** Exécute [block] dans le prochain créneau libre, ou renvoie [Permit.Refused] sans l'exécuter pendant une longue pause. */
    suspend fun <T> withPermit(priority: ApiPriority = ApiPriority.BACKGROUND, block: suspend () -> T): Permit<T> {
        acquire(priority)?.let { return it }
        try {
            return Permit.Ran(block())
        } finally {
            synchronized(lock) { busy = false; wakeAll() }
        }
    }

    /**
     * Pause demandée par le serveur (`Retry-After`) ou appliquée par défaut après un 429 / 503 : aucun créneau avant
     * [durationMs] (bornée à [MAX_PAUSE_MS]). Une pause plus courte que la pause en cours est ignorée.
     */
    fun pause(durationMs: Long, httpCode: Int) {
        synchronized(lock) {
            val until = nowMs() + durationMs.coerceIn(0L, MAX_PAUSE_MS)
            if (until > pausedUntilMs) { pausedUntilMs = until; pauseHttpCode = httpCode }
        }
    }

    /** Null = créneau obtenu (à rendre par [withPermit]) ; sinon le refus. */
    private suspend fun acquire(priority: ApiPriority): Permit.Refused? {
        val me = synchronized(lock) { Waiter(priority, seq++).also { waiters += it } }
        try {
            while (true) {
                var waitMs = WAIT_FOR_SIGNAL
                synchronized(lock) {
                    val now = nowMs()
                    val pauseLeft = pausedUntilMs - now
                    if (pauseLeft > maxPauseWaitMs) return Permit.Refused(pauseHttpCode, pauseLeft)
                    if (!busy && head() === me) {
                        val startAt = maxOf(nextStartMs, pausedUntilMs)
                        if (startAt <= now) {
                            busy = true
                            waiters.remove(me)
                            nextStartMs = now + minIntervalMs
                            return null
                        }
                        waitMs = (startAt - now).coerceAtLeast(1L)
                    }
                }
                // Tête de file : attendre l'heure du créneau (ou un changement d'état). Sinon : attendre un signal.
                if (waitMs == WAIT_FOR_SIGNAL) me.wake.receive() else withTimeoutOrNull(waitMs) { me.wake.receive() }
            }
        } finally {
            // Annulation ou refus : quitter la file et laisser la tête suivante armer son attente.
            synchronized(lock) { if (waiters.remove(me)) wakeAll() }
        }
    }

    private fun head(): Waiter? = waiters.minWithOrNull(compareBy<Waiter> { it.priority.ordinal }.thenBy { it.seq })

    private fun wakeAll() { waiters.forEach { it.wake.trySend(Unit) } }

    companion object {
        /** FOSSGIS : au plus une requête par seconde. */
        const val DEFAULT_MIN_INTERVAL_MS = 1_000L
        /** Au-delà de cette attente imposée par le serveur, mieux vaut échouer tout de suite que bloquer l'appelant. */
        const val DEFAULT_MAX_PAUSE_WAIT_MS = 10_000L
        /** Pause appliquée après un 429 / 503 sans `Retry-After`. */
        const val DEFAULT_BACKOFF_MS = 5_000L
        /** Borne d'un `Retry-After` aberrant. */
        const val MAX_PAUSE_MS = 3_600_000L

        private const val WAIT_FOR_SIGNAL = -1L
        /** « Jamais » sans risque de dépassement arithmétique (l'horloge monotone peut être négative). */
        private const val NEVER = Long.MIN_VALUE / 2

        /** Instances publiques FOSSGIS : `openstreetmap.de` et ses sous-domaines. Tout autre hôte est considéré auto-hébergé. */
        fun isPublicFossgisHost(host: String): Boolean {
            val h = host.lowercase().trimEnd('.')
            return h == "openstreetmap.de" || h.endsWith(".openstreetmap.de")
        }
    }
}
