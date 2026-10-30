# Roadly — Spécification de conception v1 (Phase 1 + début Phase 2)

Date : 2026-10-30
Statut : validé en brainstorming, en attente de relecture utilisateur
Périmètre : application Android native de navigation GPS automobile, France d'abord, extensible à l'Europe.

## 1. Objectif de la v1

Livrer une application réellement fonctionnelle et installable (APK debug) qui :

1. affiche une carte vectorielle fluide (MapLibre) avec la position et l'orientation du véhicule ;
2. permet de rechercher une adresse, une ville, un établissement ou des coordonnées GPS ;
3. gère domicile, travail, favoris et historique de recherche ;
4. calcule et compare trois itinéraires (le plus rapide, le plus court, sans péage) et les trace sur la carte ;
5. estime le coût carburant d'un itinéraire à partir d'un profil véhicule saisi par l'utilisateur ;
6. pose les fondations (design system, architecture modulaire, interfaces de fournisseurs) des phases suivantes.

Hors périmètre v1 (explicitement marqué « Bientôt » dans l'interface, jamais simulé) : guidage vocal, suivi d'itinéraire et recalcul, trafic, incidents, signalements communautaires, stations-service, bornes, parkings, péages, planificateur, statistiques, synchronisation, Android Auto.

## 2. Décisions structurantes

| Décision | Choix | Raison |
|---|---|---|
| Clés API | Aucune en v1 | Compilable immédiatement ; tous les fournisseurs choisis sont gratuits et sans clé |
| Infrastructure | Aucune en v1 ; VPS prévu plus tard | Dev sur services publics fair-use, prod sur Valhalla auto-hébergé |
| Structure | Multi-modules par couche et fonctionnalité (13 modules) | Exigence de la spec produit ; découpage ultérieur plus coûteux |
| Package racine | `fr.roadly` | — |
| Langue | Français (ressources `values/`), anglais `values-en/` prévu | — |
| Nom | Roadly (provisoire) | — |

## 3. Fournisseurs de données (v1)

Tous vérifiés joignables le 2026-10-30 (HTTP 200).

| Besoin | Fournisseur v1 | Licence / limites | Remplaçable par |
|---|---|---|---|
| Tuiles vectorielles | OpenFreeMap, style `https://tiles.openfreemap.org/styles/liberty` ; style sombre Roadly dérivé (JSON embarqué dans `core:map/assets`) | Gratuit, sans clé, usage commercial autorisé, pas de quota publié | MapTiler, Protomaps auto-hébergé |
| Rendu carte | MapLibre Android 11.13.5 | BSD-2 | — |
| Géocodage adresses France | Base Adresse Nationale : `https://api-adresse.data.gouv.fr/search/` et `/reverse/` | Licence Ouverte 2.0, ~50 req/s/IP, sans clé | Nominatim, Pelias |
| Géocodage POI, villes, Europe | Photon : `https://photon.komoot.io/api/` et `/reverse` | Fair-use, aucune garantie de production | Photon auto-hébergé |
| Coordonnées saisies | Parseur local (décimal `48.85, 2.35`, DMS `48°51'N 2°21'E`) | — | — |
| Routage | Valhalla FOSSGIS : `https://valhalla1.openstreetmap.de/route` | Fair-use, dev uniquement ; prod = Valhalla Docker sur VPS | GraphHopper, OSRM |
| Position | `play-services-location` (FusedLocationProviderClient), repli `LocationManager` | — | — |
| Limitation de vitesse | Non disponible en v1 (affichage « — ») | Phase 2 via `trace_attributes` Valhalla auto-hébergé | — |

Règles :

- Aucune donnée GPS, routière ou tarifaire n'est inventée. Si un fournisseur ne répond pas, l'interface affiche un état d'erreur explicite.
- Chaque fournisseur est accessible uniquement via une interface Kotlin définie dans un module `core:*` ; l'implémentation est liée par Hilt et remplaçable sans toucher aux modules `feature:*`.
- Les URL de base vivent dans `ProviderConfig` (valeurs par défaut dans `BuildConfig`, surcharge possible via DataStore depuis un écran de réglages avancés).
- Aucune clé secrète dans le code. Si une clé devient nécessaire, elle est lue depuis `local.properties` à la compilation et injectée dans `BuildConfig` ; `local.properties` est hors suivi de version.
- Respect du fair-use : en-tête `User-Agent: Roadly/<version> (+contact)`, cache HTTP OkHttp 10 Mo, debounce 300 ms sur la recherche, une seule requête en vol par type.

## 4. Architecture

### 4.1 Modules

```
app                    Application Hilt, MainActivity, NavHost Compose, thème, gestion des permissions
core:model             Data classes pures (sans dépendance Android) : GeoPoint, BoundingBox, Place, PlaceKind,
                       Route, RouteLeg, Maneuver, ManeuverType, RouteCosting, VehicleProfile, FuelType, CostEstimate
core:common            Result<T> / AppError, DispatcherProvider, formatters (distance, durée, heure d'arrivée, euros),
                       ConnectivityObserver, SunTimes (calcul lever/coucher local)
core:designsystem      Tokens (couleurs, typographie Inter, espacements, formes), thème clair/sombre/auto,
                       composants Glass*, icônes de manœuvre vectorielles, CompositionLocal DrivingMode
core:data              Room (favoris, récents, profil véhicule, itinéraires sauvegardés), DataStore (préférences),
                       OkHttp + Retrofit + kotlinx.serialization, ProviderConfig
core:location          LocationRepository (Flow<LocationFix>), LocationPermissionState, impl Fused + repli LocationManager
core:map               Composable RoadlyMap (MapLibre), MapCameraController, couches position / marqueurs / tracés,
                       modes de caméra (libre, centré, suivi avec rotation)
core:geocoding         GeocodingProvider, BanGeocodingProvider, PhotonGeocodingProvider, CoordinateParser,
                       CompositeGeocoder (BAN puis Photon, fusion et déduplication)
core:routing           RoutingProvider, ValhallaRoutingProvider (requête, décodage polyline précision 6, mapping manœuvres),
                       RouteAlternativesUseCase (FASTEST, SHORTEST, NO_TOLL), FuelCostCalculator
feature:home           Écran d'accueil : carte, barre de recherche flottante, badge vitesse, recentrage, panneau inférieur
feature:search         Recherche plein écran, suggestions, récents, favoris, fiche lieu (bottom sheet)
feature:route          Comparaison d'itinéraires, tracés multiples, fiche itinéraire, estimation de coût
feature:settings       Profil véhicule, thème, données locales, confidentialité, à propos et licences
```

Règles de dépendance :

- `feature:*` dépend de `core:*` uniquement, jamais d'un autre `feature:*`.
- `core:model` et `core:common` n'ont aucune dépendance Android (modules JVM purs, tests rapides).
- `app` est le seul module à connaître tous les features ; la navigation passe par des routes typées (`kotlinx.serialization`) exposées par chaque feature.

### 4.2 Interfaces de fournisseurs

```kotlin
interface GeocodingProvider {
    val id: String
    suspend fun search(query: String, near: GeoPoint?, limit: Int = 10): Result<List<Place>>
    suspend fun reverse(point: GeoPoint): Result<Place?>
}

interface RoutingProvider {
    val id: String
    suspend fun route(request: RouteRequest): Result<List<Route>>
}

data class RouteRequest(
    val origin: GeoPoint,
    val destination: GeoPoint,
    val waypoints: List<GeoPoint> = emptyList(),
    val costing: RouteCosting,          // FASTEST, SHORTEST, NO_TOLL
    val vehicle: VehicleProfile?,
    val departAt: Instant? = null,
)

interface LocationRepository {
    val permissionState: StateFlow<LocationPermissionState>
    val fixes: Flow<LocationFix>         // lat, lon, bearing?, speedMps?, accuracyM, timestamp
    val lastKnown: LocationFix?
    fun setMode(mode: LocationMode)      // MAP (1 Hz), FOLLOW (2 Hz), OFF
}
```

### 4.3 Pile technique

- Kotlin 2.2.10, AGP 8.13.2, Gradle 8.14.3, JDK 21.
- minSdk 29 (Android 10), targetSdk 36, compileSdk 36.
- Jetpack Compose (BOM 2026.05.00), Material 3, Navigation Compose 2.10.x.
- Hilt 2.60.1 (KSP), Room 2.8.5 (KSP), DataStore Preferences, WorkManager (déclaré, non utilisé en v1).
- Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization 1.8.1.
- Coroutines et Flow partout ; aucune callback non encapsulée hors `core:location`.
- MapLibre Android SDK 11.13.5.
- Tests : JUnit 5 (JVM), Turbine, MockWebServer, Room testing, Compose UI test.

## 5. Design system

### 5.1 Direction artistique

Premium, sobre, pensé pour l'OLED et la conduite de nuit. Carte en plein écran ; tout le reste est un panneau de verre flottant.

Couleurs (mode sombre, principal) :

| Rôle | Valeur |
|---|---|
| Fond | `#0B0F14` |
| Surface verre | `rgba(22, 28, 36, 0.72)` + flou 24 dp + bord 1 dp `rgba(255, 255, 255, 0.08)` |
| Texte primaire | `#F2F5F7` |
| Texte secondaire | `#9AA6B2` |
| Accent principal (ambre route) | `#FFB547` |
| Accent itinéraire actif (cyan signal) | `#4FD1E0` |
| Itinéraires alternatifs | `#5B6774` |
| Alerte / dépassement | `#FF6B6B` |
| Succès | `#5EDC9A` |

Mode clair : fond `#F4F6F8`, surface verre `rgba(255, 255, 255, 0.78)`, texte `#0F1620`, mêmes accents assombris de 10 %.

Typographie : Inter (fichiers de police embarqués, licence OFL). Échelle : titre 28/34, corps 16/24, légende 13/18, chiffres de navigation 48–64 sp en chiffres tabulaires.

Formes : coins 20 dp (chips, boutons), 28 dp (panneaux). Cibles tactiles minimum 48 dp en mode normal, 56 dp en mode conduite.

Mouvement : transitions 200–300 ms, courbes `EaseOutCubic`, panneaux glissent depuis le bas, aucune animation en boucle pendant la conduite.

Thème automatique : sombre si l'heure locale est après le coucher ou avant le lever du soleil (calcul `SunTimes` à partir de la dernière position connue, repli 7 h–20 h), sinon suit le réglage système. Forçable manuellement (Système / Clair / Sombre / Auto solaire).

Mode conduite : `LocalDrivingMode` dans le thème, multiplicateur de taille 1,3 ; activé réellement en Phase 2 avec le guidage.

### 5.2 Composants (`core:designsystem`)

`GlassPanel`, `GlassButton` (primaire / secondaire / icône), `GlassChip`, `GlassSearchBar`, `GlassBottomSheet`, `SpeedBadge` (vitesse actuelle + limitation, « — » si inconnue), `RouteCard`, `ManeuverIcon`, `StatRow`, `EmptyState`, `ErrorState`, `OfflineBanner`, `ComingSoonTag`.

Chaque composant a un aperçu `@Preview` clair et sombre.

## 6. Écrans v1

1. **Accueil (`feature:home`)**
   - Carte plein écran, position avec halo de précision et flèche d'orientation.
   - Barre de recherche flottante en haut ; tap ouvre l'écran Recherche.
   - Badge vitesse en bas à gauche : vitesse GPS actuelle ; limitation « — » en v1.
   - Bouton de recentrage en bas à droite, trois états : libre, centré (nord en haut), suivi (rotation selon le cap, zoom 17).
   - Panneau inférieur en verre : adresse inverse de la position (BAN), raccourcis Domicile et Travail (ou « Définir »), chips Stations / Bornes / Parkings / Incidents avec `ComingSoonTag` et désactivés.
   - Permission refusée : carte centrée sur la France, panneau explicatif et bouton vers les réglages système.

2. **Recherche (`feature:search`)**
   - Plein écran, champ focalisé à l'ouverture, clavier automatique.
   - Suggestions en direct : debounce 300 ms, longueur minimale 2, BAN puis Photon, fusion et déduplication par distance < 30 m et libellé normalisé.
   - Parsing de coordonnées en tête de liste si la saisie est une coordonnée valide.
   - Sections Récents (max 50, purge automatique) et Favoris quand le champ est vide.
   - États : chargement, vide, erreur réseau, hors ligne (recherche limitée aux favoris et récents).

3. **Fiche lieu (bottom sheet, `feature:search`)**
   - Nom, adresse, type, distance à vol d'oiseau depuis la position.
   - Actions : Itinéraire, Favori (bascule), Définir comme domicile, Définir comme travail.

4. **Comparaison d'itinéraires (`feature:route`)**
   - Trois requêtes Valhalla en parallèle (`auto` ; `shortest` ; `auto` avec `use_tolls = 0`). Si deux résultats sont identiques (même géométrie), l'un est fusionné et étiqueté « Identique au plus rapide ».
   - Pour chaque itinéraire : distance, durée, heure d'arrivée, coût carburant si profil véhicule saisi sinon lien « Renseigner le véhicule », péages « Phase 3 », trafic non affiché (pas de fournisseur).
   - Tracés simultanés : actif en cyan, autres en gris ; tap sur un tracé ou une carte change l'actif ; caméra ajustée à l'emprise.
   - Bouton « Démarrer » visible mais désactivé avec libellé « Guidage : Phase 2 ».
   - Départ programmé : sélecteur d'heure de départ influence uniquement l'heure d'arrivée affichée (Valhalla public n'intègre pas le trafic horaire).

