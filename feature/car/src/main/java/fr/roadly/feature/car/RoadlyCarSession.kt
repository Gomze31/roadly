package fr.roadly.feature.car

import android.content.Intent
import android.content.res.Configuration
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.navigation.NavigationManager
import androidx.car.app.navigation.NavigationManagerCallback
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.roadly.core.common.Outcome
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.model.Place
import fr.roadly.core.model.PlaceKind
import fr.roadly.core.navigation.FreeDriveState
import fr.roadly.core.navigation.NavigationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Session Android Auto : une carte (surface de l'hôte), l'accueil en racine, et les écrans de guidage / conduite libre poussés
 * dès que la session du téléphone change d'état — d'où qu'elle soit lancée.
 */
internal class RoadlyCarSession(private val deps: CarDependencies) : Session() {
    private lateinit var renderer: CarMapRenderer
    private lateinit var navigationManager: NavigationManager
    private val dark = MutableStateFlow(true)
    private var navigationStarted = false
    private var lastNav: NavigationState? = null
    private var lastFree: FreeDriveState? = null

    override fun onCreateScreen(intent: Intent): Screen {
        renderer = CarMapRenderer(carContext)
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        navigationManager = carContext.getCarService(NavigationManager::class.java)
        navigationManager.setNavigationManagerCallback(object : NavigationManagerCallback {
            // L'hôte demande l'arrêt (autre application de navigation lancée, commande vocale) : guidage et conduite libre s'arrêtent.
            override fun onStopNavigation() {
                if (deps.navigation.state.value is NavigationState.Guiding) deps.guidance.stop()
                if (deps.freeDrive.state.value is FreeDriveState.Active) deps.freeDrive.stop()
            }
        })
        dark.value = carContext.isDarkMode
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            // Téléphone verrouillé ou sur une autre application : sans service de premier plan `location`, Android suspend la
            // position et la carte de la voiture reste figée (constaté sur le HONOR, demande « inactive »).
            override fun onStart(owner: LifecycleOwner) {
                deps.carScreen.set(true)
                deps.location.setCarDisplayActive(true)
                deps.foregroundService.start()
            }
            override fun onStop(owner: LifecycleOwner) {
                deps.carScreen.set(false)
                deps.location.setCarDisplayActive(false)
            }
            override fun onDestroy(owner: LifecycleOwner) {
                deps.carScreen.set(false)
                deps.location.setCarDisplayActive(false)
                if (navigationStarted) navigationManager.navigationEnded()
                navigationStarted = false
                renderer.release()
            }
        })
        // Après le retour de l'écran racine (Dispatchers.Main, pas immediate) : un guidage en cours est poussé par-dessus l'accueil.
        lifecycleScope.launch(Dispatchers.Main) {
            combine(deps.config.config, dark) { c, d -> if (d) c.mapStyleDarkUrl else c.mapStyleLightUrl }
                .distinctUntilChanged()
                .collect { renderer.setStyle(it) }
        }
        lifecycleScope.launch(Dispatchers.Main) {
            combine(deps.prefs.mapAerial, dark) { a, d -> a to d }.distinctUntilChanged().collect { (a, d) -> renderer.setAerial(a, d) }
        }
        lifecycleScope.launch(Dispatchers.Main) { deps.navigation.state.collect(::onNavigationState) }
        lifecycleScope.launch(Dispatchers.Main) { deps.freeDrive.state.collect(::onFreeDriveState) }
        val home = CarHomeScreen(carContext, deps, renderer)
        lifecycleScope.launch(Dispatchers.Main) { handleIntent(intent) }
        return home
    }

    override fun onNewIntent(intent: Intent) = handleIntent(intent)

    override fun onCarConfigurationChanged(newConfiguration: Configuration) {
        dark.value = carContext.isDarkMode
    }

    private fun onNavigationState(state: NavigationState) {
        val previous = lastNav
        lastNav = state
        val sm = carContext.getCarService(androidx.car.app.ScreenManager::class.java)
        when (state) {
            is NavigationState.Guiding -> {
                if (!navigationStarted) {
                    navigationManager.navigationStarted()
                    navigationStarted = true
                }
                // Nouveau guidage (ou session ouverte pendant un guidage) : écran de guidage au-dessus de l'accueil.
                if (previous !is NavigationState.Guiding && sm.top !is CarGuidanceScreen) {
                    sm.popToRoot()
                    sm.push(CarGuidanceScreen(carContext, deps, renderer, navigationManager))
                }
            }
            is NavigationState.Arrived, is NavigationState.Failed -> endNavigation()
            NavigationState.Idle -> {
                endNavigation()
                if (sm.top is CarGuidanceScreen) sm.pop()
            }
        }
    }

    private fun endNavigation() {
        if (!navigationStarted) return
        navigationManager.navigationEnded()
        navigationStarted = false
    }

    private fun onFreeDriveState(state: FreeDriveState) {
        val previous = lastFree
        lastFree = state
        val sm = carContext.getCarService(androidx.car.app.ScreenManager::class.java)
        when (state) {
            is FreeDriveState.Active -> if (previous !is FreeDriveState.Active && sm.top !is CarFreeDriveScreen) {
                sm.popToRoot()
                sm.push(CarFreeDriveScreen(carContext, deps, renderer))
            }
            is FreeDriveState.Failed -> {
                if (sm.top is CarFreeDriveScreen) sm.pop()
                CarToast.makeText(carContext, state.error.userMessage(), CarToast.LENGTH_LONG).show()
            }
            FreeDriveState.Idle -> if (sm.top is CarFreeDriveScreen) sm.pop()
        }
    }

    /** « Navigue vers » de la voiture : coordonnées → guidage direct ; texte → recherche préremplie. */
    private fun handleIntent(intent: Intent) {
        if (intent.action != CarContext.ACTION_NAVIGATE) return
        when (val target = CarNavigateIntent.parse(intent.dataString)) {
            is CarNavigateTarget.Coordinates -> {
                val p = target.point
                val name = target.label ?: carContext.getString(R.string.car_destination)
                val place = Place(
                    id = String.format(Locale.ROOT, "geo:%.6f,%.6f", p.lat, p.lon),
                    name = name, address = null, point = p, kind = PlaceKind.COORDINATES, source = "android-auto",
                )
                lifecycleScope.launch {
                    val r = deps.guidance.startTo(place)
                    if (r is Outcome.Failure) CarToast.makeText(carContext, r.error.userMessage(), CarToast.LENGTH_LONG).show()
                }
            }
            is CarNavigateTarget.Query -> {
                val sm = carContext.getCarService(androidx.car.app.ScreenManager::class.java)
                sm.popToRoot()
                sm.push(CarSearchScreen(carContext, deps, renderer, initialQuery = target.text))
            }
            null -> Unit
        }
    }
}
