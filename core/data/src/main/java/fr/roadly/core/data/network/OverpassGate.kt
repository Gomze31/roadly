package fr.roadly.core.data.network

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Porte d'accès à un serveur public : borne le volume des requêtes qu'un appelant lui envoie.
 * - Un seul tour à la fois ([withTurn]) : les appelants simultanés passent l'un après l'autre, et celui qui attend relit ce que
 *   le précédent vient d'obtenir avant de demander quoi que ce soit (pas de requêtes en double).
 * - Au moins [minIntervalMs] entre la fin d'une requête et le début de la suivante.
 * - Serveur qui demande de ralentir (HTTP 429, 5xx, délai dépassé) : plus aucune requête pendant [backoffMs], doublé à chaque
 *   échec de suite jusqu'à [backoffMaxMs] ; pendant ce retrait les appels sont refusés tout de suite ([AppError.RateLimited]).
 *
 * [nowMs] : horloge monotone (jamais l'heure du téléphone), pilotée par le temps virtuel dans les tests.
 */
open class RequestGate(
    private val nowMs: () -> Long,
    private val minIntervalMs: Long,
    private val backoffMs: Long,
    private val backoffMaxMs: Long,
) {
    private val mutex = Mutex()
    private var lastRequestEndMs: Long? = null
    private var pausedUntilMs: Long? = null
    private var failuresInARow = 0

    /** Un tour : les requêtes y passent par [call], qui applique l'intervalle et le retrait. */
    inner class Turn internal constructor() {
        /** Exécute [request] au prochain créneau ; pendant un retrait, rend [AppError.RateLimited] sans rien envoyer. */
        suspend fun <T> call(request: suspend () -> Outcome<T>): Outcome<T> {
            val now = nowMs()
            pausedUntilMs?.let { if (now < it) return Outcome.Failure(AppError.RateLimited) }
            lastRequestEndMs?.let { last ->
                val wait = (last + minIntervalMs - now).coerceIn(0L, minIntervalMs)
                if (wait > 0L) delay(wait)
            }
            val result = try { request() } finally { lastRequestEndMs = nowMs() }
            when (result) {
                is Outcome.Success -> failuresInARow = 0
                is Outcome.Failure -> backOffAfter(result.error)
            }
            return result
        }
    }

    /** Réserve la porte le temps de [block] : aucun autre appelant ne demande rien au serveur pendant ce temps. */
    suspend fun <T> withTurn(block: suspend Turn.() -> T): T = mutex.withLock { Turn().block() }

    private fun backOffAfter(e: AppError) {
        val slowDown = e == AppError.RateLimited || e == AppError.Timeout || (e is AppError.ProviderUnavailable && (e.httpCode ?: 0) in 500..599)
        if (!slowDown) return
        val pause = (backoffMs shl failuresInARow.coerceAtMost(8)).coerceAtMost(backoffMaxMs)
        failuresInARow++
        pausedUntilMs = nowMs() + pause
    }

    companion object {
        /** Horloge monotone de production (indépendante de l'heure du téléphone). */
        val MONOTONIC: () -> Long = { System.nanoTime() / 1_000_000L }
    }
}

/**
 * Porte commune des appels Overpass du mode trottinette (tags des voies d'un itinéraire, lieux autour d'un point). L'instance
 * publique n'est pas faite pour servir de serveur à une application : le volume est borné ici, selon les mêmes règles que les
 * ralentisseurs (`SpeedBumps.kt`).
 *
 * L'intervalle est court (1 s) parce que ces appels répondent à une demande à l'écran : une recherche d'itinéraire trottinette
 * analyse jusqu'à trois variantes, soit trois requêtes au plus. Les ralentisseurs, demandés en arrière-plan, gardent leurs 10 s.
 */
@Singleton
class OverpassGate(
    nowMs: () -> Long,
    minIntervalMs: Long = MIN_INTERVAL_MS,
) : RequestGate(nowMs, minIntervalMs, BACKOFF_MS, BACKOFF_MAX_MS) {
    @Inject constructor() : this(MONOTONIC)

    companion object {
        /** Écart minimal entre deux requêtes Overpass du mode trottinette. */
        const val MIN_INTERVAL_MS = 1_000L
        /** Retrait après un 429, un 5xx ou un délai dépassé ; mêmes valeurs que les ralentisseurs. */
        const val BACKOFF_MS = 60_000L
        const val BACKOFF_MAX_MS = 900_000L
    }
}
