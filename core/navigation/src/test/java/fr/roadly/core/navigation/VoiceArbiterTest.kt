package fr.roadly.core.navigation

import fr.roadly.core.navigation.SpeechPriority.LOW
import fr.roadly.core.navigation.SpeechPriority.NORMAL
import fr.roadly.core.navigation.SpeechPriority.URGENT
import fr.roadly.core.navigation.VoiceChannel.ASSISTANT
import fr.roadly.core.navigation.VoiceChannel.GUIDANCE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** E1 : un seul canal de parole — urgence GPS > guidage > assistant ; l'assistant reprend 300 ms après le guidage (scénario 17). */
class VoiceArbiterTest {
    private fun item(text: String, p: SpeechPriority, at: Long, ch: VoiceChannel) = SpeechQueue.Item(text, p, at, ch)

    /** Moteur simulé comme `AndroidSpeechService` : un énoncé à la fois, fin et relance aux instants choisis par le test. */
    private class Engine(val q: SpeechQueue = SpeechQueue()) {
        var active: SpeechQueue.Item? = null
        /** (instant de début, texte) de chaque énoncé confié au moteur. */
        val said = mutableListOf<Pair<Long, String>>()
        val cut = mutableListOf<String>()

        fun offer(text: String, p: SpeechPriority, ch: VoiceChannel, at: Long, flush: Boolean = false) {
            val current = active
            if (q.offer(SpeechQueue.Item(text, p, at, ch), flush, current)) { current?.let { cut += it.text }; active = null }
            if (active == null) next(at)
        }
        fun finish(at: Long) { active?.let { q.onFinished(it.channel, at) }; active = null; next(at) }
        /** Relance programmée par le coordinateur (fin du délai de 300 ms). */
        fun tick(at: Long) { if (active == null) next(at) }
        private fun next(at: Long) { q.poll(at)?.let { active = it; said += at to it.text } }
    }

    @Test fun `arbitration table - urgent cuts all, guidance pre-empts the assistant, the assistant never pre-empts`() {
        val both = setOf(GUIDANCE, ASSISTANT)
        assertEquals(Arbitration(true, false, both), VoiceArbiter.arbitrate(GUIDANCE, URGENT, false, ASSISTANT))
        assertEquals(Arbitration(true, false, both), VoiceArbiter.arbitrate(GUIDANCE, URGENT, false, null))
        assertEquals(Arbitration(true, true, emptySet()), VoiceArbiter.arbitrate(GUIDANCE, NORMAL, false, ASSISTANT))
        assertEquals("FAR / vigilance aussi", Arbitration(true, true, emptySet()), VoiceArbiter.arbitrate(GUIDANCE, LOW, false, ASSISTANT))
        assertEquals(Arbitration(false, false, emptySet()), VoiceArbiter.arbitrate(GUIDANCE, NORMAL, false, GUIDANCE))
        assertEquals(Arbitration(true, false, setOf(GUIDANCE)), VoiceArbiter.arbitrate(GUIDANCE, NORMAL, true, GUIDANCE))
        assertEquals(Arbitration(true, true, setOf(GUIDANCE)), VoiceArbiter.arbitrate(GUIDANCE, NORMAL, true, ASSISTANT))
        assertEquals(Arbitration(false, false, emptySet()), VoiceArbiter.arbitrate(ASSISTANT, LOW, false, GUIDANCE))
        assertEquals(Arbitration(false, false, setOf(ASSISTANT)), VoiceArbiter.arbitrate(ASSISTANT, LOW, true, GUIDANCE))
        assertEquals(Arbitration(true, false, setOf(ASSISTANT)), VoiceArbiter.arbitrate(ASSISTANT, LOW, true, ASSISTANT))
    }

