package fr.roadly.core.geocoding

import fr.roadly.core.model.GeoPoint

/**
 * Reconnaît une saisie de coordonnées GPS : degrés décimaux ("48.8566, 2.3522" ou "48,8566 2,3522")
 * ou degrés-minutes-secondes ("48°51'24"N 2°21'08"E", ouest noté W ou O).
 * Renvoie `null` si le texte n'est pas une coordonnée ou si les valeurs sortent des bornes.
 */
object CoordinateParser {
    private val decimal = Regex("""^\s*(-?\d{1,2}(?:[.,]\d+)?)\s*[,; ]\s*(-?\d{1,3}(?:[.,]\d+)?)\s*$""")
    private val dms = Regex(
        """^\s*(\d{1,2})[°\s]\s*(\d{1,2})['′\s]\s*(?:(\d{1,2}(?:[.,]\d+)?)["″\s])?\s*([NS])\s*[,;]?\s*(\d{1,3})[°\s]\s*(\d{1,2})['′\s]\s*(?:(\d{1,2}(?:[.,]\d+)?)["″\s])?\s*([EWO])\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(input: String): GeoPoint? {
        decimal.matchEntire(input)?.let { m ->
            return valid(m.groupValues[1].replace(',', '.').toDouble(), m.groupValues[2].replace(',', '.').toDouble())
        }
        dms.matchEntire(input)?.let { m ->
            val lat = toDecimal(m.groupValues[1], m.groupValues[2], m.groupValues[3]) * if (m.groupValues[4].equals("S", true)) -1 else 1
            val lonSign = if (m.groupValues[8].uppercase() in setOf("W", "O")) -1 else 1
            val lon = toDecimal(m.groupValues[5], m.groupValues[6], m.groupValues[7]) * lonSign
            return valid(lat, lon)
        }
        return null
    }

    /** Convertit degrés / minutes / secondes (secondes éventuellement vides, virgule décimale tolérée) en degrés décimaux. */
    private fun toDecimal(d: String, m: String, s: String): Double =
        d.toDouble() + m.toDouble() / 60 + (s.takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDouble() ?: 0.0) / 3600

    private fun valid(lat: Double, lon: Double): GeoPoint? =
        if (lat in -90.0..90.0 && lon in -180.0..180.0) GeoPoint(lat, lon) else null
}
