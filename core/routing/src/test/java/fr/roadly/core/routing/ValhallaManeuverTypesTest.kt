package fr.roadly.core.routing

import fr.roadly.core.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Test

class ValhallaManeuverTypesTest {
    @Test fun `ramps, exits, merges and u-turns keep their side`() {
        mapOf(
            12 to ManeuverType.UTURN_RIGHT, 13 to ManeuverType.UTURN_LEFT,
            17 to ManeuverType.RAMP, 18 to ManeuverType.RAMP_RIGHT, 19 to ManeuverType.RAMP_LEFT,
            20 to ManeuverType.EXIT_RIGHT, 21 to ManeuverType.EXIT_LEFT,
            22 to ManeuverType.CONTINUE, 23 to ManeuverType.KEEP_RIGHT, 24 to ManeuverType.KEEP_LEFT,
            25 to ManeuverType.MERGE, 37 to ManeuverType.MERGE_RIGHT, 38 to ManeuverType.MERGE_LEFT,
            26 to ManeuverType.ROUNDABOUT_ENTER, 27 to ManeuverType.ROUNDABOUT_EXIT,
            1 to ManeuverType.DEPART, 6 to ManeuverType.ARRIVE, 99 to ManeuverType.OTHER,
        ).forEach { (code, type) -> assertEquals("type Valhalla $code", type, ValhallaManeuverTypes.of(code)) }
    }
}
