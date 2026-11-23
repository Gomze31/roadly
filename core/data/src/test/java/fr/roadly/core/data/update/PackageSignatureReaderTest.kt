package fr.roadly.core.data.update

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.pm.SigningInfo
import fr.roadly.core.common.update.ApkSigners
import fr.roadly.core.common.update.SignerCheck
import fr.roadly.core.common.update.SignerVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowSigningInfo
import java.io.File

/**
 * Les certificats sont fournis au PackageManager simulé (`ShadowSigningInfo`) : ce qui est vérifié ici est la traduction
 * `SigningInfo` → [ApkSigners]. L'analyse d'un vrai fichier APK signé par Android n'est pas simulable hors appareil.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageSignatureReaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val app: Application get() = RuntimeEnvironment.getApplication()
    private val reader get() = PackageSignatureReader(app)

    private val keyA = "certificat-a".toByteArray()
    private val keyB = "certificat-b".toByteArray()
    private val keyOld = "ancien-certificat".toByteArray()

    private fun signing(current: List<ByteArray>, history: List<ByteArray>? = null): SigningInfo {
        val info = Shadow.newInstanceOf(SigningInfo::class.java)
        val shadow = Shadow.extract<ShadowSigningInfo>(info)
        shadow.setSignatures(current.map(::Signature).toTypedArray())
        if (history != null) shadow.setPastSigningCertificates(history.map(::Signature).toTypedArray())
        return info
    }

    private fun packageInfo(signing: SigningInfo?) = PackageInfo().apply {
        packageName = app.packageName
        applicationInfo = ApplicationInfo().apply { packageName = app.packageName }
        signingInfo = signing
    }

    private fun install(signing: SigningInfo?) = shadowOf(app.packageManager).installPackage(packageInfo(signing))

    private fun archive(signing: SigningInfo?): File = tmp.newFile().also { shadowOf(app.packageManager).setPackageArchiveInfo(it.absolutePath, packageInfo(signing)) }

    // --- Application installée ---

    @Test fun `installed app with one signer has that key and no past key`() {
        install(signing(listOf(keyA)))
        assertEquals(ApkSigners.of(listOf(keyA)), reader.installed())
        assertEquals(ApkSigners(setOf(ApkSigners.digest(keyA)), emptySet()), reader.installed())
    }

    @Test fun `installed app with several signers lists them all and no rotation`() {
        install(signing(listOf(keyA, keyB)))
        assertEquals(ApkSigners.of(listOf(keyA, keyB)), reader.installed())
    }

    @Test fun `several signers ignore any declared history`() {
        install(signing(listOf(keyA, keyB), history = listOf(keyOld, keyA)))
        assertEquals(ApkSigners.of(listOf(keyA, keyB)), reader.installed())
    }

    @Test fun `signing history gives the last key as current and the earlier ones as past`() {
        // Android liste l'historique du plus ancien au plus récent, la clé en vigueur en dernier.
        install(signing(listOf(keyA), history = listOf(keyOld, keyB, keyA)))
        assertEquals(ApkSigners.of(listOf(keyA), listOf(keyOld, keyB)), reader.installed())
    }

    @Test fun `installed app without signing information is unreadable`() {
        install(null)
        assertNull(reader.installed())
    }

    @Test fun `installed app without any certificate is unreadable`() {
        install(signing(emptyList()))
        assertNull(reader.installed())
    }

    @Test fun `package unknown to the package manager is unreadable and does not crash`() {
        shadowOf(app.packageManager).removePackage(app.packageName)
        assertNull(reader.installed())
    }

    // --- Fichier APK téléchargé ---

    @Test fun `archive with one signer`() {
        assertEquals(ApkSigners.of(listOf(keyA)), reader.archive(archive(signing(listOf(keyA)))))
    }

    @Test fun `archive with several signers`() {
        assertEquals(ApkSigners.of(listOf(keyA, keyB)), reader.archive(archive(signing(listOf(keyA, keyB)))))
    }

    @Test fun `archive with a signing history`() {
        assertEquals(ApkSigners.of(listOf(keyB), listOf(keyA)), reader.archive(archive(signing(listOf(keyB), history = listOf(keyA, keyB)))))
    }

    @Test fun `archive without signing information is unreadable`() {
        assertNull(reader.archive(archive(null)))
    }

    @Test fun `file that is not an APK is unreadable`() {
        val garbage = tmp.newFile("faux.apk").apply { writeBytes("ceci n'est pas un APK".toByteArray()) }
        assertNull(reader.archive(garbage))
    }

    @Test fun `missing archive is unreadable`() {
        assertNull(reader.archive(File(tmp.root, "absent.apk")))
    }

    // --- Lecture + décision : ce que le contrôleur conclut des certificats lus ---

    @Test fun `same key read on both sides may be installed`() {
        install(signing(listOf(keyA)))
        assertEquals(SignerVerdict.SAME, SignerCheck.verdict(reader.installed(), reader.archive(archive(signing(listOf(keyA)))), emptySet()))
    }

    @Test fun `another key read from the archive is a different signer`() {
        install(signing(listOf(keyA)))
        assertEquals(SignerVerdict.DIFFERENT, SignerCheck.verdict(reader.installed(), reader.archive(archive(signing(listOf(keyB)))), emptySet()))
    }

    @Test fun `archive that declares a rotation from the installed key may be installed`() {
        install(signing(listOf(keyA)))
        val verdict = SignerCheck.verdict(reader.installed(), reader.archive(archive(signing(listOf(keyB), history = listOf(keyA, keyB)))), emptySet())
        assertEquals(true, SignerCheck.mayInstall(verdict, emptySet()))
    }
}
