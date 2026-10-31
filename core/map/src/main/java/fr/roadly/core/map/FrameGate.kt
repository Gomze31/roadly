package fr.roadly.core.map

/**
 * C3 : laisse passer au plus une écriture tous les [minIntervalMs] (horloge en ms, ex. `ValueAnimator.currentPlayTime`).
 * Sert à caler l'animation du marqueur, cadencée par l'écran (90 à 120 Hz), sur le plafond de rendu de la carte (60 ou 30 i/s) :
 * une image que la carte ne dessinera pas n'a pas besoin de son GeoJSON. La dernière image d'une animation passe toujours.
 */
internal class FrameGate(var minIntervalMs: Long) {
    private var lastWriteMs: Long? = null

    /** Vrai si l'écriture à [nowMs] doit se faire (et la note) ; [last] = dernière image, jamais sautée. */
    fun pass(nowMs: Long, last: Boolean = false): Boolean {
        val previous = lastWriteMs
        if (!last && previous != null && nowMs - previous < minIntervalMs) return false
        lastWriteMs = nowMs
        return true
    }

    /** Nouvelle animation : sa première image passe. */
    fun reset() { lastWriteMs = null }

    companion object {
        /** Intervalle minimal (ms) entre deux écritures pour un rendu plafonné à [fps] images par seconde (au moins 1). */
        fun intervalFor(fps: Int): Long = 1_000L / fps.coerceAtLeast(1)
    }
}
