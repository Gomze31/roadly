package fr.roadly.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoReadinessTest {
    @Test fun sideloadedBuildWithCarServiceNeedsUnknownSources() {
        val c = AndroidAutoReadiness.checks(AndroidAutoFacts(true, "14.2", "com.google.android.packageinstaller", "0.1.177"))
        assertEquals(listOf(CheckStatus.OK, CheckStatus.OK, CheckStatus.ACTION), c.map { it.status })
        assertTrue(c.last().title.contains("Sources inconnues"))
    }

    @Test fun oldBuildWithoutCarServiceIsBlocking() {
        val c = AndroidAutoReadiness.checks(AndroidAutoFacts(false, "14.2", null, "0.1.172"))
        assertEquals(CheckStatus.BLOCKING, c.first().status)
    }

    @Test fun playStoreInstallNeedsNoUnknownSources() {
        val c = AndroidAutoReadiness.checks(AndroidAutoFacts(true, null, AndroidAutoReadiness.PLAY_STORE, "1.0"))
        assertEquals(listOf(CheckStatus.OK, CheckStatus.BLOCKING), c.map { it.status })
    }
}
