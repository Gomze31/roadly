package fr.roadly.core.navigation

import fr.roadly.core.common.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** SEC-9 : le journal du guidage ne contient que la classe de l'erreur et le code HTTP. */
class GuidanceTraceTest {
    private val secret = "<html>jeton=abc123 position 48.85,2.35 " + "x".repeat(260) + "</html>"

    @Test fun `a failed guidance is logged with the error class and the HTTP code only`() {
        assertEquals("échec Http 503", GuidanceTrace.line(null, NavigationState.Failed(AppError.Http(503, secret))))
        assertEquals("échec ProviderUnavailable 502", GuidanceTrace.line(null, NavigationState.Failed(AppError.ProviderUnavailable("valhalla", 502))))
        assertEquals("échec ProviderUnavailable", GuidanceTrace.line(null, NavigationState.Failed(AppError.ProviderUnavailable("valhalla"))))
    }

    @Test fun `response bodies, parse details and exception messages never reach the log line`() {
        val errors = listOf(AppError.Http(500, secret), AppError.Parse(secret), AppError.Unknown(secret), AppError.ProviderUnavailable(secret, 500))
        for (e in errors) {
            val line = GuidanceTrace.line(null, NavigationState.Failed(e))!!
            assertFalse("$line", line.contains("abc123") || line.contains("48.85") || line.contains("html"))
        }
        assertEquals("Parse", GuidanceTrace.errorLabel(AppError.Parse(secret)))
        assertEquals("Unknown", GuidanceTrace.errorLabel(AppError.Unknown(secret)))
    }

    @Test fun `errors without content keep their name`() {
        assertEquals("Network", GuidanceTrace.errorLabel(AppError.Network))
        assertEquals("Timeout", GuidanceTrace.errorLabel(AppError.Timeout))
        assertEquals("RateLimited", GuidanceTrace.errorLabel(AppError.RateLimited))
        assertEquals("PermissionDenied", GuidanceTrace.errorLabel(AppError.PermissionDenied))
        assertEquals("NoResult", GuidanceTrace.errorLabel(AppError.NoResult))
    }

    @Test fun `two failures in a row give one line, whatever their error`() {
        // Le journal écrit une ligne par changement d'état : un second échec, même différent, n'ajoute rien.
        assertNull(GuidanceTrace.line(NavigationState.Failed(AppError.Http(500, secret)), NavigationState.Failed(AppError.Network)))
        assertEquals("arrêt", GuidanceTrace.line(NavigationState.Failed(AppError.Network), NavigationState.Idle))
    }
}
