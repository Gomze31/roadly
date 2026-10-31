# Roadly — Audit de la navigation (base de la refonte 3D premium)

Date : 2026-10-31. Branche auditée : `feat/roadly-3d` @ `7b4c800` (guidage 3D phase 1 : caméra inclinée, bâtiments, flèche de manœuvre, approche, panneau de sortie, réglage « Carte 3D »). Branche sœur lue : `feat/roadly-ai` @ `6ccde62` (copilote vocal, base commune `60033fe`).

Légende : **LIMITE** = la donnée ou la bibliothèque ne le permet pas ; *non vérifié* = déduit du code ou de connaissances externes, pas constaté sur appareil.

---

## 1. Architecture

### Modules (`settings.gradle.kts:10-14`)
`:app`, `:core:{model,common,designsystem,data,location,map,geocoding,routing,navigation}`, `:feature:{home,search,route,settings,navigation,trips}`. La branche AI ajoute `:core:assistant` et `:feature:assistant`.
Versions (`gradle/libs.versions.toml`) : AGP 8.13.2, Kotlin 2.2.10, Hilt 2.58, MapLibre 11.13.5. `minSdk 29`, `targetSdk 36`.

### Injection (Hilt)
- `NavigationModule` (`core/navigation/.../NavigationModule.kt:13-19`) : `SpeedLimitProvider→ValhallaTraceSpeedLimitProvider`, `SpeechService→AndroidSpeechService`, `NavigationServiceController→ForegroundServiceController`, `NavigationClock→SystemNavigationClock`, `VigilanceZoneProvider→RepositoryVigilanceZoneProvider`, `RainProvider→OpenMeteoRainProvider`, `TripRecorder→RoomTripRecorder`.
- `NavigationSession` : `@Singleton`, `@ApplicationScope CoroutineScope` (`NavigationSession.kt:47-61`).
- `LocationRepository` = `FusedLocationRepository` `@Singleton` (`core/location/.../FusedLocationRepository.kt:36-42`).

### Où vit l'état de navigation
- **Source unique existante** : `NavigationSession.state: StateFlow<NavigationState>` (`NavigationSession.kt:62-63`), `muted` séparé (`l.65-68`).
- `NavigationState` (`NavigationState.kt:11-37`) : `Idle | Guiding(route, progress, speedKmh, speedLimitKmh, upcoming, lanes, etaEpochMs, gpsLost, rerouting, hazards, activeZone, raining, limitLoweredByRain) | Arrived(route) | Failed(error)`.
- `RouteProgress` (`core/model/.../RouteProgress.kt:7-20`) : `routeId, segmentIndex, distanceAlongM, offRouteM, nextManeuverIndex, distanceToNextManeuverM, remainingDistanceM, remainingDurationS, snapped, bearingDeg, extrapolated`.
- `RouteTracker` (non thread-safe, `RouteTracker.kt:17-21`), une instance par course (`NavigationSession.kt:90-95`), remplacée au recalcul (`l.338-345`).
- Une course = `inner class Run` (`NavigationSession.kt:144`) sur `dispatchers.default.limitedParallelism(1)` (`l.70`) ; garde de génération + `stateLock` (`l.72-74`, `l.129-141`, `l.413-415`).

### Service de premier plan
- `NavigationService` (`core/navigation/.../NavigationService.kt:22-78`) : type `location`, `startForeground` immédiat (`l.36-46`), collecte `session.state` pour la notification, s'arrête hors `Guiding` (`l.57-60`). **`START_NOT_STICKY`** (`l.50, 53`).
- Démarré par `ForegroundServiceController.start()` ; refus système avalé (guidage continue écran allumé, sans notification).
- Le service **ne porte pas** la course : elle tourne dans le scope application ; le service sert à rester « foreground » + notification.

