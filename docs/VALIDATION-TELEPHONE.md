# Validation sur téléphone — simulation, rejeu, journal

> Séance terrain (scénarios, réglages du téléphone, fiche de défaut) : voir `PROTOCOLE-TERRAIN-HONOR.md`, qui remplace les
> sections 6, 9 et 10 ci-dessous. Ce document reste la référence des outils (simulation, rejeu GPX, journal).

Passe du 21 novembre 2026. Objectif : vérifier le guidage sur le HONOR sans refaire un trajet réel à chaque modification, puis
faire la passe sur route avec une liste précise.

**État** : le HONOR (`AREE026226000174`) était branché mais verrouillé par code pendant cette passe. Rien n'a été installé dessus
ni regardé à l'écran. Les outils ci-dessous sont testés sur JVM ; leur première utilisation sur l'appareil reste à faire.

## 1. Simulation de trajet

Réglages › Navigation › **Simulation de trajet (test)**, et **Vitesse de la simulation** (×1, ×2, ×4).

Réglage activé, au départ d'un guidage le véhicule suit l'itinéraire tout seul : les positions viennent du simulateur, pas du GPS.
Tout le reste est le vrai code : suivi, voix, écran, notification, Android Auto. Le GPS réel reprend la main à l'arrêt ou à l'arrivée.

- Vitesse : celle que Valhalla prévoit pour chaque manœuvre (longueur / durée), accélération bornée à 2 m/s², freinage à 2,5 m/s².
  Le véhicule ralentit donc avant un rond-point ou une bretelle : les seuils d'annonce liés à la vitesse sont exercés comme sur route.
- Code : `core/navigation/DriveSimulation.kt` (`RouteSimulation`, `ReplayDriveSimulation`), `core/location/LocationReplay.kt`.
- **À couper avant de prendre la route** : réglage actif, le guidage ignore le GPS.

## 2. Rejeu d'une trace GPX

Même réglage. Si le fichier `replay.gpx` existe dans le dossier de l'application, il est rejoué à la place du trajet simulé :

```
adb push ma-trace.gpx /sdcard/Android/data/fr.roadly/files/replay.gpx
adb shell rm /sdcard/Android/data/fr.roadly/files/replay.gpx     # revenir au trajet simulé
```

- Points `trkpt` (sinon `rtept`), horodatages `<time>` respectés ; sans horodatage, un point par seconde.
- Vitesse : balise `<speed>` si présente, sinon distance / durée. Précision : `<hdop>` × 5 m, sinon 5 m.
- Un trou de la trace est rejoué au plus 30 s.
- L'itinéraire doit être calculé vers la destination de la trace ; si la trace s'en écarte, le recalcul se déclenche comme sur route.
  C'est le moyen de reproduire une erreur de navigation, une route parallèle ou un GPS imprécis à l'identique.
- Une trace réelle s'obtient avec n'importe quelle application d'enregistrement GPX pendant un vrai trajet.

## 3. Journal du guidage

```
adb logcat -c
adb logcat -v time -s RoadlyGuidage RoadlySpeech RoadlyJunction
```

`RoadlyGuidage` écrit une ligne à chaque changement utile (pas à chaque position) :

```
manœuvre=14 ROUNDABOUT_ENTER dans=180m mode=ROUNDABOUT_APPROACH vitesse=42km/h précision=5m rond-point=APPROACH reste=2 confiance=HIGH
manœuvre=15 ROUNDABOUT_EXIT dans=38m mode=ROUNDABOUT vitesse=24km/h précision=5m rond-point=INSIDE reste=0 confiance=HIGH
```

En comparant l'heure d'une ligne à celle de l'annonce (`RoadlySpeech`, « Dit : … »), on mesure la latence de la voix.

## 4. Installation et captures

```
.\gradlew.bat :app:assembleDebug --offline -q
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell screenrecord --time-limit 120 /sdcard/roadly.mp4 ; adb pull /sdcard/roadly.mp4
adb exec-out screencap -p > capture.png
adb logcat -d | findstr FATAL
```

