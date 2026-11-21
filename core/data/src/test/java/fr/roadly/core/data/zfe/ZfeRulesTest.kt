package fr.roadly.core.data.zfe

import fr.roadly.core.model.CritAir
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

class ZfeRulesTest {
    /** Carré autour du centre de Toulouse (≈ 2 km de côté), au format de la base nationale. */
    private fun feature(id: String, start: String?, end: String?, vp: String?, vpHours: String? = "24/7", twoWheels: String? = null, nom: String? = null) = """
        {"type":"Feature","properties":{"id":"$id","nom":${nom?.let { "\"$it\"" } ?: "null"},"date_debut":${start?.let { "\"$it\"" } ?: "null"},
        "date_fin":${end?.let { "\"$it\"" } ?: "null"},"vp_critair":${vp?.let { "\"$it\"" } ?: "null"},"vp_horaires":${vpHours?.let { "\"$it\"" } ?: "null"},
        "vul_critair":"V3","vul_horaires":"24/7","pl_critair":"V3","pl_horaires":"24/7","deux_rm_critair":${twoWheels?.let { "\"$it\"" } ?: "null"},
        "deux_rm_horaires":null,"url_arrete":"https://example.org/arrete.pdf","url_site_information":"https://www.toulouse-metropole.fr/zfe"},
        "geometry":{"type":"MultiPolygon","coordinates":[[[[1.43,43.59],[1.46,43.59],[1.46,43.61],[1.43,43.61],[1.43,43.59]]]]}}
    """.trimIndent()

    private fun collection(vararg f: String) = """{"type":"FeatureCollection","features":[${f.joinToString(",")}]}"""
    private val names = ZfeParser.parseNames("siren;code;epci_principal;autres_siren;forme_juridique\n243100518;TOULOUSE;Toulouse Métropole;;Métropole\n")
    private val noon = LocalDateTime.of(2026, 11, 9, 12, 0)
    /** Traverse le carré d'ouest en est. */
    private val through = listOf(GeoPoint(43.60, 1.40), GeoPoint(43.60, 1.50))
    private val outside = listOf(GeoPoint(43.70, 1.40), GeoPoint(43.70, 1.50))

    @Test fun `parses rules, dates, restrictions and the agglomeration name`() {
        val rules = ZfeParser.parse(collection(feature("243100518-ZFE-003", "2023-01-01", null, "V4")), names)
        val r = rules.single()
        assertEquals("Toulouse Métropole", r.name)
        assertEquals(LocalDate.of(2023, 1, 1), r.start)
        assertNull(r.end)
        assertEquals("V4", r.restriction[ZfeVehicleClass.CAR])
        assertNull(r.restriction[ZfeVehicleClass.MOTO])
        assertEquals("https://example.org/arrete.pdf", r.decreeUrl)
        assertEquals(1, r.polygons.size)
    }

    @Test fun `unknown siren falls back to the information site`() {
        assertEquals("Grandannecy", ZfeParser.name("1", null, "https://www.grandannecy.fr/zfem", names))
        assertEquals("Strasbourg", ZfeParser.name("67447-ZFE-005", "ZFEM", "https://zfe.strasbourg.eu/", names))
    }

    @Test fun `V4 bans Crit'Air 4, 5 and unclassified, allows 3 and better`() {
        val r = ZfeParser.parse(collection(feature("243100518-ZFE-003", "2023-01-01", null, "V4")), names).single()
        assertEquals(ZfeVerdict.FORBIDDEN, ZfeRules.verdict(r, ZfeVehicleClass.CAR, CritAir.C4, noon))
        assertEquals(ZfeVerdict.FORBIDDEN, ZfeRules.verdict(r, ZfeVehicleClass.CAR, CritAir.C5, noon))
        assertEquals(ZfeVerdict.FORBIDDEN, ZfeRules.verdict(r, ZfeVehicleClass.CAR, CritAir.NONE, noon))
        assertEquals(ZfeVerdict.ALLOWED, ZfeRules.verdict(r, ZfeVehicleClass.CAR, CritAir.C3, noon))
        assertEquals(ZfeVerdict.ALLOWED, ZfeRules.verdict(r, ZfeVehicleClass.CAR, CritAir.ZERO, noon))
        assertEquals(ZfeVerdict.CRITAIR_UNKNOWN, ZfeRules.verdict(r, ZfeVehicleClass.CAR, null, noon))
        // Pas de restriction deux-roues dans cette ligne.
        assertEquals(ZfeVerdict.NO_RESTRICTION, ZfeRules.verdict(r, ZfeVehicleClass.MOTO, CritAir.C5, noon))
    }

    @Test fun `a rule outside its validity dates does not restrict`() {
        val r = ZfeParser.parse(collection(feature("243100518-ZFE-001", "2022-01-01", "2022-12-31", "V5")), names).single()
        assertEquals(ZfeVerdict.NO_RESTRICTION, ZfeRules.verdict(r, ZfeVehicleClass.CAR, CritAir.C5, noon))
    }