### Cycle de vie
- **Arrière-plan** : `location.fixes` collecté par la course dans le scope application → GPS actif tant que la course l'est ; `fixes` partagé `WhileSubscribed(5 s)`. L'UI utilise `collectAsStateWithLifecycle` → pas de recomposition en arrière-plan. Pas de `ACCESS_BACKGROUND_LOCATION` : repose sur le FGS `location`.
- **Rotation** : `configChanges="orientation|screenSize|keyboardHidden|uiMode"` → activité non recréée ; `BoxWithConstraints` recalcule `landscape` ; `bannerExpanded` / `showReport` en `rememberSaveable` ; `lastCamera` en `remember`.
- **Mort du processus** : `START_NOT_STICKY` + état en mémoire → **la course est perdue** (aucune persistance de `lastOption`). Non testé.
- Notification → `ACTION_OPEN_NAVIGATION` → `MainActivity.handleIntent` → `RoadlyNavHost` → `NavigationRoute`. « Arrêter » → `ACTION_STOP` → `session.stop()`.
- Écran allumé : `KeepScreenOn`. Retour système = `vm.stop()`.

### Calculs de distance — pas de base unique (DOUBLONS)
| Valeur | Où | Base |
|---|---|---|
| `cumDistM`, `maneuverStartM` | `RouteTracker.kt:29-38` | haversine `GeoPoint.distanceTo` |
| projection / `offRouteM` | `RouteTracker.project` `l.214-228` | équirectangulaire R = 6 371 000 |
| `distanceToNextManeuverM` | `RouteTracker.kt:162` | géométrie brute |
| `remainingDistanceM` | `RouteTracker.kt:163` | géométrie × `distScale` (= `route.distanceMeters / totalGeometryM`) |
| distances Horizon | `NavigationUiState.kt:113-117` | `distanceToNext` + **`lengthMeters` Valhalla** |
| `RouteGeometry.cumulative/project` | `RouteGeometry.kt:10-32` | haversine + projection `DEG_M = 111 320`, recherche globale O(N) sans cap |
| utilisée par | `CurveDetector`, `VigilanceZones.build`, `NavigationViewModel.reportsAhead` | |
| distance trajet | `RoomTripRecorder.onFix` | somme haversine (pas ≥ 20 m) |
| flèche | `ManeuverArrowBuilder.walk` | haversine |
| cap | 4 implémentations : `RouteTracker.bearing`, `segBearingDeg`, `CurveDetector.heading`, `ManeuverArrowBuilder.bearingDeg` | |
| texte voix | `SpokenPhrases.distance` (`AnnouncementPolicy.kt:65-71`) : < 100 m → 10 m ; sinon **pas de 50 m** ; ≥ 1 km → 0,1 km | |
| texte écran | `Formatters.distance` : < 1 km → pas de 10 m ; < 10 km → `%.1f` | |

Incohérences : 430 m = « 450 mètres » (voix) vs « 430 m » (bandeau) ; Horizon en longueurs Valhalla vs bandeau en géométrie ; restant mis à l'échelle mais pas la distance à la manœuvre. `RouteGeometry.project` global peut projeter un signalement sur le mauvais passage d'un aller-retour (*non vérifié*).

---

## 2. Données de routage Valhalla (`core/routing`)

### Requête (`ValhallaRoutingProvider.kt:49-85`)
`locations` (+ `heading` / `heading_tolerance=45` sur le 1er point au recalcul), `costing` auto/truck/motorcycle/taxi, `costing_options` (`shortest`, `use_tolls/use_highways/use_ferry=0`, gabarit truck), `units=kilometers`, `language=fr-FR`. **Aucune** option de voies, `directions_type`, `format=osrm`, `banner_instructions`, alternates. Serveur par défaut `https://valhalla1.openstreetmap.de` (`ProviderConfig.kt:16`).

### Champs lus (`ValhallaDtos.kt:7-34`, mapping `ValhallaRoutingProvider.kt:140-180`)
- Trajet : `trip.legs[].shape` (polyline6), `summary.length/time/has_toll`, `locations[].original_index`.
- Manœuvre : `type` (→ `mapType` `l.182-203`), `instruction`, `length`, `time`, `begin_shape_index` (décalage multi-legs), `street_names[0]`, `roundabout_exit_count`, `toll` (pour `hasToll`), `verbal_pre_transition_instruction`, `verbal_transition_alert_instruction`, `lanes` (→ `ValhallaLanes`), `sign.exit_number_elements[0]`, `sign.exit_toward_elements` (2 premiers, « · »), `sign.exit_branch_elements[0]`.
- Modèle `Maneuver` (`core/model/.../Route.kt:16-36`), `Lane(directions, valid)`.