Le build local porte le même numéro de version que la release installée tant qu'aucun commit n'est ajouté ; `install -r` garde les données.

## 5. Batterie et charge

```
adb shell dumpsys batterystats --reset
# … 20 minutes de guidage simulé, écran allumé …
adb shell dumpsys batterystats fr.roadly | findstr /C:"Computed drain" /C:"Estimated power"
adb shell dumpsys gfxinfo fr.roadly | findstr /C:"Janky frames" /C:"90th percentile"
adb shell top -n 1 -b | findstr fr.roadly
```

En simulation le récepteur GPS ne travaille pas : la consommation mesurée est celle du rendu, de la voix et du réseau, pas celle du
GPS. La mesure complète se fait sur route.

## 6. Scénarios à passer

Chaque ligne : itinéraire à lancer en simulation, ce qu'il faut voir, entendre et lire dans le journal.

| # | Scénario | À vérifier |
|---|---|---|
| 1 | Rond-point à 3 sorties ou plus | schéma sous le bandeau dès ~200 m ; « N sorties à passer » qui descend ; « Prochaine sortie » en orange ; « Quittez le rond-point » dit juste après la sortie précédente ; « Sortie prise » en vert |
| 2 | Petit rond-point, 1re sortie | pas de décompte inutile ; la voix n'est pas en retard |
| 3 | Deux ronds-points enchaînés | le second schéma repart de zéro ; « puis » annonce le second |
| 4 | Sortie d'autoroute | « Dans 2 kilomètres… », puis « Serrez à droite » entre 1 km et 450 m, puis la manœuvre ; barre du bandeau qui se remplit ; pastille « Serrez à droite » ; Horizon et limitation à venir masqués |
| 5 | Sortie avec voies cartographiées (A61 à Toulouse, A6/A10) | voies dessinées sous le bandeau ; « Restez sur la voie de… » à la place de « Serrez… » |
| 6 | Bifurcation puis sortie à moins de 700 m | vue 3D enchaînée, pas de retour à la carte entre les deux |
| 7 | Long tronçon après une insertion | « Continuez pendant N kilomètres », une seule fois, 3 s après |
| 8 | Arrivée | mode arrivée à 300 m ; annonce ; panneau d'arrivée ; GPS réel de retour |
| 9 | Trace GPX qui quitte l'itinéraire | « Recalcul de l'itinéraire » une fois ; plus aucune annonce de l'ancien tracé |
| 10 | Trace GPX bruitée dans un rond-point | décompte qui ne remonte jamais ; « Position imprécise » si la précision se dégrade |
| 11 | Écran éteint, application en arrière-plan | la voix continue ; la notification suit la manœuvre |
| 12 | Musique en cours, Bluetooth | début d'annonce non coupé ; musique baissée puis rétablie |
| 13 | Paysage | bandeau, schéma du rond-point (92 dp) et bloc du bas tiennent sans se chevaucher |
| 14 | Android Auto (DHU) | texte « Encore 1 sortie à passer · Rue X » sur la carte de guidage |
| 15 | Trace GPX : sortie d'autoroute manquée (on reste sur la chaussée) | « Recalcul de l'itinéraire » en moins de 15 s après la sortie, une seule fois ; nouvelle consigne claire ; dans le journal, `RECALCUL` alors que l'écart au tracé est encore inférieur à 100 m |
| 16 | Trace GPX : mauvaise branche d'une bifurcation, sortie de rond-point ratée | même attente ; jamais de recalcul sur une seule position aberrante |
| 17 | Sortie ou bifurcation, vue 3D coupée (Réglages › Navigation) | schéma de bifurcation à gauche du panneau : branche à prendre vive et fléchée, branches à éviter neutres, du bon côté |
| 18 | Rond-point sous un pont ou entre des immeubles | « Position imprécise » sous le décompte quand la précision se dégrade ; le décompte ne bouge plus, puis rattrape |

