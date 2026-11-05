package fr.roadly.core.navigation

/**
 * Aides de conduite inspirées des GPS du marché (TomTom, Coyote, Waze), calculées localement à chaque position :
 * - **baisse de limitation** annoncée une fois (« Limitation 70. »), confirmée sur [LIMIT_CONFIRM_TICKS] positions pour ignorer un
 *   changement fugace en bordure de carrefour ; jamais pour une hausse ;
 * - **pause** proposée après [BREAK_AFTER_MS] de conduite (recommandation de la Sécurité routière : une pause toutes les deux heures),
 *   puis toutes les [BREAK_REPEAT_MS] ; un arrêt d'au moins [STOP_RESETS_MS] remet le compteur à zéro.
 * Aucune donnée inventée : une limitation inconnue ne déclenche rien.
 */
class DrivingCoach {
    sealed interface Advice {
        data class LimitDropped(val kmh: Int) : Advice
        data object TakeABreak : Advice
    }

    private var announcedLimit: Int? = null
    private var candidate: Int? = null
    private var candidateTicks = 0

    private var drivingSinceMs: Long? = null
    private var stoppedSinceMs: Long? = null
    private var lastBreakAdviceMs: Long? = null

    /** Une position : [limitKmh] = limitation en vigueur (null = inconnue), [speedKmh] = vitesse mesurée. */
    fun update(nowMs: Long, limitKmh: Int?, speedKmh: Double?): List<Advice> {
        val out = ArrayList<Advice>(1)
        limitAdvice(limitKmh)?.let(out::add)
        if (breakDue(nowMs, speedKmh)) out += Advice.TakeABreak
        return out
    }

    private fun limitAdvice(limit: Int?): Advice? {
        if (limit == null) { candidate = null; candidateTicks = 0; return null }
        if (limit == announcedLimit) { candidate = null; candidateTicks = 0; return null }
        if (limit != candidate) { candidate = limit; candidateTicks = 1 } else candidateTicks++
        if (candidateTicks < LIMIT_CONFIRM_TICKS) return null
        val previous = announcedLimit
        announcedLimit = limit
        candidate = null
        candidateTicks = 0
        // Première limitation connue ou hausse : rien à dire ; baisse : une annonce.
        return if (previous != null && limit < previous) Advice.LimitDropped(limit) else null
    }

    private fun breakDue(now: Long, speedKmh: Double?): Boolean {
        val moving = (speedKmh ?: 0.0) >= MOVING_KMH
        if (!moving) {
            val since = stoppedSinceMs ?: now.also { stoppedSinceMs = it }
            if (now - since >= STOP_RESETS_MS) { drivingSinceMs = null; lastBreakAdviceMs = null }
            return false
        }
        stoppedSinceMs = null
        val start = drivingSinceMs ?: now.also { drivingSinceMs = it }
        if (now - start < BREAK_AFTER_MS) return false
        val last = lastBreakAdviceMs
        if (last != null && now - last < BREAK_REPEAT_MS) return false
        lastBreakAdviceMs = now
        return true
    }

    companion object {
        const val LIMIT_CONFIRM_TICKS = 2
        const val BREAK_AFTER_MS = 2 * 60 * 60 * 1000L
        const val BREAK_REPEAT_MS = 30 * 60 * 1000L
        const val STOP_RESETS_MS = 15 * 60 * 1000L
        const val MOVING_KMH = 10.0
    }
}
