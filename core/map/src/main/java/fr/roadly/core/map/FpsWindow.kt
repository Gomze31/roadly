package fr.roadly.core.map

/** C3 : moyenne des images par seconde du rendu sur une fenêtre de [windowMs] (journal de mesure, builds débogables). */
internal class FpsWindow(private val windowMs: Long = 5_000L) {
    private var startMs = -1L
    private var sum = 0.0
    private var count = 0

    /** Ajoute un échantillon ; renvoie la moyenne quand la fenêtre est écoulée (puis repart de zéro), sinon null. */
    fun add(fps: Double, nowMs: Long): Double? {
        if (startMs < 0) startMs = nowMs
        sum += fps
        count++
        if (nowMs - startMs < windowMs) return null
        return (sum / count).also { startMs = nowMs; sum = 0.0; count = 0 }
    }
}
