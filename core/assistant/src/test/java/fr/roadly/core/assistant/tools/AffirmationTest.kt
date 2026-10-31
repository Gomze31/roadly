package fr.roadly.core.assistant.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AffirmationTest {
    @Test fun `clear agreement is accepted`() {
        listOf("Oui", "ouais vas-y", "D'accord", "ok", "Confirmé", "c'est ça", "oui oui allez").forEach { assertTrue(it, Affirmation.isYes(it)) }
    }
    @Test fun `negation, hesitation or silence is refused`() {
        listOf("", "non", "non merci", "attends", "annule", "oui… enfin non", "surtout pas", "laisse tomber", "peut-être").forEach { assertFalse(it, Affirmation.isYes(it)) }
    }
}
