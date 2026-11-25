# Roadly — Plan de la navigation 3D premium

Date : 2026-10-31. Base : `feat/roadly-3d` @ `7b4c800` (phase 1 livrée : caméra inclinée, bâtiments, flèche, approche, panneau de sortie, réglage « Carte 3D »). Audit de référence : `ROADLY_3D_NAVIGATION_AUDIT.md`. Tests : `ROADLY_3D_NAVIGATION_TESTS.md`.

Principes : aucune fausse fonctionnalité ; les composants existants sont améliorés, pas remplacés ; `NavigationSession` reste la source de vérité unique ; chaque étape se termine par `assembleDebug` + tests verts + essai sur le HONOR ; les signatures touchées par `feat/roadly-ai` (`NavigationScreen`, overlays, `NavigationViewModel.uiState`) restent minimales.

Chaque étape ci-dessous devient un plan d'implémentation détaillé (`docs/superpowers/plans/…`) exécuté par lots avec revue.

---

## Étape B — Fiabilité du guidage (priorité 1)

### B1. Progression monotone et stable
- `RouteTracker` : `nextManeuverIndex` ne recule jamais sauf recalcul ou recul confirmé > 30 m le long du tracé ; une manœuvre est « passée » quand `along > maneuverStartM + max(10 m, 2 × précision)`.
- Fenêtre de recherche conservée ; hystérésis de l'approche (`ApproachUi` reste actif jusqu'au changement d'index).
- **Acceptation** : test JVM « jitter ±8 m autour du point de manœuvre pendant 10 positions → index stable » ; test « double rond-point à 120 m → deux cibles distinctes, jamais sautées ».

