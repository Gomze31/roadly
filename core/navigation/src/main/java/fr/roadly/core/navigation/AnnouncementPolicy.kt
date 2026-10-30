package fr.roadly.core.navigation

import fr.roadly.core.model.Maneuver
import fr.roadly.core.model.RouteProgress
import java.util.Locale
import kotlin.math.roundToInt

enum class Tier { FAR, NEAR, NOW }

data class Announcement(val text: String, val tier: Tier, val maneuverIndex: Int)

/** Seuils d'annonce (m) par classe de vitesse — spec Phase 2 §2. */
internal enum class SpeedClass(val farM: Double, val nearM: Double, val nowM: Double) {
    HIGHWAY(2000.0, 300.0, 40.0),
    ROAD(600.0, 150.0, 40.0),
    CITY(300.0, 80.0, 40.0);

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
    fun next(progress: RouteProgress, maneuvers: List<Maneuver>, speedKmh: Double?, already: Set<Pair<Int, Tier>>): Announcement? {
        val k = progress.nextManeuverIndex
        val m = maneuvers.getOrNull(k) ?: return null
        val d = progress.distanceToNextManeuverM
        val c = SpeedClass.of(speedKmh)
        val tier = when {
            d < c.nowM -> Tier.NOW
            d <= c.nearM -> Tier.NEAR
            d <= c.farM -> Tier.FAR
            else -> return null
        }
        // Position extrapolée (GPS perdu) : jamais d'annonce « maintenant », trop risquée sans fix réel.
        if (tier == Tier.NOW && progress.extrapolated) return null
        if (already.any { (i, t) -> i == k && t.ordinal >= tier.ordinal }) return null
        val text = when (tier) {
            Tier.NOW -> m.verbalPre ?: m.instruction
            Tier.NEAR, Tier.FAR -> SpokenPhrases.inDistance(d, m.verbalAlert ?: m.verbalPre ?: m.instruction)
        }
        if (text.isBlank()) return null
        return Announcement(text, tier, k)
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

    /** < 100 m : arrondi à 10 m (min. 10) ; sinon à 50 m ; ≥ 1 km : kilomètres à 0,1 près. */
    fun distance(meters: Double): String {
        val rounded = if (meters < 100.0) ((meters / 10.0).roundToInt() * 10).coerceAtLeast(10) else (meters / 50.0).roundToInt() * 50
        if (rounded < 1000) return "$rounded mètres"
        val km = (meters / 100.0).roundToInt() / 10.0
        val text = if (km % 1.0 == 0.0) km.toInt().toString() else String.format(Locale.FRANCE, "%.1f", km)
        return "$text ${if (km < 2.0) "kilomètre" else "kilomètres"}"
    }
}
