package fr.roadly.core.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AerialImageryTest {
    @Test fun `tile url is a WMTS GetTile on the IGN orthophotos in Web Mercator, z x y mapped to matrix, col and row`() {
        val u = AerialImagery.tileUrl(17, 66052, 48231)
        assertTrue(u.startsWith("https://data.geopf.fr/wmts?"))
        assertTrue("LAYER=ORTHOIMAGERY.ORTHOPHOTOS" in u && "TILEMATRIXSET=PM" in u && "FORMAT=image/jpeg" in u)
        assertTrue("TILEMATRIX=17&TILEROW=48231&TILECOL=66052" in u)
        // Gabarit MapLibre : les trois jetons, une fois chacun.
        assertEquals(1, Regex("\\{z\\}").findAll(AerialImagery.TILE_URL).count())
        assertEquals(1, Regex("\\{x\\}").findAll(AerialImagery.TILE_URL).count())
        assertEquals(1, Regex("\\{y\\}").findAll(AerialImagery.TILE_URL).count())
        assertTrue("IGN" in AerialImagery.ATTRIBUTION)
    }
}