### Champs présents dans la fixture FOSSGIS mais NON lus
Fixture `core/routing/src/test/resources/fixtures/valhalla_route.json` (14 manœuvres, 1 leg, Paris) : `verbal_succinct_transition_instruction`, `verbal_post_transition_instruction`, **`bearing_before` / `bearing_after`** (angle réel du virage → icône et flèche fidèles), `end_shape_index`, `travel_mode/travel_type`, `cost`, `has_time_restrictions`, `summary.has_highway/has_ferry`, `warnings` (ex. 401 « retrying with relaxed restrictions »), `locations[].side_of_street`, `street_names[1..]`.
Champs de l'API absents de la fixture et non modélisés (*non vérifié sur FOSSGIS*) : `sign.exit_name_elements`, `consistent_name`, `begin_street_names`, `lanes[].valid/active` comme masques. Pas de `edge_index` ni `brunnel` dans `/route` (seulement `trace_attributes`).

### Pertes de mapping (défauts)
- `mapType` écrase le côté : 12/13 → `UTURN`, 17/18/19 → `RAMP`, 20/21 → `EXIT`, 25/37/38 → `MERGE`, 7 « becomes » et 22 → `CONTINUE`. Icônes `RampRight`, `ForkRight`, `UTurnLeft`, `RoundaboutRight` (`ManeuverIcon.kt:33-50`) → **une bretelle ou sortie à gauche s'affiche à droite**.
- Types 4/5/6 des legs intermédiaires → `ARRIVE` au milieu du tracé ; `RouteTracker` ne connaît que la dernière arrivée ; étape intermédiaire de longueur nulle (*non vérifié*).
- `ValhallaLanes.parse` (`ValhallaLanes.kt:37-41`) : `valid || active` fusionnés ; la direction à suivre d'une voie multi-flèches est perdue ; `SHARP_*` fusionnés.

### Voies, panneaux, limitations sur le serveur public
- Test `ValhallaRoutingProviderTest.kt:189` : « la fixture FOSSGIS ne contient ni voies ni panneaux ». Nuance : trajet urbain sans sortie d'autoroute. Retour réel sur autoroute : *non vérifié* ; jamais vu sur appareil (`SILLAXE_PARITY.md:241`).
- **LIMITE — limitation de vitesse** : `/route` n'en renvoie pas ; `trace_attributes` refusé sur `*.openstreetmap.de` (`ValhallaTraceSpeedLimitProvider.kt:53, 97`).
- **LIMITE — péages** : oui/non seulement. **LIMITE — trafic** : aucun.
- `roundabout_exit_count` : présent sur ENTER (26) seulement, absent sur EXIT (27). Fiabilité dépendante d'OSM (*non vérifié*).

---

## 3. Logique de guidage (`core/navigation`)

### `GuidanceConstants` (`GuidanceConstants.kt:4-48`)
Hors itinéraire : > 40 m pendant 5 positions ou > 100 m une fois, si précision ≤ 50 m. Arrivée < 25 m. Recalcul ≤ 1 / 10 s, délai 10 s. Extrapolation après 3 s, badge GPS perdu après 10 s, plafond 10 s / 150 m. Fenêtre −50 m / +500 m. Filtre de cap ≥ 2,5 m/s. Saut > 100 m confirmé à 50 m. Libération TTS 20 s après l'arrivée. Manœuvres à venir : 3.

