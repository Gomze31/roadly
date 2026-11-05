package fr.roadly.feature.car

import android.Manifest
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.MapController
import androidx.car.app.navigation.model.MapWithContentTemplate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import fr.roadly.core.common.Formatters
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.model.LocationFix
import fr.roadly.core.model.Place
import fr.roadly.core.navigation.NavigationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Lieux proposés à l'accueil de la voiture. */
private data class CarPlaces(val home: Place?, val work: Place?, val favorites: List<Place>, val recents: List<Place>)

/** Accueil : carte centrée sur le véhicule, recherche, domicile, travail, favoris, récents, conduite libre. */
internal class CarHomeScreen(
    carContext: CarContext,
    private val deps: CarDependencies,
    private val renderer: CarMapRenderer,
) : Screen(carContext) {
    private var places: CarPlaces? = null
    private var guiding = false
    private var permission = deps.location.permissionState.value
    private var lastFix: LocationFix? = deps.location.lastKnown

    init {
        lifecycleScope.launch(Dispatchers.Main) {
            combine(
                deps.favorites.observeHome(), deps.favorites.observeWork(), deps.favorites.observeAll(), deps.recents.observe(),
            ) { home, work, all, recents -> CarPlaces(home, work, all, recents) }
                .collect { places = it; invalidate() }
        }
        lifecycleScope.launch(Dispatchers.Main) {
            deps.navigation.state.map { it is NavigationState.Guiding }.distinctUntilChanged().collect { guiding = it; invalidate() }
        }
        lifecycleScope.launch(Dispatchers.Main) {
            deps.location.permissionState.collect { if (it != permission) { permission = it; invalidate() } }
        }
        lifecycleScope.launch(Dispatchers.Main) {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Accueil visible : plus de tracé, carte à plat qui suit le véhicule.
                renderer.clearRoutes()
                renderer.setHud(CarHudModel.EMPTY)
                lastFix?.let { showFix(it) }
                deps.location.fixes.collect { showFix(it) }
            }
        }
    }

    private fun showFix(fix: LocationFix) {
        lastFix = fix
        renderer.setUser(fix)
        renderer.follow(fix.point, fix.bearingDeg?.toDouble(), FollowProfile.BROWSE)
    }

    override fun onGetTemplate(): Template {
        if (permission != LocationPermissionState.GRANTED) return permissionTemplate()
        val limit = carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        val list = ListTemplate.Builder().setHeader(Header.Builder().setTitle(carContext.getString(R.string.app_name_car)).setStartHeaderAction(Action.APP_ICON).build())
        val p = places
        if (p == null) {
            list.setLoading(true)
        } else {
            val items = ItemList.Builder()
            rows(p).take(limit.coerceAtLeast(1)).forEach(items::addItem)
            list.setSingleList(items.build())
        }
        return MapWithContentTemplate.Builder()
            .setContentTemplate(list.build())
            .setMapController(MapController.Builder().setMapActionStrip(CarActions.mapStrip(renderer)).setPanModeListener { }.build())
            .setActionStrip(ActionStrip.Builder().addAction(CarActions.icon(Icons.Default.Search) { openSearch() }).build())
            .build()
    }

    private fun rows(p: CarPlaces): List<Row> = buildList {
        if (guiding) {
            add(row(Icons.Default.Navigation, deps.guidance.destinationName ?: carContext.getString(R.string.car_destination), null) {
                screenManager.push(CarGuidanceScreen(carContext, deps, renderer, carContext.getCarService(androidx.car.app.navigation.NavigationManager::class.java)))
            })
        }
        add(row(Icons.Default.Search, carContext.getString(R.string.car_search), null) { openSearch() })
        p.home?.let { add(placeRow(Icons.Default.Home, carContext.getString(R.string.car_home), it)) }
        p.work?.let { add(placeRow(Icons.Default.Work, carContext.getString(R.string.car_work), it)) }
        add(row(Icons.Default.Speed, carContext.getString(R.string.car_free_drive), carContext.getString(R.string.car_free_drive_detail)) { startFreeDrive() })
        val shown = setOfNotNull(p.home?.id, p.work?.id).toMutableSet()
        for (f in p.favorites) if (shown.add(f.id)) add(placeRow(Icons.Default.Star, f.name, f))
        for (r in p.recents) if (shown.add(r.id)) add(placeRow(Icons.Default.History, r.name, r))
    }

    private fun row(icon: ImageVector, title: String, text: String?, onClick: () -> Unit): Row =
        Row.Builder().setTitle(title).setImage(CarIcons.of(icon, tint = CarColor.DEFAULT)).apply { text?.let { addText(it) } }
            .setOnClickListener(onClick).build()

    private fun placeRow(icon: ImageVector, title: String, place: Place): Row {
        val detail = listOfNotNull(
            place.name.takeIf { it != title },
            lastFix?.let { Formatters.distance(it.point.distanceTo(place.point)) },
            place.address?.takeIf { it != place.name },
        ).joinToString(" · ").ifEmpty { null }
        return row(icon, title, detail) { screenManager.push(CarRoutePreviewScreen(carContext, deps, renderer, place)) }
    }

    private fun openSearch() = screenManager.push(CarSearchScreen(carContext, deps, renderer, initialQuery = null))

    private fun startFreeDrive() {
        // La session pousse l'écran de conduite libre quand l'état devient actif.
        if (!deps.freeDrive.start()) CarToast.makeText(carContext, R.string.car_free_drive_refused, CarToast.LENGTH_LONG).show()
    }

    private fun permissionTemplate(): Template = MessageTemplate.Builder(carContext.getString(R.string.car_location_text))
        .setHeader(Header.Builder().setTitle(carContext.getString(R.string.car_location_title)).setStartHeaderAction(Action.APP_ICON).build())
        .addAction(
            Action.Builder().setTitle(carContext.getString(R.string.car_location_allow)).setFlags(Action.FLAG_PRIMARY).setOnClickListener {
                CarToast.makeText(carContext, R.string.car_location_on_phone, CarToast.LENGTH_LONG).show()
                carContext.requestPermissions(listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) { _, _ ->
                    deps.location.refreshPermission()
                }
            }.build(),
        )
        .build()
}
