package fr.roadly.feature.voice

import fr.roadly.core.model.ReportKind
import fr.roadly.core.model.RoutePreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class VoiceRepliesTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun ms(h: Int, m: Int) = LocalDateTime.of(2026, 11, 9, h, m).atZone(zone).toInstant().toEpochMilli()

    @Test fun `times, durations and distances are written to be spoken`() {
        assertEquals("14 heures 3", VoiceReplies.spokenClock(ms(14, 3), zone))
        assertEquals("9 heures", VoiceReplies.spokenClock(ms(9, 0), zone))
        assertEquals("18 minutes", VoiceReplies.spokenDuration(18 * 60.0))
        assertEquals("1 minute", VoiceReplies.spokenDuration(20.0))
        assertEquals("1 heure 5", VoiceReplies.spokenDuration(65 * 60.0))
        assertEquals("2 heures", VoiceReplies.spokenDuration(120 * 60.0))
        assertEquals("600 mètres", VoiceReplies.spokenDistance(604.0))
        assertEquals("23 kilomètres", VoiceReplies.spokenDistance(22_800.0))
    }

    @Test fun `trip answers`() {
        assertEquals("Arrivée prévue à 14 heures 3, dans 18 minutes.", VoiceReplies.arrival(ms(14, 3), 18 * 60.0, zone))
        assertEquals("Il reste 600 mètres, environ 2 minutes.", VoiceReplies.remaining(600.0, 120.0))
        assertEquals("Dans 200 mètres : Tournez à droite sur la rue de Metz.", VoiceReplies.next(198.0, "Tournez à droite sur la rue de Metz."))
        assertEquals("Limitation à 50 kilomètres heure.", VoiceReplies.speedLimit(50))
        assertEquals("La limitation n'est pas connue ici.", VoiceReplies.speedLimit(null))
    }

    @Test fun `avoid options are applied and the arrival change is announced`() {
        assertEquals(RoutePreferences(avoidTunnels = true), VoiceReplies.apply(RoutePreferences(), AvoidOption.TUNNELS, true))
        assertEquals(RoutePreferences(), VoiceReplies.apply(RoutePreferences(avoidTolls = true), AvoidOption.TOLLS, false))
        assertEquals("J'évite les péages. 7 minutes de plus.", VoiceReplies.avoidDone(AvoidOption.TOLLS, true, 7))
        assertEquals("J'évite les autoroutes. Même heure d'arrivée.", VoiceReplies.avoidDone(AvoidOption.HIGHWAYS, true, 0))
        assertEquals("Les péages sont de nouveau autorisés. 1 minute de moins.", VoiceReplies.avoidDone(AvoidOption.TOLLS, false, -1))
        assertEquals("J'évite les tunnels. Ce sera appliqué à vos prochains itinéraires.", VoiceReplies.avoidDone(AvoidOption.TUNNELS, true, null))
    }

    @Test fun `reports thank the driver`() {
        assertEquals("Signalement envoyé : accident. Merci !", VoiceReplies.reported(ReportKind.ACCIDENT))
        assertEquals("Signalement envoyé : route fermée. Merci !", VoiceReplies.reported(ReportKind.CLOSED))
    }
}
