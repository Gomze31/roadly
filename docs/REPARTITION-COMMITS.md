# Répartition des modifications non commitées

État au 21 novembre 2026, branche `main` sur `bff586b`. Rien n'est commité. Ce document propose un découpage en commits
indépendants ; il est produit à partir de `git status` et d'une règle de classement par chemin (`M` modifié, `??` nouveau).

Ordre de commit conseillé : 1, 4, 2, 5, 3, 6. Le groupe 2 dépend du groupe 1 pour `ZoneLimits` ; les groupes 3 et 5 dépendent du groupe 2.
Un push sur `main` publie une release : ne pousser qu'après la passe sur téléphone.

## 1. Android Auto (flux antérieur)

26 fichiers.

- `??` `core/map/src/main/assets/styles/roadly-car-dark.json`
- `??` `core/map/src/main/assets/styles/roadly-car-light.json`
- `M` `core/map/src/main/java/fr/roadly/core/map/MapController.kt`
- `M` `core/navigation/src/main/AndroidManifest.xml`
- `M` `core/navigation/src/main/java/fr/roadly/core/navigation/AndroidSpeechService.kt`
- `M` `core/navigation/src/main/java/fr/roadly/core/navigation/FreeDrive.kt`
- `??` `core/navigation/src/main/java/fr/roadly/core/navigation/SpeechText.kt`
- `??` `core/navigation/src/main/java/fr/roadly/core/navigation/ZoneLimits.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/SpeechTextTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/ZoneLimitsTest.kt`
- `M` `docs/ANDROID-AUTO.md`
- `??` `feature/car/src/main/java/fr/roadly/feature/car/CarCameraPolicy.kt`
- `M` `feature/car/src/main/java/fr/roadly/feature/car/CarFreeDriveScreen.kt`
- `M` `feature/car/src/main/java/fr/roadly/feature/car/CarGuidanceScreen.kt`
- `M` `feature/car/src/main/java/fr/roadly/feature/car/CarHomeScreen.kt`
- `M` `feature/car/src/main/java/fr/roadly/feature/car/CarHudView.kt`
- `M` `feature/car/src/main/java/fr/roadly/feature/car/CarMapRenderer.kt`
- `M` `feature/car/src/main/java/fr/roadly/feature/car/CarModels.kt` — **partagé** : flux Android Auto + phase 2 (décompte du rond-point : `roundaboutCue`, `roundabout`, `RoundaboutArm`)
- `M` `feature/car/src/main/java/fr/roadly/feature/car/CarSearchScreen.kt`
- `M` `feature/car/src/main/java/fr/roadly/feature/car/CarTemplates.kt`
- `??` `feature/car/src/main/java/fr/roadly/feature/car/CarVisuals.kt` — **partagé** : flux Android Auto + phase 2 (branches et position du véhicule dans `drawRoundabout`)
- `M` `feature/car/src/main/java/fr/roadly/feature/car/RoadlyCarSession.kt`
- `M` `feature/car/src/main/res/values/strings.xml`
- `??` `feature/car/src/test/java/fr/roadly/feature/car/CarDrivePassTest.kt`
- `??` `feature/car/src/test/java/fr/roadly/feature/car/CarRoundaboutModeTest.kt` — **partagé** : phase 2 ; dépend du groupe 2
- `??` `tools/car-styles.mjs`

## 2. Moteur de guidage

20 fichiers.

