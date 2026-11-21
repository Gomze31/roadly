# Protocole de validation terrain — HONOR Magic 8 Pro

Document unique pour la passe sur appareil et sur route (phase 5, 22 novembre 2026). Il remplace, pour la séance terrain, les
sections 6, 9 et 10 de `VALIDATION-TELEPHONE.md` ; ce dernier reste la référence des outils (simulation, rejeu GPX, journal).

**État au moment de la rédaction** : rien de ce protocole n'a été exécuté sur un téléphone, une voiture ou le DHU. Le HONOR était
verrouillé par code et aucune installation n'a été autorisée. Chaque scénario porte la mention de ce qui a déjà été vérifié
**hors appareil** (JVM, laboratoire, captures) ; la colonne « Terrain » est vide partout et se remplit pendant la séance.

Niveaux de preuve utilisés dans ce document :

| Mention | Ce que cela veut dire |
|---|---|
| JVM | test unitaire sur le vrai code, positions calculées |
| Labo | scénario rejoué dans la vraie session de guidage (`GuidanceLab`), positions calculées sur un tracé Valhalla réel |
| Capture | rendu Compose hors appareil (Roborazzi), sans carte |
| Simulation téléphone | simulation de trajet ou rejeu GPX **sur le HONOR** — pas encore fait |
| Terrain | conduite réelle — pas encore fait |

## 1. Installation

L'installation remplace la version 0.1.210 du téléphone : **à lancer uniquement sur accord explicite**, téléphone déverrouillé.

```
adb devices                                   # AREE026226000174 doit apparaître « device »
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell dumpsys package fr.roadly | findstr versionName
```

