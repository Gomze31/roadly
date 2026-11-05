package fr.roadly.core.data.vigilance

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.radar.Radar
import fr.roadly.core.radar.RadarStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synchronisation de la base radar : incrémentale, contrôlée, sans perte de la version précédente. */
class RadarSyncTest {
    private class MemoryStore : RadarStore {
        val rows = LinkedHashMap<String, Radar>()
        val log = ArrayList<RadarVersion>()
        override suspend fun synced() = rows.values.toList()
        override suspend fun lastSuccess() = log.lastOrNull { it.status == RadarSyncStatus.APPLIED || it.status == RadarSyncStatus.UNCHANGED }
        override suspend fun apply(radars: List<Radar>, removedIds: List<String>, version: RadarVersion) {
            radars.forEach { rows[it.id] = it }
            removedIds.forEach { id -> rows[id] = rows.getValue(id).copy(status = RadarStatus.REMOVED) }
            log += version
        }
        override suspend fun log(version: RadarVersion) { log += version }
    }

    private val fixed = String(javaClass.classLoader!!.getResourceAsStream("radars-fixes-2025-12.csv")!!.readBytes(), Charsets.ISO_8859_1)
    private val y2018 = String(javaClass.classLoader!!.getResourceAsStream("radars-automatiques-2018.csv")!!.readBytes(), Charsets.UTF_8)
    private fun api(modified: String, url: String = "https://static.data.gouv.fr/x.csv") =
        """{"resources":[{"format":"pdf","url":"https://x/doc.pdf","last_modified":"2026-01-01"},{"format":"csv","url":"$url","last_modified":"$modified"},{"format":"csv","url":"https://old.csv","last_modified":"2024-10-18"}]}"""

    private var apiBody = api("2025-12-30T13:42:47")
    private var csv = fixed
    private var offline = false
    /** Instantané MapAtlas réel (21/05/2024, CC BY 4.0) ; null = source communautaire indisponible. */
    private var mapAtlas: String? = null
    private val mapAtlasFile = String(javaClass.classLoader!!.getResourceAsStream("mapatlas-fr-2024-05-21.geojson")!!.readBytes(), Charsets.UTF_8)
    private val calls = ArrayList<String>()
    private val fetch: suspend (String) -> Outcome<String> = { url ->
        calls += url
        when {
            offline -> Outcome.Failure(AppError.Network)
            url == RadarSourceUrls.FIXED_DATASET_API -> Outcome.Success(apiBody)
            url == RadarSourceUrls.ENRICH_2018_CSV -> Outcome.Success(y2018)
            url == RadarSourceUrls.MAPATLAS_FR_GEOJSON -> mapAtlas?.let { Outcome.Success(it) } ?: Outcome.Failure(AppError.Network)
            else -> Outcome.Success(csv)
        }
    }

    @Test fun `first sync applies the newest csv resource with enrichment`() = runTest {
        val store = MemoryStore()
        val v = (RadarSync(fetch, store, clock = { 1L }).sync(force = false) as Outcome.Success).value
        assertEquals(RadarSyncStatus.APPLIED, v.status)
        assertEquals("https://static.data.gouv.fr/x.csv", v.sourceUrl)
        assertEquals(3309, v.total)
        assertEquals(3309, v.diff.added)
        assertTrue(v.enriched > 2_000)
        assertEquals(3309, store.rows.size)
    }

    @Test fun `unchanged source downloads nothing`() = runTest {
        val store = MemoryStore()
        RadarSync(fetch, store).sync(force = false)
        calls.clear()
        val v = (RadarSync(fetch, store).sync(force = false) as Outcome.Success).value
        assertEquals(RadarSyncStatus.UNCHANGED, v.status)
        assertEquals(listOf(RadarSourceUrls.FIXED_DATASET_API), calls)
    }

    @Test fun `a new version marks vanished radars as removed and keeps them`() = runTest {
        val store = MemoryStore()
        RadarSync(fetch, store).sync(force = false)
        val lines = fixed.lines()
        csv = (lines.take(1) + lines.drop(101)).joinToString("\n") // 100 équipements retirés
        apiBody = api("2026-12-30T10:00:00")
        val v = (RadarSync(fetch, store).sync(force = false) as Outcome.Success).value
        assertEquals(100, v.diff.removed)
        assertEquals(100, store.rows.values.count { it.status == RadarStatus.REMOVED })
        assertEquals(3309, store.rows.size)
    }

    @Test fun `a truncated file is rejected and the previous base stays`() = runTest {
        val store = MemoryStore()
        RadarSync(fetch, store).sync(force = false)
        csv = fixed.lines().take(1_500).joinToString("\n")
        apiBody = api("2026-12-30T10:00:00")
        assertTrue(RadarSync(fetch, store).sync(force = false) is Outcome.Failure)
        assertEquals(RadarSyncStatus.REJECTED, store.log.last().status)
        assertEquals(0, store.rows.values.count { it.status == RadarStatus.REMOVED })
    }

