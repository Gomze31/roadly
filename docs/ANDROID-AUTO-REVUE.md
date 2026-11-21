# Android Auto — revue technique avant terrain

Revue du 22 novembre 2026, faite **par lecture du code** de `feature/car` sur la branche `integration/phase5` (origin/main
`57af06b` fusionné avec le travail local). Aucun fichier de `feature/car` n'a été modifié pour cette revue, rien n'a été exécuté sur
le DHU ni en voiture. Les numéros de ligne sont ceux de cette branche.

Chemins abrégés : `car/` = `feature/car/src/main/java/fr/roadly/feature/car/`.

## Ce qui est sain

- Une seule source : l'écran voiture n'a ni suivi ni session propres, il lit `deps.navigation.state` comme le téléphone.
- Changement de tracé : la clé d'affichage (`displayKey`) contient l'étape, aucune manœuvre périmée ne survit.
- `invalidate()` n'est appelé que si la clé change ; l'angle du véhicule dans un rond-point est quantifié.
- `navigationStarted` / `navigationEnded` équilibrés, collecteurs dans `lifecycleScope`.
- Depuis la phase 5, l'état publié reste `rerouting = true` entre deux essais de recalcul échoués : la voiture montre donc le
  recalcul pendant toute sa durée, sans modification de `feature/car`.

## Défauts, par ordre de gravité

### 1. Exception possible sur une manœuvre « sortie de rond-point » portant un numéro de sortie

- **Où** : `car/CarGuidanceScreen.kt:204` (`s.roundaboutExit?.let { setRoundaboutExitNumber(it) }`) et `car/CarModels.kt:266-268`.
- **Cause** : `Maneuver.Builder.setRoundaboutExitNumber` n'est accepté que pour les types `TYPE_ROUNDABOUT_ENTER_AND_EXIT_*` ;
  il lève `IllegalArgumentException` pour `TYPE_ROUNDABOUT_EXIT_CCW`. Le code pose le numéro dès qu'il existe, quel que soit le type.
  `ValhallaRoutingProvider` recopie `roundabout_exit_count` pour toute manœuvre qui le porte.
- **Déclencheur** : une manœuvre `ROUNDABOUT_EXIT` avec `roundaboutExit` non nul. Valhalla ne le met en principe que sur l'entrée :
  déclencheur plausible, non observé.
- **Effet** : plantage de l'écran de guidage de la voiture au moment de construire le gabarit.
- **Correction isolée** : ne poser le numéro que si le type calculé est `TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW`.
- **Test** : `CarGuidanceMapper` + construction de `Maneuver` avec un `ROUNDABOUT_EXIT` portant un numéro (Robolectric, comme `CarHomeStripTest`).

### 2. Carte et vitesse figées après l'arrivée ou un échec

- **Où** : `car/CarGuidanceScreen.kt:85-96`.
- **Cause** : `update()` n'alimente le rendu (`drawMap`, HUD) que dans la branche `Guiding`. En `Arrived` ou `Failed`, le tracé, le
  marqueur et la dernière vitesse restent tels quels jusqu'à « Terminer ».
