package fr.roadly.core.navigation

/** Règle de silence pendant un appel, séparée du service vocal pour être testée sans Android. */
internal object SpeechFocus {
    // Valeurs de `AudioManager.MODE_*` (constantes de l'API Android, stables depuis l'API 1 / 11).
    private const val MODE_RINGTONE = 1
    private const val MODE_IN_CALL = 2
    private const val MODE_IN_COMMUNICATION = 3
    /** API 30 : appel filtré par l'assistant ; API 33 : appel redirigé ou communication redirigée. */
    private const val MODE_CALL_SCREENING = 4
    private const val MODE_CALL_REDIRECT = 5
    private const val MODE_COMMUNICATION_REDIRECT = 6

    /** Vrai si le téléphone sonne ou si un appel (téléphonie ou voix sur IP) est en cours : ni annonce ni bip. */
    fun inCall(audioMode: Int): Boolean = audioMode in setOf(
        MODE_RINGTONE, MODE_IN_CALL, MODE_IN_COMMUNICATION, MODE_CALL_SCREENING, MODE_CALL_REDIRECT, MODE_COMMUNICATION_REDIRECT,
    )
}
