package fr.roadly.core.data.update

import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.update.AppUpdates
import fr.roadly.core.common.update.AppVersion
import fr.roadly.core.common.update.GitHubRelease
import fr.roadly.core.common.update.ReleaseAsset
import fr.roadly.core.common.update.UpdateManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
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

class FakeInstaller(var allowed: Boolean = true) : ApkInstaller {
    val installed = mutableListOf<File>()
    override fun canInstall() = allowed
    override suspend fun install(file: File): Outcome<Unit> { installed += file; return Outcome.Success(Unit) }
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

    private fun controller(source: ReleaseSource, settings: UpdateSettings = FakeUpdateSettings(), installer: ApkInstaller = FakeInstaller(), version: Int = 100, now: Long = 1_000_000_000L) =
        AppUpdateController(source, settings, installer, AppVersion(version, "0.1.0"), 36, tmp.newFolder(), { now })

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

    @Before fun setUp() { server.start(); source = GitHubReleaseSource(OkHttpClient(), server.url("/").toString(), Dispatchers.Unconfined) }
    @After fun tearDown() = server.shutdown()

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