- `M` `core/navigation/src/main/java/fr/roadly/core/navigation/AnnouncementPolicy.kt`
- `??` `core/navigation/src/main/java/fr/roadly/core/navigation/BranchDiagram.kt`
- `??` `core/navigation/src/main/java/fr/roadly/core/navigation/GuidanceSituation.kt`
- `??` `core/navigation/src/main/java/fr/roadly/core/navigation/GuidanceTrace.kt`
- `??` `core/navigation/src/main/java/fr/roadly/core/navigation/GuidanceVoice.kt`
- `??` `core/navigation/src/main/java/fr/roadly/core/navigation/MissedManeuverDetector.kt`
- `M` `core/navigation/src/main/java/fr/roadly/core/navigation/NavigationModule.kt` — **partagé** : groupe 2 + groupe 5 (liaison `DriveSimulation`)
- `M` `core/navigation/src/main/java/fr/roadly/core/navigation/NavigationService.kt` — **partagé** : groupe 2 (journal `RoadlyGuidage`)
- `M` `core/navigation/src/main/java/fr/roadly/core/navigation/NavigationSession.kt` — **partagé** : flux Android Auto (2 lignes `ZoneLimits.reconcile`) + groupe 2 + groupe 5 (`simulation`)
- `M` `core/navigation/src/main/java/fr/roadly/core/navigation/NavigationState.kt`
- `??` `core/navigation/src/main/java/fr/roadly/core/navigation/RoundaboutProgress.kt`
- `M` `core/navigation/src/main/java/fr/roadly/core/navigation/RouteTracker.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/AnnouncementTimingTableTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/GuidanceSituationTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/RouteTrackerAcquisitionTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/junction/BranchDiagramTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/junction/MissedManeuverTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/junction/RoundaboutModeTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/junction/RoundaboutReliabilityTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/junction/SmartGuidanceSessionTest.kt`

## 3. Interface de navigation du téléphone

12 fichiers.

- `M` `feature/navigation/build.gradle.kts`
- `M` `feature/navigation/src/main/java/fr/roadly/feature/navigation/BannerStack.kt`
- `??` `feature/navigation/src/main/java/fr/roadly/feature/navigation/BranchDiagramTile.kt`
- `M` `feature/navigation/src/main/java/fr/roadly/feature/navigation/ManeuverBanner.kt`
- `M` `feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationScreen.kt`
- `M` `feature/navigation/src/main/java/fr/roadly/feature/navigation/NavigationUiState.kt`
- `??` `feature/navigation/src/main/java/fr/roadly/feature/navigation/RoundaboutModePanel.kt`
- `M` `feature/navigation/src/main/res/values/strings.xml`
- `??` `feature/navigation/src/test/java/fr/roadly/feature/navigation/GuidanceScreenshotTest.kt`
- `M` `feature/navigation/src/test/java/fr/roadly/feature/navigation/PremiumUiStateTest.kt`
- `??` `feature/navigation/src/test/java/fr/roadly/feature/navigation/RoundaboutModeUiTest.kt`
- `M` `gradle/libs.versions.toml`

## 4. Client Valhalla

14 fichiers.

- `M` `core/data/build.gradle.kts`
- `M` `core/data/src/main/java/fr/roadly/core/data/network/ApiClient.kt`
- `M` `core/data/src/main/java/fr/roadly/core/data/network/NetworkModule.kt`
- `M` `core/data/src/main/java/fr/roadly/core/data/network/UserAgentInterceptor.kt`
- `??` `core/data/src/main/java/fr/roadly/core/data/network/ValhallaRateLimiter.kt`
- `??` `core/data/src/test/java/fr/roadly/core/data/network/ApiClientRateLimitTest.kt`
- `??` `core/data/src/test/java/fr/roadly/core/data/network/ValhallaRateLimiterTest.kt`
- `M` `core/routing/src/main/java/fr/roadly/core/routing/RouteAlternativesUseCase.kt`
- `??` `core/routing/src/main/java/fr/roadly/core/routing/RouteResponseCache.kt`
- `M` `core/routing/src/main/java/fr/roadly/core/routing/ValhallaRoutingProvider.kt` — **partagé** : groupe 4 ; contient aussi `turn_lanes` (phase 2, voies réelles)
- `M` `core/routing/src/test/java/fr/roadly/core/routing/RouteAlternativesUseCaseTest.kt`
- `??` `core/routing/src/test/java/fr/roadly/core/routing/RouteResponseCacheTest.kt`
- `M` `core/routing/src/test/java/fr/roadly/core/routing/ValhallaRoutingProviderTest.kt`
- `??` `core/routing/src/test/resources/fixtures/valhalla_lanes_toulouse.json`

## 5. Simulation, rejeu GPX et laboratoire

12 fichiers.

