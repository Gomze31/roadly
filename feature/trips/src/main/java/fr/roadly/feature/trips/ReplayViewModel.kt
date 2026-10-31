package fr.roadly.feature.trips

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.data.config.ProviderConfigRepository
import fr.roadly.core.data.repository.TripRepository
import fr.roadly.core.model.GeoPoint
import fr.roadly.core.model.Trip
import fr.roadly.core.model.TripPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Position rejouée : point interpolé, cap approximatif, vitesse enregistrée. */
data class ReplayFrame(val point: GeoPoint, val bearingDeg: Float?, val speedKmh: Double?, val elapsedMs: Long)

data class ReplayUiState(
    val trip: Trip? = null,
    val styleUrl: String = "",
    val elapsedMs: Long = 0L,
    val playing: Boolean = false,
    val speed: Int = 1,
    val frame: ReplayFrame? = null,
)

/** Rejoue un trajet enregistré : timeline (ms écoulées), lecture ×1/×2/×4/×8, position interpolée entre deux points. */
@HiltViewModel
class ReplayViewModel @Inject constructor(savedStateHandle: SavedStateHandle, private val repo: TripRepository, config: ProviderConfigRepository) : ViewModel() {
    private val tripId: Long = requireNotNull(savedStateHandle.get<Long>("tripId"))
    private val trip = MutableStateFlow<Trip?>(null)
    private val elapsed = MutableStateFlow(0L)
    private val playing = MutableStateFlow(false)
    private val speed = MutableStateFlow(1)
    private val isDark = MutableStateFlow(true)
    private var ticker: Job? = null

    init { viewModelScope.launch { trip.value = repo.byId(tripId) } }

    val uiState: StateFlow<ReplayUiState> = combine(trip, elapsed, playing, speed, combine(isDark, config.config) { d, c -> if (d) c.mapStyleDarkUrl else c.mapStyleLightUrl }) { t, e, p, s, style ->
        ReplayUiState(t, style, e, p, s, t?.let { frameAt(it.points, e) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReplayUiState())

    fun setDarkTheme(dark: Boolean) { isDark.value = dark }
    fun seek(ms: Long) { elapsed.value = ms.coerceIn(0L, durationMs()) }
    fun setSpeed(x: Int) { speed.value = x.coerceIn(1, 8) }
    fun togglePlay() { if (playing.value) pause() else play() }

    private fun durationMs(): Long = trip.value?.points?.lastOrNull()?.tMs ?: 0L

    private fun play() {
        if (durationMs() == 0L) return
        if (elapsed.value >= durationMs()) elapsed.value = 0L
        playing.value = true
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (playing.value) {
                delay(TICK_MS)
                val next = elapsed.value + TICK_MS * speed.value
                if (next >= durationMs()) { elapsed.value = durationMs(); playing.value = false } else elapsed.value = next
            }
        }
    }

    private fun pause() { playing.value = false; ticker?.cancel() }

    companion object {
        const val TICK_MS = 100L

        /** Interpolation linéaire entre les deux points qui encadrent [elapsedMs]. */
        internal fun frameAt(points: List<TripPoint>, elapsedMs: Long): ReplayFrame? {
            if (points.isEmpty()) return null
            val i = points.indexOfLast { it.tMs <= elapsedMs }.coerceAtLeast(0)
            val a = points[i]
            val b = points.getOrNull(i + 1) ?: return ReplayFrame(a.point, bearing(points.getOrNull(i - 1)?.point, a.point), a.speedKmh, elapsedMs)
            val span = (b.tMs - a.tMs).coerceAtLeast(1)
            val f = ((elapsedMs - a.tMs).toDouble() / span).coerceIn(0.0, 1.0)
            val p = GeoPoint(a.point.lat + (b.point.lat - a.point.lat) * f, a.point.lon + (b.point.lon - a.point.lon) * f)
            return ReplayFrame(p, bearing(a.point, b.point), a.speedKmh, elapsedMs)
        }

        private fun bearing(from: GeoPoint?, to: GeoPoint): Float? {
            if (from == null) return null
            val dLon = Math.toRadians(to.lon - from.lon)
            val y = Math.sin(dLon) * Math.cos(Math.toRadians(to.lat))
            val x = Math.cos(Math.toRadians(from.lat)) * Math.sin(Math.toRadians(to.lat)) - Math.sin(Math.toRadians(from.lat)) * Math.cos(Math.toRadians(to.lat)) * Math.cos(dLon)
            return ((Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0).toFloat()
        }
    }
}
