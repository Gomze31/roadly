package fr.roadly.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** E2 : types des réglages d'affichage du guidage. */
class NavigationDisplayTest {
    @Test fun `the session starts on the chosen view, or north up without automatic rotation`() {
        assertEquals(CameraMode3D.FOLLOW_3D, CameraMode3D.initial(CameraMode3D.FOLLOW_3D, autoRotate = true))
        assertEquals(CameraMode3D.FOLLOW_2D, CameraMode3D.initial(CameraMode3D.FOLLOW_2D, autoRotate = true))
        assertEquals(CameraMode3D.NORTH_UP, CameraMode3D.initial(CameraMode3D.FOLLOW_3D, autoRotate = false))
        assertEquals("ancien réglage « Carte 3D » : migration de la vue au départ", CameraMode3D.FOLLOW_2D, CameraMode3D.default(map3dEnabled = false))
    }

    @Test fun `power save follows the Android saver in auto and is forced otherwise`() {
        assertTrue(PowerSaveMode.AUTO.effective(systemSaver = true))
        assertFalse(PowerSaveMode.AUTO.effective(systemSaver = false))
        assertTrue(PowerSaveMode.ON.effective(systemSaver = false))
        assertFalse(PowerSaveMode.OFF.effective(systemSaver = true))
    }

    @Test fun `pitch presets are 45, 58 and 60 degrees`() {
        assertEquals(listOf(45.0, 58.0, 60.0), PitchPreset.entries.map { it.degrees })
    }
}
