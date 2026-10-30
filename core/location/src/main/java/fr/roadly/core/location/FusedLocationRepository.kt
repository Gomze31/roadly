package fr.roadly.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import fr.roadly.core.data.di.ApplicationScope
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.shareIn
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class FusedLocationRepository @Inject constructor(
    @param:ApplicationContext private val ctx: Context,
    @param:ApplicationScope private val scope: CoroutineScope,
    private val prefs: UserPreferencesRepository,
) : LocationRepository {
    private val fused = LocationServices.getFusedLocationProviderClient(ctx)
    private val mode = MutableStateFlow(LocationMode.OFF)
    // Doit être déclaré AVANT _permission : les initialiseurs s'exécutent dans l'ordre de déclaration.
    private var _permissionInitialized = false
    private val _permission = MutableStateFlow(readPermission())
    override val permissionState: StateFlow<LocationPermissionState> = _permission

    init {
        // Le refus définitif survit à la mort du processus : on le restaure tant que la permission n'est pas accordée.
        scope.launch {
            prefs.locationDeniedForever.collect { denied ->
                if (denied && _permission.value != LocationPermissionState.GRANTED) _permission.value = LocationPermissionState.DENIED_FOREVER
            }
        }
    }

    @Volatile override var lastKnown: LocationFix? = null
        private set

    override val fixes: Flow<LocationFix> = combine(mode, _permission) { m, p -> if (p == LocationPermissionState.GRANTED) m else LocationMode.OFF }
        .flatMapLatest { m -> if (m == LocationMode.OFF) emptyFlow() else updates(m) }
        .onEach { lastKnown = it }
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000), replay = 1)

    @SuppressLint("MissingPermission")
    private fun updates(m: LocationMode): Flow<LocationFix> = callbackFlow {
        fused.lastLocation.addOnSuccessListener { loc -> if (loc != null) trySend(loc.toFix()) }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, m.intervalMs)
            .setMinUpdateIntervalMillis(m.intervalMs / 2)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) { result.lastLocation?.let { trySend(it.toFix()) } }
        }
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fused.removeLocationUpdates(callback) }
    }

    override fun setMode(mode: LocationMode) { this.mode.value = mode }

    override fun refreshPermission() {
        val fresh = readPermission()
        if (fresh == LocationPermissionState.GRANTED) scope.launch { prefs.setLocationDeniedForever(false) }
        _permission.value = nextPermissionState(_permission.value, fresh)
    }

    override fun markDeniedForever() {
        _permission.value = LocationPermissionState.DENIED_FOREVER
        scope.launch { prefs.setLocationDeniedForever(true) }
    }

    private fun readPermission(): LocationPermissionState {
        val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return when {
            fine || coarse -> LocationPermissionState.GRANTED
            _permissionInitialized -> LocationPermissionState.DENIED
            else -> LocationPermissionState.UNKNOWN
        }.also { _permissionInitialized = true }
    }

    private fun android.location.Location.toFix() = LocationFix(
        point = GeoPoint(latitude, longitude),
        bearingDeg = if (hasBearing()) bearing else null,
        speedMps = if (hasSpeed()) speed else null,
        accuracyM = if (hasAccuracy()) accuracy else 50f,
        timestampMs = time,
    )
}

/** DENIED_FOREVER ne se dégrade jamais, sauf vers GRANTED (l'utilisateur a accordé la permission dans les réglages). */
internal fun nextPermissionState(current: LocationPermissionState, fresh: LocationPermissionState): LocationPermissionState = when {
    fresh == LocationPermissionState.GRANTED -> fresh
    current == LocationPermissionState.DENIED_FOREVER -> current
    else -> fresh
}
