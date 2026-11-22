package fr.roadly.core.navigation

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Réglage du téléphone qui peut interrompre le guidage écran éteint ou application en arrière-plan. */
enum class BackgroundIssue {
    /** Notifications refusées : le service tourne, mais sans notification de guidage ni bouton « Arrêter ». */
    NOTIFICATIONS_OFF,
    /** Optimisation de batterie active : le système (MagicOS en particulier) peut arrêter l'application en arrière-plan. */
    BATTERY_OPTIMIZED,
    /** Activité en arrière-plan restreinte par l'utilisateur ou le constructeur : le service de guidage sera arrêté. */
    BACKGROUND_RESTRICTED,
}

/**
 * Contrôle fait avant de démarrer un guidage. Lecture seule : rien n'est modifié sur le téléphone, et l'application ne demande
 * pas elle-même l'exemption de batterie (autorisation `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` non déclarée) : elle ouvre seulement
 * l'écran système où l'utilisateur l'accorde ([batteryExemptionIntent]).
 */
object BackgroundReadiness {
    /** Problèmes à signaler, du plus bloquant au moins bloquant ; liste vide = rien à signaler. */
    fun issues(notificationsEnabled: Boolean, batteryExempt: Boolean, backgroundRestricted: Boolean): List<BackgroundIssue> = buildList {
        if (backgroundRestricted) add(BackgroundIssue.BACKGROUND_RESTRICTED)
        // Restriction posée : l'optimisation de batterie n'est plus le sujet, un seul message suffit.
        if (!batteryExempt && !backgroundRestricted) add(BackgroundIssue.BATTERY_OPTIMIZED)
        if (!notificationsEnabled) add(BackgroundIssue.NOTIFICATIONS_OFF)
    }

    /** Vrai si l'application est déjà exemptée de l'optimisation de batterie (ou si le téléphone ne permet pas de le savoir). */
    fun batteryExempt(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true

    /**
     * NAV-1 : écran système « Optimisation de la batterie » (liste des applications), où l'utilisateur exempte lui-même Roadly.
     * Ouvrir cet écran ne demande aucune autorisation ; la demande directe (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), elle,
     * exigerait `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, volontairement non déclarée.
     */
    fun batteryExemptionIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun read(ctx: Context): List<BackgroundIssue> {
        val activity = ctx.getSystemService(ActivityManager::class.java)
        return issues(
            notificationsEnabled = NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
            batteryExempt = batteryExempt(ctx),
            backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity?.isBackgroundRestricted == true,
        )
    }
}
