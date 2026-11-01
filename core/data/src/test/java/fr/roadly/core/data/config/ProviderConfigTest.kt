package fr.roadly.core.data.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderConfigTest {
    @Test fun `the default map styles are the embedded Roadly styles`() {
        assertEquals("asset://styles/roadly-dark.json", ProviderConfig.DEFAULT.mapStyleDarkUrl)
        assertEquals("asset://styles/roadly-light.json", ProviderConfig.DEFAULT.mapStyleLightUrl)
        assertEquals(ProviderConfig.DEFAULT, ProviderConfig.DEFAULT.normalized())
    }

    @Test fun `an external https style stays possible`() {
        val c = ProviderConfig.DEFAULT.copy(mapStyleDarkUrl = " https://tiles.openfreemap.org/styles/dark/ ").normalized()
        assertEquals("https://tiles.openfreemap.org/styles/dark", c.mapStyleDarkUrl)
    }

    @Test fun `asset uris are refused for servers and empty asset paths for styles`() {
        assertThrows(IllegalArgumentException::class.java) { ProviderConfig.DEFAULT.copy(valhallaBaseUrl = "asset://valhalla").normalized() }
        assertThrows(IllegalArgumentException::class.java) { ProviderConfig.DEFAULT.copy(mapStyleLightUrl = "asset://").normalized() }
    }
}
