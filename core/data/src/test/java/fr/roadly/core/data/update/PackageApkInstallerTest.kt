package fr.roadly.core.data.update

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Outcome
import fr.roadly.core.common.update.AppUpdates
import fr.roadly.core.common.update.AppVersion
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPackageInstaller
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * La session simulée par Robolectric jette les octets écrits : celle-ci les conserve, et compte les appels, pour vérifier ce
 * qui est réellement remis à Android. Le reste (flux encore ouvert à la validation, abandon) reste celui de Robolectric.
 */
@Implements(PackageInstaller.Session::class)
class RecordingSession : ShadowPackageInstaller.ShadowSession() {
    class Written(val name: String, val offset: Long, val length: Long, val bytes: ByteArrayOutputStream)

    @Implementation
    override fun openWrite(name: String, offsetBytes: Long, lengthBytes: Long): OutputStream {
        val real = super.openWrite(name, offsetBytes, lengthBytes)
        val bytes = ByteArrayOutputStream()
        written += Written(name, offsetBytes, lengthBytes, bytes)
        return object : OutputStream() {
            override fun write(b: Int) = bytes.write(b)
            override fun write(b: ByteArray, off: Int, len: Int) = bytes.write(b, off, len)
            override fun close() = real.close()
        }
    }

    @Implementation
    override fun fsync(out: OutputStream) { fsyncs++; super.fsync(out) }

    @Implementation
    override fun commit(statusReceiver: IntentSender) { receivers += statusReceiver; super.commit(statusReceiver); commits++ }

    @Implementation
    override fun abandon() { abandons++; super.abandon() }

    companion object {
        val written = mutableListOf<Written>()
        /** Destinataires du statut remis à `commit` : ce par quoi Android rend son verdict. */
        val receivers = mutableListOf<IntentSender>()
        @Volatile var fsyncs = 0
        @Volatile var commits = 0
        @Volatile var abandons = 0
        fun reset() { written.clear(); receivers.clear(); fsyncs = 0; commits = 0; abandons = 0 }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = UpdateTestApplication::class, shadows = [RecordingSession::class])
class PackageApkInstallerTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val sessions get() = app.packageManager.packageInstaller.allSessions
    // Plus grand que le tampon de copie (64 Kio) et pas un multiple de celui-ci.
    private val bytes = ByteArray(200_001) { (it % 251).toByte() }
    private val sha = AppUpdates.sha256(bytes.inputStream())

    @Before fun setUp() {
        RecordingSession.reset()
        UpdateTestApplication.controller = null
    }

    private fun apk(content: ByteArray = bytes) = tmp.newFile("roadly-150.apk").apply { writeBytes(content) }

    @Test fun `matching file is copied byte for byte into one session and committed`() = runBlocking {
        val file = apk()
        assertEquals(Outcome.Success(Unit), PackageApkInstaller(app).install(file, sha))
        val write = RecordingSession.written.single()
        assertEquals("roadly.apk", write.name)
        assertEquals(0L, write.offset)
        assertEquals(bytes.size.toLong(), write.length)
        assertArrayEquals(bytes, write.bytes.toByteArray())
        assertEquals(1, RecordingSession.fsyncs)
        assertEquals(1, RecordingSession.commits)
        assertEquals(0, RecordingSession.abandons)
        val session = sessions.single()
        assertEquals(app.packageName, session.appPackageName)
        // Le fichier vérifié reste en place : c'est le contrôleur qui décide de son sort.
        assertTrue(file.exists())
    }

    @Test fun `expected hash is compared without regard to case`() = runBlocking {
        assertEquals(Outcome.Success(Unit), PackageApkInstaller(app).install(apk(), sha.uppercase()))
        assertEquals(1, RecordingSession.commits)
    }

    @Test fun `file whose content differs from the expected hash is abandoned and never committed`() = runBlocking {
        val file = apk("contenu substitué".toByteArray())
        val result = PackageApkInstaller(app).install(file, sha)
        assertEquals(Outcome.Failure(ApkInstaller.CONTENT_CHANGED), result)
        assertEquals(0, RecordingSession.commits)
        assertEquals(1, RecordingSession.abandons)
        assertTrue(sessions.isEmpty())
    }

    @Test fun `one flipped byte is enough to refuse the file`() = runBlocking {
        val altered = bytes.copyOf().also { it[150_000] = (it[150_000] + 1).toByte() }
        assertEquals(Outcome.Failure(ApkInstaller.CONTENT_CHANGED), PackageApkInstaller(app).install(apk(altered), sha))
        assertEquals(0, RecordingSession.commits)
        assertTrue(sessions.isEmpty())
    }

