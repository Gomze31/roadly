package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Phrases réécrites pour la synthèse vocale, et choix de la voix (constat en voiture : noms mal prononcés, voix robotique). */
class SpeechTextTest {
    private fun n(s: String) = SpeechText.normalize(s)

    @Test
    fun `numéros de route, lettre et nombre séparés`() {
        assertEquals("Prenez la sortie 16 vers A 61.", n("Prenez la sortie 16 vers A61."))
        assertEquals("Continuez sur D 16.", n("Continuez sur D16."))
        assertEquals("Suivez N 20, puis D 820.", n("Suivez RN20, puis RD 820."))
        assertEquals("Prenez A 620, E 80.", n("Prenez A620/E80."))
        assertEquals("Continuez sur D 2 A.", n("Continuez sur D2a."))
    }

    @Test
    fun `un mot qui commence par une lettre de réseau n'est pas un numéro de route`() {
        assertEquals("Tournez à droite sur Rue Dupont.", n("Tournez à droite sur Rue Dupont."))
        assertEquals("Dans 300 mètres, tournez à gauche.", n("Dans 300 mètres, tournez à gauche."))
    }

    @Test
    fun `abréviations de voirie et de titres`() {
        assertEquals("Tournez à droite sur Avenue du Général Leclerc.", n("Tournez à droite sur Av. du Gal Leclerc."))
        assertEquals("Continuez sur Boulevard Saint-Michel.", n("Continuez sur Bd St-Michel."))
        assertEquals("Prenez Rue du Docteur Schweitzer.", n("Prenez Rue du Dr Schweitzer."))
        assertEquals("Tournez sur Chemin des Vignes.", n("Tournez sur Ch. des Vignes."))
        assertEquals("Place Sainte Anne.", n("Pl. Ste Anne."))
    }

    @Test
    fun `les mots courants ne sont pas pris pour des abréviations`() {
        assertEquals("Continuez vers Col de Puymorens.", n("Continuez vers Col de Puymorens."))
        assertEquals("Rue du Lot et de la Garonne.", n("Rue du Lot et de la Garonne."))
        assertEquals("Prenez la 2e sortie.", n("Prenez la 2e sortie."))
    }

    @Test
    fun `chiffres romains des noms propres`() {
        assertEquals("Boulevard Henri 4.", n("Bd Henri IV."))
        assertEquals("Avenue Georges 5.", n("Av. Georges V."))
        assertEquals("Rue Louis 14.", n("Rue Louis XIV."))
        assertEquals("Place François premier.", n("Place François Ier."))
        assertEquals("Rue Vincent Auriol.", n("Rue Vincent Auriol."))
    }

    @Test
    fun `unités`() {
        assertEquals("Limitation 90 kilomètres heure.", n("Limitation 90 km/h."))
    }

    private fun voice(name: String, tag: String = "fr-FR", quality: Int = 300, latency: Int = 300, network: Boolean = false, installed: Boolean = true) =
        VoiceCandidate(name, tag, quality, latency, network, installed)

    @Test
    fun `la meilleure qualité embarquée l'emporte`() {
        val voices = listOf(voice("ancienne", quality = 300), voice("neuronale", quality = 400), voice("anglaise", tag = "en-US", quality = 500))
        assertEquals("neuronale", VoiceRanking.best(voices, networkAvailable = false)?.name)
    }

    @Test
    fun `sans réseau, une voix en ligne n'est jamais retenue`() {
        val voices = listOf(voice("locale", quality = 300), voice("en-ligne", quality = 500, network = true))
        assertEquals("locale", VoiceRanking.best(voices, networkAvailable = false)?.name)
    }

    @Test
    fun `avec réseau, la voix en ligne doit être nettement meilleure`() {
        val local = voice("locale", quality = 400)
        assertEquals("locale", VoiceRanking.best(listOf(local, voice("en-ligne", quality = 500, network = true)), networkAvailable = true)?.name)
        assertEquals("en-ligne", VoiceRanking.best(listOf(voice("locale", quality = 200), voice("en-ligne", quality = 500, network = true)), networkAvailable = true)?.name)
    }

    @Test
    fun `français de France avant les autres français, voix non installée écartée`() {
        val voices = listOf(voice("canada", tag = "fr-CA", quality = 500), voice("france", quality = 300), voice("absente", quality = 500, installed = false))
        assertEquals("france", VoiceRanking.best(voices, networkAvailable = false)?.name)
        assertNull(VoiceRanking.best(listOf(voice("anglaise", tag = "en-GB")), networkAvailable = false))
    }
}
