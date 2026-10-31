package fr.roadly.core.model

/** Types de signalement (mêmes noms que l'enum SQL `report_kind`). */
enum class ReportKind { ACCIDENT, JAM, OBSTACLE, CLOSED, ROADWORK, WEATHER, POTHOLE, WORK_LIGHT, HAZARD }

/** Signalement actif renvoyé par le service communautaire. */
data class CommunityReport(
    val id: String,
    val kind: ReportKind,
    val point: GeoPoint,
    val createdAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val confirmations: Int,
    val denials: Int,
    /** Émis par cet appareil (pas de vote possible). */
    val mine: Boolean,
)
