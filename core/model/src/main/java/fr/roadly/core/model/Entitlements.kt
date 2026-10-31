package fr.roadly.core.model

/** Fonctions de Roadly soumises à l'architecture gratuit / Roadly+. [plus] = réservée au palier Roadly+ quand une boutique est configurée. */
enum class Feature(val plus: Boolean) {
    NAVIGATION(false), SPEED_ALERTS(false), VIGILANCE_ZONES(false), FUEL_PRICES(false), COMMUNITY(false), TROPHIES(false),
    VEHICLE_ADVANCED(true), TOUR_OPTIMIZATION(true), TRIP_HISTORY(true), REPLAY(true), COCKPIT_TINT(true),
}

enum class Tier { FREE, PLUS }

/**
 * Droits de l'appareil. Règle : tant qu'aucune boutique n'est configurée ([storeConfigured] = false), **tout est inclus** :
 * on ne verrouille jamais une fonction derrière un achat impossible. Dès qu'une boutique existe, les fonctions [Feature.plus]
 * exigent le palier [Tier.PLUS].
 */
data class Entitlements(val tier: Tier, val storeConfigured: Boolean) {
    fun includes(feature: Feature): Boolean = tier == Tier.PLUS || !feature.plus || !storeConfigured
    fun locked(feature: Feature): Boolean = !includes(feature)
    val allIncluded: Boolean get() = Feature.entries.all(::includes)

    companion object {
        val NO_STORE = Entitlements(Tier.FREE, storeConfigured = false)
    }
}