    @Test fun `missing file is a failure and nothing is committed`() = runBlocking {
        val missing = File(tmp.root, "absent.apk")
        val result = PackageApkInstaller(app).install(missing, sha)
        assertTrue(result.toString(), result is Outcome.Failure && result.error is AppError.Unknown)
        assertEquals(0, RecordingSession.commits)
    }

    @Test fun `empty file with a foreign hash is refused`() = runBlocking {
        assertEquals(Outcome.Failure(ApkInstaller.CONTENT_CHANGED), PackageApkInstaller(app).install(apk(ByteArray(0)), sha))
        assertEquals(0, RecordingSession.commits)
    }

    @Test fun `canInstall follows the unknown sources permission`() {
        val installer = PackageApkInstaller(app)
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        assertFalse(installer.canInstall())
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        assertTrue(installer.canInstall())
    }

    // --- De la session au contrôleur : l'intent remis à Android revient au récepteur avec le statut et le fichier ---

    private fun installingController(): Pair<AppUpdateController, File> = runBlocking {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        val dir = tmp.newFolder()
        val controller = AppUpdateController(OneReleaseSource(bytes), FakeUpdateSettings(), PackageApkInstaller(app), FakeSignatures(), AppVersion(100, "0.1.0"), 36, dir, pinnedSigners = emptySet())
        UpdateTestApplication.controller = controller
        controller.downloadAndInstall((controller.check() as UpdateState.Available).update)
        assertTrue(controller.state.value.toString(), controller.state.value is UpdateState.Installing)
        controller to dir.listFiles()!!.single()
    }

    /** Diffusions vers le récepteur. La boucle principale n'est jamais vidée : le récepteur est appelé à la main, une seule fois. */
    private fun statusBroadcasts() = shadowOf(app).broadcastIntents.filter { it.component?.className == UpdateInstallReceiver::class.java.name }

    /**
     * Verdict d'Android. Robolectric 4.18 valide la session dès `commit` et envoie l'intent sans statut ; il n'ajoute jamais
     * `EXTRA_STATUS`. Le test fait donc ce que fait le système : il renvoie par le destinataire remis à `commit` un intent de
     * complément portant le statut. Ce complément n'est fusionné que si le PendingIntent est modifiable (FLAG_MUTABLE).
     */
    private fun verdict(status: Int): Intent {
        RecordingSession.receivers.single().sendIntent(app, 0, Intent().putExtra(PackageInstaller.EXTRA_STATUS, status), null, null)
        return statusBroadcasts().last()
    }

    @Test fun `status intent given to Android targets the receiver and carries the file`() {
        val (_, file) = installingController()
        val intent = verdict(PackageInstaller.STATUS_FAILURE)
        assertEquals(app.packageName, intent.component!!.packageName)
        assertEquals(UpdateInstallReceiver::class.java.name, intent.component!!.className)
        assertEquals(file.absolutePath, intent.getStringExtra(UpdateInstallReceiver.EXTRA_FILE))
        // Intent modifiable (FLAG_MUTABLE) : le statut ajouté par le système est bien présent.
        assertEquals(PackageInstaller.STATUS_FAILURE, intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -99))
    }

    @Test fun `failed session ends in a failed state that keeps the update`() {
        val (controller, _) = installingController()
        UpdateInstallReceiver().onReceive(app, verdict(PackageInstaller.STATUS_FAILURE))
        val failed = controller.state.value as UpdateState.Failed
        assertEquals(150, failed.update!!.manifest.versionCode)
    }

    @Test fun `successful session leaves the controller installing until Android restarts the app`() {
        val (controller, _) = installingController()
        val intent = verdict(PackageInstaller.STATUS_SUCCESS)
        assertEquals(PackageInstaller.STATUS_SUCCESS, intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -99))
        UpdateInstallReceiver().onReceive(app, intent)
        assertTrue(controller.state.value is UpdateState.Installing)
    }

    @Test fun `file replaced between verification and installation is refused by the real installer`() = runBlocking {
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        val dir = tmp.newFolder()
        val controller = AppUpdateController(OneReleaseSource(bytes), FakeUpdateSettings(), PackageApkInstaller(app), FakeSignatures(), AppVersion(100, "0.1.0"), 36, dir, pinnedSigners = emptySet())
        controller.downloadAndInstall((controller.check() as UpdateState.Available).update)
        assertTrue(controller.state.value is UpdateState.NeedsPermission)
        val file = dir.listFiles()!!.single()
        file.writeBytes("contenu substitué".toByteArray())
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
        controller.install()
        assertTrue((controller.state.value as UpdateState.Failed).message.contains("modifié"))
        assertEquals(0, RecordingSession.commits)
        assertEquals(1, RecordingSession.abandons)
        assertFalse(file.exists())
    }
}
