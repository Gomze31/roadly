# ARCHITECTURE.md — Roadly

État au 2026-10-30 (Phase 2 : guidage). Les décisions d'exécution sont dans `docs/superpowers/DECISIONS-v1.md`, la spec dans `docs/superpowers/specs/2026-10-30-roadly-v1-design.md`.

## 1. Principes

- Android natif : Kotlin 2.2, Jetpack Compose + Material 3 (thème « verre » Roadly), MVVM, Coroutines/Flow, Hilt, Room, DataStore, OkHttp + kotlinx.serialization, MapLibre.
- Fournisseurs derrière des interfaces Kotlin dans `core:*` ; implémentation liée par Hilt ; URL de base modifiables à l'exécution (DataStore). Aucune clé API dans le code.
- Aucune donnée inventée : ce qui n'a pas de source s'affiche « — » ou « Bientôt ».
- Les `feature:*` ne dépendent que de `core:*`. `core:model` et `core:common` sont des modules JVM purs.

## 2. Modules (15)

```
app                 Application Hilt, MainActivity, NavHost typé, résolution du thème, permission localisation
core:model          GeoPoint, Place, Route/Maneuver, VehicleProfile, LocationFix (JVM pur)
core:common         Outcome/AppError, Formatters, SunTimes, DispatcherProvider (JVM pur)
core:designsystem   tokens (violet marque / orange action, CockpitTint), RoadlyTheme, Glass*/SurfaceCard, boutons, tuiles, SpeedBadge,
                    NavInfoBar, HorizonStrip, ListRow/ToggleRow/SegmentedControl, RoadlyTopBar, RouteHeadline/RouteOptionRow (docs/DESIGN-SYSTEM.md)
core:data           ApiClient (OkHttp, UA, cache, retry 429), ProviderConfig(+Repository), Room, DataStore, ConnectivityObserver
core:location       LocationRepository (Fused), LocationMode (+ GUIDANCE 1 Hz prioritaire), LocationPermissionState, PermissionEvents, FakeLocationRepository
core:map            MapController + RoadlyMap (MapLibre), couches GeoJSON (position, tracés, marqueurs), caméra différée
core:geocoding      GeocodingProvider : BAN, Photon, CoordinateParser, CompositeGeocoder ; NearbyPoiProvider (Photon reverse + osm_tag, PoiCategory)
core:routing        RoutingProvider : Valhalla ; Polyline6 ; RouteAlternativesUseCase ; FuelCostCalculator
core:navigation     RouteTracker, AnnouncementPolicy, SpeedSmoother, NavigationSession (StateFlow<NavigationState>), SpeechService (TTS fr-FR),
                    SpeedLimitProvider (Valhalla trace_attributes, auto-hébergé), NavigationService (premier plan location + notification)
feature:home        carte, position, badge vitesse, recentrage, panneau inférieur, permission, appui long
feature:search      recherche, récents, favoris, fiche lieu
feature:route       comparaison 3 itinéraires, heure de départ, coût, préférences d'itinéraire, « Démarrer »
feature:settings    véhicule, thème, voix et sons, alertes de vitesse, préférences d'itinéraire, données, fournisseurs, à propos
feature:navigation  écran de guidage en mode conduite : bandeau, voies, Horizon, ETA, vitesse/limitation, arrivée
feature:trips       historique des trajets (bilan, liste) et Replay (timeline, vitesse de lecture)
```

## 3. Flux principaux

- **Position** : `FusedLocationRepository.fixes` (shareIn, WhileSubscribed 5 s) → `HomeViewModel` (reverse BAN si > 50 m) → `MapController.setUserLocation` / `applyCameraMode`.
- **Recherche** : `SearchViewModel` debounce 300 ms → `CompositeGeocoder` (coordonnées locales, sinon BAN ‖ Photon en parallèle, fusion/dédoublonnage) → fiche lieu → `RouteRoute(destLat, destLon, destName)`.
- **Itinéraires** : `RouteAlternativesUseCase` lance 3 `RoutingProvider.route` en parallèle (FASTEST / SHORTEST / NO_TOLL), tolère les échecs partiels, calcule arrivée et coût carburant → `MapController.setRoutes/fitBounds`.
- **Thème** : `ThemeMode` (DataStore) + tick 60 s + `SunTimes` (dernière position) → `ThemeResolver.isDark` → `RoadlyTheme` + style de carte clair/sombre.
- **Permission** : `MainActivity` (launcher) → `LocationRepository.refreshPermission / markDeniedForever` (DENIED_FOREVER persisté) → `PermissionEvents.changed` → `HomeViewModel`.
- **Guidage** : « Démarrer » → (API 33+ POST_NOTIFICATIONS) → `NavigationSession.start` → `LocationRepository.setGuidanceActive(true)` (1 Hz) + `NavigationService` (premier plan `location`) → pour chaque fix : `SpeedSmoother` → `RouteTracker.update` → `AnnouncementPolicy` → `SpeechService` ; hors itinéraire → `RoutingProvider.route` (≤ 1/10 s) ; > 3 s sans fix → extrapolation ; `StateFlow<NavigationState>` → `NavigationViewModel` / notification. Arrivée ou « Arrêter » → GPS relâché, service arrêté.

