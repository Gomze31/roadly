# Roadly — Compte rendu de la navigation 3D premium

Date : 2026-11-01. Branche `feat/roadly-3d`, de `027f272` (fin du LOT 12) à `991795e` (fin de l'étape E), 77 commits. Étape F (validation) faite sans appareil : tout ce qui demande le téléphone ou un trajet réel est indiqué comme restant à faire, avec la marche à suivre.

Documents liés : `ROADLY_3D_NAVIGATION_AUDIT.md` (audit de départ), `ROADLY_3D_NAVIGATION_PLAN.md` (plan B à F), `ROADLY_3D_NAVIGATION_TESTS.md` (matrice des 20 scénarios, mesures, journal des essais), `SILLAXE_PARITY.md` (parité), `docs/superpowers/specs/2026-10-31-roadly-3d-guidance-design.md` et les cinq plans de `docs/superpowers/plans/` (2026-10-31-roadly-3d-guidance, -etape-b, -etape-c, 2026-11-01-roadly-3d-etape-d, -etape-e).

---

## 1. Fonctionnalités réellement implémentées

Seul ce qui est dans le code et couvert par des tests verts figure ici. Le niveau de vérification sur appareil est donné pour chaque étape.

### Phase 1 — Guidage 3D (plan 2026-10-31-roadly-3d-guidance)
- **Caméra de guidage inclinée** : `GuidanceCameraPolicy` (`core/map`, pur), avec un zoom qui suit la vitesse, une inclinaison de 58°, un cap qui suit le véhicule et des marges qui placent la voiture dans le tiers bas en portrait et en bas à droite en paysage. Appliquée par `MapController.applyGuidanceCamera`.
- **Bâtiments 3D** : couche extrudée lue dans `building` (`render_height`) d'OpenMapTiles, à partir du zoom 15, teintée par le jeton `buildings3d` (`core/designsystem`).
- **Approche d'intersection** : à 150 m, ou à 400 m pour une sortie au-delà de 70 km/h. Le zoom se resserre et une flèche de manœuvre est tracée sur l'itinéraire (`ManeuverArrowBuilder`, `setManeuverArrow`, `setArrowColor(body, casing)`).
- **Panneau de sortie** : `exitToward` / `exitBranch` lus dans Valhalla (`sign.exit_toward_elements`, `exit_branch_elements`). La pastille « SORTIE n » et la ligne « Direction … » s'affichent quand le serveur fournit le panneau.
- **Réglage « Carte 3D en guidage »** et suivi de l'économiseur d'Android (`rememberPowerSaveMode`) : sans bâtiments et à 45° en économie.
- Vérifié sur le HONOR après la vague de correctifs, 6 points sur 6 : voiture au-dessus de l'Horizon, flèche lisible de jour comme de nuit, économiseur, recentrage, aucun `FATAL`.

### Étape B — Fiabilité du guidage
- **B1, progression monotone** : `RouteTracker` ne fait reculer `nextManeuverIndex` qu'après un recul confirmé de plus de 30 m. La marge de passage vaut `min(max(10 m, 2 × précision), 60 m, la moitié du tronçon)`. Une avance par extrapolation reste provisoire. L'approche garde une hystérésis (`approachLatched`) jusqu'au changement de manœuvre. Jeu de positions simulées `FixScript` (`core/navigation/testing`).
- **B2, base de distance unique** : `RouteProgress.distancesToManeuversM` est calculé sur la même abscisse que le bandeau. La voix et l'écran partagent une même table d'arrondi (`Formatters`, `SpokenPhrases`).
- **B3, modèle de manœuvre enrichi** : `ManeuverType` connaît le côté (`RAMP_*`, `EXIT_*`, `MERGE_*`, `UTURN_*`). S'y ajoutent `bearing_before` / `bearing_after`, `end_shape_index`, `side_of_street`, les `street_names` complets et les masques de voies `valid` / `active` séparés (`ValhallaManeuverTypes`, `ValhallaLanes`, `ValhallaDtos`). La fixture FOSSGIS réelle `valhalla_motorway.json` couvre les sorties 31b et 19 et l'échangeur A620.
- **B4, ronds-points** : `RoundaboutInfo` est construit par `RoundaboutResolver` à partir de ENTER (26) et EXIT (27) et de l'anneau réel. La cible est figée à l'entrée, et la sortie n'est annoncée que dans l'anneau.
- **B5, annonces** : le NOW part à `max(40 m, 4 s × vitesse)`. Deux manœuvres rapprochées (moins de 200 m) donnent une phrase « puis … ». Aucun FAR n'est dit dans les 10 s qui suivent une manœuvre. Les priorités sont URGENT, NORMAL et LOW (`SpeechPriority`) : un NOW vide la file, une phrase périmée (8 s) est jetée, chaque phrase a un délai de garde (`SpeechQueue`, `AndroidSpeechService`).
- **B6, hors-itinéraire et continuité** : le hors-itinéraire exige un écart de cap de plus de 50° sur 3 positions, ou un écart de plus de 100 m. Les fixes de précision supérieure à 30 m sont ignorés, ce qui supprime aussi le NOW. La continuité est privilégiée sur les routes parallèles et sous les ponts. La course est mémorisée dans DataStore (`LastGuidance`) au départ, puis effacée à l'arrêt et à l'arrivée.
- Vérifié par 374 tests JVM verts à la fin de l'étape. Aucune passe appareil propre à B : le `kill -9` (scénario 14) a été vérifié pendant la passe C.

### Étape C — Carte 3D
- **C1, caméra** : `NavigationCameraController` (`core/map`, pur) filtre le cap (moyenne circulaire, τ 1,5 s, passage 359° → 1° sans tour complet). `CameraMode3D` propose 3D, 2D et nord en haut, avec des transitions de 600 ms. Un recalcul ou une alternative garde le cap et le zoom. Le marqueur est interpolé sur 1 s (`MarkerInterpolation`). Côté écran : boutons 2D/3D et nord en haut, « Recentrage dans N s · Annuler » et « Recentrer », et une rotation qui ne remet plus la caméra à zéro.
- **C2, style Roadly** : `core/map/src/main/assets/styles/roadly-dark.json` et `roadly-light.json`, produits par `core/map/tools/build-roadly-styles.ps1` à partir des styles OpenFreeMap. Ils apportent une pile routière de 7 calques (autoroute, voie rapide, nationale, départementale, locale), les tunnels dès le zoom 6, les ponts, les bretelles et des étiquettes allégées. L'URL du style est lue en `asset://` (`ProviderConfig`). Niveau de détail `MapDetail` (Normal / Réduit) avec réduction automatique au-delà de 90 km/h et retour sous 80 km/h (`MapDetailPolicy`).
- **C3, performance** : tronçon parcouru incrémental par paquets de 32 segments (`TraversedLine`). Les signalements sont projetés une fois par tracé (`ReportsAheadProjector`). Le rendu est plafonné à 60 i/s, 30 en économie (`MapRendering`, `FrameGate`). Journal `RoadlyFps` en build débogable (`FpsWindow`).
- Vérifié à l'arrêt sur le HONOR le 2026-10-31 : styles jour et nuit, bascules ×5, recentrage, rotation, économie (28,6 i/s), mode avion. Mesures : voir § 3.

### Étape D — Interface premium
- **D1, bandeau interactif** (`BannerStack`, `ManeuverBanner`) : un toucher ouvre l'aperçu de la manœuvre (`ManeuverPreviewSheet`), avec grande icône ou schéma, voies et les 3 manœuvres suivantes. L'aperçu se ferme au changement de manœuvre. La ligne « puis → … » s'affiche à moins de 500 m.
- **D2, schéma de rond-point** (`RoundaboutManeuverView`, `RoundaboutGeometry.sweepFromRing`) : anneau, branche d'entrée, arc parcouru tiré de l'anneau réel (borné à 40°–340°), sortie, numéro et « vers {rue de sortie} ». Il existe en version compacte (bandeau) et grande (aperçu).
- **D3, panneau de direction** (`ExitSignPanel`) : numéro de sortie, directions, numéro de route et distance, avec une distinction « Prochaine sortie » / « Sortie suivante ». Le texte suit le jeton `onBrandAuto` pour rester lisible sur toutes les teintes du cockpit.
- **D4, voies** (`LaneGuidanceView`, qui remplace `LaneStrip`) : voie conseillée, possible ou interdite. Les voies sont visibles de 400 m jusqu'à la manœuvre et masquées sans données.
- **D5, panneau du bas** (`NavigationBottomPanel`, `TripDetailsSheet`, `PanelGeometry`) : panneau repliable par la poignée, avec arrivée, temps, distance, voix, destination, « Trajet complet » et Arrêter. Un toucher sur l'ETA ouvre le trajet complet, et Retour replie le panneau sans arrêter le guidage. La marge de la caméra suit la hauteur mesurée (pas de 2 %). La colonne d'actions reste au-dessus du bloc du bas. Le paysage a une version compacte. L'icône d'attribution est accessible (`MapAttribution`). L'état des feuilles et du panneau est porté par le ViewModel (`guidanceSheet`, `bottomPanelExpanded`, `bottomPanelFraction`, `destinationName`).
- Vérifié : 11/11 tests instrumentés `NavigationPremiumUiTest` verts sur le HONOR, plus une passe à l'arrêt et deux vagues de correctifs. À l'issue, la flèche de manœuvre est entière avec `ZOOM_APPROACH = 17,5` et l'Horizon masqué tant que le panneau de direction est affiché (`3c02729`).

### Étape E — Audio et intégration
- **E1, coordinateur vocal** : `VoiceArbiter` (table d'arbitrage pure) et `SpeechQueue` à deux canaux, appliqués par `AndroidSpeechService`. L'urgence coupe tout. Le guidage passe avant l'assistant, qui ne reprend que 300 ms après la dernière phrase du guidage. Le focus audio est rendu sur le fil principal (`settle`). `speaking` couvre les deux canaux. `release()` est différé tant que l'assistant parle (10 s au plus). Membres `VoiceChannel`, `speak(…, priority, channel)`, `stop(channel)`, `setMuted(muted, channel)`. La session ne coupe et ne met en sourdine que le canal GUIDANCE. « Tester la voix » passe par le canal ASSISTANT.
- **E2, réglages « Navigation »** (`NavigationGroup`, `SettingsViewModel`, `UserPreferencesRepository`) : vue au départ (3D, 2D, nord en haut), rotation, zoom (−1 / 0 / +1), inclinaison (`PitchPreset` 45 / 58 / 60°), détail, bâtiments 3D (`map.3d`), ligne « puis » et économie d'énergie (`PowerSaveMode` AUTO / ON / OFF). Ces réglages sont persistés dans DataStore et appliqués à la caméra et au guidage. `CameraMode3D` a été déplacé dans `core:model`.
- **E3, reprise** (`ResumeGuidanceCard`, `HomeViewModel.resumeLastGuidance`) : la carte d'accueil « Reprendre le guidage vers … » s'affiche après la mort du processus. L'itinéraire est refait depuis la position vers la destination mémorisée. « Ignorer » oublie la course et annule un calcul en cours.
- Vérifié par 521 tests JVM verts. **La passe appareil E n'est pas faite** (§ 6).

---

## 2. Fichiers créés ou modifiés

`git diff --stat 027f272..HEAD` : **194 fichiers, 22 817 insertions, 416 suppressions** ; `git log --oneline 027f272..HEAD | wc -l` : **77 commits**.

| Module | Fichiers | Lignes | Principaux fichiers |
|---|---|---|---|
| `core/map` | 32 | +1 870 / −19 | créés : `GuidanceCameraPolicy`, `NavigationCameraController`, `Bearings`, `ManeuverArrowBuilder`, `MarkerInterpolation`, `MapDetailPolicy`, `MapRendering`, `FrameGate`, `FpsWindow`, `TraversedLine`, `MapAttribution`, `PowerSaveMode` (suivi de l'économiseur), `assets/styles/roadly-{dark,light}.json`, `tools/build-roadly-styles.ps1`, `tools/upstream/{dark,positron}.json` ; modifiés : `MapController`, `MapLayers`, `RoadlyMap`, `CameraSnapshot` ; 10 classes de test |
| `core/navigation` | 32 | +2 132 / −186 | créés : `RoundaboutResolver`, `RoundaboutGeometry`, `SpeechQueue`, `VoiceArbiter`, `testing/FixScript` ; modifiés : `RouteTracker`, `AnnouncementPolicy`, `NavigationSession`, `NavigationState`, `GuidanceConstants`, `SpeechService`, `AndroidSpeechService`, `NavigationNotifications`, `testing/FakeSpeechService`, `testing/TestRoutes` ; tests : `RouteTrackerProgressTest`, `RouteTrackerParallelRoadsTest`, `RoundaboutResolverTest`, `RoundaboutGeometryTest`, `MotorwayGuidanceTest`, `NavigationSessionGuidanceTest`, `NavigationSessionVoiceTest`, `SpeechQueueTest`, `VoiceArbiterTest`, fixture `valhalla_motorway.json` |
| `core/routing` | 9 | +221 / −29 | créé : `ValhallaManeuverTypes` ; modifiés : `ValhallaDtos`, `ValhallaLanes`, `ValhallaRoutingProvider` ; tests : `ValhallaMotorwayFixtureTest`, `ValhallaManeuverTypesTest`, fixture |
| `core/model` | 8 | +134 / −4 | créés : `CameraMode3D`, `MapDetail`, `PitchPreset`, `PowerSaveMode` ; modifiés : `Route`, `RouteProgress` |
| `core/data` | 5 | +242 / −5 | `ProviderConfig` (`asset://`), `UserPreferencesRepository` (`LastGuidance`, réglages E2), `FakeUserPreferencesRepository`, tests |
| `core/designsystem` | 5 | +100 / −5 | `Color` (`buildings3d`, `onBrandAuto`), `GlassPanel` (libellé et rôle TalkBack), `ManeuverIcon` (côtés) ; tests `ManeuverIconTest`, `OnBrandAutoTest` |
| `core/common` | 2 | +29 / −0 | `Formatters` (table d'arrondi commune) |
| `feature/navigation` | 27 | +2 938 / −158 | créés : `BannerStack`, `ExitSignPanel`, `RoundaboutManeuverView`, `LaneGuidanceView`, `ManeuverPreviewSheet`, `NavigationBottomPanel`, `TripDetailsSheet`, `PanelGeometry`, `GuidanceLayout`, `ReportsAheadProjector` ; supprimé : `LaneStrip` ; modifiés : `NavigationScreen`, `NavigationViewModel`, `NavigationUiState`, `ManeuverBanner`, `strings.xml` ; instrumentés : `NavigationPremiumUiTest`, `TestNavigation` ; 7 classes de test JVM |
| `feature/home` | 7 | +275 / −6 | créé : `ResumeGuidanceCard` ; modifiés : `HomeScreen`, `HomeViewModel`, `HomeUiState`, `strings.xml`, `build.gradle.kts`, `HomeViewModelTest` |
| `feature/settings` | 5 | +227 / −2 | créé : `NavigationGroup` ; modifiés : `SettingsScreen`, `SettingsViewModel`, `strings.xml`, `SettingsViewModelTest` |
| `feature/route` | 2 | +16 / −1 | `RouteViewModel` (`destinationName` transmis à la session), test |
| `feature/trips` | 1 | +2 / −1 | `ReplayScreen` (`animateMs = 0`) |
| Documentation | 11 | +14 631 / −0 | `ROADLY_3D_NAVIGATION_{AUDIT,PLAN,TESTS}.md`, spec et 5 plans `docs/superpowers/`, `SILLAXE_PARITY.md`, `docs/DESIGN-SYSTEM.md` |
| Captures | 48 | binaires | `docs/captures/etape-c/`, `docs/captures/etape-d/` |

Le module `app` n'est pas modifié par la branche.

---

## 3. Tests exécutés et résultats

### Étape F (2026-11-01, `991795e`, sans appareil)

| Commande | Résultat |
|---|---|
| `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q` | **vert, 521 tests, 0 échec, 0 erreur, 0 ignoré** (tâches à jour depuis le build de fin d'étape E ; résultats lus dans `*/build/test-results/**/*.xml`) |
| `.\gradlew.bat :app:assembleDebug --offline -q` | **vert** (`app-debug.apk`) |
| `.\gradlew.bat :app:lintDebug --offline` | **exécuté en entier hors ligne, échec sur 5 erreurs** ; 31 avertissements |
| Tests instrumentés | **non exécutés** (aucun appareil branché) |

Tests JVM par module :

| Module | Tests | Module | Tests |
|---|---|---|---|
| `core/navigation` | 186 | `core/geocoding` | 19 |
| `core/map` | 77 | `core/model` | 17 |
| `feature/navigation` | 64 | `core/common` | 14 |
| `core/routing` | 38 | `feature/settings` | 12 |
| `core/data` | 35 | `feature/home` | 11 |
| `feature/route` | 11 | `feature/search` | 11 |
| `app` | 9 | `core/location` | 8 |
| `core/designsystem` | 7 | `feature/trips` | 2 |
| **Total** | **521** | | |

Évolution : 224 tests avant la mission, 291 à la fin du LOT 12 (`027f272`), 303 en fin de phase 1, 374 en fin d'étape B, 431 en fin d'étape C, 473 en fin d'étape D, 521 en fin d'étape E.

### Lint (`app/build/reports/lint-results-debug.xml`)
- **5 erreurs, 31 avertissements, 0 fatal.** Les 5 erreurs sont dans des fichiers que la branche ne modifie pas (dernier commit : `027f272`, LOT 12) :
  - `app/src/main/java/fr/roadly/app/RoadlyNavHost.kt:67` — `RestrictedApi` : `NavController.getCurrentBackStack` réservé au groupe `androidx.navigation` ;
  - `app/src/main/java/fr/roadly/app/RoadlyNavHost.kt:122` — `LocalContextGetResourceValueCall` : lecture de ressource par `LocalContext.current` ;
  - `app/src/main/res/layout/widget_roadly.xml:10`, `:15`, `:20` — `UseAppTint` : `android:tint` au lieu de `app:tint`.
- Avertissements : `GradleDependency` ×21 (`gradle/libs.versions.toml`, versions plus récentes disponibles), `UnusedAttribute` ×3, `UseCompoundDrawables` ×3, `DataExtractionRules`, `MonochromeLauncherIcon`, `ObsoleteSdkInt`, `OldTargetApi` (×1 chacun).
- Portée : `lintAnalyzeDebug` tourne sur tous les modules, mais le rapport de `:app` ne cite que `app` et `gradle/libs.versions.toml`. Sans `checkDependencies`, les constats des modules `core` et `feature`, où vit le code de la branche, n'y figurent pas. Un lint par module (`:feature:navigation:lintDebug`, …) n'a pas été lancé.

### Instrumentés
- `NavigationPremiumUiTest` : **11/11 verts sur le HONOR** (`AREE026226000174`) le 2026-11-01, à `5d82e35` puis rejoués à `4028943` et `3c02729` sans changement de sélecteur. Ils couvrent bandeau → aperçu, aperçu refermé au changement de manœuvre, rond-point (annonce numéro + rue), « vers … » déplié et replié, voies absentes ou présentes à 300 m, poignée → panneau déplié (56 dp, marge de la caméra), ligne ETA → trajet complet, Retour sur le panneau déplié, overlay paysage.
- Total du projet : 14 instrumentés (+ `FavoriteDaoTest` ×2, `GlassSearchBarTest` ×1). L'étape E n'en ajoute aucun, et aucun n'a été rejoué depuis.

### Passes appareil par étape (HONOR Magic 8 Pro, toutes à l'arrêt)
| Étape | Passe | Résultat |
|---|---|---|
| Phase 1 | 2026-10-31, puis après la vague de correctifs | OK, 6 points sur 6 |
| B | aucune (téléphone verrouillé) ; scénario 14 (`kill -9`) vérifié pendant la passe C | — |
| C | 2026-10-31, 23:06–23:26 | OK, sauf le jank C3 : 8,16 % sur la fenêtre officielle (147 images), 1,37 % sur la suivante ; `RoadlyFps` 55,8 i/s (28,6 en économie) ; batterie non mesurable (téléphone en charge, l'application ne compte que l'écran : 29,4 mAh sur 20 min) |
| D | 2026-11-01 : passe complète puis deux vagues de correctifs | OK après correctifs ; LIMITE acceptée en paysage sur petit écran |
| E | **à faire** | — |

---

## 4. Fonctionnalités partiellement prises en charge

- **Paysage sur petit écran (HONOR, colonne gauche de 294 dp).** Si le bandeau déplié montre une ligne « puis », le panneau du bas ne peut pas rester déplié : il s'ouvre puis se replie seul. À l'approche d'une sortie, le panneau replié coupe la 2e direction du panneau de direction (« Tarbes - Lourdes » visible sur 5 dp). Le chevauchement de la pile bandeau + « puis » + panneau + voies à l'approche d'une sortie avec voies reste à mesurer en roulant.
- **Voies.** `LaneGuidanceView` est réel et validé sur JVM et en instrumenté. Mais le serveur public FOSSGIS n'a renvoyé aucune voie sur les trajets enregistrés : le composant n'a jamais été vu sur appareil avec des données. Débordement possible avec beaucoup de voies (cas rare, non observé).
- **Rond-point.** Les autres sorties ne sont jamais dessinées (absentes de `/route`). Le schéma suit l'arc parcouru sur l'anneau, pas le cap exact de la branche de sortie (3e sortie de l'autoroute : cap +140°, arc 282°) : les arcs courts sont portés à 40° et les demi-tours bornés à 340°. Seule la circulation à droite est gérée, un arc négatif étant ramené à 40°. Le numéro de sortie apparaît deux fois (pastille + schéma compact), écart laissé.
- **Panneau de direction.** Il n'apparaît que si Valhalla renvoie `sign`. Contraste de nuit d'environ 4,2:1, admis seulement pour du grand texte. Relevés non corrigés : lignes de direction en 16 sp sous 4,5:1 sur certaines teintes, sur-titre à 85 % d'opacité sur MENTHE de jour, couleur de la flèche sur les teintes claires. `exit_name_elements` (« Le Palays ») n'est pas modélisé.
- **Mesure du jank inconclusive.** 8,16 % sur la fenêtre officielle de 120 s (seuil d'acceptation : 5 %, donc ÉCHEC noté), 1,37 % sur la fenêtre suivante. `gfxinfo` ne voit que les overlays Compose. La mesure « avant » (`c4d05ef`) n'a pas été faite. Arbitrage : mesure non concluante, aucun défaut de code identifié, à reprendre sur plusieurs fenêtres continues de 2 min. La consommation de batterie n'a pas pu être mesurée (téléphone en charge).
- **Passe appareil E non faite** : voix avec musique, préemption de l'assistant par « Tester la voix », persistance et effet des réglages E2, carte « Reprendre » après `kill -9`, arrière-plan et écran verrouillé.
- **Aucun scénario de B, C ou D en roulant** : autoroute, ronds-points, tunnel, recalcul réel, arrivée, Replay d'un trajet enregistré (§ 6).
- **TTS avec musique.** Essai bloqué à l'étape C par le sélecteur d'application ; à faire en roulant.
- **Inclinaison.** 62° (ville dense, plan C1) et 65° (préréglage E2) sont impossibles : MapLibre Android 11.13.5 plafonne à 60° (`MAXIMUM_PITCH`). D'où `PITCH_DENSE_CITY = 60` et `PitchPreset.HIGH = 60`. Le préréglage bas (45°) supprime aussi les 60° de ville dense.
- **Zoom.** Le décalage « +1 » ne resserre pas l'approche, qui reste à 17,5 pour garder la flèche entière (`3c02729`).
- **Annonces.** Sur des fixes de précision supérieure à 30 m, le NOW n'est pas dit (le NEAR l'a précédé). Un message de l'assistant coupé par le guidage est redit depuis le début. Après un NOW, il est jeté avec sa file. S'il attend plus de 8 s, il n'est pas dit. Le bip de dépassement et de zone reste sur `STREAM_MUSIC`, hors focus audio. Le volume à zéro rend muets les deux canaux.
- **Hors-itinéraire.** Les fixes sans cap remettent à zéro la série d'écarts : un arrêt-redémarrage lent entre 40 et 100 m du tracé ne déclenche jamais le recalcul par la règle souple. Les divergences de moins de 50° ne recalculent que par la règle des 100 m.
- **Trajet complet.** La feuille donne la longueur de chaque étape (Valhalla), sans distance cumulée depuis la position (règle B2).
- **Marge de la caméra.** Elle est quantifiée par pas de 2 % et ignorée en paysage. Après la fusion avec Roadly AI, `assistantPanel` n'est pas compté tant que la mesure n'est pas déplacée (§ 8).
- **Reprise (E3).** Pas de demande de permission de notification sur ce chemin. Les arrêts intermédiaires ne sont pas mémorisés. Ce sont les préférences et le véhicule du moment qui s'appliquent. La course mémorisée n'a pas de limite d'âge. `NavigationService` reste en `START_NOT_STICKY` : la mort du processus termine la course, sans service ni notification, et la reprise est manuelle. `resumeRequested` peut rester bloqué si `start()` échoue juste après une reprise (rare).
- **Réglages.** Un « nord en haut » persisté laisse le contrôle segmenté sans sélection (cas jugé inatteignable). Partant d'un nord en haut, le bouton 2D/3D va en 3D et non vers la vue choisie (règle C1). Tant que la vue au départ n'a jamais été choisie, elle suit l'ancien `map.3d`. Le choix de la voix et le débit de synthèse ne font pas partie de E2.
- **Carte.** Une position qui arrive pendant la transition 2D/3D de 600 ms est remplacée par un glissement de 1 s : la vitesse n'est pas continue. Sur l'accueil, le marqueur glisse en 1 s quand la caméra s'anime en 0,6 s. Le seuil de `FrameGate`, en millisecondes entières, donne environ 45 écritures/s sur un écran à 90 Hz. Un léger point plus sombre peut apparaître à la jonction préfixe / queue du tronçon parcouru (à regarder sur appareil). Le cercle de précision n'est pas recoloré par `setRouteColors`. Le détail « Réduit » peut rester verrouillé si on repasse à Normal entre 80 et 90 km/h.
- **Style.** La licence des styles OpenFreeMap `dark` et `positron`, désormais embarqués, est à confirmer avant une publication sur le Play Store. Les points d'intérêt dès le zoom 14 sont limités à `hospital`, `fuel`, `parking` et `railway`. Leur présence dans les tuiles sur appareil n'est pas consignée. Les ponts ne sont pas surélevés (limite amont).
- **Horizon.** Les virages et les zones gardent l'abscisse de la géométrie brute : écart de moins de 2 %, 0,06 % sur Paris.
- **Icônes.** `MERGE_LEFT` et `MERGE_RIGHT` utilisent les flèches « légèrement à gauche / à droite » : Material n'a pas d'icône de fusion avec côté.
- **Lint.** Le rapport ne couvre pas les modules `core` et `feature` (§ 3).

---

## 5. Limites du moteur de routage et des données

### Valhalla public (FOSSGIS, `valhalla1.openstreetmap.de`, fair-use)
- **Voies** : aucune `lanes` observée sur les trajets enregistrés (Toulouse, autoroute et ville).
- **Limitations de vitesse** : `/route` n'en renvoie pas, et `trace_attributes` est refusé sur `*.openstreetmap.de`. Il faut un Valhalla auto-hébergé.
- **Ronds-points** : `roundabout_exit_count` n'est présent que sur la manœuvre ENTER (26), jamais sur EXIT (27). Le nombre total de sorties est absent (`exitCount = null`), et les autres sorties ne sont pas décrites. Fiabilité dépendante d'OSM.
- **Trafic** : aucun.
- **Péages** : oui / non seulement (`toll`) ; aucun montant.
- **Panneaux** : `sign` est présent sur les sorties numérotées (31b et 19 de la fixture). `exit_name_elements` est renvoyé mais non modélisé.
- Pas de `edge_index` ni de `brunnel` dans `/route` (seulement dans `trace_attributes`).

### Tuiles OpenMapTiles (OpenFreeMap)
- **Pas de `maxspeed`** dans les tuiles, ni feux, ni ralentisseurs, ni relief.
- **Aucun calque de points d'intérêt dans les styles amont** (`dark` : 47 calques, `positron` : 55). Les deux calques `poi` sont ajoutés par le script de style. La classe `station` n'existe pas dans OpenMapTiles (gares : `railway`).
- **Bâtiments** : `render_height` et `hide_3d` sont présents (couche `building`), d'où des bâtiments extrudés réels dès le zoom 15.
- Tunnels et ponts : `brunnel` présent dans `transportation`. L'amont ne le distingue pas (`dark`) ou seulement pour les autoroutes (`positron`) ; la pile Roadly l'exploite.

### MapLibre Android 11.13.5
- Inclinaison maximale de 60°.
- `dumpsys gfxinfo` ne mesure pas la `SurfaceView` GL de la carte.

---

## 6. Vérifications restant à effectuer sur un trajet réel

À faire par l'utilisateur, sur le HONOR, avec l'APK de `991795e` (`.\gradlew.bat :app:installDebug --offline`) et `adb logcat -s RoadlySpeech:D RoadlyFps:D`. Consigner chaque essai, avec sa date et son résultat, dans le journal de `ROADLY_3D_NAVIGATION_TESTS.md`.

1. **Autoroute à 110–130 km/h avec sortie numérotée** (par exemple la sortie 31b ou 19 de la rocade toulousaine) : approche à 400 m, panneau de direction, flèche entière, NOW au moins 3 s avant, détail réduit au-delà de 90 km/h puis rétabli sous 80 km/h, zoom selon la vitesse (scénario 7).
2. **Échangeur à plusieurs bretelles** : ligne « puis … », côté de la bretelle, « Sortie suivante » dans l'aperçu (scénario 8).
3. **Chaîne de ronds-points** (deux ronds-points rapprochés, dont un d'au moins 3 sorties) : schéma et numéro, « vers {rue} », cible stable dans l'anneau, sortie annoncée dans l'anneau, pas de FAR doublé (scénarios 4, 5 et 6).
4. **Route parallèle, puis vraie erreur de parcours** : pas de recalcul sur la desserte, recalcul après la vraie erreur, index et annonces repartis de zéro (scénarios 9 et 12).
5. **Tunnel suivi d'un virage** : extrapolation, pastille « GPS perdu » après 10 s, NOW dit au virage, aucun recul du bandeau à la sortie du tunnel (scénario 11).
6. **Caméra au recalcul et au changement d'itinéraire** (feuille « Changer d'itinéraire ») : transition douce, cap et zoom gardés, pas de coupure (C1).
7. **Tronçon parcouru sur 20 min** : ligne parcourue continue, sans point sombre à la jonction ; mesure de batterie téléphone débranché (`batterystats`, 20 min) ; jank `gfxinfo` sur plusieurs fenêtres continues de 2 min (C3).
8. **Voix avec musique en lecture** : FAR coupé par un NOW, NEAR dit, musique atténuée puis rendue ; préemption de l'assistant par « Tester la voix » (scénarios 16 et 17, passe E). Après fusion avec Roadly AI : coupure de la voix live par un NOW.
9. **Mode avion en guidage** : style embarqué, tuiles en cache, recalcul refusé proprement, ancien tracé gardé (scénario 20).
10. **Centre-ville dense à moins de 30 km/h** : inclinaison à 60° avec les bâtiments, GPS imprécis sans faux hors-itinéraire ni NOW faux (scénario 10).
11. **Préréglages de zoom (−1 / 0 / +1) et d'inclinaison (45 / 58 / 60°) à vitesse réelle**, vue au départ 2D ou nord en haut, économie d'énergie forcée (E2).
12. **Écran verrouillé 10 min** : notification vivante, annonces prononcées. Puis retour par la notification et rotation en cours de route (E3).
13. **Reprise après la mort du processus** : `adb shell run-as fr.roadly kill -9 <pid>` en guidage, puis relance, carte « Reprendre le guidage vers … », « Reprendre » et « Ignorer » (scénario 14). `am kill` ne tue pas un processus qui tient un service au premier plan.
14. **Arrivée** : panneau replié, feuille fermée, course oubliée (scénario 13). Puis **Replay** du trajet enregistré.
15. **Paysage en roulant** : approche d'une sortie avec « puis » et, si possible, avec voies (scénario 19).
16. **Vue des voies**, si un serveur en renvoie (Valhalla auto-hébergé ou autre trajet) : voie conseillée, possible ou interdite, apparition à 400 m (scénario 18).
17. **Instrumentés après E** : `.\gradlew.bat connectedDebugAndroidTest --offline` sur le HONOR, pour rejouer les 14 tests (11 attendus verts pour `NavigationPremiumUiTest`).

---

## 7. Décisions prises

Chaque décision est suivie de son coût si elle se révèle fausse.

### Phase 1
- `setArrowColor(body, casing)` avec un liseré à la couleur de marque, car le liseré du tracé est blanc de jour, comme le corps de la flèche. Coût : un paramètre de plus et un contour un peu plus marqué.
- Marge basse de 0,12 en portrait (valeur mesurée par le relecteur) plutôt que 0,35. Coût : un nouveau réglage de marge après un essai sur appareil.

### Étape B
- Marge de passage `min(max(10 m, 2 × précision), 60 m, la moitié du tronçon)`, pour que l'arrivée se déclenche sur un tronçon final court. Coût : une avance d'index un peu précoce sur les tronçons très courts.
- Une position extrapolée peut faire avancer l'index, jamais confirmer un recul. Coût : un vrai recul pendant un trou GPS est confirmé un fix plus tard.
- Approche de 400 m testée sur la sortie 19 et de 150 m sur la 31b : la donnée réelle met une bretelle à 230 m avant la 31b, et la fixture n'est pas modifiée. Coût : approche à 400 m de la 31b non couverte en JVM.
- NOW interdit sur un fix de précision supérieure à 30 m, comme l'extrapolation : un NOW faux et précoce est pire qu'un NOW manquant. Coût : NOW manquant en canyon urbain.
- Une avance provisoire par extrapolation est annulée au premier fix réel en retrait, sans confirmation à 2 fixes. Coût : un retour arrière parasite du bandeau après un trou GPS.
- `retry()` réutilise `lastDestinationName`. Coût : un nom de destination périmé après un changement de destination raté.

### Étape C
- La rotation ne réinitialise pas le contrôleur de caméra (`landscape` retiré des clés d'effet), et une caméra en attente est rejouée sans animation au chargement du style. Coût : une image abrupte quand le style arrive tard.
- Recalcul ou alternative : cap et zoom gardés, transition de 600 ms ; remise à zéro seulement au premier tracé. Coût : une image adoucie depuis un cap périmé après un recalcul.
- Préfixe parcouru reconstruit par paquets de 32 segments. Coût : une queue de 34 points au plus par fix.
- Les setters ne touchent que leurs calques ; le rejeu complet est réservé au chargement du style. Coût : un calque oublié après une évolution, visible au prochain changement de style.
- Style corrigé dans le générateur : `water_name` au-dessus de la pile routière, pile insérée à `highway_minor`, tunnels dès le zoom 6. Coût : un nouvel ordre de calques à refaire si l'amont change.
- `PITCH_DENSE_CITY = 60` au lieu de 62 (plafond MapLibre). Coût : nul tant que MapLibre reste à 60.
- Boutons caméra en `Row` (manque de hauteur en paysage). Coût : une rangée de plus à réorganiser (faite en D5).
- Cache de `reportsAhead` dans `ReportsAheadProjector`, fichier propre à `feature/navigation`, pour limiter le conflit avec Roadly AI. Coût : deux projections coexistent après la fusion (IA pour ses outils, C pour l'écran).
- Journal `RoadlyFps` en build débogable, car `gfxinfo` ignore la carte. Coût : aucune mesure de la carte en build de production.
- Jank de 8,16 % jugé « mesure non concluante », sans défaut de code identifié. Coût : un vrai défaut de fluidité laissé en place jusqu'à la prochaine mesure.

### Étape D
- État des feuilles et du panneau porté par le ViewModel (pas de `rememberSaveable` vers les overlays, dont la signature est figée). Coût : quelques `StateFlow` de plus à réinitialiser à l'arrivée ou au recalcul.
- Schéma de rond-point tiré de l'arc parcouru sur l'anneau (`sweepFromRing`), pas de l'angle B4. Coût : un schéma décalé si l'anneau OSM est mal tracé.
- Flèche compacte du rond-point : pointe à 1,5 × le trait, trait de 0,08, pour dégager l'entrée aux bornes de 40° et 340°. Coût : une pointe petite, d'environ 7 × 6 dp.
- Toucher sur l'ETA → trajet complet. Aperçu fermé au changement de manœuvre. Panneau replié automatiquement à l'approche, Horizon masqué en paysage pendant le panneau et les voies. « vers {rue de sortie} » dans le bandeau du rond-point. Coût : de petits retours en arrière d'ergonomie, tous locaux.
- Jeton `onBrandAuto` au seuil de luminance de 0,30 (frontière 3:1) plutôt que 0,45. Coût : un texte moins contrasté sur les teintes intermédiaires.
- « Recentrer » prend la place de l'icône de voix dans la ligne repliée quand la carte a été déplacée (largeur en paysage). Coût : la voix n'est plus accessible que dans la colonne d'actions pendant ce temps.
- Correctifs de la passe appareil : colonne d'actions ancrée au-dessus du bloc du bas ; panneau déplié compact en paysage, qui se replie seul s'il déborde ; pastilles au-dessus du compteur ; pile du haut mesurée pour la marge haute de la caméra. Coût : la LIMITE de paysage acceptée (§ 4).
- Flèche entière à l'approche : marge haute de 0,58, puis `ZOOM_APPROACH = 17,5` et Horizon masqué pendant le panneau de direction, dans les deux orientations. Coût : moins de détail à l'approche, et pas d'Horizon pendant le panneau de direction.
- LIMITE du paysage sur petit écran acceptée sans nouvelle réorganisation. Coût : 5 dp de direction coupés, et un panneau qui se replie seul.

### Étape E
- Le coordinateur vocal est l'existant (`AndroidSpeechService` + `SpeechQueue`) plus une table pure, `VoiceArbiter`, sans nouvelle classe Android. Coût : le cœur Android n'est vérifié que sur appareil.
- Moteur non prêt : `drain()` remplacé par `settle()`. Coût : une phrase perdue si le moteur s'initialise pendant le rendu du focus.
- `stop()` et `setMuted()` gardent le sens « tous les canaux » ; la session passe au canal GUIDANCE. Coût : un appel tiers à `stop()` coupe aussi l'assistant.
- « Tester la voix » sur le canal ASSISTANT, en priorité LOW avec `flush`. Coût : l'essai est préempté par une annonce du guidage (voulu pour la passe E).
- `CameraMode3D` déplacé dans `core:model`, sous le même nom, sans `typealias`. Coût : cinq imports à reprendre lors de la fusion.
- `map.3d` ne règle plus que les bâtiments ; la vue au départ est déduite de `map.3d` tant qu'elle n'a pas été choisie (migration). Coût : une vue au départ inattendue pour un utilisateur qui avait coupé la 3D, puis réactivé les bâtiments.
- `PitchPreset.HIGH = 60` au lieu de 65. Coût : nul tant que MapLibre reste à 60.
- À l'approche, seul le décalage de zoom −1 s'applique. Coût : « Plus proche » sans effet à l'approche.
- Drapeau `resumeOfferOpen` à sens unique : une course reprenable vient forcément d'un processus précédent. Coût : la carte ne revient pas dans le même processus après un échec.
- Reprise = nouveau calcul depuis la position (tracé enregistré non réutilisé), `NavigationService` laissé en `START_NOT_STICKY`. Coût : reprise manuelle seulement, et itinéraire refait (réseau requis).
- Laissés en l'état : « nord en haut » persisté sans sélection (inatteignable), 2D/3D depuis le nord en haut → 3D (règle C1), `tts.stop` hors verrou. Coût : de petites incohérences d'affichage.

---

## 8. Points de fusion avec `feat/roadly-ai`

Base commune `60033fe`. Détail fichier par fichier : sections « Surface de fusion avec `feat/roadly-ai` » des plans B, C, D et E.

- **Signatures des overlays intactes.** `NavigationScreen`, `NavigationContent`, `PortraitOverlay` et `LandscapeOverlay`, ainsi que leurs lignes d'appel, ne sont pas touchés par la branche 3D. Les seuls changements sont `private` → `internal` (lignes 187 et 254) pour les tests instrumentés. Les insertions de l'IA (`assistantButton()` en tête des colonnes d'actions, `assistantPanel()` en tête du bloc du bas) restent séparées des modifications 3D par au moins une ligne inchangée. Après fusion, `TestNavigation.kt` et les appels `PortraitOverlay(…)` / `LandscapeOverlay(…)` des tests instrumentés doivent passer `{}` pour les deux nouveaux paramètres de l'IA, et construire le ViewModel avec `GuidanceActions`.
- **`SpeechService` : mêmes noms, même sens.** L'IA ajoute `val speaking: StateFlow<Boolean>`. La version E est un surensemble : même `speaking`, plus `VoiceChannel`, `speak(…, priority, channel)`, `stop(channel)`, `setMuted(muted, channel)`. Garder la version E de `SpeechService`, `AndroidSpeechService` (réécrit depuis B5 : les blocs `_speaking` de l'IA portent sur du code disparu) et `FakeSpeechService`. `FakeSpeechServiceTest` (IA) passe tel quel.
- **Deux modifications à faire dans le commit de fusion** :
  1. `AssistantController.announce` : passer de `speech.speak(text, flush = false)` à `speech.speak(text, priority = SpeechPriority.LOW, channel = VoiceChannel.ASSISTANT)`, pour que l'arrêt et la sourdine de la session n'effacent plus ses messages.
  2. `GuidanceActions.start` : passer `destinationName` à la session, `session.start(option, preferences, vehicle, destinationName ?: this.destinationName)`. Sinon `lastGuidance.destinationName` (B6, D5, E3) est perdu. `HomeViewModel.resumeLastGuidance` pourra ensuite déléguer à `guidance.startTo(Place(…))`.
  - À faire aussi : déplacer le `onGloballyPositioned` de `BottomBlock` sur la colonne du bas de `PortraitOverlay`, pour que la marge de la caméra compte `assistantPanel` (plan D, une ligne).
- **`reportsAhead` : conflit certain sur le corps de la fonction.** Garder la ligne C (`reportsProjector.ahead(…)`, cache par tracé) dans `NavigationViewModel`, et retirer l'import `fr.roadly.core.navigation.reportsAhead` s'il devient inutile. La fonction IA `core/navigation/ReportsAhead.kt` reste pour `NavigationTools`.
- **Colonne d'actions et `assistantButton`.** D5 a ancré la colonne d'actions au-dessus du bloc du bas et retiré « Itinéraires » de la colonne quand le panneau est déplié. `assistantButton()` s'insère en tête de colonne (après les lignes 223 et 278 de l'époque D) et s'ajoute à la hauteur. En paysage, la colonne approche déjà la hauteur de l'écran du HONOR : il faudra une capture après fusion, et `assistantPanel` en portrait se trouve au-dessus du compteur, donc hors du bloc mesuré tant que la mesure n'est pas déplacée.
- **Après fusion, comportement voulu.** `speaking` couvre le canal ASSISTANT : un message de l'assistant dit par le moteur met en pause la voix live de l'IA (`PAUSED_FOR_GUIDANCE`), pour qu'il n'y ait jamais deux voix à la fois. La voix live (Gemini, `USAGE_ASSISTANT`) reste hors du moteur et cède la parole par `speaking`. `followGuidanceSpeech` et `RESUME_AFTER_GUIDANCE_MS` restent tels quels.
- **Sans conflit.** `feat/roadly-ai` ne touche ni `core/map`, ni `core/model`, ni `core/data` (préférences), ni `RouteTracker` / `AnnouncementPolicy` / `NavigationUiState`, ni les ressources de `feature/navigation`. `SettingsScreen` et `strings.xml` se résolvent par union (lignes distinctes). `NavigationGroup` vit dans son propre fichier. `HomeNavigation.kt`, `RoadlyNavHost.kt` et `RouteViewModel.kt` ne sont modifiés que par l'IA, à l'exception de la ligne `destinationName` de `RouteViewModel` (B) : garder l'appel IA et passer par `GuidanceActions.start` (point 2 ci-dessus).
