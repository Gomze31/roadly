package fr.roadly.core.data.vigilance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VigilanceCsvTest {
    private val header = "date_heure_dernier_changement,date_heure_creation,departement,latitude,longitude,id,direction,equipement,date_installation,type,emplacement,route,longueur_troncon_km,vitesse_poids_lourds_kmh,vitesse_vehicules_legers_kmh"

    @Test fun `parses the official columns and keeps unknown limits null`() {
        val csv = header + "\n" +
            "2018-09-05T12:20:05Z,2018-09-05T12:20:05Z,80,49.95842,2.85479,13598,PARIS VERS LILLE,MORPHO,2003-11-05T00:00:00Z,Radar fixe,HEM MONACU,A1,,,130\n" +
            "2018-06-28T12:24:19Z,2018-06-28T11:06:32Z,57,49.11907,6.20408,12923,\"Vallières, les Bordes\",FARECO,2010-02-12T00:00:00Z,Radar feu rouge,METZ,,,,\n" +
            "bad,bad,00,not-a-number,2.0,99,,,,,,,,,\n"
        val rows = VigilanceCsv.parse(csv, 42L)
        assertEquals(2, rows.size)
        assertEquals("13598", rows[0].id)
        assertEquals(49.95842, rows[0].lat, 1e-9)
        assertEquals(130, rows[0].limitKmh)
        assertEquals("A1", rows[0].road)
        assertEquals("Vallières, les Bordes", rows[1].direction)
        assertNull(rows[1].limitKmh)
        assertTrue(rows.all { it.updatedAt == 42L })
    }

    @Test fun `missing coordinate columns yield nothing`() {
        assertTrue(VigilanceCsv.parse("a,b\n1,2\n", 0L).isEmpty())
    }
}
