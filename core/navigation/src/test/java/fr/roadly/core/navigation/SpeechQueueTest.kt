package fr.roadly.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** B5 : file de parole — un NOW (URGENT) coupe un FAR long et vide la file ; une phrase périmée n'est pas dite. */
class SpeechQueueTest {
    private fun item(text: String, p: SpeechPriority, at: Long = 0L) = SpeechQueue.Item(text, p, at)

    @Test fun `items are said in order of arrival`() {
        val q = SpeechQueue()
        assertFalse(q.offer(item("a", SpeechPriority.NORMAL)))
        assertFalse(q.offer(item("b", SpeechPriority.LOW)))
        assertEquals("a", q.poll(1_000)!!.text)
        assertEquals("b", q.poll(1_000)!!.text)
        assertNull(q.poll(1_000))
    }

    @Test fun `a long far in progress is cut by a now, and queued phrases are dropped`() {
        val q = SpeechQueue()
        q.offer(item("Dans 2 kilomètres, prenez la sortie 19 vers Carcassonne.", SpeechPriority.LOW))
        q.offer(item("Dans 300 mètres, prenez la sortie.", SpeechPriority.NORMAL))
        assertTrue("URGENT interrompt l'annonce en cours", q.offer(item("Prenez la sortie.", SpeechPriority.URGENT, 100)))
        assertEquals("Prenez la sortie.", q.poll(200)!!.text)
        assertNull("FAR et NEAR en attente jetés", q.poll(200))
    }

    @Test fun `low and normal phrases expire after 8 s of waiting, urgent never`() {
        val q = SpeechQueue()
        q.offer(item("lent", SpeechPriority.LOW)); q.offer(item("normal", SpeechPriority.NORMAL))
        assertNull(q.poll(GuidanceConstants.SPEECH_STALE_AFTER_MS + 1))
        q.offer(item("urgent", SpeechPriority.URGENT))
        assertEquals("urgent", q.poll(60_000)!!.text)
    }

    @Test fun `flush also interrupts`() = assertTrue(SpeechQueue().offer(item("x", SpeechPriority.NORMAL), flush = true))
}
