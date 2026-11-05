package fr.roadly.core.radar

import fr.roadly.core.model.GeoPoint
import kotlin.math.max
import kotlin.math.pow

/** Ligne brute d'une source, avant validation (champs tels que publiés). */
data class RawRadar(
    val id: String?,
    val lat: Double?,
    val lon: Double?,
    val latText: String,
    val lonText: String,
    val kindCode: String?,
    val kindLabel: String? = null,
    val speedLimitText: String? = null,
    val heavySpeedLimitText: String? = null,
    val directionText: String? = null,
    val road: String? = null,
    val commune: String? = null,
    val department: String? = null,
    val sectionLengthKmText: String? = null,
    val commissionedAt: String? = null,
)

/** Découpage CSV (séparateur paramétrable, guillemets doubles). */
internal object Csv {
    fun rows(text: String, separator: Char): List<List<String>> = text.lineSequence().filter { it.isNotBlank() }.map { split(it, separator) }.toList()

    private fun split(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quoted = false
        for (ch in line) {
            when {
                ch == '"' -> quoted = !quoted
                ch == sep && !quoted -> { out += sb.toString(); sb.setLength(0) }
                ch == '\r' -> Unit
                else -> sb.append(ch)
            }
        }
        out += sb.toString()
        return out
    }

    /** En-tête normalisé : minuscules, sans accents ni espaces superflus (le fichier 2025 est en ISO-8859-1). */
    fun norm(h: String): String = java.text.Normalizer.normalize(h.trim().lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace('�', ' ').trim()
}

/** Parseurs des deux jeux officiels (Licence Ouverte 2.0). */
object RadarSources {
    /**
     * « Liste des radars fixes en France » (DSR, annuel) : `Numéro de radar;Type de radar;Date de mise en service;VMA ;Latitude; Longitude`.
     * Colonnes repérées par leur début (robuste au mauvais décodage des accents).
     */
    fun parseFixedList(text: String): List<RawRadar> {
        val rows = Csv.rows(text.removePrefix("﻿"), ';')
        if (rows.isEmpty()) return emptyList()
        val h = rows.first().map(Csv::norm)
        fun col(prefix: String) = h.indexOfFirst { it.startsWith(prefix) }
        val iId = col("num"); val iType = col("type"); val iDate = col("date"); val iVma = col("vma"); val iLat = col("lat"); val iLon = col("lon")
        if (iLat < 0 || iLon < 0) return emptyList()
        return rows.drop(1).map { c ->
            val latT = c.getOrNull(iLat)?.trim().orEmpty(); val lonT = c.getOrNull(iLon)?.trim().orEmpty()
            RawRadar(
                id = c.getOrNull(iId)?.trim()?.ifBlank { null },
                lat = latT.toDoubleOrNull(), lon = lonT.toDoubleOrNull(), latText = latT, lonText = lonT,
                kindCode = c.getOrNull(iType)?.trim()?.ifBlank { null },
                speedLimitText = c.getOrNull(iVma)?.trim(),
                commissionedAt = c.getOrNull(iDate)?.trim()?.substringBefore(' ')?.ifBlank { null },
            )
        }
    }

    /** « Radars automatiques » (2018, extraction du site radars.securite-routiere.gouv.fr) : sens, route, commune, tronçon. */
    fun parse2018(text: String): List<RawRadar> {
        val rows = Csv.rows(text.removePrefix("﻿"), ',')
        if (rows.isEmpty()) return emptyList()
        val h = rows.first().map(Csv::norm)
        fun col(name: String) = h.indexOf(name)
        val iLat = col("latitude"); val iLon = col("longitude")
        if (iLat < 0 || iLon < 0) return emptyList()
        val iId = col("id"); val iDir = col("direction"); val iType = col("type"); val iRoute = col("route"); val iDep = col("departement")
        val iPlace = col("emplacement"); val iLen = col("longueur_troncon_km"); val iVl = col("vitesse_vehicules_legers_kmh"); val iPl = col("vitesse_poids_lourds_kmh")
        val iInst = col("date_installation")
        return rows.drop(1).map { c ->
            fun v(i: Int) = c.getOrNull(i)?.trim()?.ifBlank { null }
            val latT = v(iLat).orEmpty(); val lonT = v(iLon).orEmpty()
            RawRadar(
                id = v(iId), lat = latT.toDoubleOrNull(), lon = lonT.toDoubleOrNull(), latText = latT, lonText = lonT,
                kindCode = null, kindLabel = v(iType), speedLimitText = v(iVl), heavySpeedLimitText = v(iPl), directionText = v(iDir),
                road = v(iRoute), commune = v(iPlace), department = v(iDep), sectionLengthKmText = v(iLen), commissionedAt = v(iInst),
            )
        }
    }
}

/** Sens publié → cap contrôlé. Seuls les points cardinaux se convertissent ; « PARIS vers LYON » reste un libellé. */
object DirectionParser {
    private val cardinal = mapOf(
        "nord" to 0.0, "nord est" to 45.0, "est" to 90.0, "sud est" to 135.0,
        "sud" to 180.0, "sud ouest" to 225.0, "ouest" to 270.0, "nord ouest" to 315.0,
    )

