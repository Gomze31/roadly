package fr.roadly.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import fr.roadly.core.designsystem.component.GlassButton
import fr.roadly.core.designsystem.component.GlassButtonStyle
import fr.roadly.core.designsystem.component.IconTileStyle
import fr.roadly.core.designsystem.component.ListRow
import fr.roadly.core.designsystem.component.SettingsGroup
import fr.roadly.core.designsystem.theme.LocalRoadlyColors
import fr.roadly.core.designsystem.theme.RoadlySpacing

/** Constate sur le téléphone ce qui conditionne l'affichage de Roadly dans Android Auto. */
internal object AndroidAutoProbe {
    fun facts(context: Context): AndroidAutoFacts {
        val pm = context.packageManager
        val carService = runCatching {
            pm.queryIntentServices(Intent("androidx.car.app.CarAppService").setPackage(context.packageName), 0).isNotEmpty()
        }.getOrDefault(false)
        @Suppress("DEPRECATION")
        val autoVersion = try { pm.getPackageInfo(AndroidAutoReadiness.ANDROID_AUTO_PACKAGE, 0).versionName ?: "?" } catch (e: PackageManager.NameNotFoundException) { null }
        val installer = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) pm.getInstallSourceInfo(context.packageName).installingPackageName
            else @Suppress("DEPRECATION") pm.getInstallerPackageName(context.packageName)
        }.getOrNull()
        @Suppress("DEPRECATION")
        val version = runCatching { pm.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        return AndroidAutoFacts(carService, autoVersion, installer, version)
    }

    /**
     * Android Auto n'a pas d'icône sur les téléphones récents. Ses propres réglages (où se trouvent « Version » et le menu
     * développeur) s'ouvrent par `APPLICATION_PREFERENCES` (vérifié sur le HONOR Magic 8 Pro, Android Auto 17.9) ; sinon son
     * écran de lancement, sinon sa fiche dans les réglages Android.
     */
    fun open(context: Context) {
        val pkg = AndroidAutoReadiness.ANDROID_AUTO_PACKAGE
        val prefs = Intent(Intent.ACTION_APPLICATION_PREFERENCES).setPackage(pkg)
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        val details = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
        for (intent in listOfNotNull(prefs, launch, details, Intent(Settings.ACTION_SETTINGS))) {
            try { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (e: ActivityNotFoundException) { continue } catch (e: SecurityException) { continue }
        }
    }
}

/** Réglages › Android Auto : diagnostic et marche à suivre pour faire apparaître Roadly dans la voiture. */
@Composable
internal fun AndroidAutoGroup() {
    val context = LocalContext.current
    val c = LocalRoadlyColors.current
    var facts by remember { mutableStateOf(AndroidAutoProbe.facts(context)) }
    var showSteps by remember { mutableStateOf(false) }
    // Réévalué au retour au premier plan (après être passé par les réglages d'Android Auto).
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) { owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { facts = AndroidAutoProbe.facts(context) } }

    SettingsGroup("Android Auto") {
        AndroidAutoReadiness.checks(facts).forEach { check ->
            ListRow(
                check.title, subtitle = check.detail,
                icon = when (check.status) { CheckStatus.OK -> Icons.Default.CheckCircle; CheckStatus.ACTION -> Icons.Default.Warning; CheckStatus.BLOCKING -> Icons.Default.ErrorOutline },
                iconStyle = when (check.status) { CheckStatus.OK -> IconTileStyle.Brand; CheckStatus.ACTION -> IconTileStyle.Warning; CheckStatus.BLOCKING -> IconTileStyle.Danger },
                onClick = if (check.status == CheckStatus.ACTION) ({ showSteps = !showSteps }) else null,
            )
        }
        ListRow("Faire apparaître Roadly dans la voiture", subtitle = "Marche à suivre (4 étapes)", icon = Icons.Default.DirectionsCar, onClick = { showSteps = !showSteps }, chevron = false, trailing = { ExpandArrow(showSteps) }, divider = false)
        ExpandableContent(showSteps) {
            Column(verticalArrangement = Arrangement.spacedBy(RoadlySpacing.s)) {
                AndroidAutoReadiness.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall, color = c.textSecondary) }
                GlassButton("Ouvrir Android Auto", onClick = { AndroidAutoProbe.open(context) }, style = GlassButtonStyle.Secondary)
            }
        }
    }
}
