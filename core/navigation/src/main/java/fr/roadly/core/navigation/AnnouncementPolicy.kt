package fr.roadly.core.navigation

import fr.roadly.core.common.Formatters
import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.ManeuverType
import fr.roadly.core.model.RouteProgress
import java.util.Locale
import kotlin.math.max

enum class Tier { FAR, NEAR, NOW }

data class Announcement(val text: String, val tier: Tier, val maneuverIndex: Int)

/** B5 : NOW → URGENT, NEAR → NORMAL, FAR → LOW. */
internal fun Tier.speechPriority(): SpeechPriority = when (this) {
    Tier.NOW -> SpeechPriority.URGENT
    Tier.NEAR -> SpeechPriority.NORMAL
    Tier.FAR -> SpeechPriority.LOW
}

/** Seuils FAR / NEAR (m) par classe de vitesse — spec Phase 2 §2 ; le seuil NOW dépend du temps (B5, `AnnouncementPolicy.nowThresholdM`). */
internal enum class SpeedClass(val farM: Double, val nearM: Double) {
    HIGHWAY(2000.0, 300.0),
    ROAD(600.0, 150.0),
    CITY(300.0, 80.0);

    companion object {
        fun of(speedKmh: Double?): SpeedClass = when {
            speedKmh == null -> CITY
            speedKmh >= 90.0 -> HIGHWAY
            speedKmh >= 50.0 -> ROAD
            else -> CITY
        }
    }
}

/** Décide quoi annoncer pour la prochaine manœuvre ; pur et sans état (l'anti-répétition est portée par [already]). */
object AnnouncementPolicy {
    /** B5 : « maintenant » à max(40 m, 4 s × vitesse) — 144 m à 130 km/h, jamais sauté entre deux fixes à 1 Hz. */
    internal fun nowThresholdM(speedKmh: Double?): Double =
        max(GuidanceConstants.NOW_MIN_M, GuidanceConstants.NOW_LEAD_S * (speedKmh ?: 0.0) / 3.6)

    /** [sincePreviousManeuverMs] = temps depuis le passage de la manœuvre précédente (null = aucune depuis le départ) : pas de FAR avant 10 s. */
    fun next(
        progress: RouteProgress,
        maneuvers: List<Maneuver>,
        speedKmh: Double?,
        already: Set<Pair<Int, Tier>>,
        sincePreviousManeuverMs: Long? = null,
    ): Announcement? {
        val k = progress.nextManeuverIndex
        val m = maneuvers.getOrNull(k) ?: return null
        val d = progress.distanceToNextManeuverM
        val c = SpeedClass.of(speedKmh)
        val tier = when {
            d < nowThresholdM(speedKmh) -> Tier.NOW
            d <= c.nearM -> Tier.NEAR
            d <= c.farM -> Tier.FAR
            else -> return null
        }
        // Position extrapolée (GPS perdu) : jamais d'annonce « maintenant », trop risquée sans fix réel.
        if (tier == Tier.NOW && progress.extrapolated) return null
        if (tier == Tier.FAR && sincePreviousManeuverMs != null && sincePreviousManeuverMs < GuidanceConstants.FAR_QUIET_AFTER_MANEUVER_MS) return null
        if (already.any { (i, t) -> i == k && t.ordinal >= tier.ordinal }) return null
        val text = when (tier) {
            Tier.NOW -> m.verbalPre ?: m.instruction
            Tier.NEAR, Tier.FAR -> SpokenPhrases.inDistance(d, m.verbalAlert ?: m.verbalPre ?: m.instruction)
        }
        if (text.isBlank()) return null
        return Announcement(if (tier == Tier.FAR) text else chained(text, progress, maneuvers, k), tier, k)
    }

    /** B5 : manœuvre suivante à moins de 200 m → « …, puis … » (sauf la sortie du rond-point qu'on aborde, déjà dite par l'entrée). */
    private fun chained(text: String, progress: RouteProgress, maneuvers: List<Maneuver>, k: Int): String {
        val following = maneuvers.getOrNull(k + 1) ?: return text
        val gap = (progress.distancesToManeuversM.firstOrNull() ?: return text) - progress.distanceToNextManeuverM
        if (gap >= GuidanceConstants.COMBINE_NEXT_MANEUVER_M || following.type == ManeuverType.ROUNDABOUT_EXIT) return text
        val then = (following.verbalPre ?: following.instruction).trim()
        return if (then.isEmpty()) text else SpokenPhrases.then(text, then)
    }
}

/** Phrases prononcées composées par la logique JVM (exception assumée à la règle « strings en ressources »). */
internal object SpokenPhrases {
    const val REROUTING = "Recalcul de l'itinéraire."

    /** Entrée en zone de vigilance : jamais de position exacte, seulement la zone et sa limitation si connue. */
    fun vigilanceZone(limitKmh: Int?): String =
        if (limitKmh != null) "Zone de vigilance, limitation à $limitKmh." else "Zone de vigilance."

    fun inDistance(meters: Double, instruction: String): String =
        "Dans ${distance(meters)}, ${instruction.replaceFirstChar { it.lowercase(Locale.FRANCE) }}"

    /** « Tournez à droite dans X, puis tournez à gauche dans Y. » */
    fun then(first: String, second: String): String =
        "${first.trimEnd().removeSuffix(".")}, puis ${second.replaceFirstChar { it.lowercase(Locale.FRANCE) }}"

    /** Arrondi partagé avec l'écran (`Formatters.roundManeuverMeters`, B2) ; ≥ 1 km : kilomètres à 0,1 près. */
    fun distance(meters: Double): String {
        val rounded = Formatters.roundManeuverMeters(meters)
        if (rounded < 1000) return "$rounded mètres"
        val km = rounded / 1000.0
        val text = if (km % 1.0 == 0.0) km.toInt().toString() else String.format(Locale.FRANCE, "%.1f", km)
        return "$text ${if (km < 2.0) "kilomètre" else "kilomètres"}"
    }
}
