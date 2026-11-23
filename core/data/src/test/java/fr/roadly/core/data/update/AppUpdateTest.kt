package fr.roadly.core.data.update

import fr.roadly.core.common.update.SignerCheck
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.CompletableDeferred
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.update.ApkSigners
import fr.roadly.core.common.update.AppUpdates
import fr.roadly.core.common.update.AppVersion
import fr.roadly.core.common.update.GitHubRelease
import fr.roadly.core.common.update.ReleaseAsset
import fr.roadly.core.common.update.UpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FakeUpdateSettings(token: String? = null, repo: String = AppUpdates.DEFAULT_REPOSITORY) : UpdateSettings {
    override val repository = MutableStateFlow(repo)
    override suspend fun setRepository(value: String) { repository.value = value }
    override val token = MutableStateFlow(token)
    override suspend fun setToken(value: String?) { this.token.value = value }
    override val autoCheck = MutableStateFlow(true)
    override suspend fun setAutoCheck(value: Boolean) { autoCheck.value = value }
    override val lastCheckAtMs = MutableStateFlow<Long?>(null)
    override suspend fun setLastCheckAtMs(value: Long) { lastCheckAtMs.value = value }
    override val skippedVersionCode = MutableStateFlow<Int?>(null)
    override suspend fun setSkippedVersionCode(value: Int?) { skippedVersionCode.value = value }
}

/** Comme l'installateur réel : copie le fichier en calculant son empreinte, et refuse si elle n'est pas celle attendue. */
class FakeInstaller(var allowed: Boolean = true) : ApkInstaller {
    val installed = mutableListOf<File>()
    override fun canInstall() = allowed
    override suspend fun install(file: File, expectedSha256: String): Outcome<Unit> {
        val copied = file.inputStream().use { AppUpdates.copyHashing(it, java.io.ByteArrayOutputStream()) }
        if (!copied.equals(expectedSha256, ignoreCase = true)) return Outcome.Failure(ApkInstaller.CONTENT_CHANGED)
        installed += file
        return Outcome.Success(Unit)
    }
}

/** Signatures simulées : [installed] pour l'application en place, [archive] pour tout APK téléchargé. */
class FakeSignatures(var installed: ApkSigners? = ApkSigners(setOf("clé-a")), var archive: ApkSigners? = ApkSigners(setOf("clé-a")), var failing: Boolean = false) : ApkSignatureReader {
    var archiveReads = 0
    override fun installed(): ApkSigners? = installed
    override fun archive(file: File): ApkSigners? { archiveReads++; if (failing) error("lecture impossible"); return archive }
}

class AppUpdateControllerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val apkBytes = "contenu de l'apk".toByteArray()
    private val apkSha = AppUpdates.sha256(apkBytes.inputStream())
    private val apkAsset = ReleaseAsset(1, "roadly-0.2.0.apk", apkBytes.size.toLong(), "api://1")
    private val manifestAsset = ReleaseAsset(2, "update.json", 100, "api://2")
    private val release = GitHubRelease("v0.2.0", body = "Notes", assets = listOf(apkAsset, manifestAsset))

    private inner class FakeSource(var manifest: UpdateManifest = UpdateManifest(150, "0.2.0", "roadly-0.2.0.apk", apkSha, 29), var latestError: AppError? = null, var corrupt: Boolean = false) : ReleaseSource {
        val tokens = mutableListOf<String?>()
        override suspend fun latest(owner: String, repo: String, token: String?): Outcome<GitHubRelease> {
            tokens += token
            return latestError?.let { Outcome.Failure(it) } ?: Outcome.Success(release)
        }
        override suspend fun text(asset: ReleaseAsset, token: String?) = Outcome.Success(AppUpdates.encodeManifest(manifest))
        override suspend fun download(asset: ReleaseAsset, token: String?, dest: File, onProgress: (Long, Long) -> Unit): Outcome<File> {
            dest.writeBytes(if (corrupt) "autre".toByteArray() else apkBytes); onProgress(apkBytes.size.toLong(), apkBytes.size.toLong())
            return Outcome.Success(dest)
        }
    }

    private fun controller(
        source: ReleaseSource, settings: UpdateSettings = FakeUpdateSettings(), installer: ApkInstaller = FakeInstaller(), version: Int = 100, now: Long = 1_000_000_000L,
        signatures: ApkSignatureReader = FakeSignatures(),
        pinned: Set<String> = emptySet(),
    ) = AppUpdateController(source, settings, installer, signatures, AppVersion(version, "0.1.0"), 36, tmp.newFolder(), { now }, pinned)

    // --- Téléchargement annulé ou interrompu ---

    @Test fun cancelledDownloadReturnsToAvailableAndLeavesNoFile() = runTest {
        val started = CompletableDeferred<Unit>()
        val stuck = object : ReleaseSource by FakeSource() {
            override suspend fun download(asset: ReleaseAsset, token: String?, dest: File, onProgress: (Long, Long) -> Unit): Outcome<File> {
                File(dest.parentFile, dest.name + ".part").writeBytes(ByteArray(10)); onProgress(10, 100)
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val installer = FakeInstaller()
        val c = controller(stuck, installer = installer)
        val update = (c.check() as UpdateState.Available).update
        val job = launch { c.downloadAndInstall(update) }
        started.await()
        assertTrue(c.state.value is UpdateState.Downloading)
        c.cancelDownload()
        job.join()
        assertEquals(UpdateState.Available(update), c.state.value)
        assertTrue("aucun fichier partiel ne reste", tmp.root.walk().none { it.isFile })
        assertTrue(installer.installed.isEmpty())
    }

    @Test fun downloadInterruptedByItsCallerDoesNotLeaveTheStateStuck() = runTest {
        val started = CompletableDeferred<Unit>()
        val stuck = object : ReleaseSource by FakeSource() {
            override suspend fun download(asset: ReleaseAsset, token: String?, dest: File, onProgress: (Long, Long) -> Unit): Outcome<File> {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val c = controller(stuck)
        val update = (c.check() as UpdateState.Available).update
        // L'écran qui a lancé le téléchargement est fermé.
        val job = launch { c.downloadAndInstall(update) }
        started.await()
        job.cancel(); job.join()
        assertEquals(UpdateState.Available(update), c.state.value)
        // La vérification automatique n'est plus bloquée par un état « téléchargement » fantôme.
        c.cancelDownload()
        assertEquals(UpdateState.Available(update), c.check())
    }

    @Test fun cancelWithoutADownloadDoesNothing() = runTest {
        val c = controller(FakeSource())
        c.cancelDownload()
        assertEquals(UpdateState.Idle, c.state.value)
    }

    // --- REL-2 / SEC-1 : signature comparée avant l'installation ---

    @Test fun updateSignedWithAnotherKeyIsNeverHandedToTheInstaller() = runTest {
        val installer = FakeInstaller()
        val settings = FakeUpdateSettings()
        val c = controller(FakeSource(), settings, installer, signatures = FakeSignatures(installed = ApkSigners(setOf("clé-debug")), archive = ApkSigners(setOf("clé-publication"))))
        val update = (c.check() as UpdateState.Available).update
        c.downloadAndInstall(update)
        assertEquals(UpdateState.ReinstallRequired(update), c.state.value)
        assertTrue(installer.installed.isEmpty())
        // L'APK inutilisable ne reste pas sur l'appareil.
        assertTrue(tmp.root.walk().none { it.name.endsWith(".apk") })
    }

    @Test fun updateSignedWithAnotherKeyIsNotOfferedAgain() = runTest {
        val installer = FakeInstaller()
        val settings = FakeUpdateSettings()
        val signatures = FakeSignatures(installed = ApkSigners(setOf("clé-debug")), archive = ApkSigners(setOf("clé-publication")))
        val c = controller(FakeSource(), settings, installer, signatures = signatures)
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        // Ni invite automatique, ni état « disponible » : pas de boucle téléchargement → refus.
        assertNull(c.promptable())
        assertEquals(150, settings.skippedVersionCode.value)
        settings.lastCheckAtMs.value = 0
        c.checkIfDue()
        assertTrue(c.state.value is UpdateState.ReinstallRequired)
        assertNull(c.promptable())
        assertTrue(c.check() is UpdateState.ReinstallRequired)
        // « Installer » ou « Réessayer » ne font rien dans cet état.
        c.install()
        assertTrue(c.state.value is UpdateState.ReinstallRequired)
        assertEquals(1, signatures.archiveReads)
        assertTrue(installer.installed.isEmpty())
    }

    @Test fun aLaterVersionIsOfferedAgainAfterAKeyMismatch() = runTest {
        val source = FakeSource()
        val signatures = FakeSignatures(installed = ApkSigners(setOf("clé-debug")), archive = ApkSigners(setOf("clé-publication")))
        val installer = FakeInstaller()
        val c = controller(source, installer = installer, signatures = signatures)
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        // Version suivante, cette fois signée avec la clé de l'application installée.
        source.manifest = source.manifest.copy(versionCode = 151, versionName = "0.2.1")
        signatures.archive = ApkSigners(setOf("clé-debug"))
        val next = c.check()
        assertTrue(next is UpdateState.Available)
        assertEquals(151, c.promptable()!!.manifest.versionCode)
        c.downloadAndInstall((next as UpdateState.Available).update)
        assertEquals(1, installer.installed.size)
    }

    @Test fun declaredKeyRotationIsInstalled() = runTest {
        val installer = FakeInstaller()
        val c = controller(FakeSource(), installer = installer, signatures = FakeSignatures(installed = ApkSigners(setOf("ancienne")), archive = ApkSigners(setOf("nouvelle"), past = setOf("ancienne"))))
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        assertEquals(1, installer.installed.size)
    }

    @Test fun unreadableSignatureIsLeftToAndroidWhileNoKeyIsPinned() = runTest {
        // Lecture impossible (null ou exception) : pas de faux « réinstallation nécessaire » ; Android reste le juge.
        for (signatures in listOf(FakeSignatures(archive = null), FakeSignatures(installed = null), FakeSignatures(failing = true))) {
            val installer = FakeInstaller()
            val c = controller(FakeSource(), installer = installer, signatures = signatures)
            c.downloadAndInstall((c.check() as UpdateState.Available).update)
            assertEquals(1, installer.installed.size)
            assertTrue(c.state.value is UpdateState.Installing)
        }
    }

    // --- Clé de publication épinglée ---

    @Test fun pinnedKeyOfficialUpdateIsInstalled() = runTest {
        val official = ApkSigners(setOf(SignerCheck.OFFICIAL_SIGNER))
        val installer = FakeInstaller()
        val c = controller(FakeSource(), installer = installer, signatures = FakeSignatures(installed = official, archive = official), pinned = SignerCheck.PINNED_SIGNERS)
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        assertEquals(1, installer.installed.size)
        assertTrue(c.state.value is UpdateState.Installing)
    }

    @Test fun pinnedKeyRefusesAnUpdateSignedByAnotherKeyEvenIfTheInstalledAppHasThatKey() = runTest {
        // Clé de debug des deux côtés : Android accepterait ; l'application, elle, n'accepte que la clé officielle.
        val debug = ApkSigners(setOf("7611a323b2f1b159e445174dd57a19c87a42b7d0648de7398e7935de3aa3c9c2"))
        val installer = FakeInstaller()
        val c = controller(FakeSource(), installer = installer, signatures = FakeSignatures(installed = debug, archive = debug), pinned = SignerCheck.PINNED_SIGNERS)
        val update = (c.check() as UpdateState.Available).update
        c.downloadAndInstall(update)
        assertEquals(UpdateState.ReinstallRequired(update), c.state.value)
        assertTrue(installer.installed.isEmpty())
        assertTrue(tmp.root.walk().none { it.name.endsWith(".apk") })
    }

    @Test fun pinnedKeyLeavesAnApkWhoseSignatureCannotBeReadToAndroid() = runTest {
        // Lecture impossible sur l'appareil : pas de blocage définitif des mises à jour ; Android vérifie la clé à l'installation.
        val official = ApkSigners(setOf(SignerCheck.OFFICIAL_SIGNER))
        for (signatures in listOf(FakeSignatures(installed = official, archive = null), FakeSignatures(failing = true))) {
            val installer = FakeInstaller()
            val c = controller(FakeSource(), installer = installer, signatures = signatures, pinned = SignerCheck.PINNED_SIGNERS)
            c.downloadAndInstall((c.check() as UpdateState.Available).update)
            assertEquals(1, installer.installed.size)
            assertTrue(c.state.value is UpdateState.Installing)
        }
    }

    @Test fun productionControllerIsPinnedToTheOfficialKey() = runTest {
        // Constructeur sans liste explicite, comme en production (UpdateModule) : la clé de test « clé-a » est refusée.
        val installer = FakeInstaller()
        val c = AppUpdateController(FakeSource(), FakeUpdateSettings(), installer, FakeSignatures(), AppVersion(100, "0.1.0"), 36, tmp.newFolder())
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        assertTrue(installer.installed.isEmpty())
        assertTrue(c.state.value is UpdateState.ReinstallRequired)
    }

    @Test fun corruptedDownloadIsRejectedBeforeAnySignatureCheck() = runTest {
        val signatures = FakeSignatures()
        val c = controller(FakeSource(corrupt = true), signatures = signatures)
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        assertEquals(0, signatures.archiveReads)
        assertTrue(c.state.value is UpdateState.Failed)
    }

    // --- SEC-7 : le fichier ne peut pas changer entre sa vérification et son installation ---

    @Test fun fileChangedAfterVerificationIsNotInstalled() = runTest {
        val installer = FakeInstaller(allowed = false)
        val c = controller(FakeSource(), installer = installer)
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        assertTrue(c.state.value is UpdateState.NeedsPermission)
        // Le fichier vérifié est remplacé pendant que l'utilisateur accorde l'autorisation.
        val file = tmp.root.walk().first { it.name.endsWith(".apk") }
        file.writeBytes("contenu substitué".toByteArray())
        installer.allowed = true
        c.install()
        assertTrue(installer.installed.isEmpty())
        val failed = c.state.value as UpdateState.Failed
        assertTrue(failed.message.contains("modifié"))
        assertFalse(file.exists())
        // « Réessayer » repart d'un téléchargement neuf.
        c.downloadAndInstall(failed.update!!)
        assertEquals(1, installer.installed.size)
    }

    @Test fun downloadGuardsHaveReadableMessages() {
        assertTrue(GitHubErrors.message(GitHubReleaseSource.UNTRUSTED_HOST, false).contains("GitHub"))
        assertTrue(GitHubErrors.message(GitHubReleaseSource.TOO_LARGE, false).contains("volumineux"))
    }

    @Test fun newerReleaseIsDownloadedVerifiedAndInstalled() = runTest {
        val installer = FakeInstaller()
        val c = controller(FakeSource(), installer = installer)
        val s = c.check()
        assertTrue(s is UpdateState.Available)
        c.downloadAndInstall((s as UpdateState.Available).update)
        assertEquals(1, installer.installed.size)
        assertTrue(c.state.value is UpdateState.Installing)
    }

    @Test fun sameVersionIsUpToDateAndRecordsCheckTime() = runTest {
        val settings = FakeUpdateSettings()
        assertTrue(controller(FakeSource(), settings, version = 150).check() is UpdateState.UpToDate)
        assertEquals(1_000_000_000L, settings.lastCheckAtMs.value)
    }

    @Test fun corruptedDownloadIsRejected() = runTest {
        val installer = FakeInstaller()
        val c = controller(FakeSource(corrupt = true), installer = installer)
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        assertTrue(installer.installed.isEmpty())
        assertTrue((c.state.value as UpdateState.Failed).message.contains("SHA-256"))
    }

    @Test fun missingPermissionThenRetry() = runTest {
        val installer = FakeInstaller(allowed = false)
        val c = controller(FakeSource(), installer = installer)
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        assertTrue(c.state.value is UpdateState.NeedsPermission)
        // Retour dans l'appli (vérification automatique due) : l'APK téléchargé n'est pas oublié.
        c.checkIfDue()
        assertTrue(c.state.value is UpdateState.NeedsPermission)
        installer.allowed = true
        c.install()
        assertEquals(1, installer.installed.size)
    }

    @Test fun privateRepositoryWithoutTokenExplainsWhatToDo() = runTest {
        val s = controller(FakeSource(latestError = AppError.Http(404, "Not Found"))).check()
        assertTrue((s as UpdateState.Failed).message.contains("jeton"))
    }

    @Test fun tokenIsPassedAndAutoCheckRespectsInterval() = runTest {
        val source = FakeSource()
        val settings = FakeUpdateSettings(token = "github_pat_x")
        settings.lastCheckAtMs.value = 1_000_000_000L - 60_000
        val c = controller(source, settings)
        c.checkIfDue()
        assertTrue(source.tokens.isEmpty()) // vérifiée il y a 1 min : pas de nouvelle requête
        settings.lastCheckAtMs.value = 0
        c.checkIfDue()
        assertEquals(listOf<String?>("github_pat_x"), source.tokens)
        settings.autoCheck.value = false; settings.lastCheckAtMs.value = 0
        c.checkIfDue()
        assertEquals(1, source.tokens.size)
        // Plus de 30 min après : nouvelle vérification (plusieurs releases par jour).
        settings.autoCheck.value = true
        settings.lastCheckAtMs.value = 1_000_000_000L - AppUpdateController.AUTO_CHECK_INTERVAL_MS - 1
        c.checkIfDue()
        assertEquals(2, source.tokens.size)
    }

    @Test fun skippedVersionIsNotPromptedAgain() = runTest {
        val settings = FakeUpdateSettings()
        val c = controller(FakeSource(), settings)
        c.check()
        val u = c.promptable()!!
        c.skip(u)
        assertNull(c.promptable())
    }

    @Test fun cancelledConfirmationKeepsTheDownloadedFile() = runTest {
        val c = controller(FakeSource())
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        val file = tmp.root.walk().first { it.name.endsWith(".apk") }
        c.onInstallCancelled(file)
        assertTrue(c.state.value is UpdateState.ReadyToInstall)
        c.onInstallResult(false, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match")
    }

    @Test fun signatureMismatchIsExplained() = runTest {
        val c = controller(FakeSource())
        c.downloadAndInstall((c.check() as UpdateState.Available).update)
        c.onInstallResult(false, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match")
        assertTrue((c.state.value as UpdateState.Failed).message.contains("même clé"))
    }
}

class GitHubReleaseSourceTest {
    private val server = MockWebServer()
    private lateinit var source: GitHubReleaseSource
    @get:Rule val tmp = TemporaryFolder()

    // Serveur local en HTTP : la liste d'hôtes GitHub est remplacée par « tout accepter », sauf dans les tests qui la vérifient.
    @Before fun setUp() { server.start(); source = GitHubReleaseSource(OkHttpClient(), server.url("/").toString(), Dispatchers.Unconfined, allowed = { true }) }
    @After fun tearDown() = server.shutdown()

    private fun otherHost(path: String) = server.url(path).let { it.newBuilder().host(if (it.host == "localhost") "127.0.0.1" else "localhost").build() }
    private fun sourceTrustingOnlyTheServer(maxText: Long = GitHubReleaseSource.MAX_TEXT_BYTES, maxApk: Long = GitHubReleaseSource.MAX_APK_BYTES) =
        GitHubReleaseSource(OkHttpClient(), server.url("/").toString(), Dispatchers.Unconfined, allowed = { it.host == server.url("/").host }, maxTextBytes = maxText, maxApkBytes = maxApk)

    // --- SEC-6 : hôtes acceptés ---

    @Test fun onlyGitHubHostsOverHttpsAreAllowedByDefault() {
        fun allowed(url: String) = GitHubHosts.isAllowed(url.toHttpUrl())
        assertTrue(allowed("https://api.github.com/repos/Gomze31/roadly/releases/latest"))
        assertTrue(allowed("https://API.GitHub.com/repos/x/y/releases/assets/1"))
        assertTrue(allowed("https://github.com/Gomze31/roadly/releases/download/v1/roadly.apk"))
        assertTrue(allowed("https://objects.githubusercontent.com/github-production-release-asset/x"))
        assertTrue(allowed("https://release-assets.githubusercontent.com/github-production-release-asset/x"))
        assertFalse(allowed("http://api.github.com/repos/x/y/releases/latest"))
        assertFalse(allowed("https://api.github.com.evil.example/repos/x/y"))
        assertFalse(allowed("https://evilgithub.com/x.apk"))
        assertFalse(allowed("https://raw.githubusercontent.com/x/y/main/roadly.apk"))
        assertFalse(allowed("https://example.org/roadly.apk"))
        assertFalse(allowed("https://api.github.com@evil.example/x"))
    }

    @Test fun defaultAllowListRefusesAnyOtherServerWithoutContactingIt() = runTest {
        val strict = GitHubReleaseSource(OkHttpClient(), server.url("/").toString(), Dispatchers.Unconfined)
        assertEquals(GitHubReleaseSource.UNTRUSTED_HOST, (strict.latest("Gomze31", "roadly", "github_pat_abc") as Outcome.Failure).error)
        val asset = ReleaseAsset(1, "roadly.apk", 10, server.url("/assets/1").toString())
        assertEquals(GitHubReleaseSource.UNTRUSTED_HOST, (strict.text(asset, null) as Outcome.Failure).error)
        val dest = File(tmp.root, "roadly.apk")
        assertEquals(GitHubReleaseSource.UNTRUSTED_HOST, (strict.download(asset, null, dest) { _, _ -> } as Outcome.Failure).error)
        assertEquals(0, server.requestCount)
        assertFalse(dest.exists())
    }

    @Test fun assetAddressOnAnUntrustedHostIsNotFetched() = runTest {
        val strict = sourceTrustingOnlyTheServer()
        val asset = ReleaseAsset(1, "roadly.apk", 10, otherHost("/assets/1").toString())
        assertEquals(GitHubReleaseSource.UNTRUSTED_HOST, (strict.text(asset, null) as Outcome.Failure).error)
        assertEquals(GitHubReleaseSource.UNTRUSTED_HOST, (strict.download(asset, null, File(tmp.root, "x.apk")) { _, _ -> } as Outcome.Failure).error)
        assertEquals(0, server.requestCount)
    }

    @Test fun redirectToAnUntrustedHostIsNotFollowed() = runTest {
        val strict = sourceTrustingOnlyTheServer()
        val asset = ReleaseAsset(9, "roadly.apk", 10, server.url("/repos/Gomze31/roadly/releases/assets/9").toString())
        val dest = File(tmp.root, "roadly.apk")
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", otherHost("/ailleurs/roadly.apk").toString()))
        assertEquals(GitHubReleaseSource.UNTRUSTED_HOST, (strict.download(asset, null, dest) { _, _ -> } as Outcome.Failure).error)
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", otherHost("/ailleurs/update.json").toString()))
        assertEquals(GitHubReleaseSource.UNTRUSTED_HOST, (strict.text(asset, null) as Outcome.Failure).error)
        // Seules les deux requêtes initiales sont parties : la cible des redirections n'a rien reçu.
        assertEquals(2, server.requestCount)
        assertFalse(dest.exists())
    }

    @Test fun redirectToATrustedHostIsFollowed() = runTest {
        val strict = sourceTrustingOnlyTheServer()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/signed/update.json").toString()))
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals("{}", (strict.text(ReleaseAsset(2, "update.json", 2, server.url("/assets/2").toString()), null) as Outcome.Success).value)
    }

    // --- SEC-6 : tailles bornées ---

    @Test fun sizeLimitsAreOneAndTwoHundredMebibytes() {
        assertEquals(1_048_576L, GitHubReleaseSource.MAX_TEXT_BYTES)
        assertEquals(209_715_200L, GitHubReleaseSource.MAX_APK_BYTES)
    }

    @Test fun oversizedManifestOrReleaseIsRefused() = runTest {
        val small = sourceTrustingOnlyTheServer(maxText = 64)
        val asset = ReleaseAsset(2, "update.json", 2, server.url("/assets/2").toString())
        server.enqueue(MockResponse().setBody("x".repeat(64)))
        assertEquals(64, (small.text(asset, null) as Outcome.Success).value.length)
        server.enqueue(MockResponse().setBody("x".repeat(65)))
        assertEquals(GitHubReleaseSource.TOO_LARGE, (small.text(asset, null) as Outcome.Failure).error)
        // Taille non annoncée (transfert par morceaux) : la limite porte sur les octets reçus.
        server.enqueue(MockResponse().setChunkedBody("x".repeat(500), 16))
        assertEquals(GitHubReleaseSource.TOO_LARGE, (small.text(asset, null) as Outcome.Failure).error)
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1","body":"${"n".repeat(100)}","assets":[]}"""))
        assertEquals(GitHubReleaseSource.TOO_LARGE, (small.latest("Gomze31", "roadly", null) as Outcome.Failure).error)
    }

    @Test fun oversizedApkIsRefusedAndLeavesNoFile() = runTest {
        val small = sourceTrustingOnlyTheServer(maxApk = 1000)
        val dest = File(tmp.root, "roadly.apk")
        val url = server.url("/assets/9").toString()
        // Taille annoncée par la release : aucune requête.
        assertEquals(GitHubReleaseSource.TOO_LARGE, (small.download(ReleaseAsset(9, "roadly.apk", 1001, url), null, dest) { _, _ -> } as Outcome.Failure).error)
        assertEquals(0, server.requestCount)
        // Taille annoncée par le serveur.
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(1001))))
        assertEquals(GitHubReleaseSource.TOO_LARGE, (small.download(ReleaseAsset(9, "roadly.apk", 10, url), null, dest) { _, _ -> } as Outcome.Failure).error)
        // Taille non annoncée : arrêt dès que la limite est dépassée.
        server.enqueue(MockResponse().setChunkedBody(okio.Buffer().write(ByteArray(5000)), 256))
        assertEquals(GitHubReleaseSource.TOO_LARGE, (small.download(ReleaseAsset(9, "roadly.apk", 10, url), null, dest) { _, _ -> } as Outcome.Failure).error)
        assertTrue(tmp.root.listFiles().orEmpty().isEmpty())
        // Juste à la limite : accepté.
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(1000))))
        assertTrue(small.download(ReleaseAsset(9, "roadly.apk", 10, url), null, dest) { _, _ -> } is Outcome.Success)
        assertEquals(1000L, dest.length())
    }

    @Test fun latestSendsTokenAndApiHeaders() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v1","assets":[]}"""))
        val r = source.latest("Gomze31", "roadly", "github_pat_abc")
        assertEquals("v1", (r as Outcome.Success).value.tag)
        val req = server.takeRequest()
        assertEquals("/repos/Gomze31/roadly/releases/latest", req.path)
        assertEquals("Bearer github_pat_abc", req.getHeader("Authorization"))
        assertEquals("application/vnd.github+json", req.getHeader("Accept"))
        assertFalse(req.path!!.contains("github_pat"))
    }

    @Test fun tokenIsNeverSentToAnAssetUrlOnAnotherHost() = runTest {
        // Adresse de fichier qui ne serait pas celle de l'API (réponse altérée) : le jeton ne part pas.
        val other = server.url("/assets/1").let { it.newBuilder().host(if (it.host == "localhost") "127.0.0.1" else "localhost").build() }
        server.enqueue(MockResponse().setBody("{}"))
        source.text(ReleaseAsset(1, "update.json", 2, apiUrl = other.toString()), "github_pat_abc")
        assertNull(server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setBody("{}"))
        source.text(ReleaseAsset(1, "update.json", 2, apiUrl = server.url("/assets/1").toString()), "github_pat_abc")
        assertEquals("Bearer github_pat_abc", server.takeRequest().getHeader("Authorization"))
    }

    @Test fun anonymousRequestHasNoAuthorization() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}"""))
        val r = source.latest("Gomze31", "roadly", null)
        assertEquals(404, ((r as Outcome.Failure).error as AppError.Http).code)
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test fun assetDownloadFollowsRedirectWithoutLeakingToken() = runTest {
        val bytes = ByteArray(300_000) { (it % 251).toByte() }
        // Autre hôte que celui de l'API (localhost ⇄ 127.0.0.1, selon le nom que MockWebServer donne à la machine).
        val signed = server.url("/signed/roadly.apk").let { it.newBuilder().host(if (it.host == "localhost") "127.0.0.1" else "localhost").build() }
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", signed.toString()))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
        val asset = ReleaseAsset(9, "roadly.apk", bytes.size.toLong(), server.url("/repos/Gomze31/roadly/releases/assets/9").toString())
        var progress = 0L
        val dest = File(tmp.root, "roadly.apk")
        val r = source.download(asset, "github_pat_abc", dest) { done, _ -> progress = done }
        assertTrue(r is Outcome.Success)
        assertEquals(bytes.size.toLong(), dest.length())
        assertEquals(bytes.size.toLong(), progress)
        val first = server.takeRequest()
        assertEquals("application/octet-stream", first.getHeader("Accept"))
        assertEquals("Bearer github_pat_abc", first.getHeader("Authorization"))
        val second = server.takeRequest()
        assertEquals("/signed/roadly.apk", second.path)
        assertNull(second.getHeader("Authorization")) // autre hôte : jeton non transmis
    }
}