    private fun card(s: String): Double? = cardinal[Csv.norm(s).replace('-', ' ').replace(Regex("\\s+"), " ").trim()]

    fun parse(text: String?): ControlledDirection? {
        val t = text?.trim()?.ifBlank { null } ?: return null
        if (Csv.norm(t) == "double sens") return ControlledDirection(null, bothWays = true, label = t)
        val parts = t.split(Regex("\\s+vers\\s+", RegexOption.IGNORE_CASE))
        if (parts.size == 2) {
            val from = card(parts[0]); val to = card(parts[1])
            // « Nord vers Sud » : on circule vers le sud. Origine et destination doivent être opposées (±45°).
            if (from != null && to != null && GeoMath.angleDiff(from, to) >= 135.0) return ControlledDirection(to, bothWays = false, label = t)
        }
        return ControlledDirection(null, bothWays = false, label = t)
    }
}

/** Contrôles de validité, précision et score de confiance. */
object RadarQuality {
    /** France métropolitaine et Corse ; DROM. */
    private val boxes = listOf(
        doubleArrayOf(41.0, -5.5, 51.5, 10.0), doubleArrayOf(15.8, -61.9, 16.6, -60.9), doubleArrayOf(14.3, -61.3, 15.0, -60.7),
        doubleArrayOf(2.0, -54.7, 6.0, -51.5), doubleArrayOf(-21.5, 55.1, -20.8, 55.9), doubleArrayOf(-13.1, 44.9, -12.5, 45.4),
    )

    fun inFrance(lat: Double, lon: Double): Boolean = boxes.any { lat in it[0]..it[2] && lon in it[1]..it[3] }

    /** Limitations réglementaires plausibles (km/h). */
    val ALLOWED_LIMITS = setOf(20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130)

    fun speed(text: String?): Int? = text?.trim()?.toIntOrNull()?.takeIf { it in ALLOWED_LIMITS }

    /** Demi-pas de la dernière décimale publiée, en mètres, borné à 5 m (pose du point sur l'accotement). */
    fun precisionFromText(latText: String, lonText: String): Double {
        fun decimals(s: String) = s.substringAfter('.', "").trim().length
        val d = minOf(decimals(latText), decimals(lonText))
        return max(5.0, 0.5 * 10.0.pow(-d) * GeoMath.M_PER_DEG_LAT)
    }

    /** Coordonnées identiques partagées par au moins 3 radars : point générique (mairie, centre), pas un emplacement réel. */
    const val SHARED_POINT_MIN = 3
    const val SHARED_POINT_PRECISION_M = 500.0

    /**
     * Score 0–100. Source officielle : 70 ; précision ≤ 10 m : +15, ≤ 60 m : +5, au-delà : −20, au-delà de 300 m : −45 ;
     * enrichissement concordant (même équipement à ≤ 30 m dans un second jeu) : +10 ; catégorie inconnue : −10.
     */
    fun confidence(precisionM: Double, corroborated: Boolean, kind: RadarKind): Int {
        var s = 70
        s += when {
            precisionM <= 10.0 -> 15
            precisionM <= 60.0 -> 5
            precisionM <= 300.0 -> -20
            else -> -45
        }
        if (corroborated) s += 10
        if (kind == RadarKind.UNKNOWN) s -= 10
        return s.coerceIn(0, 100)
    }

    /** Seuils d'usage du score. */
    const val MIN_ALERT = 40
    const val MIN_VOICE = 60
}

/** Rapport d'une fusion : ce qui a été écarté, enrichi, et la qualité obtenue. */
data class MergeReport(
    val read: Int,
    val kept: Int,
    val rejectedInvalid: Int,
    val rejectedDuplicateId: Int,
    val sharedPointRadars: Int,
    val enriched: Int,
    val withDirection: Int,
    val withSpeedLimit: Int,
    val highPrecision: Int,
)

/**
 * Fusion : la liste officielle à jour fait foi (existence, type, vitesse, position) ; le jeu 2018 n'apporte que des champs
 * absents (sens, route, commune, département, longueur de tronçon, vitesse PL), et seulement pour un équipement compatible
 * à moins de [ENRICH_MAX_M] mètres.
 */
object RadarMerger {
    const val ENRICH_MAX_M = 50.0
    const val CORROBORATE_MAX_M = 30.0

    data class Result(val radars: List<Radar>, val report: MergeReport)

