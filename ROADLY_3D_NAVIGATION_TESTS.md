# Roadly — Plan de tests de la navigation 3D premium

Date : 2026-10-31. Référence : `ROADLY_3D_NAVIGATION_PLAN.md`. État actuel : 303 tests JVM verts, 3 instrumentés, aucune CI.

Commandes : `.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test --offline -q` ; `.\gradlew.bat :app:assembleDebug --offline -q` ; instrumentés : `.\gradlew.bat connectedDebugAndroidTest --offline` (HONOR `AREE026226000174`).

## Jeux de données
- `TestRoutes.straight` (1 464 m, virage à droite) ; `TestRoutes.outAndBack` (demi-tour) — existants.
- `ParisRoute` (fixture FOSSGIS réelle, 2 ronds-points) — existante.
- Créés (Étape B) : `TestRoutes.leftTurn`, `TestRoutes.twoRoundabouts` (120 m d'écart) ; fixture FOSSGIS réelle `valhalla_motorway.json` (Toulouse 43.6300,1.4100 → 43.5540,1.5000, enregistrée le 2026-10-31 : sorties 31b et 19 avec panneaux, échangeur A620, 4 ronds-points). LIMITE : aucune voie (`lanes`) renvoyée par le serveur public sur ce trajet.
- **À créer** : `TestRoutes.parallelRoads` (desserte à 25 m), `TestRoutes.overpass` (passage supérieur).
- Positions GPS simulées : générateur `FixScript` (vitesse, bruit gaussien, précision, trous) dans `core/navigation/testing`.

## Matrice des 20 scénarios

| # | Scénario | Automatisé (JVM / instrumenté) | Réel (trajet) | Étape |
|---|---|---|---|---|
| 1 | Trajet rectiligne | JVM : distances décroissantes, aucune annonce répétée | oui, 1 km | B2, B5 |
| 2 | Virage à droite | JVM `straight` : paliers, index stable au virage sous jitter | oui | B1 |
| 3 | Virage à gauche | JVM `leftTurn` : icône gauche, `turnAngleDeg` < 0 | oui | B3 |
| 4 | Rond-point 2 sorties | JVM `ParisRoute` : `RoundaboutInfo`, cible stable, sortie annoncée au bon moment ; Compose preview | oui | B4, D2 |
| 5 | Rond-point ≥ 3 sorties | JVM `ParisRoute` (5e sortie) ; preview | oui | B4, D2 |
| 6 | Deux ronds-points rapprochés | JVM `twoRoundabouts` : deux cibles, aucune sautée, pas de FAR doublé | oui | B1, B5 |
| 7 | Sortie d'autoroute | JVM fixture autoroute : `exitNumber`, `exitToward`, approche 400 m, panneau | oui | B3, D3 |
| 8 | Échangeur plusieurs bretelles | JVM fixture : côté de la bretelle, manœuvre suivante affichée sans masquer l'actuelle | oui | B3, D3 |
| 9 | Deux routes parallèles | JVM `parallelRoads` : 8 s à 25 m → pas de recalcul | oui | B6 |
| 10 | GPS imprécis | JVM : précision 60 m → pas de hors-itinéraire, pas de NOW | simulé | B6 |
| 11 | Perte temporaire de GPS | JVM existant (`NavigationSessionTest`) + extrapolation puis reprise | tunnel réel | — |
| 12 | Recalcul après erreur | JVM existant + nouvelle route → index et annonces réinitialisés | oui | B6 |
| 13 | Arrivée | JVM existant | oui | — |
| 14 | Reprise après arrière-plan / mort du processus | JVM : `lastOption` persisté ; appareil : `am kill` → reprise proposée | appareil | B6, E3 |
| 15 | Changement 2D/3D | JVM `GuidanceCameraPolicy` ; appareil : animation, retour | appareil | C1 |
| 16 | Désactivation des annonces | JVM existant (sourdine, voix off) | appareil | — |
| 17 | Interruption audio par Roadly AI | JVM `FakeSpeechService` : NOW coupe l'assistant, reprise après | appareil, musique | E1 |
| 18 | Affichage sans données de voies | JVM : `lanes` vide → composant absent ; instrumenté Compose | appareil | D4 |
| 19 | Mode paysage | instrumenté Compose (overlay paysage) ; appareil captures | appareil | D5 |
| 20 | Absence d'Internet | JVM : échec de recalcul → ancien tracé conservé, message ; appareil : mode avion en guidage | appareil | B6 |

## Vérifications supplémentaires
- Jitter ±8 m sur 10 positions au point de manœuvre → `nextManeuverIndex` inchangé (B1).
- Distances voix / écran cohérentes (B2).
- Aucune annonce dupliquée sur un trajet complet simulé (B5).
- Performance (C3) : `adb shell dumpsys gfxinfo <package> reset` puis 2 min de guidage stationnaire → % de frames en jank ; `dumpsys batterystats` sur 20 min écran allumé. Valeurs mesurées à consigner ici (avant / après).

## Journal des essais réels
| Date | Scénarios | Appareil | Résultat | Notes |
|---|---|---|---|---|
| 2026-10-31 | 1 (stationnaire), 2 (approche), 15 (3D off), 19 | HONOR Magic 8 Pro | OK (phase 1) | sans sortie ni voies sur le trajet d'essai ; économie d'énergie et recentrage à refaire |
| 2026-10-31 | B : 1–14, 20 automatisés (JVM) | — | verts | essais réels à faire : `adb shell am kill fr.roadly` en guidage → `lastGuidance` présent (bouton « Reprendre » : E3), 130 km/h sur autoroute, double rond-point |
