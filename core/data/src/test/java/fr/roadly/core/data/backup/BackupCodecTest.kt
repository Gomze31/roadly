package fr.roadly.core.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InputStream

class BackupCodecTest {
    private val home = BackupFavorite("ban:1", "Maison", "1 rue de la Paix, Paris", 48.8686, 2.3314, "ADDRESS", "ban", 1000, isHome = true)
    private val gym = BackupFavorite("photon:2", "Salle d'escalade « Bloc »", null, 43.6, 1.44, "POI", "photon", 2000)
    private val drive = BackupTrip(9000, 9600, "Gare", 4200.5, 600, 87.0, 0.42, true, "0,48.10000,2.10000,50.0;300,48.20000,2.20000,80.0", "CAR")
    private val ride = BackupTrip(20000, 21000, "Travail", 3100.0, 1000, 24.0, null, false, "", "SCOOTER", 35.0, 12.0, 41.5, 90, 71)
    private val document = BackupCodec.document(123456789, "0.1.215", listOf(home, gym), listOf(drive, ride))
    private val empty = BackupCodec.document(1, null, emptyList(), emptyList())

    private fun decoded(json: String) = BackupCodec.decode(json.toByteArray())
    private fun error(json: String) = (decoded(json) as BackupResult.Failed).error
    private fun ok(json: String) = (decoded(json) as BackupResult.Done).value

    @Test fun `a document survives encoding and decoding unchanged`() {
        val bytes = BackupCodec.encode(document)!!
        val back = (BackupCodec.decode(bytes.inputStream()) as BackupResult.Done).value
        assertEquals(document, back.document)
        assertEquals(0, back.rejectedRows)
    }

    @Test fun `the document states its format and version explicitly`() {
        val text = String(BackupCodec.encode(empty)!!)
        assertTrue(text.contains("\"format\":\"roadly-backup\""))
        assertTrue(text.contains("\"formatVersion\":1"))
        assertEquals(1, BackupCodec.FORMAT_VERSION)
    }

    @Test fun `an empty backup is valid`() {
        val back = ok(String(BackupCodec.encode(empty)!!))
        assertTrue(back.document.favorites.isEmpty() && back.document.trips.isEmpty())
    }

    @Test fun `unknown keys are ignored and optional fields default`() {
        val back = ok(
            """{"format":"roadly-backup","formatVersion":1,"futur":{"x":[1,2]},
               "favorites":[{"id":"a","name":"A","lat":1.0,"lon":2.0,"kind":"POI","source":"s","createdAt":5,"couleur":"rouge"}],
               "trips":[{"startedAt":1,"endedAt":2,"destinationName":"X","distanceM":10.0,"durationS":1,"maxSpeedKmh":3.0,"arrived":true,"meteo":"pluie"}]}""",
        )
        assertEquals(BackupFavorite("a", "A", null, 1.0, 2.0, "POI", "s", 5), back.document.favorites.single())
        assertEquals(BackupTrip(1, 2, "X", 10.0, 1, 3.0, null, true), back.document.trips.single())
        assertEquals(0L, back.document.exportedAtMs)
    }

    @Test fun `files that are not a Roadly backup are refused`() {
        assertEquals(BackupError.NOT_A_BACKUP, error("<html>"))
        assertEquals(BackupError.NOT_A_BACKUP, error(""))
        assertEquals(BackupError.NOT_A_BACKUP, error("[1,2,3]"))
        assertEquals(BackupError.NOT_A_BACKUP, error("""{"format":"autre-chose","formatVersion":1}"""))
        assertEquals(BackupError.NOT_A_BACKUP, error("""{"format":"roadly-backup","formatVersion":0}"""))
        assertEquals(BackupError.NOT_A_BACKUP, error("""{"format":"roadly-backup","formatVersion":"un"}"""))
        // Champ obligatoire absent d'une ligne : le document entier est illisible.
        assertEquals(BackupError.NOT_A_BACKUP, error("""{"format":"roadly-backup","formatVersion":1,"favorites":[{"id":"a"}]}"""))
        // Un objet JSON quelconque, sans marque de format ou sans version, ne passe pas pour une sauvegarde vide.
        assertEquals(BackupError.NOT_A_BACKUP, error("{}"))
        assertEquals(BackupError.NOT_A_BACKUP, error("""{"formatVersion":1}"""))
        assertEquals(BackupError.NOT_A_BACKUP, error("""{"format":"roadly-backup"}"""))
        assertEquals(BackupError.NOT_A_BACKUP, error("""{"name":"package.json","version":"1.0.0"}"""))
    }

    @Test fun `a backup from a newer format is refused, not half read`() {
        assertEquals(BackupError.NEWER_FORMAT, error("""{"format":"roadly-backup","formatVersion":2,"favorites":[],"trips":[]}"""))
    }

    @Test fun `a document above the size limit is refused on read`() {
        val bytes = BackupCodec.encode(document)!!
        assertTrue(BackupCodec.decode(bytes.inputStream(), maxBytes = bytes.size) is BackupResult.Done)
        assertEquals(BackupError.TOO_LARGE, (BackupCodec.decode(bytes.inputStream(), maxBytes = bytes.size - 1) as BackupResult.Failed).error)
        assertEquals(32 * 1024 * 1024, BackupCodec.MAX_DOCUMENT_BYTES)
    }

    @Test fun `reading stops at the limit instead of loading an endless file`() {
        var served = 0L
        val endless = object : InputStream() {
            override fun read(): Int { served++; return 'x'.code }
            override fun read(b: ByteArray, off: Int, len: Int): Int { b.fill('x'.code.toByte(), off, off + len); served += len; return len }
        }
        assertNull(BackupCodec.readBounded(endless, 1_000_000))
        assertTrue("lu : $served octets", served <= 1_000_000 + 64 * 1024)
    }

