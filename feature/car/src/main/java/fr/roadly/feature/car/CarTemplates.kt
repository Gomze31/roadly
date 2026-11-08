package fr.roadly.feature.car

/** Famille de gabarits utilisée pour l'accueil et l'aperçu des itinéraires. */
internal enum class TemplateTier {
    /** Niveau 7 et plus : carte + contenu (`MapWithContentTemplate` avec une liste). */
    MAP_WITH_CONTENT,

    /**
     * Niveaux 1 à 6 : gabarits de navigation historiques (`PlaceListNavigationTemplate`, `RoutePreviewNavigationTemplate`,
     * `NavigationTemplate`) sur la même carte dessinée par Roadly.
     */
    NAVIGATION,
}

/**
 * Ce que l'hôte Android Auto sait afficher, d'après le niveau d'API voiture négocié (`CarContext.carAppApiLevel`) et les
 * annotations `@RequiresCarApi` de la bibliothèque Car App 1.7.0. Ce qu'un niveau ne permet pas est simplement absent.
 */
internal data class CarCapabilities(
    val level: Int,
    val tier: TemplateTier,
    /** `Header` (niveau 5) sur `PlaceListNavigationTemplate` / `RoutePreviewNavigationTemplate` ; sinon titre + action d'en-tête. */
    val header: Boolean,
    /** `MessageTemplate.setHeader` (niveau 7) ; sinon titre + action d'en-tête. */
    val messageHeader: Boolean,
    /** Bandeau de carte et mode panoramique sur `NavigationTemplate` (niveau 2). */
    val navigationMapStrip: Boolean,
    /** Bandeau de carte et mode panoramique sur les gabarits de liste de navigation (niveau 4). */
    val listMapStrip: Boolean,
    /** Limite de lignes fournie par l'hôte (`ConstraintManager`, niveau 2). */
    val contentLimit: Boolean,
    /** Alertes de l'hôte (`AppManager.showAlert`, niveau 5). */
    val alerts: Boolean,
)

internal object CarTemplates {
    /** Valeur de `androidx.car.app.minCarApiLevel` dans le manifeste : tous les hôtes Android Auto. */
    const val MIN_CAR_API_LEVEL = 1
    const val MAP_WITH_CONTENT_LEVEL = 7

    /** Lignes affichées quand l'hôte ne donne pas sa limite (niveau 1) : minimum garanti par Android Auto. */
    const val DEFAULT_LIST_LIMIT = 6

    fun level(carAppApiLevel: Int): CarCapabilities = CarCapabilities(
        level = carAppApiLevel,
        tier = if (carAppApiLevel >= MAP_WITH_CONTENT_LEVEL) TemplateTier.MAP_WITH_CONTENT else TemplateTier.NAVIGATION,
        header = carAppApiLevel >= 5,
        messageHeader = carAppApiLevel >= 7,
        navigationMapStrip = carAppApiLevel >= 2,
        listMapStrip = carAppApiLevel >= 4,
        contentLimit = carAppApiLevel >= 2,
        alerts = carAppApiLevel >= 5,
    )
}
