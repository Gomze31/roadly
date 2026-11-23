package fr.roadly.core.navigation

import android.os.SystemClock
import fr.roadly.core.model.LocationFix
import javax.inject.Inject

/**
 * Horloge injectable, à deux bases de temps (NAV-2) :
 * - [nowMs] : heure calendaire (epoch ms), pour ce qui est montré ou enregistré (heure d'arrivée, début d'un trajet, course mémorisée) ;
 * - [elapsedMs] : temps monotone, pour toute durée (âge d'un fix, silence du GPS, intervalle entre deux recalculs). L'heure du
 *   téléphone peut être fausse ou changer en roulant (réglage, synchronisation réseau) : une durée ne doit jamais en dépendre.
 *
 * En test, une seule lambda de temps virtuel sert aux deux ([elapsedMs] rend [nowMs] par défaut).
 */
fun interface NavigationClock {
    fun nowMs(): Long

    fun elapsedMs(): Long = nowMs()
}

/** Horloge de production : `System.currentTimeMillis()` et `SystemClock.elapsedRealtime()` (même base que `Location.elapsedRealtimeNanos`). */
class SystemNavigationClock @Inject constructor() : NavigationClock {
    override fun nowMs(): Long = System.currentTimeMillis()

    override fun elapsedMs(): Long = SystemClock.elapsedRealtime()
}

/**
 * Vrai si [fix] date d'avant le démarrage de la course (moins [GuidanceConstants.STALE_FIX_TOLERANCE_MS]) : rejeu périmé du flux de
 * localisation. L'âge est mesuré sur l'horloge monotone quand le fix la porte ([startedElapsedMs]) ; l'heure du téléphone
 * ([startedWallMs]) ne sert que pour une source sans instant monotone (rejeu d'un trajet, doublures de test), qui horodate
 * elle-même avec l'heure du téléphone. Comparer l'heure GNSS d'un fix à l'heure du téléphone rejetait tous les fixes d'un
 * téléphone en avance de quelques secondes.
 */
internal fun LocationFix.isStale(startedElapsedMs: Long, startedWallMs: Long): Boolean {
    val elapsed = elapsedRealtimeMs
    return if (elapsed != null) {
        elapsed < startedElapsedMs - GuidanceConstants.STALE_FIX_TOLERANCE_MS
    } else {
        timestampMs < startedWallMs - GuidanceConstants.STALE_FIX_TOLERANCE_MS
    }
}
