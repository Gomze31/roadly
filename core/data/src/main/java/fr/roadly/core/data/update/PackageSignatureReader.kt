package fr.roadly.core.data.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.roadly.core.common.update.ApkSigners
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Certificats de signature lus par le PackageManager : ceux de Roadly installé et ceux d'un APK téléchargé (sans l'installer).
 * Toute lecture impossible donne null : l'appelant décide alors (voir `SignerCheck.mayInstall`).
 */
@Singleton
class PackageSignatureReader @Inject constructor(@ApplicationContext private val context: Context) : ApkSignatureReader {
    override fun installed(): ApkSigners? = runCatching {
        signers(context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES))
    }.getOrNull()

    override fun archive(file: File): ApkSigners? = runCatching {
        signers(context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES))
    }.getOrNull()

    private fun signers(info: PackageInfo?): ApkSigners? {
        val signing = info?.signingInfo ?: return null
        // Plusieurs signataires (schémas v1/v2) : tous comptent, sans rotation possible.
        if (signing.hasMultipleSigners()) {
            return signing.apkContentsSigners?.takeIf { it.isNotEmpty() }?.let { all -> ApkSigners.of(all.map { it.toByteArray() }) }
        }
        // Un signataire : l'historique liste les anciennes clés d'une rotation, la clé en vigueur en dernier.
        val history = signing.signingCertificateHistory?.takeIf { it.isNotEmpty() } ?: return null
        return ApkSigners.of(listOf(history.last().toByteArray()), history.dropLast(1).map { it.toByteArray() })
    }
}
