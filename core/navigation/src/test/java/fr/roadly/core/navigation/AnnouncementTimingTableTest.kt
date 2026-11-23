package fr.roadly.core.navigation

import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.RouteProgress
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * Table des délais d'annonce (docs/GUIDAGE-INTELLIGENT.md §10.2) : pour chaque vitesse, combien de secondes avant le point de
 * manœuvre part chaque palier, avec une position par seconde. Le vrai [AnnouncementPolicy] est interrogé seconde par seconde, pour
 * toutes les phases possibles de l'échantillonnage (la première position peut tomber n'importe où dans la seconde).
 *
 * Ce test **mesure** et écrit la table dans `build/announcement-timing.txt` ; il ne fixe qu'un plancher. Il ne dit rien de la
 * latence de la synthèse vocale ni de la durée de la phrase, qui ne se mesurent que sur le téléphone.
 */
class AnnouncementTimingTableTest {
    private val maneuvers = ParisRoute.route().maneuvers
    private fun progress(d: Double) = RouteProgress("paris", 0, 0.0, 0.0, 1, d, 5_000.0, 300.0, GeoPoint(48.85, 2.35), null)

    /** Délais (s) minimal et maximal de chaque palier à [kmh], sur 20 phases d'échantillonnage. */
    private fun leads(kmh: Double): Map<Tier, Pair<Double, Double>> {
        val v = kmh / 3.6
        val out = HashMap<Tier, Pair<Double, Double>>()
        for (phase in 0 until 20) {
            val said = HashSet<Pair<Int, Tier>>()
            var d = 3_000.0 + v * phase / 20.0
            while (d > 0) {
                AnnouncementPolicy.next(progress(d), maneuvers, kmh, said)?.let { a ->
                    said += 1 to a.tier
                    val lead = d / v
                    val (lo, hi) = out[a.tier] ?: (lead to lead)
                    out[a.tier] = minOf(lo, lead) to maxOf(hi, lead)
                }
                d -= v
            }
        }
        return out
    }

    @Test fun `lead time of each announcement tier at 30, 50, 90, 110 and 130 km h`() {
        val lines = ArrayList<String>()
        lines += "km/h | FAR (s)        | NEAR (s)       | NOW (s)       | NOW (m)"
        for (kmh in listOf(30.0, 50.0, 90.0, 110.0, 130.0)) {
            val l = leads(kmh)
            fun cell(t: Tier) = l[t]?.let { String.format(Locale.ROOT, "%5.1f – %5.1f", it.first, it.second) } ?: "      —      "
            val now = l.getValue(Tier.NOW)
            lines += String.format(Locale.ROOT, "%4.0f | %s | %s | %s | %3.0f – %3.0f", kmh, cell(Tier.FAR), cell(Tier.NEAR), cell(Tier.NOW), now.first * kmh / 3.6, now.second * kmh / 3.6)
            // Plancher : « maintenant » part au moins 2,9 s avant le point de manœuvre, à toute vitesse et toute phase.
            assertTrue("$kmh km/h : NOW à ${now.first} s", now.first >= 2.9)
            // L'annonce précédente (NEAR) laisse au moins 5 s de plus pour se placer.
            assertTrue("$kmh km/h : NEAR à ${l.getValue(Tier.NEAR).first} s", l.getValue(Tier.NEAR).first >= 5.0)
        }
        File("build").mkdirs()
        File("build/announcement-timing.txt").writeText(lines.joinToString("\n"))
    }
}
