package fr.roadly.core.navigation

import fr.roadly.core.common.Outcome
import fr.roadly.core.model.LocationFix
import javax.inject.Inject

/** Limitation de vitesse de la route courante (km/h) ; `null` = inconnue, jamais devinée. */
interface SpeedLimitProvider {
    suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?>
}

/** Fournisseur par défaut : aucune source fiable, la limitation reste inconnue. */
class NoopSpeedLimitProvider @Inject constructor() : SpeedLimitProvider {
    override suspend fun speedLimitKmh(recent: List<LocationFix>): Outcome<Int?> = Outcome.Success(null)
}
