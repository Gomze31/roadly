package fr.roadly.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoReadinessTest {
    @Test fun sideloadedBuildIsRefusedByAndroidAutoAndPointsToUnknownAuto() {
        // Android Auto 17.9 : « Package DENIED » pour une appli Car App Library installée hors Play Store, même avec « Sources inconnues ».
        val c = AndroidAutoReadiness.checks(AndroidAutoFacts(true, "17.9", "com.google.android.packageinstaller", "0.1.203"))
        assertEquals(listOf(CheckStatus.OK, CheckStatus.OK, CheckStatus.BLOCKING), c.map { it.status })
        assertTrue(c.last().detail.contains("UnknownAuto"))
        assertTrue(AndroidAutoReadiness.steps.any { "Shizuku" in it } && AndroidAutoReadiness.steps.any { "UnknownAuto" in it })
        // Les mises à jour suivantes doivent garder la marque.
        assertTrue(AndroidAutoReadiness.steps.last().contains("UnknownAuto"))
    }

    @Test fun oldBuildWithoutCarServiceIsBlocking() {
        val c = AndroidAutoReadiness.checks(AndroidAutoFacts(false, "14.2", null, "0.1.172"))
        assertEquals(CheckStatus.BLOCKING, c.first().status)
    }

    @Test fun playMarkedInstallIsAccepted() {
        val c = AndroidAutoReadiness.checks(AndroidAutoFacts(true, "17.9", AndroidAutoReadiness.PLAY_STORE, "0.1.204"))
        assertEquals(listOf(CheckStatus.OK, CheckStatus.OK, CheckStatus.OK), c.map { it.status })
    }
}