- **Effet** : la voiture roule encore (recherche d'une place), l'écran montre une vitesse et une position périmées.
- **Correction isolée** : dans la branche non-`Guiding`, vider le HUD (`CarHudModel.EMPTY`), retirer le tracé, et suivre
  `deps.location.fixes` comme l'écran d'accueil.
- **Test** : état `Arrived` → le modèle de HUD rendu ne porte plus de vitesse.

### 3. Aucune indication de GPS perdu ou de position incertaine

- **Où** : `car/CarModels.kt:49` (`gpsLost` ne fait qu'effacer la vitesse), `car/CarHudView.kt` (ne lit pas `gpsLost`), `car/CarModels.kt:182`.
  L'écran de conduite libre, lui, l'affiche (`car/CarFreeDriveScreen.kt:88`).
- **Cause** : le drapeau est transporté jusqu'au modèle mais jamais dessiné en guidage ; `situation` (`GPS_UNCERTAIN`) n'est pas lue.
- **Effet** : sous un tunnel, la distance continue de décompter (extrapolation, 150 m au plus) comme si elle était mesurée. Depuis la
  phase 5, « Signal GPS perdu. » est dit une fois, ce qui couvre l'essentiel à la voix.
- **Correction isolée** : dessiner `car_gps_lost` dans `CarHudView` quand `model.gpsLost`.

### 4. Décompte de rond-point affiché même quand il n'est pas sûr

- **Où** : `car/CarModels.kt:192-204` (`roundaboutCue`) et `:231-243` (`roundabout`).
- **Cause** : ni l'un ni l'autre ne lit `RoundaboutLive.confidence`. Le téléphone affiche « Position imprécise » et retient le
  décompte quand la confiance est `LOW`.
- **Effet** : décompte figé et marqueur de véhicule présentés comme certains.
- **Correction isolée** : avec `confidence == LOW` dans l'anneau, rendre `null` depuis `roundaboutCue` sauf si la prochaine sortie est
  la bonne, et ne pas transmettre `vehicleAngleDeg`.
- **Test** : `CarRoundaboutModeTest`, cas `LOW`.

### 5. `updateTrip` envoyé à chaque émission d'état

- **Où** : `car/CarGuidanceScreen.kt:95` et `:233-250`.
- **Cause** : un `Trip` complet est reconstruit et envoyé à chaque émission (1 Hz au moins, plus les changements de sourdine), alors
  qu'`invalidate()` est filtré par `displayKey`.
- **Effet** : charge inutile côté hôte ; à mesurer sur le DHU (l'hôte peut aussi limiter de lui-même).
- **Correction isolée** : n'envoyer le `Trip` que si la clé (étape, distance arrondie, heure d'arrivée à la minute) a changé.

### 6. Voie sans direction dessinée « tout droit »

- **Où** : `car/CarVisuals.kt:39` et `:102` (`lane.directions.ifEmpty { setOf(LaneDirection.STRAIGHT) }`).
- **Cause** : repli qui invente une donnée absente d'OpenStreetMap.
- **Effet** : flèche « tout droit » affichée sur une voie dont on ne sait rien.
- **Correction isolée** : si une voie n'a aucune direction, ne pas envoyer le bloc de voies (ni `addLane`, ni image).

## Plan de correction proposé au chantier Android Auto

| Ordre | Défaut | Fichiers | Taille |
|---|---|---|---|
| 1 | 1 (exception) | `CarGuidanceScreen.kt` | une condition + un test |
| 2 | 6 (voie inventée) | `CarVisuals.kt` | deux conditions + un test |
| 3 | 4 (décompte incertain) | `CarModels.kt` | deux fonctions + un test |
| 4 | 2 (écran figé) | `CarGuidanceScreen.kt` | une branche |
| 5 | 3 (GPS perdu) | `CarHudView.kt` | un dessin |
| 6 | 5 (`updateTrip`) | `CarGuidanceScreen.kt` | une clé, après mesure sur le DHU |

Chaque correction tient dans `feature/car` et ne demande aucun changement du moteur de guidage.

## Points nés de la fusion, à relire par le chantier Android Auto

- `car/CarHudView.kt` : la bulle de vitesse d'origin/main a été gardée ; la « prochaine limitation » et le nom de la voie du travail
  local y ont été ajoutés. La position du petit panneau (`drawNextLimit`) à droite de la bulle n'a jamais été vue à l'écran.
- `car/CarHomeScreen.kt` : le bandeau d'origin/main (recherche + photos aériennes) a été gardé ; le bouton « domicile » du travail
  local a été retiré du bandeau (`goHome` n'est plus appelé) ; les lignes Domicile et Travail restent en tête de liste.
- `car/CarMapRenderer.kt` : détail selon la vitesse (local) conservé à la place de `MapDetail.REDUCED` (origin/main), `setAerial` ajouté.
- `CarHomeStripTest` (origin/main) échoue sur ce PC : Robolectric ne charge pas sa bibliothèque native quand le dossier utilisateur
  contient une espace (`C:\Users\Forza%20PC\.m2\…`). Défaut d'environnement, pas du code.

## À vérifier sur le DHU ou en voiture

- Limitation par l'hôte des images de carrefour dans un rond-point (jusqu'à 18 images).
- Rendu du `Trip` sur le combiné d'instruments.
- Troncature de « Encore N sorties à passer · rue ».
- Départ du guidage depuis la voiture, téléphone verrouillé (démarrage du service de premier plan).
