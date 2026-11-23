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

    @Test fun copyHashingHashesExactlyTheCopiedBytes() {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        val out = java.io.ByteArrayOutputStream()
        val hash = AppUpdates.copyHashing(bytes.inputStream(), out)
        assertTrue(bytes.contentEquals(out.toByteArray()))
        assertEquals(AppUpdates.sha256(bytes.inputStream()), hash)
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", AppUpdates.copyHashing("abc".byteInputStream(), java.io.ByteArrayOutputStream()))
    }
}

class SignerCheckTest {
    private val debugKey = ApkSigners.digest("certificat de debug".toByteArray())
    private val releaseKey = ApkSigners.digest("certificat de publication".toByteArray())
    private val otherKey = ApkSigners.digest("autre certificat".toByteArray())

    @Test fun digestIsSha256HexOfTheCertificateBytes() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ApkSigners.digest("abc".toByteArray()))
        assertEquals(ApkSigners(setOf(debugKey), setOf(otherKey)), ApkSigners.of(listOf("certificat de debug".toByteArray()), listOf("autre certificat".toByteArray())))
    }

    /** Règle de comparaison seule, sans épinglage (l'épinglage a ses propres tests plus bas). */
    private fun unpinned(installed: ApkSigners?, candidate: ApkSigners?) = SignerCheck.verdict(installed, candidate, pinned = emptySet())

    @Test fun sameKeyIsAccepted() {
        assertEquals(SignerVerdict.SAME, unpinned(ApkSigners(setOf(releaseKey)), ApkSigners(setOf(releaseKey))))
        assertEquals(SignerVerdict.SAME, unpinned(ApkSigners(setOf(releaseKey.uppercase())), ApkSigners(setOf(releaseKey))))
    }

    @Test fun anotherKeyIsRefused() {
        // Cas du téléphone signé en debug à qui l'on propose une version signée avec la clé de publication.
        assertEquals(SignerVerdict.DIFFERENT, unpinned(ApkSigners(setOf(debugKey)), ApkSigners(setOf(releaseKey))))
        // L'ancienneté joue dans un seul sens : un APK signé avec une ancienne clé de l'application installée n'est pas une mise à jour.
        assertEquals(SignerVerdict.DIFFERENT, unpinned(ApkSigners(setOf(releaseKey), past = setOf(debugKey)), ApkSigners(setOf(debugKey))))
    }

    @Test fun declaredKeyRotationIsAccepted() {
        assertEquals(SignerVerdict.SAME, unpinned(ApkSigners(setOf(debugKey)), ApkSigners(setOf(releaseKey), past = setOf(debugKey))))
        assertEquals(SignerVerdict.DIFFERENT, unpinned(ApkSigners(setOf(debugKey)), ApkSigners(setOf(releaseKey), past = setOf(otherKey))))
    }

    @Test fun multipleSignersMustAllMatch() {
        assertEquals(SignerVerdict.SAME, unpinned(ApkSigners(setOf(debugKey, otherKey)), ApkSigners(setOf(otherKey, debugKey))))
        assertEquals(SignerVerdict.DIFFERENT, unpinned(ApkSigners(setOf(debugKey, otherKey)), ApkSigners(setOf(debugKey))))
        assertEquals(SignerVerdict.DIFFERENT, unpinned(ApkSigners(setOf(debugKey)), ApkSigners(setOf(debugKey, otherKey))))
        // Pas de rotation avec plusieurs signataires.
        assertEquals(SignerVerdict.DIFFERENT, unpinned(ApkSigners(setOf(debugKey, otherKey)), ApkSigners(setOf(releaseKey), past = setOf(debugKey, otherKey))))
    }

    @Test fun unreadableCertificatesGiveNoVerdict() {
        assertEquals(SignerVerdict.UNKNOWN, unpinned(null, ApkSigners(setOf(releaseKey))))
        assertEquals(SignerVerdict.UNKNOWN, unpinned(ApkSigners(setOf(releaseKey)), null))
        assertEquals(SignerVerdict.UNKNOWN, unpinned(ApkSigners(setOf(releaseKey)), ApkSigners(emptySet())))
        assertEquals(SignerVerdict.UNKNOWN, unpinned(ApkSigners(emptySet()), ApkSigners(setOf(releaseKey))))
    }

    @Test fun theOfficialReleaseKeyIsTheOnlyPinnedSigner() {
        // Empreinte publiée dans docs/SIGNATURE.md et déclarée dans la variable GitHub ROADLY_SIGNER_SHA256.
        assertEquals(setOf("fd785ce163d39cf00ab361b3e5c2bd4b9b49b8d074a91df59d9156b7e0f27834"), SignerCheck.PINNED_SIGNERS)
        assertTrue(SignerCheck.PINNED_SIGNERS.all { it.matches(Regex("[0-9a-f]{64}")) })
    }

    @Test fun byDefaultOnlyTheOfficialKeyMayUpdateTheApp() {
        val official = ApkSigners(setOf(SignerCheck.OFFICIAL_SIGNER))
        assertEquals(SignerVerdict.SAME, SignerCheck.verdict(official, official))
        assertEquals(SignerVerdict.SAME, SignerCheck.verdict(official, ApkSigners(setOf(SignerCheck.OFFICIAL_SIGNER.uppercase()))))
        // Application installée signée en debug (ancien téléphone) : une mise à jour en debug n'est plus acceptée.
        assertEquals(SignerVerdict.DIFFERENT, SignerCheck.verdict(ApkSigners(setOf(debugKey)), ApkSigners(setOf(debugKey))))
        // Version officielle proposée à une application signée en debug : clé différente de celle installée.
        assertEquals(SignerVerdict.DIFFERENT, SignerCheck.verdict(ApkSigners(setOf(debugKey)), official))
        // Certificat illisible sur l'appareil : laissé à Android, qui refuse lui-même toute autre clé que celle installée.
        assertEquals(SignerVerdict.UNKNOWN, SignerCheck.verdict(official, null))
        assertTrue(SignerCheck.mayInstall(SignerVerdict.UNKNOWN))
    }

    @Test fun pinnedKeysRestrictWhatMayBeInstalled() {
        val pins = setOf(releaseKey.uppercase())
        assertEquals(SignerVerdict.SAME, SignerCheck.verdict(ApkSigners(setOf(releaseKey)), ApkSigners(setOf(releaseKey)), pins))
        // Même clé que l'application installée, mais pas celle épinglée : refusé.
        assertEquals(SignerVerdict.DIFFERENT, SignerCheck.verdict(ApkSigners(setOf(debugKey)), ApkSigners(setOf(debugKey)), pins))
        assertEquals(SignerVerdict.DIFFERENT, SignerCheck.verdict(null, ApkSigners(setOf(debugKey)), pins))
        assertEquals(SignerVerdict.UNKNOWN, SignerCheck.verdict(ApkSigners(setOf(releaseKey)), null, pins))
    }

    @Test fun unreadableSignatureIsLeftToAndroidOnlyWithoutPinnedKeys() {
        assertTrue(SignerCheck.mayInstall(SignerVerdict.SAME))
        assertTrue(!SignerCheck.mayInstall(SignerVerdict.DIFFERENT))
        assertTrue(SignerCheck.mayInstall(SignerVerdict.UNKNOWN, pinned = emptySet()))
        assertTrue(SignerCheck.mayInstall(SignerVerdict.UNKNOWN, pinned = setOf(releaseKey)))
        assertTrue(!SignerCheck.mayInstall(SignerVerdict.DIFFERENT, pinned = emptySet()))
    }
}
