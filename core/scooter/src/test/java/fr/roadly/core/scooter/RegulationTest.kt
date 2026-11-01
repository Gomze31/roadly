package fr.roadly.core.scooter

import fr.roadly.core.scooter.regulation.AreaType
import fr.roadly.core.scooter.regulation.FranceEdpmRegulation
import fr.roadly.core.scooter.regulation.LegalStatus
import fr.roadly.core.scooter.regulation.ScooterRegulations
import fr.roadly.core.scooter.regulation.SegmentFacts
import fr.roadly.core.scooter.segment.WayCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class RegulationTest {
    private fun facts(c: WayCategory, roadClass: String? = "residential", speed: Int? = null, area: AreaType = AreaType.UNKNOWN, sev: String? = null, bicycle: String? = null, access: String? = null, motorroad: Boolean = false) =
        SegmentFacts(c, roadClass, speed, area, sev, bicycle, access, motorroad)
    private fun status(f: SegmentFacts) = FranceEdpmRegulation.assess(f).status

    @Test fun cycleTrackAndGreenwayAllowedEvenOutsideTowns() {
        assertEquals(LegalStatus.ALLOWED, status(facts(WayCategory.CYCLE_TRACK, area = AreaType.RURAL)))
        assertEquals(LegalStatus.ALLOWED, status(facts(WayCategory.GREENWAY, area = AreaType.RURAL)))
    }

    @Test fun cycleTrackWithLocalBanIsForbidden() {
        assertEquals(LegalStatus.FORBIDDEN, status(facts(WayCategory.CYCLE_TRACK, sev = "no")))
    }

    @Test fun motorwayAndMotorroadForbidden() {
        assertEquals(LegalStatus.FORBIDDEN, status(facts(WayCategory.SHARED_ROAD, roadClass = "motorway")))
        assertEquals(LegalStatus.FORBIDDEN, status(facts(WayCategory.SHARED_ROAD, roadClass = "trunk", motorroad = true)))
    }

    @Test fun sharedRoadRules() {
        assertEquals(LegalStatus.ALLOWED, status(facts(WayCategory.SHARED_ROAD, speed = 50, area = AreaType.URBAN)))
        assertEquals(LegalStatus.FORBIDDEN, status(facts(WayCategory.SHARED_ROAD, speed = 70, area = AreaType.URBAN)))
        assertEquals(LegalStatus.FORBIDDEN, status(facts(WayCategory.SHARED_ROAD, speed = 50, area = AreaType.RURAL)))
        // Données insuffisantes : jamais « autorisé ».
        assertEquals(LegalStatus.UNVERIFIED, status(facts(WayCategory.SHARED_ROAD, speed = 50, area = AreaType.UNKNOWN)))
        assertEquals(LegalStatus.UNVERIFIED, status(facts(WayCategory.SHARED_ROAD, speed = null, area = AreaType.URBAN)))
    }

    @Test fun cycleLaneDependsOnAgglomeration() {
        assertEquals(LegalStatus.ALLOWED, status(facts(WayCategory.CYCLE_LANE, area = AreaType.URBAN)))
        assertEquals(LegalStatus.FORBIDDEN, status(facts(WayCategory.CYCLE_LANE, area = AreaType.RURAL)))
        assertEquals(LegalStatus.UNVERIFIED, status(facts(WayCategory.CYCLE_LANE)))
    }

    @Test fun sidewalkStepsAndDismount() {
        assertEquals(LegalStatus.FORBIDDEN, status(facts(WayCategory.SIDEWALK)))
        assertEquals(LegalStatus.NOT_RIDABLE, status(facts(WayCategory.STEPS)))
        assertEquals(LegalStatus.NOT_RIDABLE, status(facts(WayCategory.CYCLE_TRACK, bicycle = "dismount")))
        assertEquals(LegalStatus.FORBIDDEN, status(facts(WayCategory.SHARED_ROAD, access = "private")))
    }

    @Test fun explicitScooterAccessWins() {
        assertEquals(LegalStatus.ALLOWED, status(facts(WayCategory.SIDEWALK, sev = "yes")))
    }

    @Test fun registryDefaultsToFranceAndNeverAssumesOtherCountries() {
        assertEquals("FR", ScooterRegulations.forCountry(null).countryCode)
        val de = ScooterRegulations.forCountry("de")
        assertEquals(LegalStatus.UNVERIFIED, de.assess(facts(WayCategory.CYCLE_TRACK)).status)
        assertEquals(LegalStatus.FORBIDDEN, de.assess(facts(WayCategory.SHARED_ROAD, roadClass = "motorway")).status)
    }
}