    fun merge(primary: List<RawRadar>, enrichment: List<RawRadar>, collectedAtMs: Long, sourceVersion: String?): Result {
        var invalid = 0
        var dupIds = 0
        val seen = HashSet<String>()
        val valid = ArrayList<RawRadar>()
        for (r in primary) {
            val lat = r.lat; val lon = r.lon; val id = r.id
            if (lat == null || lon == null || id == null || !RadarQuality.inFrance(lat, lon)) { invalid++; continue }
            if (!seen.add(id)) { dupIds++; continue }
            valid += r
        }
        val pointCounts = valid.groupingBy { it.latText to it.lonText }.eachCount()
        // Le jeu 2018 est indexé comme des radars « fantômes » dont l'identifiant est le rang de la ligne source.
        val enrichIndex = RadarIndex(enrichment.mapIndexedNotNull { i, e ->
            val la = e.lat ?: return@mapIndexedNotNull null; val lo = e.lon ?: return@mapIndexedNotNull null
            Radar(i.toString(), GeoPoint(la, lo), RadarKind.fromLabel2018(e.kindLabel) ?: RadarKind.UNKNOWN, source = "idx", collectedAtMs = 0, precisionM = 0.0, confidence = 0)
        })
        var enriched = 0; var shared = 0
        val radars = valid.map { r ->
            val point = GeoPoint(r.lat!!, r.lon!!)
            val kind = RadarKind.fromCode(r.kindCode)
            val isShared = (pointCounts[r.latText to r.lonText] ?: 0) >= RadarQuality.SHARED_POINT_MIN
            if (isShared) shared++
            val precision = if (isShared) RadarQuality.SHARED_POINT_PRECISION_M else RadarQuality.precisionFromText(r.latText, r.lonText)
            val match = if (isShared) null else enrichIndex.near(point, ENRICH_MAX_M)
                .filter { compatible(kind, it.kind) }
                .minByOrNull { it.point.distanceTo(point) }
            val e = match?.let { enrichment[it.id.toInt()] }
            val dist = match?.point?.distanceTo(point)
            if (e != null) enriched++
            Radar(
                id = r.id!!, point = point, kind = kind,
                speedLimitKmh = RadarQuality.speed(r.speedLimitText),
                heavySpeedLimitKmh = e?.let { RadarQuality.speed(it.heavySpeedLimitText) },
                direction = e?.let { DirectionParser.parse(it.directionText) },
                road = e?.road, commune = e?.commune, department = e?.department,
                sectionLengthM = if (kind == RadarKind.SECTION) e?.sectionLengthKmText?.toDoubleOrNull()?.takeIf { it in 0.2..60.0 }?.times(1000) else null,
                commissionedAt = r.commissionedAt,
                source = RadarSourceIds.FIXED_FR, sourceVersion = sourceVersion,
                enrichedFrom = e?.let { RadarSourceIds.AUTOMATIQUES_2018 },
                collectedAtMs = collectedAtMs, verifiedAtMs = collectedAtMs,
                precisionM = precision,
                confidence = RadarQuality.confidence(precision, dist != null && dist <= CORROBORATE_MAX_M, kind),
            )
        }
        val report = MergeReport(
            read = primary.size, kept = radars.size, rejectedInvalid = invalid, rejectedDuplicateId = dupIds, sharedPointRadars = shared,
            enriched = enriched, withDirection = radars.count { it.direction?.isKnownOneWay == true || it.direction?.bothWays == true },
            withSpeedLimit = radars.count { it.speedLimitKmh != null }, highPrecision = radars.count { it.precisionM <= 10.0 },
        )
        return Result(radars, report)
    }

    /** Le jeu 2018 ne distingue pas ETT / ETU / ETF : « Radar fixe » enrichit ces trois catégories. */
    private fun compatible(kind: RadarKind, label2018: RadarKind): Boolean = when (kind) {
        RadarKind.FIXED, RadarKind.NEW_GENERATION, RadarKind.URBAN -> label2018 == RadarKind.FIXED
        RadarKind.UNKNOWN -> false
        else -> label2018 == kind
    }
}

/** Différences entre deux versions de la base (par identifiant). */
data class RadarDiff(val added: Int, val removed: Int, val moved: Int, val limitChanged: Int) {
    companion object {
        const val MOVED_M = 30.0

        fun of(previous: List<Radar>, next: List<Radar>): RadarDiff {
            val prev = previous.filter { it.status == RadarStatus.ACTIVE }.associateBy { it.id }
            val nextIds = next.associateBy { it.id }
            var moved = 0; var limit = 0
            next.forEach { n -> prev[n.id]?.let { p -> if (p.point.distanceTo(n.point) > MOVED_M) moved++; if (p.speedLimitKmh != n.speedLimitKmh) limit++ } }
            return RadarDiff(added = nextIds.keys.count { it !in prev }, removed = prev.keys.count { it !in nextIds }, moved = moved, limitChanged = limit)
        }
    }
}
