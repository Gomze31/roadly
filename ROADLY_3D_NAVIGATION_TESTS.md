# Roadly — Plan de tests de la navigation 3D premium

Date : 2026-10-31. Référence : `ROADLY_3D_NAVIGATION_PLAN.md`. État actuel (fin d'étape C, `5906c87`) : 431 tests JVM verts, 3 instrumentés, aucune CI.

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
| 10 | GPS imprécis | JVM : fix à 120 m du tracé avec précision 60 m → pas de hors-itinéraire (témoin précis : hors itinéraire) ; jitter de 80 à 100 m avant le virage → index inchangé ; précision > 30 m → pas de NOW. LIMITE : sur fixes imprécis, le NOW n'est pas dit (le NEAR l'a précédé) — un NOW faux et précoce serait pire qu'un NOW manquant | simulé | B6 |
| 11 | Perte temporaire de GPS | JVM existant (`NavigationSessionTest`) + extrapolation puis reprise | tunnel réel | — |
| 12 | Recalcul après erreur | JVM existant + nouvelle route → index et annonces réinitialisés | oui | B6 |
| 13 | Arrivée | JVM existant | oui | — |
| 14 | Reprise après arrière-plan / mort du processus | JVM : `lastOption` persisté ; appareil : `am kill` → reprise proposée | appareil | B6, E3 |
| 15 | Changement 2D/3D | JVM `NavigationCameraControllerTest` (modes, transition 600 ms, cap 359° → 1° sans tour, arrêt sans dérive), `GuidanceCameraPolicyTest`, `NavigationViewModelTest` (mode de session, compte à rebours) ; appareil : animation, retour | appareil | C1 |
| 16 | Désactivation des annonces | JVM existant (sourdine, voix off) | appareil | — |
| 17 | Interruption audio par Roadly AI | JVM `FakeSpeechService` : NOW coupe l'assistant, reprise après | appareil, musique | E1 |
| 18 | Affichage sans données de voies | JVM : `lanes` vide → composant absent ; instrumenté Compose | appareil | D4 |
| 19 | Mode paysage | instrumenté Compose (overlay paysage, D5) ; appareil : captures, boutons caméra en bas à droite (C) | appareil | C1, D5 |
| 20 | Absence d'Internet | JVM : échec de recalcul → ancien tracé conservé, message ; appareil : mode avion en guidage | appareil | B6 |

## Vérifications supplémentaires
- Jitter ±8 m sur 10 positions au point de manœuvre → `nextManeuverIndex` inchangé (B1).
- Distances voix / écran cohérentes (B2).
- Aucune annonce dupliquée sur un trajet complet simulé (B5).
- Performance (C3) : voir la section « Performance (C3) — mesures » ; JVM : `TraversedLineTest` (GeoJSON par position borné), `ReportsAheadProjectorTest` (une projection par signalement et par tracé).

## Performance (C3) — mesures

Conditions : HONOR `AREE026226000174`, guidage stationnaire vers 43.6045,1.4440 (Capitole, 4,6 km, première manœuvre à 200 m), thème Auto (nuit → sombre), Carte 3D, luminosité automatique laissée telle quelle (non réglée à la main), économiseur coupé, téléphone sur câble USB, 2026-10-31 (23:06–23:26).

| Mesure (commande) | Avant (`c4d05ef`) | Après (`5906c87`) |
|---|---|---|
| Images rendues, 120 s (`gfxinfo` « Total frames rendered ») | non mesuré | 147 |
| Images en jank (`gfxinfo` « Janky frames ») | non mesuré | 12 (8,16 %) ; « legacy » 0 (0,00 %) |
| 50e / 90e / 95e / 99e centile (ms) | non mesuré | 11 / 16 / 17 / 20 |
| Rendu de la carte (`logcat -s RoadlyFps`, moyenne 120 s) | non mesurable (pas de journal) | 55,8 i/s (24 relevés de 5 s, 55,5 à 56,4) |
| Rendu de la carte en économie d'énergie (`RoadlyFps`, 45 s) | non mesurable (pas de journal) | 28,6 i/s (9 relevés, 28,5 à 29,1) |
| Batterie, 20 min, application (`batterystats` « UID u0a535 ») | non mesuré | 29,4 mAh (écran seul) |
| Batterie, 20 min, total (« Computed drain ») | non mesuré | 0 mAh |

