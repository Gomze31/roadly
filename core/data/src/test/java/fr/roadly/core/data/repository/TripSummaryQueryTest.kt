package fr.roadly.core.data.repository

import fr.roadly.core.data.db.TRIP_SUMMARY_SQL
import fr.roadly.core.data.db.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

/** La liste des trajets ne charge pas les tracés : la requête de résumé doit rendre toutes les colonnes sauf `pointsCsv`, vide. */
class TripSummaryQueryTest {
    private val selected = TRIP_SUMMARY_SQL.substringAfter("SELECT ").substringBefore(" FROM ").split(",").map { it.trim() }

    @Test fun `the summary query returns every column of the trip row, so a new column cannot be forgotten`() {
        val columns = TripEntity::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !it.isSynthetic }.map { it.name }.toSet()
        val returned = selected.map { it.substringAfter(" AS ") }.toSet()
        assertEquals(columns, returned)
    }

    @Test fun `the GPS track is never read by the summary query`() {
        assertTrue("'' AS pointsCsv" in selected)
        assertFalse("la colonne du tracé ne doit pas être lue", selected.any { it == "pointsCsv" })
        assertFalse(TRIP_SUMMARY_SQL.contains("*"))
    }

    @Test fun `a summary row maps to a trip with its totals and no points`() {
        val row = TripEntity(7, 1_000L, 61_000L, "Capitole", 6_500.0, 60L, 52.0, 1.2, true, pointsCsv = "", travelMode = "CAR")
        val trip = row.toModel()
        assertEquals(emptyList<Any>(), trip.points)
        assertEquals("Capitole", trip.destinationName)
        assertEquals(6_500.0, trip.distanceM, 0.0)
        // Les totaux d'une liste de résumés restent justes.
        assertEquals(6_500.0, listOf(trip).statsSince(0L).distanceM, 0.0)
    }
}
