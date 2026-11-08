package fr.roadly.feature.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MessageInfo
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.roadly.core.navigation.FreeDriveState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Conduite libre sur l'écran de la voiture (gabarit de navigation sans itinéraire) : nom de la route, vitesse, limitation et
 * zones de danger devant soi, avec la même session que le téléphone.
 */
internal class CarFreeDriveScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val renderer: CarMapRenderer,
) : Screen(carContext) {
    private var state: FreeDriveState = deps.freeDrive.state.value
    private var toleranceKmh = 0
    private var shownKey: Any? = null
    private val alerts = CarZoneAlerts(carContext, deps, lifecycleScope)

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                renderer.clearRoutes()
                (state as? FreeDriveState.Active)?.let(::drawMap)
            }
            override fun onStop(owner: LifecycleOwner) = renderer.setHud(CarHudModel.EMPTY)
        })
        lifecycleScope.launch(Dispatchers.Main) {
            combine(deps.freeDrive.state, deps.prefs.speedAlertToleranceKmh) { s, t -> s to t }.collect { (s, t) ->
                state = s
                toleranceKmh = t
                if (s is FreeDriveState.Active && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) drawMap(s)
                val key = when (s) {
                    is FreeDriveState.Active -> listOf("a", s.roadName, s.gpsLost)
                    else -> s::class
                }
                if (key != shownKey) { shownKey = key; invalidate() }
            }
        }
    }

    private fun drawMap(s: FreeDriveState.Active) {
        val hud = CarHudModel.from(s, toleranceKmh)
        renderer.setHud(hud)
        alerts.onZone(s.zone?.id ?: s.nextZone?.key, hud.zone)
        val fix = s.lastFix ?: return
        renderer.setUser(fix)
        renderer.follow(fix.point, fix.bearingDeg?.toDouble(), FollowProfile.FREE_DRIVE)
    }

    override fun onGetTemplate(): Template {
        val s = state
        val title = carContext.getString(R.string.car_free_drive)
        val text = when {
            s is FreeDriveState.Active && s.gpsLost -> carContext.getString(R.string.car_gps_lost)
            s is FreeDriveState.Active -> s.roadName ?: carContext.getString(R.string.car_free_drive_unknown_road)
            else -> null
        }
        val actions = if (s is FreeDriveState.Active) {
            ActionStrip.Builder().addAction(CarActions.text(carContext, R.string.car_stop) { deps.freeDrive.stop() }).build()
        } else {
            ActionStrip.Builder().addAction(CarActions.text(carContext, R.string.car_close) { screenManager.pop() }).build()
        }
        val builder = NavigationTemplate.Builder()
            .setNavigationInfo(MessageInfo.Builder(title).apply { text?.let { setText(it) } }.build())
            .setActionStrip(actions)
            .setBackgroundColor(CarColor.createCustom(CARD, CARD))
        // Bandeau de carte : niveau 2 ; au niveau 1 la carte suit le véhicule, sans boutons.
        if (carContext.capabilities().navigationMapStrip) builder.setMapActionStrip(CarActions.mapStrip(renderer)).setPanModeListener { }
        return builder.build()
    }

    private companion object {
        const val CARD = 0xFF1E1B2E.toInt()
    }
}
