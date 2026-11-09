package fr.roadly.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.roadly.core.common.SunTimes
import fr.roadly.core.data.repository.ThemeMode
import fr.roadly.core.data.repository.UserPreferencesRepository
import fr.roadly.core.designsystem.theme.CockpitTint
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** Source unique de la logique de thème : SYSTEM / LIGHT / DARK / AUTO_SOLAR. */
object ThemeResolver {
    private fun isNight(lat: Double?, lon: Double?, nowMs: Long): Boolean =
        if (lat != null && lon != null) SunTimes.isNight(lat, lon, nowMs) else {
            val h = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).hour
            h < 7 || h >= 20
        }

    fun isDark(mode: ThemeMode, systemDark: Boolean, lat: Double?, lon: Double?, nowMs: Long): Boolean = when (mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        // Nuit (élévation du soleil si la position est connue, sinon 7h–20h locale) : sombre ; de jour : thème du système.
        ThemeMode.AUTO_SOLAR -> isNight(lat, lon, nowMs) || systemDark
    }
}

@HiltViewModel
class ThemeViewModel @Inject constructor(prefs: UserPreferencesRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = prefs.themeMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.AUTO_SOLAR)
    /** Teinte du cockpit choisie dans Réglages → Personnalisation. */
    val tint: StateFlow<CockpitTint> = prefs.cockpitTint.map { name -> CockpitTint.entries.firstOrNull { it.name == name } ?: CockpitTint.MAGENTA }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CockpitTint.MAGENTA)

    /** Fond de carte en photos aériennes IGN (Réglages, bouton de l'accueil). */
    val mapAerial: StateFlow<Boolean> = prefs.mapAerial.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Horloge à la minute pour réévaluer le mode solaire. */
    val nowTick: StateFlow<Long> = flow { while (true) { emit(System.currentTimeMillis()); delay(60_000) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())
}
