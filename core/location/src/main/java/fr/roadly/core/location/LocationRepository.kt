package fr.roadly.core.location

import fr.roadly.core.model.LocationFix
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** GUIDANCE : 1 Hz pendant le guidage (spec Phase 2 §8), prioritaire sur le mode demandé par les écrans. */
enum class LocationMode(val intervalMs: Long) { OFF(0), MAP(1000), FOLLOW(500), GUIDANCE(1000) }

enum class LocationPermissionState { UNKNOWN, GRANTED, DENIED, DENIED_FOREVER }

interface LocationRepository {
    val permissionState: StateFlow<LocationPermissionState>
    /** Flux partagé ; n'émet rien tant que la permission n'est pas accordée ou que le mode est OFF. */
    val fixes: Flow<LocationFix>
    val lastKnown: LocationFix?
    fun setMode(mode: LocationMode)
    /** Guidage actif : impose [LocationMode.GUIDANCE] tant que la permission est accordée, quel que soit [setMode]. */
    fun setGuidanceActive(active: Boolean)
    /** À appeler après le retour du dialogue système de permission. */
    fun refreshPermission()
    /** À appeler quand `shouldShowRequestPermissionRationale` est false après un refus. */
    fun markDeniedForever()
}
