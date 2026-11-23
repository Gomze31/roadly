package fr.roadly.core.location

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Ce qui empêche d'obtenir une position exploitable alors que l'autorisation de position est accordée. */
enum class LocationIssue {
    /** Interrupteur « Localisation » du téléphone coupé : Android ne fournit aucune position, l'attente serait sans fin. */
    SERVICES_OFF,
    /** Seule la position approximative est accordée (plusieurs centaines de mètres) : le guidage exige 30 m ou mieux. */
    APPROXIMATE_ONLY,
}

/**
 * Contrôle de la position avant d'attendre un fix ou de démarrer un guidage. Lecture seule : l'application ne peut ni rallumer la
 * localisation ni s'accorder la position exacte ; elle le dit et ouvre l'écran système.
 */
object LocationReadiness {
    /**
     * Sans aucune autorisation, rien n'est signalé ici : la demande d'autorisation a son propre parcours. Localisation coupée :
     * signalé en premier (sans elle, la précision de l'autorisation n'a pas d'importance).
     */
    fun issue(fineGranted: Boolean, coarseGranted: Boolean, servicesEnabled: Boolean): LocationIssue? = when {
        !fineGranted && !coarseGranted -> null
        !servicesEnabled -> LocationIssue.SERVICES_OFF
        !fineGranted -> LocationIssue.APPROXIMATE_ONLY
        else -> null
    }

    fun read(ctx: Context): LocationIssue? = issue(
        fineGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        coarseGranted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        servicesEnabled = ctx.getSystemService(LocationManager::class.java)?.let(LocationManagerCompat::isLocationEnabled) ?: true,
    )

    /**
     * État courant puis chaque changement de l'interrupteur de localisation (diffusions système). Un changement d'autorisation
     * n'est pas diffusé : l'appelant relit [read] au retour au premier plan.
     */
    fun changes(ctx: Context): Flow<LocationIssue?> = callbackFlow {
        trySend(read(ctx))
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) { trySend(read(ctx)) }
        }
        val filter = IntentFilter(LocationManager.MODE_CHANGED_ACTION).apply { addAction(LocationManager.PROVIDERS_CHANGED_ACTION) }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        awaitClose { ctx.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
