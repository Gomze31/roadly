package fr.roadly.core.navigation

import fr.roadly.core.data.repository.GuidanceDefaults
import fr.roadly.core.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onEach
import java.util.concurrent.atomic.AtomicReference

/**
 * Réglages lus pendant un guidage ou une conduite libre (NAV-6). Une lecture qui échoue rend la dernière valeur lue, ou la
 * valeur par défaut si aucune ne l'a été : une erreur de réglages ne doit jamais arrêter le guidage. Les noms sont ceux de
 * [UserPreferencesRepository], pour que les sessions lisent ces flux sans autre changement.
 */
internal class GuidancePreferences(prefs: UserPreferencesRepository) {
    val voiceEnabled: Flow<Boolean> = prefs.voiceEnabled.orLastKnown(GuidanceDefaults.VOICE_ENABLED)
    val voiceVolume: Flow<Int> = prefs.voiceVolume.orLastKnown(GuidanceDefaults.VOICE_VOLUME)
    val voiceBrief: Flow<Boolean> = prefs.voiceBrief.orLastKnown(false)
    val speedAlertToleranceKmh: Flow<Int> = prefs.speedAlertToleranceKmh.orLastKnown(GuidanceDefaults.SPEED_TOLERANCE_KMH)
    val speedAlertSound: Flow<Boolean> = prefs.speedAlertSound.orLastKnown(GuidanceDefaults.SPEED_ALERT_SOUND)
    val vigilanceEnabled: Flow<Boolean> = prefs.vigilanceEnabled.orLastKnown(GuidanceDefaults.VIGILANCE_ENABLED)
    val vigilanceSound: Flow<Boolean> = prefs.vigilanceSound.orLastKnown(GuidanceDefaults.VIGILANCE_SOUND)
    val rainLowersLimit: Flow<Boolean> = prefs.rainLowersLimit.orLastKnown(GuidanceDefaults.RAIN_LOWERS_LIMIT)
    val junction3dEnabled: Flow<Boolean> = prefs.junction3dEnabled.orLastKnown(GuidanceDefaults.JUNCTION_3D)
    val limitDropVoice: Flow<Boolean> = prefs.limitDropVoice.orLastKnown(true)
    val breakReminder: Flow<Boolean> = prefs.breakReminder.orLastKnown(true)
    val roadHazardVoice: Flow<Boolean> = prefs.roadHazardVoice.orLastKnown(true)
}

/** Rend la dernière valeur lue (ou [default]) quand la lecture échoue ; le flux se termine alors au lieu de lever. */
internal fun <T> Flow<T>.orLastKnown(default: T): Flow<T> {
    val last = AtomicReference(default)
    return onEach { last.set(it) }.catch { e -> if (e is CancellationException) throw e else emit(last.get()) }
}
