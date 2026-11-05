package fr.roadly.core.navigation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import fr.roadly.core.common.Formatters

/** Canal, contenu et actions de la notification persistante du guidage. */
internal object NavigationNotifications {
    const val CHANNEL_ID = "roadly.navigation"
    const val NOTIFICATION_ID = 4201

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        // IMPORTANCE_LOW : visible et persistante, sans son (la voix du guidage suffit).
        val channel = NotificationChannel(CHANNEL_ID, ctx.getString(R.string.nav_channel_name), NotificationManager.IMPORTANCE_LOW)
        channel.description = ctx.getString(R.string.nav_channel_description)
        nm.createNotificationChannel(channel)
    }

    /** (titre, texte) affichés ; sert aussi à ne notifier que lorsqu'ils changent. */
    fun content(ctx: Context, state: NavigationState.Guiding?): Pair<String, String> {
        if (state == null) return ctx.getString(R.string.nav_notif_title_default) to ""
        if (state.rerouting) return ctx.getString(R.string.nav_notif_rerouting) to ""
        val m = state.route.maneuvers[state.progress.nextManeuverIndex.coerceIn(0, state.route.maneuvers.lastIndex)]
        val text = ctx.getString(R.string.nav_notif_text, Formatters.maneuverDistance(state.progress.distanceToNextManeuverM), Formatters.clockTime(state.etaEpochMs))
        return m.instruction to text
    }

    /** Conduite libre : limitation (connue ou non) et zone de vigilance ; la vitesse n'y figure pas (pas de mise à jour à chaque seconde). */
    fun freeDriveContent(ctx: Context, state: FreeDriveState.Active): Pair<String, String> {
        val limit = state.speedLimitKmh?.let { ctx.getString(R.string.free_notif_limit, it) } ?: ctx.getString(R.string.free_notif_limit_unknown)
        val text = if (state.zone != null) ctx.getString(R.string.free_notif_zone, limit) else limit
        return ctx.getString(R.string.free_notif_title) to text
    }

    fun build(ctx: Context, content: Pair<String, String>): Notification {
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            action = NavigationService.ACTION_OPEN_NAVIGATION
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val openPending = open?.let { PendingIntent.getActivity(ctx, 1, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT) }
        val stopPending = PendingIntent.getService(
            ctx, 2, Intent(ctx, NavigationService::class.java).setAction(NavigationService.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_navigation)
            .setContentTitle(content.first)
            .setContentText(content.second)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openPending)
            .addAction(0, ctx.getString(R.string.nav_notif_stop), stopPending)
            .build()
    }

    /** API 33+ : sans POST_NOTIFICATIONS, la notification n'est pas affichée (le service tourne quand même). */
    fun canNotify(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    @android.annotation.SuppressLint("MissingPermission")
    fun notify(ctx: Context, notification: Notification) {
        if (canNotify(ctx)) NotificationManagerCompat.from(ctx).notify(NOTIFICATION_ID, notification)
    }
}