    @Test fun `offline sync fails without touching the local base`() = runTest {
        val store = MemoryStore()
        RadarSync(fetch, store).sync(force = false)
        offline = true
        assertTrue(RadarSync(fetch, store).sync(force = true) is Outcome.Failure)
        assertEquals(3309, store.rows.values.count { it.status == RadarStatus.ACTIVE })
        assertEquals(RadarSyncStatus.FAILED, store.log.last().status)
    }

    @Test fun `api failure falls back to the known resource`() = runTest {
        apiBody = "<html>"
        val r = RadarSync(fetch, MemoryStore()).latestResource()
        assertEquals(RadarSourceUrls.FIXED_FALLBACK_CSV, r.url)
        assertNotNull(RadarSync(fetch, MemoryStore()).parseResources(api("2025")))
    }

    @Test fun `entity mapping round trip keeps provenance`() = runTest {
        val store = MemoryStore()
        RadarSync(fetch, store).sync(force = false)
        val r = store.rows.values.first { it.direction?.bearingDeg != null }
        assertEquals(r, RadarMapping.toRadar(RadarMapping.toEntity(r)))
    }

    @Test fun `community reports corroborate, hint directions and stay out of the official count`() = runTest {
        mapAtlas = mapAtlasFile
        val store = MemoryStore()
        val v = (RadarSync(fetch, store, clock = { 1L }).sync(force = false) as Outcome.Success).value
        val c = v.community!!
        println("MapAtlas réel : $c ; ${v.message}")
        assertEquals(3853, c.read)
        assertTrue("confirmés : ${c.corroborated}", c.corroborated >= 2_500)
        assertTrue("sens indicatifs : ${c.directionHints}", c.directionHints >= 200)
        assertTrue("en plus : ${c.added}", c.added in 300..1_000)
        // Le total et le contrôle qualité portent sur la seule liste officielle.
        assertEquals(3309, v.total)
        val community = store.rows.values.filter { it.source == fr.roadly.core.radar.RadarSourceIds.MAPATLAS }
        assertEquals(c.added, community.size)
        assertTrue(community.all { it.confidence < fr.roadly.core.radar.RadarQuality.MIN_VOICE && it.id.startsWith("MA-") })
        assertTrue(store.rows.values.filter { it.direction?.hint == true }.none { it.direction!!.isKnownOneWay })
        assertEquals("2018+mapatlas:2024-05-21", v.enrichment)
        // Aller-retour Room : le sens indicatif reste indicatif.
        val hinted = store.rows.values.first { it.direction?.hint == true && it.source != fr.roadly.core.radar.RadarSourceIds.MAPATLAS }
        assertEquals(hinted, RadarMapping.toRadar(RadarMapping.toEntity(hinted)))
    }

    @Test fun `a base synced before mapatlas is redone once even if the official list did not change`() = runTest {
        val store = MemoryStore()
        store.log += RadarVersion(0L, RadarSyncStatus.APPLIED, "https://static.data.gouv.fr/x.csv", "2025-12-30T13:42:47", total = 3309)
        mapAtlas = mapAtlasFile
        val v = (RadarSync(fetch, store).sync(force = false) as Outcome.Success).value
        assertEquals(RadarSyncStatus.APPLIED, v.status)
        val again = (RadarSync(fetch, store).sync(force = false) as Outcome.Success).value
        assertEquals(RadarSyncStatus.UNCHANGED, again.status)
    }

    @Test fun `mapatlas parsing keeps only regulatory limits and known classes`() {
        val body = """{"snapshot":"2024-05-21","features":[
            {"type":"Feature","geometry":{"type":"Point","coordinates":[2.35,48.85]},"properties":{"ref":"a","class":"spot","limitKph":140,"approach":"N"}},
            {"type":"Feature","geometry":{"type":"Point","coordinates":[2.35,48.86]},"properties":{"ref":"b","class":"signal","limitKph":50,"approach":"both"}},
            {"type":"Feature","geometry":{"type":"Point","coordinates":[2.35,48.87]},"properties":{"ref":"c","class":"mobile"}},
            {"type":"Feature","geometry":{"type":"Point","coordinates":[-74.0,40.7]},"properties":{"ref":"d","class":"spot"}}
        ]}"""
        val (snapshot, cams) = RadarSync({ Outcome.Failure(AppError.Network) }, MemoryStore()).parseMapAtlas(body)!!
        assertEquals("2024-05-21", snapshot)
        assertEquals(listOf("a", "b"), cams.map { it.ref })
        assertEquals(null, cams[0].limitKmh)
        assertEquals(0.0, cams[0].approachDeg!!, 0.0)
        assertEquals(null, cams[1].approachDeg)
        assertEquals(fr.roadly.core.radar.RadarKind.RED_LIGHT, cams[1].kind)
    }
}
