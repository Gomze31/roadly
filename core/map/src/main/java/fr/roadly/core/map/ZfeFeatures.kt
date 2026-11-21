package fr.roadly.core.map

import fr.roadly.core.model.GeoPoint
import java.util.Locale

/** Périmètre d'une ZFE à dessiner : anneaux (contour puis trous) de chaque polygone, et état (« forbidden », « caution », « ok »). */
data class ZfeShape(val state: String, val polygons: List<List<List<GeoPoint>>>)

/** GeoJSON des périmètres ZFE (calques `roadly-zfe-*`) : remplissage translucide et contour, couleur selon l'état. */
object ZfeFeatures {
    const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
    const val OK_COLOR = "#2BC48A"
    const val CAUTION_COLOR = "#F5A524"
    const val FORBIDDEN_COLOR = "#FF4D6D"

    fun build(shapes: List<ZfeShape>): String {
        if (shapes.isEmpty()) return EMPTY
        val sb = StringBuilder("""{"type":"FeatureCollection","features":[""")
        shapes.forEachIndexed { i, s ->
            if (i > 0) sb.append(',')
            sb.append("""{"type":"Feature","properties":{"state":"""").append(s.state).append(""""},"geometry":{"type":"MultiPolygon","coordinates":[""")
            s.polygons.forEachIndexed { pi, rings ->
                if (pi > 0) sb.append(',')
                sb.append('[')
                rings.forEachIndexed { ri, ring ->
                    if (ri > 0) sb.append(',')
                    sb.append('[')
                    val closed = if (ring.isNotEmpty() && ring.first() != ring.last()) ring + ring.first() else ring
                    closed.forEachIndexed { k, p ->
                        if (k > 0) sb.append(',')
                        sb.append(String.format(Locale.ROOT, "[%.6f,%.6f]", p.lon, p.lat))
                    }
                    sb.append(']')
                }
                sb.append(']')
            }
            sb.append("]}}")
        }
        return sb.append("]}").toString()
    }
}