### `RouteTracker`
- `update` (`l.69-110`) : recherche fenêtrée autour de `lastAlong` ; si > 40 m, recherche globale avec confirmation ; cap = GPS si ≥ 1 m/s sinon cap du segment ; arrivée = dernière manœuvre et (< 25 m de la destination ou ≥ total − 25 m et ≤ 40 m d'écart).
- **Règle « manœuvre effectuée »** (`progressAt l.150-153`) : `next` = première manœuvre dont `maneuverStartM > along`. Aucune hystérésis ni monotonie : la fenêtre autorise un recul de 50 m. **Le bruit GPS au point de manœuvre peut faire alterner `nextManeuverIndex` k ↔ k+1** → bandeau, `approach`, flèche, zoom clignotent. La voix est protégée par `announced`. Aucun test ne couvre cette alternance. *Non vérifié sur appareil.*
- `extrapolate` (`l.117-128`) : position estimée marquée `extrapolated`.

### `AnnouncementPolicy`
- Classes de vitesse sur la vitesse lissée (`SpeedSmoother` α = 0,3) : autoroute ≥ 90 km/h (FAR 2000 / NEAR 300 / NOW 40 m), route ≥ 50 (600 / 150 / 40), ville (300 / 80 / 40).
- Anti-répétition par `(index, palier)`, vidé au recalcul. NOW interdit en position extrapolée.
- Texte : NOW = `verbalPre ?: instruction` ; NEAR/FAR = « Dans X, » + `verbalAlert ?: verbalPre ?: instruction`. **Toutes les phrases viennent de Valhalla**, sauf recalcul et vigilance.
- Risques : NOW à 40 m quelle que soit la vitesse ; à 130 km/h et 1 Hz le palier peut tomber entre deux positions (*non vérifié*).

### `NavigationSession`
`start` (`l.81-105`), `onFix` (`l.210-232`), recalcul (`l.307-355`, intervalle 10 s, phrase une fois par épisode, ancien tracé conservé en cas d'échec), `watchdog` (`l.358-372`), arrivée (`l.285-305`), `say` (`l.404-411` : sourdine, `prefs.voiceEnabled.first()` **à chaque annonce**, `speech.speak(text)` sans flush), alertes (dépassement, zones, pluie).

### `AndroidSpeechService`
`USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`, focus `GAIN_TRANSIENT_MAY_DUCK`, pris au premier énoncé et rendu au dernier. File `QUEUE_ADD` (flush optionnel). **Aucune priorité, péremption ni remplacement** : une phrase FAR longue retarde le NOW suivant. Bip via `ToneGenerator(STREAM_MUSIC)` hors focus.

### Ronds-points aujourd'hui
Bandeau : « SORTIE n » si `exitNumber`, sinon « 1re / n-e sortie » depuis `roundaboutExit` (ENTER seulement). Voix : phrase Valhalla. Approche à 150 m ; flèche 80 m avant / **40 m après** l'entrée → la sortie n'est pas montrée dans un grand rond-point. Icône unique `RoundaboutRight`. Aucune représentation de l'anneau.

---

## 4. Carte (`core/map`)

- **Styles distants uniquement** : `tiles.openfreemap.org/styles/positron` (clair) et `/dark` (sombre), `Style.Builder().fromUri`. Aucun `assets`, aucun `fromJson`. Un look « premium sombre » demande **un style JSON à nous** (asset ou hébergé, glyphes + sprites) ou des retouches de calques à l'exécution (fragile vis-à-vis des ids OpenFreeMap).
- **Calques Roadly** (`MapController.installLayers`) : bâtiments extrudés (sous le 1er `symbol` du style, `render_height`, zoom ≥ 15, si source `openmaptiles`) ; tracé (lueur, liseré, inactif, actif, parcouru) ; flèche (liseré, trait, tête) ; marqueurs ; signalements ; précision ; utilisateur. Rejoués après changement de style (`replayState`).
- **Caméra** : `applyGuidanceCamera` → `easeCamera` linéaire 1 s ; `GuidanceCameraPolicy` (58° / 50° approche / 45° économie / 0° 2D ; zoom 17,5 → 15,8 ; approche 150 m ou 400 m à > 70 km/h ; marges portrait (0, 0,45, 0, 0,12), paysage (0,40, 0,25, 0, 0)). **Pas d'hystérésis** d'approche.
- MapView : boussole off, rotation on, **inclinaison au geste off**, pas de `MapLibreMapOptions`, pas de `setMaximumFps`.
- **OpenMapTiles** (schéma, *non vérifié dans le dépôt*) : `transportation` (class, `brunnel`, `ramp`, `oneway`, `layer`, `toll`), `transportation_name`, `poi`, `building` (`render_height`, `hide_3d`). Le dépôt confirme la source `openmaptiles` et `building` plat dans les deux styles.
- **LIMITE** : pas de `maxspeed` dans les tuiles ; pas de feux / ralentisseurs ; densité des étiquettes seulement via le style ; aucune hiérarchie routière ni `brunnel` exploitée (le tracé passe au-dessus des tunnels, *non vérifié*) ; pas de relief.

---

## 5. Interface (`feature/navigation`)

- `NavigationScreen` → `RoadlyTheme(drivingMode = true)` → `NavigationContent` : effets couleurs / tracé / bâtiments / flèche / position et parcouru à chaque `progress` / caméra ; `RoadlyMap` ; `PortraitOverlay` / `LandscapeOverlay` ; `ReportSheet`, `AlternativesSheet`.
- **Portrait** : bandeau + `LaneStrip` + pastille recalcul, bouton replier ; colonne d'actions (Signaler, Voix, Destination, Alternatives) ; en bas `SpeedBadge` + pastilles + `BottomBlock` (`HorizonStrip` + `NavInfoBar` : arrivée / min / km, Recentrer, Arrêter).
- **Paysage** : colonne gauche 46 % (bandeau, voies, `BottomBlock`), actions en haut à droite, compteur en bas à droite.
- **Interactif** : replier, Signaler, Sourdine, Destination, Alternatives, Recentrer (reprise auto après 8 s), Arrêter, Terminer / Réessayer. **Non interactif** : tap bandeau, ETA / barre, Horizon, compteur, voies ; **aucun panneau inférieur repliable**.
- Design system : `GlassPanel` (pas de vrai flou), `SurfaceCard`, `IconTile` (6 styles), `RoundIconButton` (4), `GlassButton` (5), `GlassChip`, `HorizonStrip` (11 types), `NavInfoBar`, `SpeedBadge`, `ManeuverIcon`, `StatTile`, `GlassBottomSheet`, `ListRow` / `ToggleRow` / `SegmentedControl` / `SettingsGroup`. Tokens `Color.kt` (dont `buildings3d`), `CockpitTint` (5), `RoadlyMotion` 150/240/380 ms, `NavDigits`, `LocalDrivingMode`.

---

## 6. Branche Roadly AI — coordination vocale

- `SpeechService.speaking: StateFlow<Boolean>` ajouté ; `AssistantController.followGuidanceSpeech` (`l.294-310`) : pause de l'assistant dès que le guidage parle, reprise 300 ms après (`RESUME_AFTER_GUIDANCE_MS`). Guidage prioritaire.
- Focus assistant `GAIN_TRANSIENT` + `USAGE_ASSISTANT` ; gère `LOSS`, `LOSS_TRANSIENT`, `GAIN`, **pas `LOSS_TRANSIENT_CAN_DUCK`** → seule `speaking` met l'assistant en pause (*non vérifié*).
- Les erreurs de l'assistant passent par le TTS du guidage (`speech.speak(text, flush=false)`) : `setMuted` / `stop` / `release` de la session les coupent aussi.
- `GuidanceActions` : actions partagées UI / assistant ; `NavigationViewModel` y délègue. Emplacements `assistantButton` / `assistantPanel` dans les deux mises en page.
- **Contrat à respecter par un futur coordinateur vocal** : `speaking` fidèle (file comprise) ; `speak(text, flush)` ; ne pas effacer les messages assistant via `stop`/`setMuted`/`release` session (ou canaux séparés) ; priorité guidage > assistant avec reprise 300 ms ; attributs navigation + `MAY_DUCK` ; hauteur d'`assistantPanel` et marges caméra (portrait).

---

## 7. Réglages

`UserPreferencesRepository` : `themeMode` (AUTO_SOLAR), `routePreferences`, `voiceEnabled`, `voiceVolume`, `speedAlertToleranceKmh`, `speedAlertSound`, `vigilanceEnabled`, `vigilanceSound`, `rainLowersLimit`, `map3dEnabled`, `cockpitTint`. `ProviderConfigRepository` (URLs). Écran : Trajets, Roadly+, Conduite, Apparence (Carte 3D, thème), Voix, Alertes de vitesse, Alertes, Itinéraires, Données, Confidentialité, Personnalisation, Communauté, Fournisseurs, À propos.
**Absents** : vue par défaut 2D/3D, zoom, inclinaison, rotation auto / nord en haut, niveau de détail, aperçu de la manœuvre suivante, mode économie manuel, voix TTS / débit.

---

## 8. Tests

306 `@Test` = **303 JVM** + **3 instrumentés** (`FavoriteDaoTest` 2, `GlassSearchBarTest` 1). **Aucune CI**. Pas de macrobenchmark ni de baseline profile.

Couverture navigation : `AnnouncementPolicyTest` (16), `CurveDetectorTest` (4), `NavigationSessionHazardsTest` (3), `NavigationSessionTest` (34 : démarrage, recalcul, délais, GPS perdu, arrivée, sourdine, voix, volume, dépassement), `RouteTrackerContinuityTest` (6 : demi-tour, cap, sauts), `RouteTrackerOffRouteTest` (9), `RouteTrackerTest` (20), `ValhallaTraceSpeedLimitProviderTest` (11), `VigilanceZonesTest` (4) ; carte : `GuidanceCameraPolicyTest` (10), `ManeuverArrowBuilderTest` (5), `RouteFeatureBuilderTest`, `TraversedFeatureBuilderTest` ; routage : `ValhallaRoutingProviderTest` (15), `ValhallaLanesTest` (3) ; UI : `NavigationViewModelTest` (9), `HazardsAheadTest`, `SplitFormattersTest`.
Routes de test : `TestRoutes.straight` (≈ 1 464 m, virage droite au point 10), `TestRoutes.outAndBack` (demi-tour), `ParisRoute` (fixture FOSSGIS réelle, 2 ronds-points).
**Manquants** : route autoroutière avec panneaux et voies réels, multi-étapes, rond-point sous bruit GPS, alternance au point de manœuvre, performance / recomposition.

---

## 9. Défauts connus

- Code : aucun `TODO` / `FIXME`.
- `SILLAXE_PARITY.md` « Manque » : limitation ⛔, autres conducteurs, arrêts restants et « étape atteinte », trafic / travaux / services, montant des péages ⛔, feux et ralentisseurs ⛔.
- Reportés (phase 1, `.superpowers/sdd/.../progress.md`) : pas d'hystérésis d'approche ; `lastCamera` jamais réinitialisé ; `exitBranch` et `CameraSnapshot.tilt` inutilisés ; flèche qui commence derrière la voiture ; `setBuildings3d` / `setArrowColor` relancent tout `replayState` ; hauteur d'`assistantPanel` après fusion ; tests de bornes manquants.
- Constatés par cet audit (*non vérifiés sur appareil*) : alternance de l'index de manœuvre ; NOW sautable à grande vitesse ; voix en file sans priorité ; côté perdu pour bretelles / sorties / demi-tours ; arrondis voix ≠ écran ; Horizon en longueurs Valhalla ; marqueur posé instantanément alors que la caméra glisse 1 s ; `reportsAhead` O(N·R) à chaque position ; course perdue à la mort du processus ; bip sur `STREAM_MUSIC` hors focus.

---

## 10. Performance

- GPS : `LocationMode.GUIDANCE(1000)`, `PRIORITY_HIGH_ACCURACY`, min 500 ms, callbacks sur le Looper principal. Chien de garde 1 s.
- **À chaque position** : (1) nouveau `Guiding` ; (2) `combine` → `buildUiState` recrée tout `NavigationUiState` ; (3) `reportsAhead` recrée `RouteGeometry` (O(N)) et projette chaque signalement (O(N·R)) ; (4) recomposition de tout `NavigationContent` + `state.copy` + `horizonItems` ; (5) `setUserLocation` + `setTraversed` qui **reconstruit le GeoJSON p0..p_segmentIndex** (croissant) ; (6) `easeCamera` 1 s ; (7) notification si le texte change.
- Marqueur utilisateur sans interpolation à 1 Hz alors que la caméra glisse 1 s → saut probable (*non vérifié*).
- Rendu MapLibre : pas de `setMaximumFps`, pas de `textureMode`, pas de préchargement.
- **Mesures : aucune** (ni FPS, ni jank, ni batterie). Toute affirmation chiffrée est *non vérifiée*.
