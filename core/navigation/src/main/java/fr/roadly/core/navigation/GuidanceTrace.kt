package fr.roadly.core.navigation

import fr.roadly.core.common.AppError

/**
 * Journal du guidage pour la validation sur téléphone (docs/VALIDATION-TELEPHONE.md) : une ligne par changement utile — manœuvre
 * visée, situation, décompte du rond-point, recalcul, GPS. Lu avec `adb logcat -s RoadlyGuidage`, il permet de comparer ce que
 * l'écran et la voix ont fait avec ce que le guidage a décidé, et de retrouver l'instant exact d'un problème. Kotlin pur.
 */
object GuidanceTrace {
    const val TAG = "RoadlyGuidage"

    /** Ce qui, en changeant, mérite une ligne. Les distances n'en font pas partie : elles changent à chaque position. */
    private data class Key(
        val state: String, val routeId: String?, val maneuver: Int?, val mode: GuidanceMode?, val rerouting: Boolean, val gpsLost: Boolean,
        val roundabout: RoundaboutPhase?, val exitsRemaining: Int?, val confidence: RoundaboutConfidence?,
    )

    private fun key(s: NavigationState): Key = when (s) {
        is NavigationState.Guiding -> Key(
            "GUIDING", s.route.id, s.progress.nextManeuverIndex, s.situation.mode, s.rerouting, s.gpsLost,
            s.roundaboutLive?.phase, s.roundaboutLive?.exitsRemaining, s.roundaboutLive?.confidence,
        )
        NavigationState.Idle -> Key("IDLE", null, null, null, false, false, null, null, null)
        is NavigationState.Arrived -> Key("ARRIVED", s.route.id, null, null, false, false, null, null, null)
        is NavigationState.Failed -> Key("FAILED", null, null, null, false, false, null, null, null)
    }

    /** Ligne à écrire pour le passage de [previous] à [current], ou null si rien d'utile n'a changé. */
    fun line(previous: NavigationState?, current: NavigationState): String? {
        if (previous != null && key(previous) == key(current)) return null
        return when (current) {
            is NavigationState.Guiding -> {
                val p = current.progress
                val m = current.route.maneuvers.getOrNull(p.nextManeuverIndex)
                val r = current.roundaboutLive
                buildString {
                    append("manœuvre=").append(p.nextManeuverIndex).append(' ').append(m?.type)
                    append(" dans=").append(p.distanceToNextManeuverM.toInt()).append("m")
                    append(" mode=").append(current.situation.mode)
                    append(" vitesse=").append(current.speedKmh?.toInt() ?: "?").append("km/h")
                    append(" précision=").append(p.accuracyM.toInt()).append("m")
                    if (p.extrapolated) append(" extrapolé")
                    if (r != null) {
                        append(" rond-point=").append(r.phase).append(" reste=").append(r.exitsRemaining ?: "?")
                        append(" confiance=").append(r.confidence)
                    }
                    if (current.rerouting) append(" RECALCUL")
                    if (current.gpsLost) append(" GPS-PERDU")
                }
            }
            NavigationState.Idle -> "arrêt"
            is NavigationState.Arrived -> "arrivée"
            is NavigationState.Failed -> "échec ${errorLabel(current.error)}"
        }
    }

    /**
     * Erreur telle qu'elle est écrite au journal (SEC-9) : sa classe et, s'il y en a un, le code HTTP. Jamais le contenu — corps de
     * réponse d'un serveur (jusqu'à 300 caractères dans [AppError.Http]), détail d'analyse ou message d'exception — : le journal
     * d'une version publiée est lisible par d'autres outils du téléphone.
     */
    internal fun errorLabel(error: AppError): String = when (error) {
        is AppError.Http -> "Http ${error.code}"
        is AppError.ProviderUnavailable -> error.httpCode?.let { "ProviderUnavailable $it" } ?: "ProviderUnavailable"
        AppError.Network -> "Network"
        AppError.Timeout -> "Timeout"
        AppError.RateLimited -> "RateLimited"
        AppError.PermissionDenied -> "PermissionDenied"
        AppError.NoResult -> "NoResult"
        is AppError.Parse -> "Parse"
        is AppError.Unknown -> "Unknown"
    }
}
