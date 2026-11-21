package fr.roadly.core.map

/**
 * Photos aériennes de l'IGN (orthophotographies, ~20 cm, France métropolitaine et outre-mer) servies par la Géoplateforme en WMTS,
 * sans clé ni compte, sous Licence Ouverte Etalab 2.0. Hors de la couverture, les tuiles manquent et la carte vectorielle reste
 * visible dessous : rien n'est inventé.
 */
/** Fond de carte demandé par l'app : photos aériennes ou non, et assombries la nuit. Fourni une fois pour toutes les cartes. */
data class MapAerialMode(val enabled: Boolean = false, val night: Boolean = false)

val LocalMapAerial = androidx.compose.runtime.compositionLocalOf { MapAerialMode() }

object AerialImagery {
    const val ATTRIBUTION = "<a href=\"https://geoservices.ign.fr\">© IGN – Géoplateforme</a> (photos aériennes)"
    const val MAX_ZOOM = 19
    /** Assombrissement la nuit (luminosité maximale des pixels) : une photo pleine lumière éblouirait en conduite de nuit. */
    const val NIGHT_BRIGHTNESS_MAX = 0.55f

    /** Gabarit de tuile WMTS (pyramide Web Mercator « PM »), au format attendu par MapLibre ({z}/{x}/{y}). */
    const val TILE_URL = "https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0" +
        "&LAYER=ORTHOIMAGERY.ORTHOPHOTOS&STYLE=normal&TILEMATRIXSET=PM&FORMAT=image/jpeg" +
        "&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}"

    fun tileUrl(z: Int, x: Int, y: Int): String =
        TILE_URL.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())
}
