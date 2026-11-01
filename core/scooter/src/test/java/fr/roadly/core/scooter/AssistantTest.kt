package fr.roadly.core.scooter

import fr.roadly.core.model.ScooterRouteGoal
import fr.roadly.core.model.ScooterRoutePreferences
import fr.roadly.core.scooter.assistant.AssistantAction
import fr.roadly.core.scooter.assistant.ScooterAssistant
import fr.roadly.core.scooter.assistant.ScooterAssistantContext
import fr.roadly.core.scooter.assistant.ScooterCommand
import fr.roadly.core.scooter.assistant.ScooterCommandParser
import fr.roadly.core.scooter.profile.ScooterPresets
import fr.roadly.core.scooter.range.EnergyLeg
import fr.roadly.core.scooter.range.ScooterRangeEstimator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantTest {
    @Test fun userExamplesAreUnderstood() {
        val p = ScooterCommandParser
        assertEquals(ScooterCommand.SetGoal(ScooterRouteGoal.SAFEST), p.parse("Trouve-moi le trajet le plus sécurisé."))
        assertEquals(ScooterCommand.AvoidBusyRoads, p.parse("Évite les routes avec beaucoup de circulation."))
        assertEquals(ScooterCommand.PreferCycleways, p.parse("Privilégie les pistes cyclables."))
        assertEquals(ScooterCommand.AskRange(toHome = true), p.parse("Est-ce que j'ai assez de batterie pour rentrer ?"))
        assertEquals(ScooterCommand.AvoidHills, p.parse("Trouve-moi un trajet avec moins de montées."))
        assertEquals(ScooterCommand.NextCycleway, p.parse("Quelle est la prochaine piste cyclable ?"))
        assertEquals(ScooterCommand.CloserDestination, p.parse("Trouve une destination plus proche si je manque de batterie."))
        assertEquals(ScooterCommand.SetBattery(65), p.parse("Ma batterie est à 65 %"))
        assertEquals(ScooterCommand.Unknown, p.parse("Raconte une blague"))
    }

    @Test fun neverInventsBatteryLevel() {
        val est = ScooterRangeEstimator(ScooterPresets.NINEBOT_MAX_G2).estimate(listOf(EnergyLeg(3000.0)), null, null)
        val reply = ScooterAssistant.respond(ScooterCommand.AskRange(false), ScooterAssistantContext(ScooterRoutePreferences(), rangeToDestination = est))
        assertTrue(reply.speech.contains("Je ne connais pas le niveau de batterie"))
        assertNull(reply.action)
        val closer = ScooterAssistant.respond(ScooterCommand.CloserDestination, ScooterAssistantContext(ScooterRoutePreferences(), rangeToDestination = est))
        assertNull(closer.action)
    }

    @Test fun rangeAnswerUsesEstimate() {
        val est = ScooterRangeEstimator(ScooterPresets.NINEBOT_MAX_G2).estimate(listOf(EnergyLeg(3000.0)), 90.0, 20.0)
        val reply = ScooterAssistant.respond(ScooterCommand.AskRange(false), ScooterAssistantContext(ScooterRoutePreferences(), rangeToDestination = est))
        assertTrue(reply.speech, reply.speech.startsWith("Oui"))
        val search = ScooterAssistant.respond(ScooterCommand.CloserDestination, ScooterAssistantContext(ScooterRoutePreferences(), rangeToDestination = est)).action
        assertTrue(search is AssistantAction.SearchWithinRange)
    }

    @Test fun preferenceActionsKeepOtherSettings() {
        val prefs = ScooterRoutePreferences(goal = ScooterRouteGoal.FASTEST, avoidSteepHills = false, avoidUnpaved = false)
        val action = ScooterAssistant.respond(ScooterCommand.AvoidHills, ScooterAssistantContext(prefs)).action as AssistantAction.ApplyPreferences
        assertEquals(prefs.copy(avoidSteepHills = true), action.preferences)
    }

    @Test fun homeRangeWithoutHome() {
        val r = ScooterAssistant.respond(ScooterCommand.AskRange(true), ScooterAssistantContext(ScooterRoutePreferences(), hasHome = false))
        assertNull(r.action)
    }
}
