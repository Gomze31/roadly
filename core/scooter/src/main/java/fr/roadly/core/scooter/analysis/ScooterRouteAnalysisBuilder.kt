package fr.roadly.core.scooter.analysis

import fr.roadly.core.model.ElectricScooterProfile
import fr.roadly.core.model.Route
import fr.roadly.core.scooter.geo.RouteLine
import fr.roadly.core.scooter.regulation.ScooterRegulation
import fr.roadly.core.scooter.regulation.ScooterRegulations
import fr.roadly.core.scooter.regulation.UnsupportedCountryRegulation
import fr.roadly.core.scooter.segment.ScooterSegmentClassifier
import fr.roadly.core.scooter.valhalla.TraceAttributesResult

/** Chaîne pure : arêtes Valhalla + tags OSM → segments classés → analyse confort / sécurité. */
object ScooterRouteAnalysisBuilder {
    /**
     * Réglementation applicable : pays unique traversé (`admins`), sinon la France par défaut quand aucun pays
     * n'est renvoyé ; plusieurs pays → règles non intégrées (rien n'est affirmé), sauf si tous sont gérés à l'identique.
     */
    fun regulationFor(countries: List<String>, defaultCountry: String = "FR"): ScooterRegulation = when {
        countries.isEmpty() -> ScooterRegulations.forCountry(defaultCountry)
        countries.size == 1 -> ScooterRegulations.forCountry(countries.first())
        else -> UnsupportedCountryRegulation(countries.joinToString("/"))
    }

    fun build(
        route: Route,
        trace: TraceAttributesResult,
        wayTags: Map<Long, Map<String, String>>?,
        profile: ElectricScooterProfile,
        defaultCountry: String = "FR",
    ): ScooterRouteAnalysis {
        val regulation = regulationFor(trace.countryCodes, defaultCountry)
        val length = RouteLine(route.geometry).lengthM.takeIf { it > 0 } ?: route.distanceMeters
        val segments = ScooterSegmentClassifier.classify(trace.edges, wayTags.orEmpty(), regulation, length)
        return ScooterComfortAnalyzer.analyze(route, segments, profile, regulation.displayName, osmTagsAvailable = wayTags != null)
    }
}