    @Test fun `scenario 17 - a now cuts the assistant and drops its queue, the near follows, the assistant speaks again 300 ms later`() {
        val e = Engine()
        e.offer("Le parking le plus proche est à 400 mètres.", LOW, ASSISTANT, at = 0)
        e.offer("Il reste 3 places.", LOW, ASSISTANT, at = 100)
        e.offer("Tournez à droite.", URGENT, GUIDANCE, at = 1_000)
        assertEquals("coupé, pas mis en pause", listOf("Le parking le plus proche est à 400 mètres."), e.cut)
        e.offer("Dans 150 mètres, tournez à gauche.", NORMAL, GUIDANCE, at = 1_200)
        e.finish(at = 2_000)
        assertEquals("Dans 150 mètres, tournez à gauche.", e.active!!.text)
        e.offer("Voulez-vous que je vous y guide ?", LOW, ASSISTANT, at = 2_500)
        e.finish(at = 4_000)
        assertNull("l'assistant attend 300 ms après le guidage", e.active)
        assertEquals(4_300L, e.q.readyAtMs(4_000))
        e.tick(at = 4_300)
        assertEquals("Voulez-vous que je vous y guide ?", e.active!!.text)
        assertEquals(
            "« Il reste 3 places. » jeté avec la file de l'assistant",
            listOf(0L to "Le parking le plus proche est à 400 mètres.", 1_000L to "Tournez à droite.", 2_000L to "Dans 150 mètres, tournez à gauche.", 4_300L to "Voulez-vous que je vous y guide ?"),
            e.said,
        )
    }

    @Test fun `a near cuts the assistant, which is said again in full 300 ms after the guidance`() {
        val e = Engine()
        e.offer("Il y a une station à 2 kilomètres.", LOW, ASSISTANT, at = 0)
        e.offer("Dans 300 mètres, tournez à droite.", NORMAL, GUIDANCE, at = 500)
        assertEquals(listOf("Il y a une station à 2 kilomètres."), e.cut)
        assertEquals("Dans 300 mètres, tournez à droite.", e.active!!.text)
        e.finish(at = 2_500)
        assertNull(e.active)
        e.tick(at = 2_799)
        assertNull("pas avant 300 ms", e.active)
        e.tick(at = 2_800)
        assertEquals("redit en entier", "Il y a une station à 2 kilomètres.", e.active!!.text)
    }

    @Test fun `the assistant never cuts the guidance, even with flush, and waits for its end`() {
        val e = Engine()
        e.offer("Dans 2 kilomètres, prenez la sortie 19.", LOW, GUIDANCE, at = 0)
        e.offer("Essai.", LOW, ASSISTANT, at = 100, flush = true)
        assertTrue(e.cut.isEmpty())
        assertEquals("Dans 2 kilomètres, prenez la sortie 19.", e.active!!.text)
        e.finish(at = 3_000)
        e.tick(at = 3_300)
        assertEquals("Essai.", e.active!!.text)
    }

    @Test fun `an assistant flush replaces only the assistant message`() {
        val e = Engine()
        e.offer("Première réponse.", LOW, ASSISTANT, at = 0)
        e.offer("Réponse corrigée.", LOW, ASSISTANT, at = 100, flush = true)
        assertEquals(listOf("Première réponse."), e.cut)
        assertEquals("Réponse corrigée.", e.active!!.text)
    }

    @Test fun `an assistant message waiting more than 8 s is dropped`() {
        val q = SpeechQueue()
        val g = item("Guidage", NORMAL, 0, GUIDANCE)
        q.offer(g)
        assertEquals(g, q.poll(0))
        q.offer(item("Assistant", LOW, 0, ASSISTANT), active = g)
        q.onFinished(GUIDANCE, 9_000)
        assertNull(q.poll(9_300))
        assertNull(q.readyAtMs(9_300))
        assertTrue(q.isEmpty)
    }

    @Test fun `clearing the guidance keeps the assistant messages, clearing all drops both`() {
        val q = SpeechQueue()
        q.offer(item("g", NORMAL, 0, GUIDANCE)); q.offer(item("a", LOW, 0, ASSISTANT))
        q.clear(GUIDANCE)
        assertEquals("a", q.poll(100)!!.text)
        q.offer(item("g", NORMAL, 0, GUIDANCE)); q.offer(item("a", LOW, 0, ASSISTANT))
        q.clear()
        assertTrue(q.isEmpty)
    }

    @Test fun `assistant work covers the message in progress and the queue - the engine release waits for it`() {
        val q = SpeechQueue()
        val a = item("a", LOW, 0, ASSISTANT)
        val g = item("g", NORMAL, 0, GUIDANCE)
        assertFalse(q.hasAssistantWork(null))
        assertFalse(q.hasAssistantWork(g))
        assertTrue("message en cours", q.hasAssistantWork(a))
        q.offer(a, active = g)
        assertTrue("message en file", q.hasAssistantWork(g))
    }
}