### B2. Base de distance unique
- `RouteProgress` porte `distanceToNextManeuverM` et les distances aux manœuvres suivantes depuis la **même** abscisse curviligne (géométrie mise à l'échelle `distScale`) ; `NavigationUiState` n'additionne plus `lengthMeters`.
- `SpokenPhrases.distance` et `Formatters.distance` partagent une table d'arrondi (voix : 50 m sous 1 km, 100 m au-delà ; écran : idem pour la distance à la manœuvre).
- **Acceptation** : test « Horizon et bandeau cohérents (différence < 1 m) » ; test « 430 m → voix "450 mètres", écran "450 m" ».

### B3. Modèle de manœuvre enrichi (Valhalla)
- Parser `bearing_before`, `bearing_after`, `end_shape_index`, `verbal_post_transition_instruction`, `side_of_street`, `street_names` complets, masques `valid` / `active` des voies (séparés).
- `ManeuverType` gagne le côté : `RAMP_LEFT/RIGHT`, `EXIT_LEFT/RIGHT`, `MERGE_LEFT/RIGHT`, `UTURN_LEFT/RIGHT` (icônes fidèles) ; `Maneuver.turnAngleDeg` dérivé des bearings.
- **Acceptation** : fixture FOSSGIS réelle d'un trajet autoroutier (sortie + échangeur) ajoutée ; tests du parseur ; aucune régression `ValhallaRoutingProviderTest`.
- **LIMITE documentée** : voies et panneaux seulement si le serveur public les renvoie ; limitation de vitesse hors périmètre.

### B4. Ronds-points
- `RoundaboutInfo(exitCount: Int?, exitNumber: Int?, entryBearing, exitBearing, ringShape: List<GeoPoint>, exitStreet: String?)` calculé depuis ENTER (26) + EXIT (27) et la géométrie entre `begin_shape_index(ENTER)` et `begin_shape_index(EXIT)`.
- Angle de sortie = `bearing_after(EXIT) − bearing_before(ENTER)` ; **les autres sorties ne sont pas dessinées** (données absentes) ; sans `roundabout_exit_count` fiable → instruction directionnelle (« quittez le rond-point à droite ») sans numéro inventé.
- Cible de sortie figée à l'entrée (B1) ; manœuvre terminée seulement quand `along` dépasse le point de sortie.
- **Acceptation** : tests sur `ParisRoute` (2 ronds-points réels) : info calculée, cible stable sous jitter, instruction de sortie au bon moment.

### B5. Annonces : paliers par temps, priorité, anti-chevauchement
- `AnnouncementPolicy` : NOW déclenché à `max(40 m, 4 s × vitesse)` ; manœuvres rapprochées (< 200 m) → phrase combinée « puis … » (depuis `verbal_post` / instruction suivante) ; pas de FAR si la manœuvre précédente est à moins de 10 s.
- `SpeechService` : niveaux de priorité (NOW > NEAR > FAR > info) ; un NOW vide la file ; une phrase périmée (palier dépassé) n'est pas dite.
- **Acceptation** : tests « 130 km/h, 1 Hz → NOW jamais sauté » ; « FAR long en cours + NOW → NOW prononcé, FAR coupé » ; « aucune annonce dupliquée sur 10 positions ».

### B6. Recalcul et routes parallèles
- Hors-itinéraire : exiger en plus un écart de cap > 50° sur 3 positions ou `offRoute` > 100 m, et ignorer les positions avec précision > 30 m ; voies de desserte / ponts : la fenêtre de recherche privilégie la continuité (`along` croissant) à la distance brute.
- Persistance de la course (`lastOption`) dans DataStore → reprise proposée après mort du processus (« Reprendre le guidage vers … »).
- **Acceptation** : tests « route parallèle à 25 m pendant 8 s → pas de recalcul » ; « pont au-dessus → index stable » ; essai `adb shell am kill` → reprise proposée.

---

## Étape C — Carte 3D

### C1. Caméra : lissage et transitions
- `NavigationCameraController` (core/map) : cap filtré (EMA circulaire, τ ≈ 1,5 s), position du marqueur interpolée entre deux positions (animation 1 s synchronisée avec `easeCamera`), zoom lissé, inclinaison adaptée (58° route / 50° approche / 62° ville dense à < 30 km/h).
- Transition 2D ↔ 3D animée (600 ms) ; bouton 2D/3D ; option « nord en haut » ; reprise auto après geste (8 s) avec compte à rebours annulable ; recentrage immédiat.
- **Acceptation** : tests JVM du filtre de cap (passage 359° → 1° sans tour complet, arrêt sans dérive) ; sur appareil : aucun saut du marqueur à 1 Hz, bascule 2D/3D fluide.

### C2. Style de carte Roadly
- Style JSON embarqué (`core/map/src/main/assets/styles/roadly-dark.json`, `roadly-light.json`) dérivé des styles OpenFreeMap (mêmes sources, glyphes et sprites distants) : hiérarchie routière lisible (autoroute / voie rapide / secondaire / locale), tunnels en pointillé et ponts via `brunnel`, bretelles distinctes (`ramp`), parcs / eau / repères, étiquettes à densité réduite au-delà du zoom 16 en conduite, bâtiments extrudés déjà en place.
- Le réglage « Fournisseurs » garde la possibilité d'une URL de style externe (repli = style distant actuel).
- **Acceptation** : style chargé hors ligne depuis l'asset ; captures jour / nuit sur le HONOR ; aucune donnée inventée (tout vient des tuiles).

### C3. Performance
- `setMaximumFps(60)` ; `setTraversed` incrémental ; `reportsAhead` projeté depuis `RouteTracker` (pas de reprojection globale) ; `NavigationContent` découpé pour que la carte et les effets ne recomposent que sur leurs clés ; mesure `dumpsys gfxinfo` avant / après et `batterystats` sur 20 min.
- **Acceptation** : chiffres mesurés notés dans `ROADLY_3D_NAVIGATION_TESTS.md` ; jank < 5 % en guidage stationnaire ; aucune allocation GeoJSON croissante par position.

---

## Étape D — Interface premium

### D1. `ManeuverBanner` interactif + aperçu
- Tap → feuille « Aperçu de la manœuvre » : grande icône / `RoundaboutManeuverView`, instruction, rue, voies, puis les 3 manœuvres suivantes ; manœuvre suivante en petit sous le bandeau (« puis → Rue X ») quand elle est à moins de 500 m.

### D2. `RoundaboutManeuverView`
- Anneau, branche d'entrée (bas), arc parcouru, sortie recommandée à l'angle calculé (B4), flèche, numéro de sortie en grand, nom de la rue cible ; variante compacte dans le bandeau, grande dans l'aperçu.
- **Acceptation** : prévisualisations Compose pour 2e, 3e sortie, demi-tour, sans numéro ; rendu sur appareil avec `ParisRoute`.

### D3. Panneau de direction
- `ExitSignPanel` : numéro de sortie, direction(s), numéro de route (`exitBranch`), voies conseillées, distance ; distinction prochaine / suivante ; affiché à l'approche (déjà `ApproachUi`).

### D4. `LaneGuidanceView`
- `LaneStrip` enrichi : flèches actives vs valides vs interdites, voies multi-directions, apparition à 400 m / disparition après la manœuvre ; masqué sans données.

### D5. `NavigationBottomPanel`
- Panneau compact repliable (poignée) : heure d'arrivée, temps, distance, état de la voix, destination, « Trajet complet » (feuille avec la liste des manœuvres), Arrêter ; tap sur l'ETA → détails ; les marges caméra suivent la hauteur mesurée du panneau (`onGloballyPositioned`), ce qui règle aussi l'`assistantPanel` de `feat/roadly-ai`.
- Contrôles carte : recentrer, 2D/3D, son, bouton Roadly AI (emplacement existant).
- **Acceptation** : captures portrait / paysage ; cibles ≥ 56 dp ; aucune intersection cachée par le panneau replié.

---

## Étape E — Audio et intégration

### E1. `NavigationVoiceCoordinator`
- Canal unique de parole : priorités (urgence GPS > guidage > assistant), ducking des médias, pas de chevauchement, l'assistant est coupé (pas seulement pausé) par un NOW ; `stop` / `setMuted` de la session ne détruisent plus les messages de l'assistant.
- **Acceptation** : tests avec `FakeSpeechService` ; essai sur appareil avec musique en lecture.

### E2. Réglages
- Vue par défaut 2D/3D, zoom (−1 / 0 / +1), inclinaison (45 / 58 / 65), rotation auto ou nord en haut, détail cartographique (normal / réduit), bâtiments 3D, aperçu de la manœuvre suivante, économie d'énergie manuelle ; tous persistés (DataStore), groupe « Navigation » dans Réglages.

### E3. Arrière-plan et reprise
- Vérification écran verrouillé 10 min (notification vivante, annonces prononcées), rotation, retour depuis la notification, reprise après `am kill` (B6).

---

## Étape F — Validation

- `testDebugUnitTest` complet, `assembleDebug`, tests instrumentés ajoutés (Compose : bandeau, rond-point, panneau), `lint`.
- Matrice des 20 scénarios de `ROADLY_3D_NAVIGATION_TESTS.md` : automatisés verts, réels consignés avec date et résultat.
- Compte rendu final : fonctionnalités réelles, fichiers, tests, partiel, limites, vérifications restantes en trajet réel.

---

## Hors périmètre (limites documentées)
Limitations de vitesse (serveur public), trafic, montant des péages, feux / ralentisseurs, autres sorties d'un rond-point (non fournies par `/route`), relief, voies si le serveur n'en renvoie pas.