Réglages recommandés pour la passe : luminosité au maximum, mode jour puis mode nuit, volume média à mi-course, vue 3D d'abord
activée puis coupée (scénario 17), simulation à ×1 pour la voix et ×2 pour les longs tronçons.

## 7. Ce que la simulation ne remplace pas

- Le GPS réel : précision, sauts, tunnels, temps d'acquisition. Seule une trace GPX enregistrée les reproduit.
- Le comportement du conducteur : lisibilité d'un coup d'œil, en mouvement, au soleil.
- L'audio dans la voiture : latence Bluetooth, volume face au bruit.
- La batterie avec le GPS actif et la chauffe sur un support.
- Android Auto dans une vraie voiture : exige une installation depuis Google Play (voir `ANDROID-AUTO.md`).

## 8. Jeu de traces GPX

`.\gradlew.bat :core:navigation:testDebugUnitTest --offline -q --tests "*GpxValidationSetTest*"` écrit cinq fichiers dans
`core/navigation/build/gpx-validation/`.

**Toutes sont synthétiques** (nom `synth-…`, mention dans les métadonnées) : la géométrie vient d'itinéraires Valhalla réels
enregistrés, les positions, vitesses et bruits sont calculés. Aucun enregistrement GPS réel n'existe dans le dépôt. Une trace
enregistrée en roulant, avec n'importe quelle application GPX, vaut plus que ces cinq fichiers réunis.

| Fichier | Départ (lat, lon) | Destination à saisir (lat, lon) | Sert aux scénarios |
|---|---|---|---|
| `synth-toulouse-trajet-complet.gpx` | 43.63012, 1.41013 | 43.55397, 1.49993 | 1 à 8 |
| `synth-toulouse-sortie-19-manquee.gpx` | 43.57207, 1.45979 | 43.55397, 1.49993 | 15 |
| `synth-toulouse-perte-gps-20s.gpx` | 43.63012, 1.41013 | 43.55397, 1.49993 | 20 |
| `synth-etoile-6e-sortie-gps-bruite.gpx` | 48.87552, 2.28931 | 48.87275, 2.29830 | 1, 10, 18 |
| `synth-muret-deux-ronds-points.gpx` | 43.46094, 1.32700 | 43.43964, 1.29559 | 3 |

Procédure de rejeu, identique pour chaque trace :

1. `adb push <fichier> /sdcard/Android/data/fr.roadly/files/replay.gpx`
2. Réglages › Navigation › Simulation de trajet : activée, vitesse ×1.
3. Rechercher la destination par ses coordonnées, choisir « Le plus rapide », démarrer.
4. `adb logcat -c` puis `adb logcat -v time -s RoadlyGuidage RoadlySpeech > passe.log`.
5. À la fin : `adb shell rm /sdcard/Android/data/fr.roadly/files/replay.gpx`, puis couper la simulation.

Limite : l'itinéraire calculé le jour du test peut différer de celui enregistré (travaux, mise à jour de la carte). Si la trace
s'écarte du tracé, le recalcul se déclenche : le noter, ce n'est pas un échec du guidage.

## 9. Séance sur le HONOR Magic 8 Pro

État lu par adb le 21 novembre 2026, sans rien modifier :

| Élément | Valeur | Action avant la séance |
|---|---|---|
| Appareil | `AREE026226000174`, Android 17, batterie 85 %, 25 °C | — |
| Écran | verrouillé par code | **déverrouiller** : rien ne peut être regardé sinon |
| Roadly installé | 0.1.210 (code 210) | sera remplacé par le build de test, données conservées |
| Localisation | activée, haute précision | — |
| Position précise | accordée | passer à « Toujours autoriser » pour le scénario 11 |
| Notifications | **refusées** | les accorder : la notification de guidage et le service en dépendent |
| Optimisation de batterie | Roadly non exempté | l'exempter pour le scénario 11 (écran éteint), puis noter la différence |

