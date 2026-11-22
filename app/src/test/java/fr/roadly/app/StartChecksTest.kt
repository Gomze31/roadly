package fr.roadly.app

import fr.roadly.core.location.LocationIssue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartChecksTest {
    @Test fun `guidance does not start with location switched off or approximate-only location`() {
        assertTrue(StartChecks.blocksGuidance(LocationIssue.SERVICES_OFF))
        assertTrue(StartChecks.blocksGuidance(LocationIssue.APPROXIMATE_ONLY))
    }

    @Test fun `guidance starts as soon as the location issue is gone`() {
        assertFalse(StartChecks.blocksGuidance(null))
    }

    @Test fun `no French voice - the warning is shown once, then never again in the same launch`() {
        assertTrue(StartChecks.voiceNoticeDue(voiceAvailable = false, alreadyShown = false))
        assertFalse(StartChecks.voiceNoticeDue(voiceAvailable = false, alreadyShown = true))
    }

    @Test fun `a working voice never shows the warning`() {
        assertFalse(StartChecks.voiceNoticeDue(voiceAvailable = true, alreadyShown = false))
        assertFalse(StartChecks.voiceNoticeDue(voiceAvailable = true, alreadyShown = true))
    }
}
