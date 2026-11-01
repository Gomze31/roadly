package fr.roadly.core.model

/** E2 : économie d'énergie du guidage (sans bâtiments, inclinaison 45°, 30 images/s) — AUTO suit l'économiseur d'Android, ON / OFF la forcent. */
enum class PowerSaveMode {
    AUTO, ON, OFF;

    /** Économie effective, selon l'économiseur d'Android ([systemSaver]). */
    fun effective(systemSaver: Boolean): Boolean = when (this) {
        AUTO -> systemSaver
        ON -> true
        OFF -> false
    }
}
