package fr.roadly.feature.onboarding

import fr.roadly.core.model.FuelType
import fr.roadly.core.model.VehicleType

/** Étapes de l'accueil du premier lancement, dans l'ordre d'affichage. */
enum class OnboardingStep { WELCOME, LOCATION, NOTIFICATIONS, VEHICLE, ALERTS, ROUTE, THANKS }

/** Logique pure (testée en JVM) : liste des étapes, progression, saisie de la consommation. */
object OnboardingFlow {
    /** Android 12 et moins n'ont pas de permission de notification : l'étape est retirée (6 segments au lieu de 7). */
    fun steps(notificationPermissionExists: Boolean): List<OnboardingStep> =
        OnboardingStep.entries.filter { it != OnboardingStep.NOTIFICATIONS || notificationPermissionExists }

    /** Étape suivante, ou null après la dernière (l'accueil est alors terminé). */
    fun next(steps: List<OnboardingStep>, current: OnboardingStep): OnboardingStep? = steps.getOrNull(steps.indexOf(current) + 1)

    fun previous(steps: List<OnboardingStep>, current: OnboardingStep): OnboardingStep? = steps.getOrNull(steps.indexOf(current) - 1)

    /** Segments de la barre de progression déjà remplis (l'étape affichée compte). */
    fun filledSegments(steps: List<OnboardingStep>, current: OnboardingStep): Int = steps.indexOf(current) + 1

    /**
     * Consommation saisie (« 6,5 », « 6.5 », « 17 ») ; null si vide, illisible ou hors bornes plausibles
     * (0 < x ≤ 60 L ou kWh / 100 km). Aucune valeur par défaut n'est inventée : sans saisie, le profil n'est pas enregistré.
     */
    fun parseConsumption(input: String): Double? {
        val v = input.trim().replace(',', '.').toDoubleOrNull() ?: return null
        return v.takeIf { it > 0.0 && it <= 60.0 }
    }

    /** Types proposés à l'accueil (le gabarit poids lourd et le taxi restent dans Réglages › Véhicule). */
    val quickVehicleTypes = listOf(VehicleType.CITY, VehicleType.SEDAN, VehicleType.SUV, VehicleType.VAN, VehicleType.MOTORCYCLE)

    /** Énergies proposées à l'accueil, dans l'ordre des pompes françaises. */
    val quickFuels = listOf(FuelType.E10, FuelType.SP95, FuelType.SP98, FuelType.DIESEL, FuelType.E85, FuelType.LPG, FuelType.ELECTRIC)
}