Build de test : `app/build/outputs/apk/debug/app-debug.apk`, version 0.1.210 (même numéro que l'installée tant qu'aucun commit
n'est ajouté), signé avec la clé de debug du poste. Installation, **seulement sur accord explicite** :

```
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

Retour à la version publiée : Réglages › Mises à jour dans l'application, ou réinstaller l'APK de la release 0.1.210.

Ordre conseillé (45 minutes, à l'arrêt) : scénarios 1, 4, 5, 7, 8 avec `synth-toulouse-trajet-complet.gpx` en ×2 ; puis 15 ; puis
20 ; puis 10 et 18 avec l'Étoile ; puis 13 en paysage ; puis 11 et 12 ; enfin 17 (vue 3D coupée). La batterie (§5) se mesure sur
un trajet complet en ×1, écran allumé.

## 10. Critères par scénario

Départ : toujours simulation activée, voix active, volume à mi-course. « T » = `synth-toulouse-trajet-complet.gpx`.

| # | Départ | Échec si |
|---|---|---|
| 1 | T, attendre le premier rond-point à plusieurs sorties | le décompte remonte ; « Quittez le rond-point » avant la dernière sortie à laisser ; schéma absent dans l'anneau |
| 2 | T, rond-point en 1re sortie | un décompte « N sorties à passer » s'affiche ; annonce après la sortie |
| 3 | trace Muret | le second schéma garde le décompte du premier |
| 4 | T, sortie 19 | pas de « Serrez à droite » ; consigne dite deux fois ; Horizon visible pendant la préparation |
| 5 | itinéraire passant par l'A61 à Toulouse, sans trace | voies absentes alors que le journal montre une manœuvre avec voies ; voies du mauvais côté dites à voix haute |
| 6 | T, sortie 19 puis bifurcation à 605 m | retour à la carte nue entre les deux ; « puis » absent |
| 7 | T, insertion avant les 11 km | confirmation absente, ou redite |
| 8 | T, fin du trajet | arrivée non annoncée ; simulation encore active après l'arrivée |
| 9 | trace 15, couper le réseau après le départ | « Recalcul » redit ; une manœuvre de l'ancien tracé annoncée après l'écart |
| 10 | trace Étoile | décompte qui remonte ; « Sortie prise » avant d'avoir quitté l'anneau |
| 11 | T, éteindre l'écran 2 minutes | voix coupée ; position figée au rallumage |
| 12 | T, musique en Bluetooth | début de phrase coupé ; musique non rétablie |
| 13 | T, téléphone en paysage | bandeau et bloc du bas qui se chevauchent ; texte coupé |
| 14 | DHU, T | texte différent de celui du téléphone |
| 15 | trace sortie manquée | recalcul demandé après plus de 25 s ; ou avant la sortie |
| 16 | à enregistrer en roulant | recalcul sur une seule position aberrante |
| 17 | T, vue 3D coupée | schéma de bifurcation du mauvais côté ; schéma et vue 3D affichés ensemble |
| 18 | trace Étoile | « Position imprécise » jamais affichée alors que le journal montre `confiance=LOW` |
| 19 | **nouveau** — recalcul avec demi-tour : démarrer un guidage, partir à l'opposé | position qui saute de plusieurs centaines de mètres ; manœuvre annoncée aussitôt ; arrivée déclarée au départ |
| 20 | **nouveau** — trace perte GPS 20 s | « maintenant » dit pendant le trou ; annonce doublée à la reprise |
| 21 | **nouveau** — route parallèle (contre-allée, voie de service), à enregistrer | recalcul alors que l'on suit l'itinéraire |
| 22 | **nouveau** — bifurcation suivie d'une sortie à moins de 300 m, à enregistrer | seconde manœuvre découverte trop tard pour changer de voie |
| 23 | **nouveau** — échangeur à plusieurs niveaux, à enregistrer | position accrochée sur la voie du dessus ou du dessous |
| 24 | **nouveau** — départ d'un guidage à moins de 200 m d'une manœuvre | même phrase dite deux fois |

Les scénarios 16, 21, 22 et 23 ne sont pas reproductibles sans une trace réelle : aucun itinéraire enregistré ne les contient.