La version de débogage est signée avec une autre clé que la version publiée : si `adb install -r` répond
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, ne pas désinstaller (les favoris, l'historique et les réglages seraient perdus) ; arrêter
et décider d'abord.

## 2. Réglages du téléphone avant la séance

État lu par adb le 21 novembre (lecture seule) : Android 17, position en haute précision, `ACCESS_FINE_LOCATION` accordée,
**notifications refusées**, application **non exemptée** de l'optimisation de batterie.

| Réglage | Valeur attendue | Pourquoi | Vérification |
|---|---|---|---|
| Position | activée, « Précision de la position Google » activée | fixes à 1 s en haute précision | `adb shell settings get secure location_mode` → `3` |
| Autorisation de position de Roadly | « Lorsque l'appli est en cours d'utilisation », position **exacte** | le guidage n'a pas besoin de « Toujours » : le service de premier plan tient la position écran éteint | `adb shell dumpsys package fr.roadly \| findstr ACCESS_FINE` |
| Notifications de Roadly | **autorisées** | sans elles le service tourne mais il n'y a ni notification de guidage, ni bouton « Arrêter », ni indice que le guidage continue | `adb shell dumpsys package fr.roadly \| findstr POST_NOTIFICATIONS` → `granted=true` |
| Batterie | Roadly en « gestion manuelle » : lancement automatique, lancement secondaire et **exécution en arrière-plan** autorisés (Paramètres › Batterie › Lancement d'applications — libellés MagicOS à confirmer sur l'appareil) | MagicOS arrête les applications en arrière-plan ; l'application ne demande pas elle-même l'exemption | `adb shell dumpsys deviceidle whitelist \| findstr roadly` |
| Mode économie d'énergie | désactivé (sauf scénario 10.4) | il bride la position écran éteint | — |
| Synthèse vocale | moteur Google installé, voix française téléchargée | sinon le moteur du constructeur est utilisé (voix robotique) | Réglages › Voix de Roadly, bouton d'essai |
| Volume média | ≥ 50 % | la voix sort sur le flux de guidage | — |
| Heure | automatique | un fix plus ancien que l'heure du téléphone de plus de 2 s est écarté au départ | — |

Rien dans l'application n'avertit aujourd'hui que les notifications ou la batterie sont mal réglées : c'est un risque connu
(§ 8), à contrôler à la main avant chaque séance.

## 3. Journal et preuves

```
adb logcat -c
adb logcat -s RoadlyGuidage RoadlySpeech > seance.log     # une ligne par changement d'état du guidage, une par annonce
adb exec-out screencap -p > capture.png
adb shell screenrecord /sdcard/roadly.mp4                  # 3 min au plus par fichier
```

Enregistrer une trace réelle pendant chaque trajet (application d'enregistrement GPX au choix, 1 point par seconde) : c'est elle
qui permettra de rejouer le défaut ensuite (`adb push trace.gpx /sdcard/Android/data/fr.roadly/files/replay.gpx`, puis
Réglages › Navigation › Simulation de trajet). Les cinq traces `synth-*.gpx` du dépôt sont **calculées**, pas enregistrées.

## 4. Scénarios

Chaque scénario : conditions de départ, résultat attendu, critère d'échec, preuve hors appareil existante. « Terrain » : à
remplir par OK / KO / non fait, avec l'heure dans le journal.

### 4.1 Navigation de base

| # | Scénario | Attendu | Échec si | Hors appareil | Terrain |
|---|---|---|---|---|---|
| 1.1 | Départ à l'arrêt, trajet urbain de 3 km | phrase de départ une fois ; position sur le tracé dès le premier fix ; première manœuvre correcte | position posée ailleurs que là où est la voiture ; manœuvre suivante annoncée aussitôt | JVM (`RouteTrackerAcquisitionTest`) | |
| 1.2 | Départ sur un trajet qui commence par un demi-tour ou revient près de son départ | accroché au départ, jamais sur le retour ; pas d'arrivée au départ | saut de plusieurs centaines de mètres ; « vous êtes arrivé » au départ | JVM | |
| 1.3 | Virages successifs en ville | chaque virage dit une fois, dans l'ordre ; « puis » pour deux virages rapprochés | virage dit deux fois, oublié, ou dit après le point | Labo | |
| 1.4 | Arrivée | arrivée dite une fois, à moins de 25 m de la destination | arrivée dite trop tôt ou jamais | JVM, Labo | |
| 1.5 | Destination de l'autre côté d'une route à chaussées séparées, arrêt au feu en face | le demi-tour reste à faire, pas d'arrivée | arrivée déclarée avant le demi-tour (défaut corrigé en phase 5) | JVM (`RouteTrackerPlausibilityTest`) | |

### 4.2 Fiabilité du GPS

| # | Scénario | Attendu | Échec si | Hors appareil | Terrain |
|---|---|---|---|---|---|
| 2.1 | Tunnel court ou passage couvert (< 10 s) | la distance continue de descendre (150 m au plus), aucun « maintenant » sur une position devinée | « maintenant » dit dans le tunnel ; saut à la sortie | Labo | |
| 2.2 | Tunnel long (> 30 s) | badge « GPS perdu » après 10 s, « Signal GPS perdu. » dit une fois ; à la sortie, position reprise en 2 fixes, **pas de recalcul** | recalcul lancé à la sortie ; phrase répétée ; guidage muet après la sortie | JVM, Labo (20 s) ; 60 s : JVM seulement | |
| 2.3 | Route parallèle à 25–30 m (contre-allée, voie de desserte) | pas de recalcul tant que le cap suit le tracé | recalculs en boucle | JVM, Labo | |
| 2.4 | Arrêt prolongé (feu, bouchon) en ville dense | position stable, aucune annonce répétée, pas de saut vers un autre passage du tracé | la position saute ; une manœuvre est réputée faite | JVM, Labo | |
| 2.5 | Parking couvert ou rue étroite (précision > 30 m) | pastille « GPS imprécis », distance estompée ; ni recalcul, ni arrivée, ni « maintenant » sur ces fixes | arrivée ou recalcul décidé sur un fix imprécis | JVM | |
| 2.6 | Pont au-dessus du tracé, échangeur à niveaux | la position reste sur la bonne chaussée | saut sur l'autre niveau | JVM (tracé `overpass`) ; échangeur réel : non | |

### 4.3 Ronds-points

| # | Scénario | Attendu | Échec si | Hors appareil | Terrain |
|---|---|---|---|---|---|
| 3.1 | Rond-point simple, 1re ou 2e sortie | schéma avec la bonne sortie ; « quittez le rond-point » quand la prochaine sortie est la bonne | mauvais rang ; sortie dite trop tard | Labo, Capture | |
| 3.2 | Grand rond-point, 4e sortie ou plus | décompte « N sorties à passer » qui descend ; jamais en arrière | décompte faux ou qui remonte | Labo (Étoile, tracé réel) | |
| 3.3 | Deux ronds-points à moins de 300 m | chacun annoncé une fois, le second pas confondu avec le premier | annonces mélangées | Labo | |
| 3.4 | Rond-point sous mauvais GPS | « Position imprécise », pas de décompte affirmé | décompte affiché comme sûr alors qu'il est faux | JVM | |
| 3.5 | Sortie manquée dans un rond-point (un tour de plus) | le décompte ne ment pas ; recalcul ou reprise propre | « sortie prise » à tort | JVM (preuve de sortie) ; tour complet : non | |

### 4.4 Intersections complexes

| # | Scénario | Attendu | Échec si | Hors appareil | Terrain |
|---|---|---|---|---|---|
| 4.1 | Sortie d'autoroute à 110–130 km/h | anticipation, placement, « prenez la sortie » avec au moins 3 s d'avance ; panneau lisible | sortie dite après le point ; panneau tronqué | Labo (90, 110, 130) ; délais mesurés hors voix réelle | |
| 4.2 | Sortie suivie d'une bifurcation à moins de 600 m | les deux dites, dans l'ordre, chacune avant son point | la seconde masquée par la première | Labo | |
| 4.3 | Bifurcation gauche/droite | côté affiché = côté dit = côté du tracé ; voies jamais en contradiction | voies qui indiquent l'autre côté | JVM, Labo | |
| 4.4 | Échangeur à plusieurs niveaux | schéma de bifurcation ou vue 3D (jamais les deux) ; branches réelles | schéma illisible ou faux | Capture (schéma) ; vue 3D : non | |
| 4.5 | Carrefour sans donnée de voies | pas de voies inventées | voies affichées sans source | JVM | |

### 4.5 Recalcul

| # | Scénario | Attendu | Échec si | Hors appareil | Terrain |
|---|---|---|---|---|---|
| 5.1 | Virage manqué en ville | « Recalcul de l'itinéraire. » une fois, en moins de 15 s ; nouveau tracé guidé ; rien de l'ancien n'est annoncé | annonce d'une manœuvre de l'ancien tracé ; « Recalcul » répété | Labo | |
| 5.2 | Sortie d'autoroute manquée | recalcul en 12–17 s (mesure labo) | plus de 30 s sans réaction | Labo | |
| 5.3 | Recalcul qui impose un demi-tour | position posée au début du nouveau tracé, pas sur son retour | saut en avant | JVM | |
| 5.4 | Virage manqué **sans réseau** (mode avion après le départ) | « Recalcul » puis « Recalcul impossible pour le moment. Nouvel essai en cours. », une fois chacun ; l'écran reste en recalcul ; essais à 10, 10, 20, 40 puis 60 s ; reprise dès le retour du réseau ou du tracé | écran qui remontre l'ancienne manœuvre ; silence total ; essais toutes les secondes | JVM, Labo | |
| 5.5 | Retour volontaire sur le tracé avant la réponse | le guidage reprend sans recalcul affiché | recalcul qui persiste | Labo | |

### 4.6 Navigation en arrière-plan

Aucune preuve hors appareil n'existe pour cette catégorie : le comportement dépend d'Android et de MagicOS.

| # | Scénario | Attendu | Échec si | Terrain |
|---|---|---|---|---|
| 6.1 | Écran éteint 15 min en roulant | annonces à l'heure ; au rallumage, position juste | annonces en retard ou absentes ; « GPS perdu » écran éteint | |
| 6.2 | Passage sur une autre application (musique, messages) 5 min | annonces maintenues ; notification de guidage à jour | guidage muet ; retour sur une position figée | |
| 6.3 | Application balayée des récents pendant le guidage | à noter : le service n'est pas relancé (`START_NOT_STICKY`) ; reprise proposée à la réouverture | reprise non proposée | |
| 6.4 | Écran éteint 1 h sans exemption de batterie | à noter : MagicOS coupe-t-il la position ou le processus ? | — (mesure) | |
| 6.5 | Notifications refusées | le guidage fonctionne mais sans notification : à constater, puis les autoriser | guidage qui ne démarre pas | |
| 6.6 | Appel téléphonique pendant le guidage | à noter : la voix parle-t-elle pendant l'appel ? | — (le résultat de la demande de focus audio n'est pas lu par le code) | |
| 6.7 | Verrouillage puis déverrouillage, rotation | état conservé | bandeau vide, guidage relancé | |

### 4.7 Guidage vocal

| # | Scénario | Attendu | Échec si | Hors appareil | Terrain |
|---|---|---|---|---|---|
| 7.1 | Délai réel entre « maintenant » et le point de manœuvre à 30, 50, 90, 110, 130 km/h | 3 à 4 s avant le point, fin de phrase avant le point | phrase terminée après le point | délais calculés (`AnnouncementTimingTableTest`) : hors latence de synthèse et Bluetooth | |
| 7.2 | Aucune phrase deux fois de suite | — | même phrase en moins de 10 s | JVM, Labo | |
| 7.3 | Bluetooth voiture ou enceinte | première syllabe audible ; musique baissée puis rétablie | début de phrase coupé ; musique qui reste baissée | non | |
| 7.4 | Musique en cours (téléphone seul) | atténuation pendant l'annonce | musique coupée ou annonce inaudible | non (passe partielle 0.1.183 documentée ailleurs) | |
| 7.5 | Voix après 1 h de trajet | toujours présente ; si le moteur vocal a été tué, l'annonce suivante le recrée (phase 5) | silence jusqu'à la fin du trajet | non (aucun test : le moteur Android n'existe pas sur JVM) | |
| 7.6 | Sourdine puis reprise | aucune annonce en sourdine, reprise à la suivante | annonces en retard déversées | JVM | |

### 4.8 Android Auto

Nécessite le DHU (voir `ANDROID-AUTO.md`) ; une vraie voiture exige une installation depuis Google Play. Défauts déjà relevés
à la lecture du code, non corrigés (module `feature/car` réservé à l'autre chantier), à confirmer : § 8.

| # | Scénario | Attendu | Échec si | Terrain |
|---|---|---|---|---|
| 8.1 | Même manœuvre et même distance que le téléphone | identiques à 1 s près | écart de manœuvre | |
| 8.2 | Rond-point | bras, sortie, décompte ; rien d'affirmé sous mauvais GPS | décompte figé présenté comme sûr | |
| 8.3 | Voies | affichées si connues, absentes sinon | flèche « tout droit » inventée | |
| 8.4 | Recalcul, avec et sans réseau | écran de recalcul pendant toute la durée | ancienne manœuvre remontrée | |
| 8.5 | Arrivée | écran d'arrivée, carte et vitesse à jour | carte et vitesse figées | |
| 8.6 | GPS perdu | indication visible | distance qui décompte comme si de rien n'était | |
| 8.7 | Départ depuis la voiture, téléphone verrouillé | le service de position démarre | guidage sans position | |

### 4.9 Longue distance

| # | Scénario | Attendu | Échec si | Hors appareil | Terrain |
|---|---|---|---|---|---|
| 9.1 | Trajet de plus de 100 km, autoroute | aucune annonce pendant les longues sections ; confirmation après manœuvre si la suite dépasse 1,5 km | bavardage ; silence à l'approche d'une sortie | Labo (croisière) | |
| 9.2 | Trajet de plus de 2 h | voix, position et heure d'arrivée toujours justes ; rappel de pause si activé | dérive de l'heure d'arrivée ; processus tué | non | |
| 9.3 | Calcul d'un trajet de 500 km | réponse du serveur public en moins de 15 s, sinon message d'erreur clair | attente sans fin | non (aucune requête envoyée pendant cette phase) | |

### 4.10 Batterie et performances

Rien n'a été mesuré : toutes les valeurs sont à relever.

| # | Mesure | Commande | Terrain |
|---|---|---|---|
| 10.1 | Consommation sur 30 min de guidage, écran allumé | `adb shell dumpsys batterystats --reset`, trajet, puis `adb shell dumpsys batterystats --charged fr.roadly` | |
| 10.2 | Idem écran éteint | idem | |
| 10.3 | Fluidité de l'interface | `adb shell dumpsys gfxinfo fr.roadly framestats` (surcouche seulement : la carte est une surface à part) | |
| 10.4 | Mode économie d'énergie, écran éteint | constater si les fixes ralentissent (`RoadlyGuidage`) | |
| 10.5 | Température et charge après 1 h | `adb shell dumpsys battery` | |
| 10.6 | Recalculs de l'interface | Layout Inspector, compteur de recompositions | |

## 5. Ordre conseillé de la séance

1. § 2 (réglages), § 3 (journal).
2. À l'arrêt, **simulation de trajet** sur le téléphone : scénarios 1.1, 1.3, 3.1, 4.1 — premier regard sur l'écran réel.
3. Rejeu des traces `synth-*.gpx` : 3.3, 4.2, 5.2.
4. Trajet réel court en ville (15 min) : 1.x, 2.4, 3.x, 5.1, 7.1 à 30 et 50 km/h.
5. Trajet réel avec voie rapide (30 min) : 4.x, 5.2, 7.1 à 90–130 km/h, 6.1, 6.2.
6. Arrière-plan et batterie : 6.3 à 6.7, 10.x.
7. Android Auto sur DHU : 8.x.

## 6. Fiche de défaut

```
Scénario : (numéro)                         Date et heure :
Version : (adb shell dumpsys package fr.roadly | findstr versionName)
Lieu : (commune, route, sens)               Vitesse :
Ce que j'attendais :
Ce qui s'est passé :
Ce que la voix a dit (mot pour mot) :
Écran : (capture ou description)            Écran allumé / éteint / autre application :
Audio : téléphone / Bluetooth / Android Auto
Réseau : 4G-5G / faible / aucun
Reproductible : oui / non / pas réessayé
Pièces : seance.log (heure du défaut), trace GPX, capture
```

## 7. Ce que seule la conduite peut dire

- Délai réel des annonces : latence du moteur vocal, longueur des phrases, Bluetooth.
- Lisibilité au coup d'œil, de jour au soleil et de nuit ; densité de l'écran en excès de vitesse.
- Comportement de MagicOS écran éteint, sans exemption de batterie, et après une heure.
- Dérive GPS réelle en ville dense, sous les ponts, en tunnel.
- Justesse du décompte des sorties sur de vrais ronds-points (voies de service, sorties privées).
- Vue 3D des carrefours (OpenGL, impossible à rendre hors appareil).
- Charge et réponse réelle du serveur public Valhalla, perte de réseau en roulant.
- Android Auto : limites de rafraîchissement de l'hôte, rendu sur l'écran de la voiture.

## 8. Risques connus au départ de la séance

| Risque | Origine | État |
|---|---|---|
| Notifications refusées ou batterie optimisée, sans avertissement dans l'application | lecture du code et de l'état du téléphone | non corrigé (ajout d'écran, hors périmètre de la stabilisation) |
| Processus tué en arrière-plan : guidage perdu, reprise seulement à la réouverture | lecture du code | non corrigé |
| La voix peut parler pendant un appel ; le bip d'excès de vitesse ne demande pas le focus audio | lecture du code | à constater |
| Android Auto : carte figée après l'arrivée ; pas d'indication GPS perdu ; décompte de rond-point affiché même incertain ; `updateTrip` à chaque seconde ; voie sans direction dessinée « tout droit » ; exception possible si un numéro de sortie accompagne une manœuvre « sortie de rond-point » | lecture du code de `feature/car` | non corrigé (chantier Android Auto séparé) |
| Hors du tracé de plus de 40 m, recherche sur tout le tracé à chaque fix (coûteux sur 500 km) | lecture du code | non corrigé, à mesurer |
| Le marqueur de position réécrit sa source à chaque image, même à l'arrêt | lecture du code | non corrigé, à mesurer |
| La conduite libre interroge le serveur public toutes les 20 s au moins | lecture du code | à arbitrer (conditions d'usage FOSSGIS) |
| Aucune trace GPS réelle dans le dépôt | — | à enregistrer pendant la séance |
