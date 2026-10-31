package fr.roadly.core.map

import android.content.Context
import org.maplibre.android.maps.MapLibreMapOptions

/** C3 : réglages de rendu de la carte. */
object MapRendering {
    /** Plafond d'images par seconde du rendu. */
    const val MAX_FPS = 60
    /** Plafond en économie d'énergie (économiseur de batterie Android). */
    const val MAX_FPS_POWER_SAVE = 30

    /** Options explicites : SurfaceView (pas de TextureView, plus coûteuse) et inclinaison plafonnée au maximum rendu par MapLibre. */
    fun options(context: Context): MapLibreMapOptions =
        MapLibreMapOptions.createFromAttributes(context).textureMode(false).maxPitchPreference(GuidanceCameraPolicy.MAX_PITCH)
}
