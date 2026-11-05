# Roadly — Vue 3D des carrefours

Date : 2026-11-05. Guidage visuel 3D des sorties d'autoroute, bifurcations, échangeurs, insertions et ronds-points, intégré au guidage réel (pas de démonstration isolée).

## 1. Analyse de l'existant

- **Carte** : MapLibre Android 11.13.5, styles OpenFreeMap (`openmaptiles`) réécrits en `roadly-dark.json` / `roadly-light.json`. Caméra inclinée 58° (60° au plus : limite MapLibre `MAXIMUM_PITCH`), bâtiments extrudés, flèche de manœuvre (`ManeuverArrowBuilder`).
- **Guidage** : `NavigationSession` (pur Kotlin, testé JVM), `RouteTracker` (progression monotone), `RoundaboutResolver`, panneau `ExitSignPanel`, voies `LaneGuidanceView`, schéma 2D de rond-point.
- **Données** : itinéraire Valhalla (géométrie, manœuvres, `sign`, `lanes`, `roundabout_exit_count`) ; un profil `trace_attributes` par tracé pour les limitations.
- **Manque** : aucune vue en perspective du carrefour lui-même (voies, bretelles, séparateurs, ponts, numéros de sortie).

## 2. Choix du moteur de rendu

| Option | Verdict |
|---|---|
| MapLibre seul | Rejeté : 60° maximum, pas de calque 3D personnalisé sans code natif C++ (`CustomLayer`), pas de marquage voie par voie. Gardé pour la carte, rendu plus large aux échangeurs. |
| SceneView / Filament | Rejeté : +4 à 6 Mo, orienté glTF (pas de maillage procédural simple), dépendance à télécharger alors que le projet se construit `--offline`. |
| Vulkan | Rejeté : coût de développement sans gain visible pour quelques dizaines de milliers de triangles. |
| **OpenGL ES 3.0 maison** | **Retenu** : aucune dépendance, maillage procédural construit depuis les données réelles, 2 appels de dessin pour toute la géométrie statique, MSAA 4×, rendu plafonné (60 i/s, 30 en économie, 20 à l'arrêt). |

La vue est une `TextureView` (et non une `GLSurfaceView`) : elle se compose avec Compose (coins arrondis, fondu) au-dessus de la `MapView` sans conflit d'ordre de surfaces. Thread de rendu dédié cadencé par le `Choreographer`, contexte EGL détruit dès que la vue disparaît. Sans OpenGL ES 3, la vue est retirée pour la session et le guidage 2D reste complet.

## 3. Architecture

```
NavigationSession ──► JunctionAnticipation (core:navigation, pur)
   │                     ├─ JunctionClassifier  : sortie / bifurcation / échangeur / insertion / rond-point / carrefour complexe
   │                     ├─ RoundaboutExitCounter : rang de sortie compté sur la topologie réelle
   │                     └─ JunctionTiming      : distances selon vitesse, type de route, complexité
   ├─► ValhallaJunctionTopologyProvider : trace_attributes sur ~1,3 km autour du carrefour (pendant PREPARE)
   └─► NavigationState.Guiding.junction : JunctionGuidance (core:model)
            │
NavigationScreen ─► rememberJunctionScene (core:map)
                      ├─ MapController.junctionContext → JunctionTileQuery (routes + bâtiments des tuiles chargées)
                      ├─ JunctionSceneBuilder (pur) → JunctionScene
                      └─ JunctionMeshBuilder (pur)  → JunctionMeshes
                 ─► JunctionPanel → JunctionView → JunctionGlView (EGL) → JunctionRenderer (GLES 3)
                                                     └─ JunctionCamera + VehicleInterpolator (purs)
```

### Sources de données (rien n'est inventé)

| Élément | Source |
|---|---|
| Trajectoire, chaussée sous la trajectoire | Géométrie de l'itinéraire calculé |
| Nombre de voies, classe, bretelle, pont, tunnel, rond-point | `trace_attributes` (`edge.lane_count`, `road_class`, `use`, `bridge`, `tunnel`, `roundabout`) |
| Autres branches d'un nœud (cap, sens) | `node.intersecting_edge.*` (`begin_heading`, `driveability`) ; branches non carrossables écartées |
| Autres routes, ponts (`brunnel`, `layer`), bâtiments (`render_height`) | Tuiles vectorielles **déjà chargées** par MapLibre (cartes hors ligne comprises) |
| Panneau, numéro de sortie, direction, route | `sign` Valhalla de la manœuvre ; aucun panneau sans donnée |
| Voies conseillées, flèches au sol | `lanes` Valhalla ; sans données, pas de marquage de voie ni de décalage de trajectoire |
| Numéros de sortie du rond-point | Comptage topologique ; affichés seulement s'il donne le même rang que `roundabout_exit_count`, sinon seule la sortie cible est numérotée |

Vérifié le 2026-11-05 sur l'instance publique : `edge_walk` renvoie la forme envoyée (indices de forme = indices de `Route.geometry`), et le comptage topologique égale Valhalla sur 32 ronds-points réels (Toulouse, Muret, Colomiers, Blagnac, Balma, Labège, place de la Nation 6e sortie, place Charles-de-Gaulle 6e sortie).

## 4. Anticipation

Distances de base (cahier des charges) : 800 m préparation, 500 m affichage, 250 m guidage renforcé, 100 m précision, 20 m manœuvre. Chaque étape a aussi une durée minimale (30, 20, 11, 5, 1 s) : à grande vitesse c'est elle qui fixe la distance (affichage vers 720 m à 130 km/h). Facteurs : ×0,7 rond-point, ×0,6 carrefour complexe urbain, +12 % par point de complexité (branches, niveaux, sorties rapprochées, ≥ 4 voies), plafond 1,6 km. Tout est réglable par `JunctionTiming`.

- Les étapes n'avancent que dans l'ordre ; retour en arrière seulement si la distance remonte de plus de 80 m (saut GPS, demi-tour).
- Précision > 25 m ou position extrapolée : vue « dégradée » (chevrons atténués, mention « GPS imprécis »), étape PRECISE jamais atteinte.
- GPS perdu (> 10 s), recalcul, écart au tracé > 45 m : vue retirée ; après un recalcul, tout repart du nouveau tracé.
- Après la manœuvre : RECOVER (fondu sur max(150 m, 4 s)) ; une manœuvre retenue déjà visible prend le relais sans retour à la carte (sorties successives, ronds-points enchaînés).
- Tournants simples en ville : jamais de vue (changements trop fréquents). Carrefour complexe = 3 voies ou plus.

## 5. Caméras

- **Vue 3D** (`JunctionCamera`) : derrière le véhicule sur la trajectoire, visant devant lui. Vue d'ensemble haute à l'apparition (descente progressive), caméra de poursuite à la manœuvre, remontée douce après. Plus haute pour un rond-point (visée glissant vers le centre de l'anneau) et pour un échangeur ou une complexité élevée ; recul et visée allongés avec la vitesse. Œil et cible sur ressorts amortis critiques : aucune rotation brusque, aucun saut au changement d'étape (testé : < 2 m d'une image à l'autre).
- **Carte** : pendant la préparation et l'affichage d'un échangeur, d'une bifurcation ou d'un carrefour complexe, zoom de croisière − 0,6 (contexte large) au lieu du zoom serré d'approche ; aller et retour par paliers de 0,5.
- **Véhicule** : positions à ~1 Hz extrapolées à la vitesse courante (1,5 s au plus), rattrapage doux, jamais de recul visible.

## 6. Rendu

Ciel en dégradé, sol quadrillé, brouillard de distance, chaussées par classe, rives continues, séparations de voies en pointillés (si le nombre de voies est connu), glissières et parapets, tabliers de ponts avec flancs et piles, tunnels assombris, musoir hachuré entre bretelle et chaussée qui continue, anneau et îlot central, bâtiments extrudés (450 au plus dans 320 m), portique avec panneau façon signalisation française (bleu autoroute, vert ailleurs, cartouche de route), trajectoire violette à chevrons animés avec halo et pointe, flèches au sol des voies, numéros de sortie toujours lisibles. Palette jour / nuit dérivée des jetons Roadly (teinte du cockpit pour la trajectoire). Économie d'énergie : sans bâtiments ni piles, rayon réduit, 30 i/s.

## 7. Intégration

- Portrait : panneau de 34 % de la hauteur sous le bandeau, à la place du panneau de direction et des voies 2D (dessinés dans la scène) ; la marge haute de la caméra de la carte suit la pile mesurée.
- Paysage : panneau en haut, entre la colonne gauche et la colonne d'actions.
- Réglage : Réglages › Navigation › « Vue 3D des carrefours » (activé par défaut, `map.junction3d`). Désactivé, aucune requête de topologie n'est faite.
- Hors ligne : sans réseau, pas de topologie ; avec une carte téléchargée, routes et bâtiments viennent des tuiles. Sans les deux, la scène se réduit à la trajectoire réelle et au numéro de sortie Valhalla.
- Android Auto : non branché (l'écran voiture garde son guidage actuel).

## 8. Tests

| Scénario | Test | Données |
|---|---|---|
| Sortie autoroutière simple | `JunctionPipelineTest` sortie 31b | Toulouse, réel |
| Sortie avec bifurcation | `JunctionPipelineTest` sortie 19 puis bifurcation de Labège ; `JunctionAnticipationTest` | Toulouse, réel |
| Rond-point 3 sorties | Muret 3e sortie (`JunctionTopologyTest`, `JunctionPipelineTest`) | réel |
| Rond-point 5 sorties et plus / complexe | Nation et Étoile, 6e sortie | Paris, réel |
| Échangeur à plusieurs niveaux | A620 (ponts, piles, classement INTERCHANGE) | Toulouse, réel |
| Sorties rapprochées | ronds-points enchaînés (146 m), sortie 19 → bifurcation (605 m) | réel |
| GPS imprécis / extrapolé | jamais PRECISE, vue dégradée | réel |
| Perte de signal GPS | vue retirée | réel |
| Recalcul | vue retirée puis nouveau tracé, nouvelle clé de scène | réel |
| Changement de direction | recul > 80 m → étape abaissée | réel |
| Hors ligne | sans topologie ni tuiles : scène du seul tracé, seule la sortie cible numérotée | réel |
| Divergence de comptage | aucun numéro intermédiaire | réel modifié |
| Géométrie, maillage, caméra | `JunctionSceneTest` (cercle, triangulation, voies, ponts, musoir, NaN, sauts de caméra) | synthétique |

Commandes :

```
.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test :core:radar:test --offline -q
.\gradlew.bat :app:assembleDebug --offline -q
```

## 9. Limites connues

- **Pas encore vu sur appareil** : aucun téléphone n'était branché. À vérifier sur le HONOR Magic 8 Pro : rendu GL (MSAA, textures de panneaux), fluidité (journal `RoadlyJunction`), lisibilité de jour, consommation.
- FOSSGIS public ne renvoie pas de `lanes` sur les trajets enregistrés : voies conseillées et flèches au sol jamais vues avec des données réelles.
- Les rampes de pont des tuiles sont approchées (tablier à 6,5 m × `layer`, rampes de 40 à 70 m) : l'altitude réelle n'est pas dans les tuiles.
- Les bretelles partent de l'axe de la chaussée (topologie OSM) et non du bord de la voie de droite.
- Sorties d'un rond-point au-delà de la sortie prise : dessinées depuis les tuiles si elles sont chargées, jamais numérotées.
- Circulation à droite seulement pour l'ordre des sorties à un même nœud.
