package fr.roadly.core.common.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatesTest {
    private val sha = "a".repeat(64)
    private val releaseJson = """{
        "tag_name":"v0.2.0","name":"Roadly 0.2.0","body":"Mode trottinette","html_url":"https://github.com/Gomze31/roadly/releases/tag/v0.2.0",
        "draft":false,"prerelease":false,"unknown_field":1,
        "assets":[
          {"id":11,"name":"roadly-0.2.0.apk","size":12345,"url":"https://api.github.com/repos/Gomze31/roadly/releases/assets/11","browser_download_url":"https://github.com/x.apk"},
          {"id":12,"name":"update.json","size":120,"url":"https://api.github.com/repos/Gomze31/roadly/releases/assets/12","browser_download_url":"https://github.com/update.json"}
        ]}"""
    private val release = AppUpdates.parseRelease(releaseJson)!!
    private val manifest = UpdateManifest(150, "0.2.0", "roadly-0.2.0.apk", sha, minSdk = 29)

    @Test fun parsesReleaseAndFindsManifest() {
        assertEquals("v0.2.0", release.tag)
        assertEquals(12L, AppUpdates.manifestAsset(release)!!.id)
        assertEquals("https://api.github.com/repos/Gomze31/roadly/releases/assets/11", release.assets[0].apiUrl)
        assertNull(AppUpdates.parseRelease("<html>"))
    }

    @Test fun manifestRoundTrip() {
        assertEquals(manifest, AppUpdates.parseManifest(AppUpdates.encodeManifest(manifest)))
    }

    @Test fun onlyStrictlyNewerVersionIsOffered() {
        val d = AppUpdates.decide(release, manifest, AppVersion(149, "0.1.0"), 36)
        assertTrue(d is UpdateDecision.Available)
        assertEquals(11L, (d as UpdateDecision.Available).update.apk.id)
        assertEquals("Mode trottinette", d.update.notes)
        assertEquals(UpdateDecision.UpToDate, AppUpdates.decide(release, manifest, AppVersion(150, "0.2.0"), 36))
        assertEquals(UpdateDecision.UpToDate, AppUpdates.decide(release, manifest, AppVersion(151, "0.2.1"), 36))
    }

    @Test fun incompatibleAndMalformedReleases() {
        assertTrue(AppUpdates.decide(release, manifest.copy(minSdk = 40), AppVersion(1, "x"), 36) is UpdateDecision.Incompatible)
        assertTrue(AppUpdates.decide(release, null, AppVersion(1, "x"), 36) is UpdateDecision.Malformed)
        assertTrue(AppUpdates.decide(release, manifest.copy(apk = "autre.apk"), AppVersion(1, "x"), 36) is UpdateDecision.Malformed)
        assertTrue(AppUpdates.decide(release, manifest.copy(sha256 = "zz"), AppVersion(1, "x"), 36) is UpdateDecision.Malformed)
    }

    @Test fun repositoryParsing() {
        assertEquals("Gomze31" to "roadly", AppUpdates.parseRepository(" Gomze31/roadly "))
        assertNull(AppUpdates.parseRepository("https://github.com/Gomze31/roadly"))
        assertNull(AppUpdates.parseRepository("a/b/c"))
    }

    @Test fun sha256OfKnownInput() {
        // SHA-256("abc") de la FIPS 180-2.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AppUpdates.sha256("abc".byteInputStream()))
    }
}