    @Test fun `an unreadable file is an input error`() {
        val broken = object : InputStream() { override fun read(): Int = throw IOException("carte retirée") }
        assertEquals(BackupError.IO, (BackupCodec.decode(broken) as BackupResult.Failed).error)
    }

    @Test fun `a document above the size limit is not written`() {
        assertNotNull(BackupCodec.encode(document, maxBytes = BackupCodec.encode(document)!!.size))
        assertNull(BackupCodec.encode(document, maxBytes = 100))
    }

    @Test fun `too many rows are refused as a whole`() {
        fun favorites(n: Int) = (1..n).joinToString(",") { """{"id":"f$it","name":"n","lat":1.0,"lon":2.0,"kind":"POI","source":"s","createdAt":1}""" }
        fun trips(n: Int) = (1..n).joinToString(",") { """{"startedAt":$it,"endedAt":$it,"destinationName":"x","distanceM":1.0,"durationS":1,"maxSpeedKmh":1.0,"arrived":true}""" }
        assertEquals(BackupCodec.MAX_FAVORITES, ok("""{"format":"roadly-backup","formatVersion":1,"favorites":[${favorites(BackupCodec.MAX_FAVORITES)}]}""").document.favorites.size)
        assertEquals(BackupError.TOO_LARGE, error("""{"format":"roadly-backup","formatVersion":1,"favorites":[${favorites(BackupCodec.MAX_FAVORITES + 1)}]}"""))
        assertEquals(BackupError.TOO_LARGE, error("""{"format":"roadly-backup","formatVersion":1,"trips":[${trips(BackupCodec.MAX_TRIPS + 1)}]}"""))
    }

    @Test fun `invalid rows are dropped and counted, valid ones are kept`() {
        val bad = listOf(
            home.copy(id = ""), home.copy(id = "x".repeat(BackupCodec.MAX_KEY_CHARS + 1)), home.copy(name = "n".repeat(BackupCodec.MAX_TEXT_CHARS + 1)),
            home.copy(address = "a".repeat(BackupCodec.MAX_TEXT_CHARS + 1)), home.copy(lat = 91.0), home.copy(lon = -180.5), home.copy(kind = "PLANETE"),
            home.copy(kind = "poi"), home.copy(createdAt = -1), home.copy(source = "s".repeat(BackupCodec.MAX_KEY_CHARS + 1)),
        )
        val badTrips = listOf(
            drive.copy(startedAt = -1), drive.copy(endedAt = drive.startedAt - 1), drive.copy(distanceM = -1.0), drive.copy(durationS = -5),
            drive.copy(maxSpeedKmh = -1.0), drive.copy(destinationName = "d".repeat(BackupCodec.MAX_TEXT_CHARS + 1)),
            drive.copy(points = "1".repeat(BackupCodec.MAX_TRACK_CHARS + 1)), drive.copy(batteryStartPct = 101), drive.copy(batteryEndPct = -1),
            drive.copy(travelMode = "m".repeat(BackupCodec.MAX_KEY_CHARS + 1)),
        )
        bad.forEach { assertFalse(it.toString().take(80), BackupCodec.isValid(it)) }
        badTrips.forEach { assertFalse(it.toString().take(80), BackupCodec.isValid(it)) }
        val back = (BackupCodec.decode(BackupCodec.encode(document.copy(favorites = bad + gym, trips = badTrips + ride))!!) as BackupResult.Done).value
        assertEquals(listOf(gym), back.document.favorites)
        assertEquals(listOf(ride), back.document.trips)
        assertEquals(bad.size + badTrips.size, back.rejectedRows)
    }

    @Test fun `limits accept values exactly at the bound`() {
        assertTrue(BackupCodec.isValid(home.copy(id = "x".repeat(BackupCodec.MAX_KEY_CHARS), name = "n".repeat(BackupCodec.MAX_TEXT_CHARS), lat = 90.0, lon = -180.0, createdAt = 0)))
        assertTrue(BackupCodec.isValid(drive.copy(points = "1".repeat(BackupCodec.MAX_TRACK_CHARS), batteryStartPct = 100, batteryEndPct = 0, endedAt = drive.startedAt)))
        // Un mode de déplacement inconnu de cette version reste importable (lu comme « voiture » par l'historique).
        assertTrue(BackupCodec.isValid(drive.copy(travelMode = "VELO")))
    }

    @Test fun `numbers that are not real are refused`() {
        assertFalse(BackupCodec.isValid(home.copy(lat = Double.NaN)))
        assertFalse(BackupCodec.isValid(drive.copy(distanceM = Double.POSITIVE_INFINITY)))
        assertFalse(BackupCodec.isValid(drive.copy(costEur = Double.NaN)))
        // Un nombre non représentable en JSON n'est jamais écrit.
        assertNull(BackupCodec.encode(document.copy(trips = listOf(drive.copy(ascentM = Double.NaN)))))
    }

    @Test fun `the document carries no secret and no setting`() {
        val text = String(BackupCodec.encode(document)!!)
        listOf("token", "jeton", "github", "supabase", "apikey", "password").forEach { assertFalse(it, text.contains(it, ignoreCase = true)) }
        assertEquals(setOf("format", "formatVersion", "exportedAtMs", "appVersion", "favorites", "trips"), BackupDocument.serializer().descriptor.let { d -> (0 until d.elementsCount).map(d::getElementName).toSet() })
    }
}
