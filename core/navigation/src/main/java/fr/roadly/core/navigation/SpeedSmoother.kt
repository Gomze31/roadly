package fr.roadly.core.navigation

/** Vitesse GPS lissée (moyenne exponentielle) : référence des paliers d'annonce et de l'alerte de dépassement. */
class SpeedSmoother(private val alpha: Double = 0.3) {
    private var value: Double? = null

    fun update(sampleKmh: Double?): Double? {
        if (sampleKmh == null) return value
        val prev = value
        value = if (prev == null) sampleKmh else alpha * sampleKmh + (1 - alpha) * prev
        return value
    }
}