5. **Réglages (`feature:settings`)**
   - Profil véhicule : type (citadine, berline, SUV, utilitaire), motorisation (essence, gazole, E85, GPL, hybride, électrique), consommation moyenne, prix du carburant saisi manuellement (aucun prix par défaut inventé).
   - Thème, mode conduite (aperçu), unités.
   - Données : effacer l'historique de recherche, effacer les favoris, explication des permissions, mention « aucune donnée envoyée hors fournisseurs listés ».
   - Avancé : URL des fournisseurs (lecture, modification, réinitialisation).
   - À propos : version, licences (OSM © contributeurs, OpenFreeMap, MapLibre, BAN, Photon, Valhalla).

## 7. Données et état

### 7.1 Room (`core:data`)

- `favorite_places(id, name, address, lat, lon, kind, created_at, is_home, is_work)`
- `recent_searches(id, query, place_json, searched_at)` — plafond 50, suppression des plus anciens à l'insertion.
- `vehicle_profile(id = 1, type, fuel, consumption_l_100, consumption_kwh_100, fuel_price_eur, updated_at)`
- `saved_routes(id, name, origin_json, destination_json, waypoints_json, costing, created_at)` — créée en v1, utilisée en Phase 4.

### 7.2 DataStore

Thème, identifiants domicile et travail, drapeau « explication localisation vue », URLs fournisseurs surchargées, unités.

