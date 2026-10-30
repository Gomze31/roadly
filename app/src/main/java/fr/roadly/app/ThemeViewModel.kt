package fr.roadly.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.SunTimes
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.location.LocationRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Source unique de la logique de thème : SYSTEM / LIGHT / DARK / AUTO_SOLAR. */
object ThemeResolver {
    fun isDark(mode: ThemeMode, systemDark: Boolean, lat: Double?, lon: Double?, nowMs: Long): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        // Position connue : élévation du soleil ; sinon repli sur 7h–20h (heure locale).
        ThemeMode.AUTO_SOLAR -> if (lat != null && lon != null) SunTimes.isNight(lat, lon, nowMs) else {
            val h = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).hour
            h < 7 || h >= 20
        }
    }
}

@HiltViewModel
class ThemeViewModel @Inject constructor(prefs: UserPreferencesRepository, private val location: LocationRepository) : ViewModel() {
    // Horloge à la minute pour réévaluer le mode solaire.
    private val tick = flow { while (true) { emit(System.currentTimeMillis()); delay(60_000) } }
    val themeMode: StateFlow<ThemeMode> = prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.AUTO_SOLAR)

    /** Nuit solaire (null hors mode AUTO_SOLAR). */
    val solarNight: StateFlow<Boolean?> = combine(themeMode, tick) { mode, now ->
        if (mode != ThemeMode.AUTO_SOLAR) null else {
            val p = location.lastKnown?.point
            ThemeResolver.isDark(mode, systemDark = false, lat = p?.lat, lon = p?.lon, nowMs = now)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
