package fr.roadly.core.data.premium

import fr.roadly.core.common.Outcome
import fr.roadly.core.model.Feature
import fr.roadly.core.model.Tier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementsRepositoryTest {
    @Test fun `no billing provider means store unavailable and everything included`() = runTest {
        val repo = DefaultEntitlementsRepository(NoBillingProvider())
        val e = repo.entitlements.first()
        assertFalse(e.storeConfigured)
        assertTrue(e.allIncluded)
        assertTrue(NoBillingProvider().purchase() is Outcome.Failure)
        assertTrue(NoBillingProvider().restore() is Outcome.Failure)
    }

    @Test fun `a ready store with free tier locks plus features, plus tier unlocks`() = runTest {
        val tier = MutableStateFlow(Tier.FREE)
        val billing = object : BillingProvider {
            override val store = MutableStateFlow<StoreState>(StoreState.Ready("Roadly+ mensuel", "2,99 €"))
            override val tier = tier
            override suspend fun purchase() = Outcome.Success(Tier.PLUS).also { tier.value = Tier.PLUS }
            override suspend fun restore() = Outcome.Success(tier.value)
        }
        val repo = DefaultEntitlementsRepository(billing)
        assertTrue(repo.entitlements.first().locked(Feature.REPLAY))
        billing.purchase()
        assertTrue(repo.entitlements.first().includes(Feature.REPLAY))
    }
}
