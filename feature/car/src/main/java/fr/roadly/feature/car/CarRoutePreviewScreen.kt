package fr.roadly.feature.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import fr.roadly.core.common.AppError
import fr.roadly.core.common.Formatters
import fr.roadly.core.common.Outcome
import fr.roadly.core.designsystem.component.userMessage
import fr.roadly.core.model.Place
import fr.roadly.core.routing.RouteOption
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Itinéraires vers [place] (mêmes variantes que le téléphone : préférences et profil du véhicule) ; une ligne = démarrer. */
internal class CarRoutePreviewScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val renderer: CarMapRenderer,
    private val place: Place,
) : Screen(carContext) {
    private var options: List<RouteOption>? = null
    private var error: AppError? = null
    /** Zones de danger par variante (même calcul que le guidage). */
    private var zones: Map<String, Int> = emptyMap()

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = showOnMap()
            override fun onStop(owner: LifecycleOwner) = renderer.clearRoutes()
        })
        load()
    }

    private fun load() {
        options = null
        error = null
        invalidate()
        lifecycleScope.launch {
            val origin = deps.location.lastKnown?.point
            if (origin == null) {
                error = AppError.NoResult
                invalidate()
                return@launch
            }
            val prefs = deps.prefs.routePreferences.first()
            val vehicle = deps.vehicles.profile.first()
            when (val r = deps.alternatives(origin, place.point, vehicle, deps.clock.nowMs(), prefs)) {
                is Outcome.Success -> options = r.value.takeIf { it.isNotEmpty() } ?: run { error = AppError.NoResult; null }
                is Outcome.Failure -> error = r.error
            }
            showOnMap()
            invalidate()
            val counted = HashMap<String, Int>()
            for (o in options.orEmpty()) {
                try {
                    counted[o.route.id] = deps.dangerZones.zonesAlong(o.route).size
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Base locale illisible : pas de décompte.
                }
            }
            if (counted.isNotEmpty()) {
                zones = counted
                invalidate()
            }
        }
    }

    private fun showOnMap() {
        val routes = options?.map { it.route } ?: return
        renderer.setRoutes(routes, routes.firstOrNull()?.id)
        renderer.overview(routes.flatMap { it.geometry })
    }

    override fun onGetTemplate(): Template {
        error?.let { e ->
            val message = if (deps.location.lastKnown == null && e == AppError.NoResult) carContext.getString(R.string.car_location_unknown) else e.userMessage()
            return MessageTemplate.Builder(message)
                .setHeader(Header.Builder().setTitle(place.name).setStartHeaderAction(Action.BACK).build())
                .addAction(CarActions.text(carContext, R.string.car_retry) { load() })
                .build()
        }
        val header = Header.Builder().setTitle(carContext.getString(R.string.car_routes_title, place.name)).setStartHeaderAction(Action.BACK).build()
        val list = ListTemplate.Builder().setHeader(header)
        val opts = options
        if (opts == null) {
            list.setLoading(true)
        } else {
            val items = ItemList.Builder()
            for (o in opts) {
                val r = o.route
                val second = listOfNotNull(
                    carContext.getString(R.string.car_route_arrival, Formatters.clockTime(o.arrivalEpochMs)),
                    carContext.getString(R.string.car_route_toll).takeIf { r.hasToll },
                    zones[r.id]?.let { n -> carContext.resources.getQuantityString(R.plurals.car_danger_zones, n, n) },
                ).joinToString(" · ")
                items.addItem(
                    Row.Builder()
                        .setTitle(o.label)
                        .addText(carContext.getString(R.string.car_route_line, Formatters.duration(r.durationSeconds), Formatters.distance(r.distanceMeters)))
                        .addText(second)
                        .setOnClickListener { start(o) }
                        .build(),
                )
            }
            list.setSingleList(items.build())
        }
        return MapWithContentTemplate.Builder()
            .setContentTemplate(list.build())
            .setMapController(MapController.Builder().setMapActionStrip(CarActions.mapStrip(renderer)).setPanModeListener { }.build())
            .build()
    }

    /** La session pousse l'écran de guidage dès que l'état passe en guidage. */
    private fun start(option: RouteOption) {
        lifecycleScope.launch {
            val prefs = deps.prefs.routePreferences.first()
            val vehicle = deps.vehicles.profile.first()
            deps.guidance.start(option, prefs, vehicle, emptyList(), place.name)
        }
    }
}
