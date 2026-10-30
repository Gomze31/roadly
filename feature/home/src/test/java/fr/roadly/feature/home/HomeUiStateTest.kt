package fr.roadly.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test fun compass_only_when_map_is_rotated() {
        assertFalse(HomeUiState(mapBearingDeg = 0.0).isRotated)
        assertFalse(HomeUiState(mapBearingDeg = 1.5).isRotated)
        assertFalse(HomeUiState(mapBearingDeg = 359.0).isRotated)
        assertFalse(HomeUiState(mapBearingDeg = -1.0).isRotated)
        assertTrue(HomeUiState(mapBearingDeg = 45.0).isRotated)
        assertTrue(HomeUiState(mapBearingDeg = -90.0).isRotated)
        assertTrue(HomeUiState(mapBearingDeg = 180.0).isRotated)
    }
}