- `M` `core/data/src/main/java/fr/roadly/core/data/repository/UserPreferencesRepository.kt`
- `M` `core/data/src/main/java/fr/roadly/core/data/testing/FakeUserPreferencesRepository.kt`
- `M` `core/location/src/main/java/fr/roadly/core/location/FusedLocationRepository.kt`
- `??` `core/location/src/main/java/fr/roadly/core/location/LocationReplay.kt`
- `??` `core/navigation/src/main/java/fr/roadly/core/navigation/DriveSimulation.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/junction/DriveSimulationTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/junction/GpxValidationSetTest.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/lab/GuidanceLab.kt`
- `??` `core/navigation/src/test/java/fr/roadly/core/navigation/lab/GuidanceLabScenariosTest.kt`
- `M` `feature/settings/src/main/java/fr/roadly/feature/settings/NavigationGroup.kt`
- `M` `feature/settings/src/main/java/fr/roadly/feature/settings/SettingsViewModel.kt`
- `M` `feature/settings/src/main/res/values/strings.xml`

## 6. Documentation

7 fichiers.

- `M` `README.md`
- `M` `SILLAXE_PARITY.md`
- `M` `docs/API-PROVIDERS.md`
- `??` `docs/BENCHMARK-GPS.md`
- `??` `docs/GUIDAGE-INTELLIGENT.md`
- `??` `docs/VALHALLA-HEBERGEMENT.md`
- `??` `docs/VALIDATION-TELEPHONE.md`

## Fichiers partagés entre groupes

Ils ne peuvent pas être commités en entier dans un seul groupe sans emporter du travail d'un autre. Deux façons de faire :
`git add -p` pour séparer les blocs, ou commiter le groupe 1 et le groupe 2 ensemble.

- `feature/car/src/main/java/fr/roadly/feature/car/CarModels.kt` — flux Android Auto + phase 2 (décompte du rond-point : `roundaboutCue`, `roundabout`, `RoundaboutArm`)
- `feature/car/src/main/java/fr/roadly/feature/car/CarVisuals.kt` — flux Android Auto + phase 2 (branches et position du véhicule dans `drawRoundabout`)
- `feature/car/src/test/java/fr/roadly/feature/car/CarRoundaboutModeTest.kt` — phase 2 ; dépend du groupe 2
- `core/navigation/src/main/java/fr/roadly/core/navigation/NavigationSession.kt` — flux Android Auto (2 lignes `ZoneLimits.reconcile`) + groupe 2 + groupe 5 (`simulation`)
- `core/navigation/src/main/java/fr/roadly/core/navigation/NavigationModule.kt` — groupe 2 + groupe 5 (liaison `DriveSimulation`)
- `core/navigation/src/main/java/fr/roadly/core/navigation/NavigationService.kt` — groupe 2 (journal `RoadlyGuidage`)
- `core/routing/src/main/java/fr/roadly/core/routing/ValhallaRoutingProvider.kt` — groupe 4 ; contient aussi `turn_lanes` (phase 2, voies réelles)

## Ajouts de la phase 5 (22 novembre 2026)

| Fichier | Groupe |
|---|---|
| `core/navigation/.../RouteTracker.kt`, `NavigationSession.kt`, `AnnouncementPolicy.kt`, `AndroidSpeechService.kt` | 2. Moteur de guidage |
| `core/navigation/src/test/.../RouteTrackerPlausibilityTest.kt` (nouveau), `NavigationSessionTest.kt`, `lab/GuidanceLab.kt`, `lab/GuidanceLabScenariosTest.kt` | 2 et 5 |
| `core/designsystem/.../theme/Color.kt` (ambre du thème clair), `feature/navigation/.../NavigationScreen.kt` (bouton 48 dp) | 3. Interface de navigation |
| `core/data/.../network/ApiClient.kt`, `core/routing/.../ValhallaRoutingProvider.kt`, `core/routing/src/test/.../RouteResponseCacheTest.kt` | 4. Client Valhalla |
| `core/model/.../Route.kt` (`RouteRequest.reroute`) | partagé : 2 (émetteur) et 4 (lecteur) — à livrer avec le groupe 4, avant le 2 |
| `docs/PROTOCOLE-TERRAIN-HONOR.md` (nouveau), `docs/GUIDAGE-INTELLIGENT.md` § 11, `docs/VALIDATION-TELEPHONE.md` (renvoi) | 6. Documentation |

`feature/car` : aucun fichier touché en phase 5.
