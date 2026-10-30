package fr.roadly.core.location.testing

import fr.roadly.core.location.LocationMode
import fr.roadly.core.location.LocationPermissionState
import fr.roadly.core.location.LocationRepository
import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Double de test : pousser des fixes avec [emit], changer la permission via [permission]. */
class FakeLocationRepository(initial: LocationPermissionState = LocationPermissionState.GRANTED) : LocationRepository {
    val permission = MutableStateFlow(initial)
    override val permissionState = permission
    private val _fixes = MutableSharedFlow<LocationFix>(replay = 1)
    override val fixes = _fixes
    override var lastKnown: LocationFix? = null
    var mode: LocationMode = LocationMode.OFF
        private set
    suspend fun emit(fix: LocationFix) { lastKnown = fix; _fixes.emit(fix) }
    override fun setMode(mode: LocationMode) { this.mode = mode }
    override fun refreshPermission() = Unit
    override fun markDeniedForever() { permission.value = LocationPermissionState.DENIED_FOREVER }
}
