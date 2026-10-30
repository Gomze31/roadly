package fr.roadly.core.data.vigilance

import fr.roadly.core.data.db.VigilanceZoneEntity

/**
 * Lecture du fichier « Radars automatiques » (Sécurité routière, data.gouv.fr, Licence Ouverte 2.0).
 * Colonnes utilisées : `latitude`, `longitude`, `id`, `direction`, `type`, `route`, `vitesse_vehicules_legers_kmh`.
 * Seules les coordonnées sont obligatoires ; une ligne illisible est ignorée, jamais inventée.
 */
object VigilanceCsv {
    fun parse(text: String, updatedAtMs: Long): List<VigilanceZoneEntity> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.iterator()
        if (!lines.hasNext()) return emptyList()
        val header = split(lines.next()).map { it.trim().lowercase() }
        val iLat = header.indexOf("latitude"); val iLon = header.indexOf("longitude")
        if (iLat < 0 || iLon < 0) return emptyList()
        val iId = header.indexOf("id"); val iDir = header.indexOf("direction"); val iType = header.indexOf("type")
        val iRoute = header.indexOf("route"); val iLimit = header.indexOf("vitesse_vehicules_legers_kmh")
        val out = ArrayList<VigilanceZoneEntity>()
        var n = 0
        for (line in lines) {
            val c = split(line)
            val lat = c.getOrNull(iLat)?.trim()?.toDoubleOrNull() ?: continue
            val lon = c.getOrNull(iLon)?.trim()?.toDoubleOrNull() ?: continue
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) continue
            val id = c.getOrNull(iId)?.trim()?.ifBlank { null } ?: "row${n}"
            out += VigilanceZoneEntity(
                id = id, lat = lat, lon = lon,
                direction = c.getOrNull(iDir)?.trim()?.ifBlank { null },
                type = c.getOrNull(iType)?.trim()?.ifBlank { null },
                road = c.getOrNull(iRoute)?.trim()?.ifBlank { null },
                limitKmh = c.getOrNull(iLimit)?.trim()?.toIntOrNull(),
                updatedAt = updatedAtMs,
            )
            n++
        }
        return out
    }

    /** Découpage CSV minimal : virgules, guillemets doubles. */
    private fun split(line: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        for (ch in line) {
            when {
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out += sb.toString(); sb.setLength(0) }
                else -> sb.append(ch)
            }
        }
        out += sb.toString()
        return out
    }
}