## 4. Données locales

Room `roadly.db` : `favorite_places` (dont domicile/travail), `recent_searches` (50), `vehicle_profile` (1 ligne), `saved_routes` (Phase 4). DataStore : thème, URLs fournisseurs, drapeaux permission, réglages de guidage (voix, volume, son et tolérance de dépassement, préférences d'itinéraire). `allowBackup=false`.

## 5. Erreurs

Chaque fournisseur renvoie `Outcome.Failure(AppError)` ; aucune exception ne traverse (`safely`, `toHttpUrlOrNull`, lecture de config gardée). `ApiClient` : 429 → 2 essais (2 s, 8 s) puis `RateLimited` ; 5xx → `ProviderUnavailable` ; `UnknownServiceException` (clair HTTP refusé) → `ProviderUnavailable` ; timeout → `Timeout` ; `IOException` → `Network`.

## 6. Guidage (Phase 2) — implémenté

- `core:navigation` :
  - `RouteTracker` (pur Kotlin) : projection équirectangulaire locale sur le segment le plus proche dans une fenêtre −50/+500 m autour de la dernière position projetée, repli global (un saut global > 100 m doit être confirmé par un second fix). Garde de cap : au-delà de 2,5 m/s, les segments à plus de 90° du cap GPS sont écartés (demi-tours, ronds-points) ; à égalité de distance (10 m) on préfère la continuité le long du tracé. Restant = durées Valhalla avec la manœuvre courante au prorata. Hors itinéraire : 40 m pendant 5 fixes, ou 100 m une fois (ignoré si précision > 50 m). Arrivée : < 25 m sur la dernière manœuvre. Extrapolation plafonnée à 150 m et 10 s après le dernier fix réel.
  - `AnnouncementPolicy` : trois paliers (loin, proche, maintenant) selon la vitesse lissée (`SpeedSmoother`) : autoroute 2000/300/40 m, route 600/150/40 m, ville 300/80/40 m ; anti-répétition par (manœuvre, palier) ; pas d'annonce « maintenant » sur une position extrapolée.
  - `NavigationSession` (`@Singleton`, dispatcher confiné, verrou de génération pour qu'un `stop()` ou un nouveau `start()` invalide les tâches en vol) : recalcul au plus 1 fois / 10 s avec délai de 10 s (échec ou délai dépassé : l'ancien tracé est conservé), filtre des fixes périmés (antérieurs au démarrage, tolérance 2 s), extrapolation après 3 s sans fix, badge « GPS perdu » après 10 s, bip de dépassement (coupé si voix désactivée), filet `catch` global.
  - `SpeechService` (`AndroidSpeechService`) : TTS `Locale.FRANCE`, focus `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, volume par annonce, moteur libéré 20 s après l'arrivée.
  - `NavigationService` : premier plan de type `location`, notification avec « Arrêter », arrêt hors guidage.
  - `SpeedLimitProvider` : `ValhallaTraceSpeedLimitProvider` n'interroge jamais `openstreetmap.de` (FOSSGIS) et au plus toutes les 5 s ; `null` (« — ») sinon.
- `core:location` : `LocationMode.GUIDANCE` (1 Hz) imposé par `setGuidanceActive(true)`, prioritaire sur le mode des écrans.
- `feature:navigation` : écran de guidage, `RoadlyTheme(drivingMode = true)`, `FLAG_KEEP_SCREEN_ON`, tracé parcouru atténué (`MapController.setTraversed`, seconde source GeoJSON).
- Doubles de test partagés : `FakeUserPreferencesRepository` (`core:data`), `FakeSpeechService`, `FakeNavigationServiceController`, `TestRoutes` (`core:navigation`).
- Vérifié sur appareil à l'arrêt (HONOR Magic 8 Pro, Android 17) : service de premier plan `location`, notification et action « Arrêter », focus audio et voix Google TTS, écran maintenu allumé, refus de `POST_NOTIFICATIONS` toléré. Non vérifié en conduite : recalcul, paliers, arrivée (voir `README.md`).

## 7. Backend (Phase 3+)

Supabase : Auth (pseudonyme d'appareil puis e-mail sans mot de passe), tables `reports`, `report_votes`, `favorites`, `trips`, `shares` ; RLS par utilisateur ; Edge Functions pour l'expiration et la modération ; séparation stricte des données personnelles et communautaires. Aucune position conservée côté serveur hors signalements.

## 8. Qualité

276 tests unitaires (JVM) + 3 tests instrumentés (Room ×2, Compose ×1) verts sur appareil ; fixtures = enregistrements réels des API ; revue par diff à chaque tâche ; `FEATURES.md` tient le statut de chaque fonction.
