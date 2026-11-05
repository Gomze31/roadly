package fr.roadly.core.data.repository

import fr.roadly.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveMemoryCodecTest {
    @Test fun `toll reminders round trip`() {
        val list = listOf(TollReminder(1L, 1L, listOf("A 79"), "FR", 3), TollReminder(2L, 2L, emptyList(), null, 1))
        assertEquals(list, DriveMemoryCodec.decodeTolls(DriveMemoryCodec.encodeTolls(list)))
    }

    @Test fun `unreadable values are treated as absent`() {
        assertTrue(DriveMemoryCodec.decodeTolls("not json").isEmpty())
        assertTrue(DriveMemoryCodec.decodeTolls(null).isEmpty())
        assertNull(DriveMemoryCodec.decodeParked("{"))
        assertNull(DriveMemoryCodec.decodeParked("""{"lat":91.0,"lon":0.0,"at":1}"""))
    }

    @Test fun `parked vehicle round trip`() {
        val v = ParkedVehicle(GeoPoint(43.6, 1.44), 42L, "Capitole")
        assertEquals(v, DriveMemoryCodec.decodeParked(DriveMemoryCodec.encodeParked(v)))
    }

    @Test fun `french free-flow tolls are due 72 hours later, others have no deadline`() {
        assertEquals(1_000L + 72 * 3_600_000L, TollReminder(1, 1_000L, emptyList(), "FR", 1).dueAtEpochMs)
        assertNull(TollReminder(1, 1_000L, emptyList(), "PT", 1).dueAtEpochMs)
    }
}