    @Test fun `weekday daytime restrictions apply only at those hours`() {
        val r = ZfeParser.parse(collection(feature("217500016-ZFE-001", "2021-06-01", null, "V3", "Mo-Fr 08:00-20:00; PH off")), names).single()
        val mondayNoon = LocalDateTime.of(2026, 11, 9, 12, 0)
        val mondayNight = LocalDateTime.of(2026, 11, 9, 22, 0)
        val sunday = LocalDateTime.of(2026, 11, 8, 12, 0)
        assertEquals(ZfeVerdict.FORBIDDEN, ZfeRules.verdict(r, ZfeVehicleClass.CAR, CritAir.C3, mondayNoon))
        assertEquals(ZfeVerdict.FORBIDDEN_OTHER_HOURS, ZfeRules.verdict(r, ZfeVehicleClass.CAR, CritAir.C3, mondayNight))
        assertEquals(ZfeVerdict.FORBIDDEN_OTHER_HOURS, ZfeRules.verdict(r, ZfeVehicleClass.CAR, CritAir.C3, sunday))
    }

    @Test fun `opening hours variants found in the national base`() {
        val tue8 = LocalDateTime.of(2026, 11, 10, 8, 0)
        val tue12 = LocalDateTime.of(2026, 11, 10, 12, 0)
        assertEquals(true, ZfeRules.Hours.applies("24/7", tue12))
        assertEquals(true, ZfeRules.Hours.applies("Mo-Su : 00:00-00:00", tue12))
        assertEquals(true, ZfeRules.Hours.applies("Mo-Su 00:00-24:00; May 1 off", tue12))
        assertEquals(true, ZfeRules.Hours.applies("Mo-Fr 07:00-9:00,16:00-19:00", tue8))
        assertEquals(false, ZfeRules.Hours.applies("Mo-Fr 07:00-9:00,16:00-19:00", tue12))
        assertEquals(true, ZfeRules.Hours.applies("Mo-Su 00:00-23:59; PH off", tue12))
        assertNull("valeur non interprétable : prudence", ZfeRules.Hours.applies("NC", tue12))
        assertNull(ZfeRules.Hours.applies(null, tue12))
    }

    @Test fun `a route through the zone is reported once per agglomeration with the strictest active rule`() {
        val rules = ZfeParser.parse(collection(
            feature("243100518-ZFE-001", "2022-03-01", null, null),
            feature("243100518-ZFE-003", "2023-01-01", null, "V4"),
        ), names)
        val c = ZfeRules.crossings(through, rules, ZfeVehicleClass.of(VehicleType.SEDAN), CritAir.C4, noon).single()
        assertEquals("Toulouse Métropole", c.rule.name)
        assertEquals(ZfeVerdict.FORBIDDEN, c.verdict)
        assertEquals("V4", c.restriction)
        assertTrue(ZfeRules.crossings(outside, rules, ZfeVehicleClass.CAR, CritAir.C4, noon).isEmpty())
    }

    @Test fun `holes are excluded from the zone`() {
        val poly = ZfePolygon(
            listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 10.0), GeoPoint(10.0, 10.0), GeoPoint(10.0, 0.0)),
            listOf(listOf(GeoPoint(4.0, 4.0), GeoPoint(4.0, 6.0), GeoPoint(6.0, 6.0), GeoPoint(6.0, 4.0))),
        )
        assertTrue(ZfeRules.contains(poly, GeoPoint(2.0, 2.0)))
        assertFalse(ZfeRules.contains(poly, GeoPoint(5.0, 5.0)))
        assertFalse(ZfeRules.contains(poly, GeoPoint(11.0, 5.0)))
    }

    @Test fun `vehicle types map to the base categories`() {
        assertEquals(ZfeVehicleClass.CAR, ZfeVehicleClass.of(VehicleType.SUV))
        assertEquals(ZfeVehicleClass.VAN, ZfeVehicleClass.of(VehicleType.VAN))
        assertEquals(ZfeVehicleClass.TRUCK, ZfeVehicleClass.of(VehicleType.TRUCK))
        assertEquals(ZfeVehicleClass.MOTO, ZfeVehicleClass.of(VehicleType.MOTORCYCLE))
        assertEquals(ZfeVehicleClass.CAR, ZfeVehicleClass.of(null))
    }

    /** Contrôle sur le vrai fichier national, si fourni : -Droadly.zfe.file=/chemin/aires.geojson (inactif sinon). */
    @Test fun `real national file parses and Toulouse centre is restricted for Crit'Air 4`() {
        val path = System.getProperty("roadly.zfe.file")
        assumeTrue(path != null && File(path).exists())
        val rules = ZfeParser.parse(File(path!!).readText(), names)
        assertTrue("au moins 30 réglementations", rules.size >= 30)
        val capitole = listOf(GeoPoint(43.6045, 1.4440), GeoPoint(43.6047, 1.4445))
        val c = ZfeRules.crossings(capitole, rules, ZfeVehicleClass.CAR, CritAir.C4, noon)
        assertEquals("Toulouse Métropole", c.single().rule.name)
        assertEquals(ZfeVerdict.FORBIDDEN, c.single().verdict)
    }
}