Acceptation : jank stationnaire < 5 % → ÉCHEC (8,16 %). LIMITE : `gfxinfo` ne mesure que les overlays Compose ; le rendu GL de la carte (SurfaceView) est suivi par `RoadlyFps` (builds débogables).

Notes de mesure :
- « Avant » non mesuré : il demandait un second cycle de build et d'installation (`c4d05ef`) et 20 min de batterie de plus ; écarté pour cette passe.
- Le jank vient de 12 « Frame deadline missed », dont 8 « Slow UI thread » et 3 « High input latency » ; aucun « Missed Vsync ». Écran en mode 120 Hz, rendu à 60 Hz.
- Une seconde fenêtre de 120 s, prise aussitôt après (23:09–23:11) pour le relevé `RoadlyFps`, donne 146 images, 2 en jank (1,37 %), centiles 10 / 14 / 15 / 17 ms. Elle est notée pour information et ne remplace pas la mesure officielle.
- `RoadlyFps` : le tampon logcat du HONOR ne gardait que les 15 dernières secondes de la première fenêtre (55,9 / 55,9 / 55,8 i/s). La moyenne du tableau vient de la seconde fenêtre, lue en continu (`logcat -T 1 -s RoadlyFps:D`).
- Batterie : téléphone en charge, débranchement simulé (`dumpsys battery unplug`), d'où un « Computed drain » et un « actual drain » à 0. Le modèle d'énergie du HONOR n'attribue à l'application que l'écran (29,4 mAh). Le GNSS (23,6 mAh) n'est attribué à aucune application. Temps CPU de l'application sur 20 min 12 s : 15 min 44 s utilisateur et 2 min 14 s système, soit environ 0,9 cœur en moyenne.

## Journal des essais réels
| Date | Scénarios | Appareil | Résultat | Notes |
|---|---|---|---|---|
| 2026-10-31 | 1 (stationnaire), 2 (approche), 15 (3D off), 19 | HONOR Magic 8 Pro | OK (phase 1) | sans sortie ni voies sur le trajet d'essai ; économie d'énergie et recentrage à refaire |
| 2026-10-31 | B : 1–14, 20 automatisés (JVM) | — | verts | essais réels à faire : `adb shell am kill fr.roadly` en guidage → `lastGuidance` présent (bouton « Reprendre » : E3), 130 km/h sur autoroute, double rond-point |
| 2026-10-31 | C : 15, 19, style jour / nuit, détail, recentrage, hors ligne ; B : 14 (`kill -9`) | HONOR Magic 8 Pro | OK, sauf jank C3 : ÉCHEC (8,16 %) | captures `docs/captures/etape-c/` ; 2D/3D et nord en haut ×5 sans disparition du marqueur (vidéo image par image), transition d'environ 0,4 à 0,6 s ; « Recentrage dans 8 s » → recentrage seul, « Annuler » tient au-delà de 12 s, « Recentrer » immédiat ; rotation sans remise à zéro de la caméra ; économie : 28,6 i/s, sans bâtiments, inclinaison réduite ; mode avion : style embarqué et tuiles en cache, aucune erreur de style ; `kill -9` : clés `guidance.last.*` présentes, service non relancé, accueil sans reprise (E3) ; après arrêt : clés effacées. Écart : l'icône d'attribution est sous la barre de navigation à 3 boutons en portrait (inaccessible), accessible en paysage. À faire en roulant : voix avec musique (FAR coupé par NOW, NEAR dit), autoroute 130 km/h, rond-point, tunnel |
| 2026-10-31 | C3 : suivi du jank stationnaire | HONOR Magic 8 Pro | à reprendre | 8,16 % mesuré (147 images, 12 « Frame deadline missed », 8 « Slow UI thread ») ; fenêtre suivante à 1,37 % : à remesurer sur plusieurs fenêtres et à comparer avec `c4d05ef` avant toute conclusion |
