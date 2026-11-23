package fr.roadly.core.data.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.internal.GeneratedComponent
import dagger.hilt.internal.GeneratedComponentManager
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.update.AppUpdates
import fr.roadly.core.common.update.AppVersion
import fr.roadly.core.common.update.AvailableUpdate
import fr.roadly.core.common.update.GitHubRelease
import fr.roadly.core.common.update.ReleaseAsset
import fr.roadly.core.common.update.UpdateManifest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Application de test : fournit le point d'entrée Hilt lu par [UpdateInstallReceiver] (`EntryPointAccessors.fromApplication`)
 * sans graphe Hilt. Le récepteur réel est donc exécuté tel quel, avec un vrai [AppUpdateController].
 */
class UpdateTestApplication : Application(), GeneratedComponentManager<Any> {
    private val component = object : GeneratedComponent, UpdateEntryPoint {
        override fun controller(): AppUpdateController = controller ?: error("contrôleur de test non défini")
    }
    override fun generatedComponent(): Any = component

    companion object { @Volatile var controller: AppUpdateController? = null }
}

/** Release 0.2.0 (versionCode 150) dont l'APK contient [bytes]. */
class OneReleaseSource(private val bytes: ByteArray) : ReleaseSource {
    private val apk = ReleaseAsset(1, "roadly-0.2.0.apk", bytes.size.toLong(), "api://1")
    private val manifest = UpdateManifest(150, "0.2.0", "roadly-0.2.0.apk", AppUpdates.sha256(bytes.inputStream()), 29)
    override suspend fun latest(owner: String, repo: String, token: String?): Outcome<GitHubRelease> =
        Outcome.Success(GitHubRelease("v0.2.0", body = "Notes", assets = listOf(apk, ReleaseAsset(2, "update.json", 100, "api://2"))))
    override suspend fun text(asset: ReleaseAsset, token: String?) = Outcome.Success(AppUpdates.encodeManifest(manifest))
    override suspend fun download(asset: ReleaseAsset, token: String?, dest: File, onProgress: (Long, Long) -> Unit): Outcome<File> {
        dest.writeBytes(bytes)
        return Outcome.Success(dest)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = UpdateTestApplication::class)
class UpdateInstallReceiverTest {
    @get:Rule val tmp = TemporaryFolder()
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var controller: AppUpdateController
    private lateinit var update: AvailableUpdate
    private lateinit var apk: File

    /** Contrôleur amené dans l'état « installation en cours », comme au moment où Android rend son verdict. */
    @Before fun setUp() = runBlocking {
        val dir = tmp.newFolder()
        controller = AppUpdateController(OneReleaseSource("contenu de l'apk".toByteArray()), FakeUpdateSettings(), FakeInstaller(), FakeSignatures(), AppVersion(100, "0.1.0"), 36, dir)
        UpdateTestApplication.controller = controller
        update = (controller.check() as UpdateState.Available).update
        controller.downloadAndInstall(update)
        assertEquals(UpdateState.Installing(update), controller.state.value)
        apk = dir.listFiles()!!.single()
    }

    private fun deliver(status: Int?, configure: Intent.() -> Unit = {}) {
        val intent = Intent(context, UpdateInstallReceiver::class.java)
        if (status != null) intent.putExtra(PackageInstaller.EXTRA_STATUS, status)
        UpdateInstallReceiver().onReceive(context, intent.apply(configure))
    }

    @Test fun `success leaves the installing state untouched`() {
        deliver(PackageInstaller.STATUS_SUCCESS) { putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "ignoré") }
        assertEquals(UpdateState.Installing(update), controller.state.value)
        assertNull(shadowOf(context as Application).nextStartedActivity)
    }

    @Test fun `failure reports the system message and keeps the update for a retry`() {
        deliver(PackageInstaller.STATUS_FAILURE) { putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "INSTALL_FAILED_INSUFFICIENT_STORAGE") }
        val failed = controller.state.value as UpdateState.Failed
        assertTrue(failed.message, failed.message.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE"))
        assertEquals(update, failed.update)
    }

    @Test fun `every other failure status is a refusal`() {
        val statuses = listOf(
            PackageInstaller.STATUS_FAILURE_BLOCKED, PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_STORAGE, PackageInstaller.STATUS_FAILURE_TIMEOUT, 12345,
        )
        for (status in statuses) {
            setUp()
            deliver(status) { putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "statut $status") }
            val failed = controller.state.value as UpdateState.Failed
            assertTrue(failed.message, failed.message.contains("statut $status"))
        }
    }

    @Test fun `signature conflict reported by Android is explained`() {
        deliver(PackageInstaller.STATUS_FAILURE_CONFLICT) { putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match") }
        assertTrue((controller.state.value as UpdateState.Failed).message.contains("même clé"))
    }

    @Test fun `intent without a status counts as a failure`() {
        deliver(null)
        val failed = controller.state.value as UpdateState.Failed
        assertEquals("Installation refusée", failed.message)
    }

    @Test fun `aborted confirmation keeps the downloaded file ready`() {
        deliver(PackageInstaller.STATUS_FAILURE_ABORTED) { putExtra(UpdateInstallReceiver.EXTRA_FILE, apk.absolutePath) }
        assertEquals(UpdateState.ReadyToInstall(update, apk), controller.state.value)
    }

    @Test fun `aborted confirmation with a deleted or unknown file offers the update again`() {
        apk.delete()
        deliver(PackageInstaller.STATUS_FAILURE_ABORTED) { putExtra(UpdateInstallReceiver.EXTRA_FILE, apk.absolutePath) }
        assertEquals(UpdateState.Available(update), controller.state.value)
        setUp()
        deliver(PackageInstaller.STATUS_FAILURE_ABORTED)
        assertEquals(UpdateState.Available(update), controller.state.value)
    }

    @Test fun `pending user action opens the Android confirmation in a new task`() {
        val confirm = Intent("fr.roadly.test.CONFIRM_INSTALL")
        deliver(PackageInstaller.STATUS_PENDING_USER_ACTION) { putExtra(Intent.EXTRA_INTENT, confirm) }
        val started = shadowOf(context as Application).nextStartedActivity
        assertNotNull(started)
        assertEquals("fr.roadly.test.CONFIRM_INSTALL", started.action)
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        // La décision n'est pas encore prise : l'installation reste en cours.
        assertEquals(UpdateState.Installing(update), controller.state.value)
    }

    @Test fun `pending user action without a confirmation intent is a failure`() {
        deliver(PackageInstaller.STATUS_PENDING_USER_ACTION) { putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "confirmation absente") }
        assertNull(shadowOf(context as Application).nextStartedActivity)
        assertTrue((controller.state.value as UpdateState.Failed).message.contains("confirmation absente"))
    }

    @Test fun `status received while nothing is installing does not invent an update`() {
        UpdateTestApplication.controller = AppUpdateController(OneReleaseSource(ByteArray(1)), FakeUpdateSettings(), FakeInstaller(), FakeSignatures(), AppVersion(100, "0.1.0"), 36, tmp.newFolder())
            .also { controller = it }
        deliver(PackageInstaller.STATUS_FAILURE_ABORTED) { putExtra(UpdateInstallReceiver.EXTRA_FILE, apk.absolutePath) }
        assertEquals(UpdateState.Idle, controller.state.value)
        deliver(PackageInstaller.STATUS_FAILURE) { putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "tardif") }
        assertEquals(UpdateState.Failed("Installation refusée : tardif", null), controller.state.value)
    }
}