### 7.3 Flux et état

- MVVM : un `ViewModel` Hilt par écran, `StateFlow<UiState>` scellé (`Loading`, `Content`, `Error`, `Offline`), événements ponctuels via `Channel`.
- Position : `callbackFlow` sur FusedLocation, 1 Hz en mode carte, 2 Hz en mode suivi ; arrêt à `onStop` (aucun service de premier plan avant la Phase 2).
- Recherche : `flatMapLatest` sur le texte, annulation de la requête précédente.
- Itinéraires : `async` sur les trois costings, agrégation partielle acceptée (deux succès et un échec donnent deux cartes et un message sur la troisième).

## 8. Gestion des erreurs et des permissions

- `AppError` scellé : `Network`, `Timeout`, `RateLimited`, `ProviderUnavailable`, `PermissionDenied`, `NoResult`, `Parse`, `Unknown`.
- Chaque fournisseur convertit exceptions et codes HTTP en `AppError` ; aucune exception ne remonte jusqu'à l'UI.
- `CompositeGeocoder` : si BAN échoue, Photon seul ; si les deux échouent, `ProviderUnavailable`.
- Hors ligne (`ConnectivityObserver`) : bandeau persistant, recherche réseau désactivée, carte conserve les tuiles en cache MapLibre.
- Permission localisation : demandée au premier tap sur recentrage ou à l'ouverture après écran explicatif ; refus définitif détecté et redirigé vers les réglages ; l'application reste utilisable sans position (recherche et itinéraires à partir d'une origine choisie sur la carte par appui long).
- Fair-use dépassé (HTTP 429) : message « Service temporairement limité », nouvelle tentative après 2 s puis 8 s, maximum 2.

## 9. Tests

- Unitaires JVM : parseurs BAN, Photon, coordonnées ; décodage polyline Valhalla ; mapping des manœuvres ; `FuelCostCalculator` ; formatters ; `SunTimes` ; `CompositeGeocoder` (fusion, repli) ; ViewModels de recherche et d'itinéraire avec fakes et Turbine.
- Fixtures : réponses JSON réelles enregistrées depuis les fournisseurs (fichiers `src/test/resources/fixtures/`), jamais rédigées à la main.
- Instrumentés : DAO Room (favoris, plafond des récents), un test Compose sur `GlassSearchBar`.
- Commande de référence : `./gradlew testDebugUnitTest` doit être verte avant livraison.

## 10. Livraison

- `./gradlew :app:assembleDebug` produit `app/build/outputs/apk/debug/app-debug.apk`.
- `README.md` : prérequis, installation, fournisseurs et limites, variables `local.properties`, structure des modules, roadmap.
- `docs/API-PROVIDERS.md` : chaque API utilisée, endpoints, licence, limites, prérequis de production (dont docker-compose Valhalla indicatif pour le VPS).
- `docs/ROADMAP.md` : Phases 2 à 5 détaillées.
- Pas d'initialisation Git sauf demande explicite.

## 11. Feuille de route après v1

- **Phase 2** : guidage vocal TTS français, suivi d'itinéraire et détection de sortie, recalcul, service de premier plan avec notification, mode conduite complet, limitations de vitesse via Valhalla auto-hébergé, alerte de dépassement.
- **Phase 3** : trafic (fournisseur à contractualiser, jamais simulé), incidents, signalements communautaires (Supabase, modération, expiration, zones de danger conformes à la loi française), stations et prix `prix-carburants.gouv.fr`, bornes IRVE `transport.data.gouv.fr`, parkings, péages.
- **Phase 4** : planificateur multi-étapes, pauses, mode tournée, statistiques, historique, profils, synchronisation.
- **Phase 5** : tests sur appareils, batterie, hors ligne, Android Auto, Play Store.
