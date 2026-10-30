# ARCHITECTURE.md — Roadly

État au 2026-10-30 (fin de la v1). Les décisions d'exécution sont dans `docs/superpowers/DECISIONS-v1.md`, la spec dans `docs/superpowers/specs/2026-10-30-roadly-v1-design.md`.

## 1. Principes

- Android natif : Kotlin 2.2, Jetpack Compose + Material 3 (thème « verre » Roadly), MVVM, Coroutines/Flow, Hilt, Room, DataStore, OkHttp + kotlinx.serialization, MapLibre.
- Fournisseurs derrière des interfaces Kotlin dans `core:*` ; implémentation liée par Hilt ; URL de base modifiables à l'exécution (DataStore). Aucune clé API dans le code.
- Aucune donnée inventée : ce qui n'a pas de source s'affiche « — » ou « Bientôt ».
- Les `feature:*` ne dépendent que de `core:*`. `core:model` et `core:common` sont des modules JVM purs.

## 2. Modules (13)

```
app                 Application Hilt, MainActivity, NavHost typé, résolution du thème, permission localisation
core:model          GeoPoint, Place, Route/Maneuver, VehicleProfile, LocationFix (JVM pur)
core:common         Outcome/AppError, Formatters, SunTimes, DispatcherProvider (JVM pur)
core:designsystem   tokens, RoadlyTheme, composants Glass*, SpeedBadge, RouteCard, états
core:data           ApiClient (OkHttp, UA, cache, retry 429), ProviderConfig(+Repository), Room, DataStore, ConnectivityObserver
core:location       LocationRepository (Fused), LocationMode, LocationPermissionState, PermissionEvents, FakeLocationRepository
core:map            MapController + RoadlyMap (MapLibre), couches GeoJSON (position, tracés, marqueurs), caméra différée
core:geocoding      GeocodingProvider : BAN, Photon, CoordinateParser, CompositeGeocoder
core:routing        RoutingProvider : Valhalla ; Polyline6 ; RouteAlternativesUseCase ; FuelCostCalculator
feature:home        carte, position, badge vitesse, recentrage, panneau inférieur, permission, appui long
feature:search      recherche, récents, favoris, fiche lieu
feature:route       comparaison 3 itinéraires, heure de départ, coût
feature:settings    véhicule, thème, données, fournisseurs, à propos
```

## 3. Flux principaux

- **Position** : `FusedLocationRepository.fixes` (shareIn, WhileSubscribed 5 s) → `HomeViewModel` (reverse BAN si > 50 m) → `MapController.setUserLocation` / `applyCameraMode`.
- **Recherche** : `SearchViewModel` debounce 300 ms → `CompositeGeocoder` (coordonnées locales, sinon BAN ‖ Photon en parallèle, fusion/dédoublonnage) → fiche lieu → `RouteRoute(destLat, destLon, destName)`.
- **Itinéraires** : `RouteAlternativesUseCase` lance 3 `RoutingProvider.route` en parallèle (FASTEST / SHORTEST / NO_TOLL), tolère les échecs partiels, calcule arrivée et coût carburant → `MapController.setRoutes/fitBounds`.
- **Thème** : `ThemeMode` (DataStore) + tick 60 s + `SunTimes` (dernière position) → `ThemeResolver.isDark` → `RoadlyTheme` + style de carte clair/sombre.
- **Permission** : `MainActivity` (launcher) → `LocationRepository.refreshPermission / markDeniedForever` (DENIED_FOREVER persisté) → `PermissionEvents.changed` → `HomeViewModel`.

## 4. Données locales

Room `roadly.db` : `favorite_places` (dont domicile/travail), `recent_searches` (50), `vehicle_profile` (1 ligne), `saved_routes` (Phase 4). DataStore : thème, URLs fournisseurs, drapeaux permission. `allowBackup=false`.

## 5. Erreurs

Chaque fournisseur renvoie `Outcome.Failure(AppError)` ; aucune exception ne traverse (`safely`, `toHttpUrlOrNull`, lecture de config gardée). `ApiClient` : 429 → 2 essais (2 s, 8 s) puis `RateLimited` ; 5xx → `ProviderUnavailable` ; `UnknownServiceException` (clair HTTP refusé) → `ProviderUnavailable` ; timeout → `Timeout` ; `IOException` → `Network`.

## 6. Cibles Phase 2 (guidage) — architecture prévue

- `core:navigation` (nouveau) : `RouteTracker` (projection sur polyligne, distance à la manœuvre, sortie d'itinéraire, recalcul), `GuidanceEngine` (file d'annonces, anticipation, anti-répétition), `SpeechService` (TTS `fr-FR`, focus audio), `NavigationService` (foreground service + notification, `LocationMode.FOLLOW`).
- `feature:navigation` : écran conduite (bandeau manœuvre, vitesse/limitation, bandeau « à venir », ETA/restant), `LocalDrivingMode = true`.
- Limitation de vitesse : `SpeedLimitProvider` (interface) ; impl Valhalla `trace_attributes` sur instance auto-hébergée ; `null` tant qu'indisponible.

## 7. Backend (Phase 3+)

Supabase : Auth (pseudonyme d'appareil puis e-mail sans mot de passe), tables `reports`, `report_votes`, `favorites`, `trips`, `shares` ; RLS par utilisateur ; Edge Functions pour l'expiration et la modération ; séparation stricte des données personnelles et communautaires. Aucune position conservée côté serveur hors signalements.

## 8. Qualité

83 tests unitaires (JVM) + 2 tests instrumentés (Room, Compose) ; fixtures = enregistrements réels des API ; revue par diff à chaque tâche ; `FEATURES.md` tient le statut de chaque fonction.
