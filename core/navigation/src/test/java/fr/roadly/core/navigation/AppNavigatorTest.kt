package fr.roadly.core.navigation

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigatorTest {
    @Test fun `a request sent before anyone listens is delivered once`() = runTest {
        val nav = AppNavigator()
        nav.navigate(NavRequest.OpenSearch("FUEL"))
        nav.requests.test {
            assertEquals(NavRequest.OpenSearch("FUEL"), awaitItem())
            nav.navigate(NavRequest.OpenGuidance)
            assertEquals(NavRequest.OpenGuidance, awaitItem())
            expectNoEvents()
        }
    }
}
