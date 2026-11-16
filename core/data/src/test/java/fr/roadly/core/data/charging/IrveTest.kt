package fr.roadly.core.data.charging

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Réponses réelles de l'API tabulaire (base nationale consolidée du PAN, Toulouse, 2026-11-16). */
class IrveTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    @Test fun `real page is parsed and charge points are grouped by station`() {
        val (rows, more) = Irve.parsePage(json, fixture("irve_toulouse.json"))!!
        assertEquals(20, rows.size)
        assertTrue("30 au total : une page suivante", more)
        val stations = Irve.group(rows)
        val ionity = stations.single { it.name.startsWith("IONITY") }
        assertEquals(350, ionity.maxKw)
        assertEquals(6, ionity.chargePoints)
        assertEquals(6, ionity.pdcIds.size)
        assertTrue(Connector.CCS in ionity.connectors)
        assertTrue(stations.all { it.maxKw >= 50 })
        // Tarif rédigé sur plusieurs lignes par l'opérateur : ramené sur une ligne.
        assertTrue(stations.mapNotNull { it.price }.none { '\n' in it })
    }

    @Test fun `published status of the same charge points is read with its timestamp`() {
        val st = Irve.parseStatus(json, fixture("irve_status_toulouse.json"))!!
        assertEquals(10, st.size)
        val down = st.getValue("FRIOYE477204")
        assertEquals(false, down.inService)
        assertEquals(Occupancy.UNKNOWN, down.occupancy)
        val busy = st.getValue("FRIOYE477202")
        assertEquals(true, busy.inService)
        assertEquals(Occupancy.OCCUPIED, busy.occupancy)
        assertEquals(java.time.Instant.parse("2026-11-15T16:31:10.939Z").toEpochMilli(), busy.atEpochMs)
    }

    @Test fun `only plain roaming ids go into the in filter`() {
        assertTrue(Irve.validId("FRIOYE477204"))
        assertTrue(Irve.validId("FR*55C*E06270*43"))
        assertFalse(Irve.validId("FR1,FR2"))
        assertFalse(Irve.validId("x' or 1=1"))
    }

    @Test fun `swapped then corrected coordinates are kept and power entered in watts is converted`() {
        fun row(vararg kv: Pair<String, Any?>) = JsonObject(kv.associate { (k, v) ->
            k to when (v) { null -> kotlinx.serialization.json.JsonNull; is Boolean -> JsonPrimitive(v); is Number -> JsonPrimitive(v); else -> JsonPrimitive(v.toString()) }
        })
        val corrected = row("nom_station" to "A", "consolidated_latitude" to 43.627319, "consolidated_longitude" to 1.432381, "puissance_nominale" to 150000,
            "consolidated_is_lon_lat_correct" to false)
        val outOfRange = row("nom_station" to "B", "consolidated_latitude" to 143.7, "consolidated_longitude" to 1.5, "puissance_nominale" to 50)
        val noPower = row("nom_station" to "C", "consolidated_latitude" to 43.8, "consolidated_longitude" to 1.6)
        val s = Irve.group(listOf(corrected, outOfRange, noPower))
        assertEquals(listOf("A"), s.map { it.name })
        assertEquals(150, s.single().maxKw)
    }

    @Test fun `technical station names are replaced by operator and address`() {
        fun row(vararg kv: Pair<String, String>) = JsonObject(kv.associate { (k, v) -> k to JsonPrimitive(v) })
        assertEquals("Tohmo · 12 rue du Port 31000 Toulouse", Irve.readableName(row("nom_station" to "Tohmo/ee057d6d-495a-43ae-8920-4ff0660253", "nom_operateur" to "Tohmo", "adresse_station" to "12 rue du Port 31000 Toulouse")))
        assertEquals("Freshmile", Irve.readableName(row("nom_station" to "Freshmile France/QL0SAGXFVN", "nom_enseigne" to "Freshmile")))
        assertEquals("Tisséo Ramonville", Irve.readableName(row("nom_station" to "Tisséo Ramonville")))
    }

    @Test fun `garbage is not a page`() {
        assertNull(Irve.parsePage(json, "<html>"))
        assertNull(Irve.parsePage(json, """{"message":"error"}"""))
    }
}
