package fr.roadly.feature.car

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.model.Action
import androidx.car.app.model.Alert
import androidx.car.app.model.AlertCallback
import androidx.car.app.model.CarText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.lifecycle.LifecycleCoroutineScope
import fr.roadly.core.common.Formatters
import kotlinx.coroutines.launch

/**
 * Alerte de l'hôte Android Auto à l'apparition d'une zone de danger (préavis ou entrée) : titre, distance, limitation, et un
 * bouton « Fausse alerte » qui masque la zone (même effet que sur le téléphone). Une seule alerte par zone et par passage.
 */
internal class CarZoneAlerts(
    private val carContext: CarContext,
    private val deps: CarDependencies,
    private val scope: LifecycleCoroutineScope,
) {
    private var alertedKey: String? = null
    private var nextId = 1

    fun onZone(key: String?, zone: CarZoneModel?) {
        if (key == null || zone == null) return
        if (key == alertedKey) return
        alertedKey = key
        val id = nextId++
        val res = carContext.resources
        val detail = listOfNotNull(
            res.getString(if (zone.inside) R.string.car_zone_remaining else R.string.car_zone_in, Formatters.distance(zone.distanceM)),
            zone.limitKmh?.let { "$it ${res.getString(R.string.car_kmh)}" },
            res.getString(R.string.car_zone_low_confidence).takeUnless { zone.reliable },
        ).joinToString(" · ")
        val alert = Alert.Builder(id, CarText.create(res.getString(R.string.car_zone_title)), ALERT_MS)
            .setSubtitle(CarText.create(detail))
            .setIcon(CarIcons.of(Icons.Default.Shield, color = if (zone.inside) INSIDE else AHEAD))
            .addAction(
                Action.Builder().setTitle(res.getString(R.string.car_zone_false_alarm)).setOnClickListener {
                    scope.launch { deps.radarSettings.hideZone(key) }
                    appManager().dismissAlert(id)
                }.build(),
            )
            .addAction(Action.Builder().setTitle(res.getString(R.string.car_ok)).setOnClickListener { appManager().dismissAlert(id) }.build())
            .setCallback(object : AlertCallback {
                override fun onCancel(reason: Int) = Unit
                override fun onDismiss() = Unit
            })
            .build()
        try {
            appManager().showAlert(alert)
        } catch (e: RuntimeException) {
            // Hôte trop ancien ou refus : la pastille sur la carte reste l'indication principale.
        }
    }

    /** Fin de guidage ou de conduite libre : la prochaine zone sera de nouveau annoncée. */
    fun reset() { alertedKey = null }

    private fun appManager() = carContext.getCarService(AppManager::class.java)

    private companion object {
        const val ALERT_MS = 8_000L
        const val AHEAD = 0xFFFFB020.toInt()
        const val INSIDE = 0xFFFF4D4F.toInt()
    }
}
