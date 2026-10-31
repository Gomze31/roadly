package fr.roadly.core.model

/** Profil communautaire (serveur) : pseudonyme d'appareil et points calculés côté serveur. */
data class CommunityProfile(val pseudonym: String, val points: Int)

/** Ligne de classement (serveur) : pseudonyme public, points, rang. */
data class LeaderboardEntry(val pseudonym: String, val points: Int, val rank: Int)

/** Trophée local, calculé d'après l'historique des trajets de l'appareil. */
data class Trophy(val id: String, val unlocked: Boolean, val progress: Double, val target: Double)

object Trophies {
    /** Identifiants stables : km cumulés, nombre de trajets, trajet le plus long. */
    const val KM_10 = "km_10"; const val KM_100 = "km_100"; const val KM_1000 = "km_1000"
    const val TRIPS_1 = "trips_1"; const val TRIPS_10 = "trips_10"; const val TRIPS_100 = "trips_100"
    const val LONGEST_50 = "longest_50"; const val LONGEST_200 = "longest_200"

    fun compute(trips: List<Trip>): List<Trophy> {
        val km = trips.sumOf { it.distanceM } / 1000.0
        val n = trips.size.toDouble()
        val longest = (trips.maxOfOrNull { it.distanceM } ?: 0.0) / 1000.0
        fun t(id: String, value: Double, target: Double) = Trophy(id, value >= target, value.coerceAtMost(target), target)
        return listOf(
            t(TRIPS_1, n, 1.0), t(KM_10, km, 10.0), t(TRIPS_10, n, 10.0), t(LONGEST_50, longest, 50.0),
            t(KM_100, km, 100.0), t(TRIPS_100, n, 100.0), t(LONGEST_200, longest, 200.0), t(KM_1000, km, 1000.0),
        )
    }
}
