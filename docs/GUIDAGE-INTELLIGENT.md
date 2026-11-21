# Guidage intelligent — Roadly comprend la route

Passe du 21 novembre 2026. Ce document décrit ce qui a été construit, pourquoi, comment c'est vérifié, et ce qui reste à faire.
Benchmark des concurrents : [BENCHMARK-GPS.md](BENCHMARK-GPS.md). Vue 3D de carrefour : [JUNCTION-3D.md](JUNCTION-3D.md).

## 1. Constat de départ

L'audit du code et des documents donne une application déjà large (923 tests JVM, guidage, voix, radars, trottinette, Android Auto
sur DHU, vue 3D de carrefour), mais dont le guidage restait celui d'un GPS classique :

- la voix disait trois fois la même instruction à des distances fixes par classe de vitesse (lointain, proche, maintenant) ;
- rien ne disait où se placer avant une sortie, ni ne rassurait après une manœuvre ;
- au rond-point, un schéma fixe montrait la sortie à prendre et son rang : le conducteur comptait encore les sorties ;
- dans l'anneau, « Quittez le rond-point » partait à une distance de la sortie, sans savoir si une autre sortie restait avant ;
- l'écran affichait la même quantité d'information en ligne droite et dans un échangeur.

Le benchmark confirme que ce sont les plaintes les plus fréquentes chez tous les concurrents : consigne trop tardive, sortie de
rond-point ambiguë, voie découverte au dernier moment. Aucune application étudiée ne montre un décompte des sorties déjà passées
(point non vérifiable sur route, relevé dans la documentation et les avis).

## 2. Situation de conduite

`core/navigation/GuidanceSituation.kt` — `SituationEngine` classe ce que vit le conducteur à chaque position. Un seul mode à la
fois, publié dans `NavigationState.Guiding.situation`.

| Mode | Déclencheur | Densité |
|---|---|---|
| `CRUISE` | rien à préparer | minimale |
| `PREPARE` | virage simple dans sa fenêtre | standard |
| `ROUNDABOUT_APPROACH` | entrée de rond-point dans sa fenêtre | renforcée |
| `ROUNDABOUT` | dans l'anneau | renforcée |
| `ROUNDABOUT_EXITED` | 60 m après la sortie | standard |
| `EXIT` | sortie ou bretelle | renforcée |
| `FORK` | bifurcation | renforcée |
| `COMPLEX` | manœuvre suivante à moins de 200 m, ou 3 voies et plus | renforcée |
| `ARRIVAL` | arrivée | renforcée |
| `REROUTING` | recalcul en cours | standard |
| `GPS_UNCERTAIN` | GPS perdu, ou 3 positions de suite imprécises (> 40 m) ou extrapolées | standard |

Règles :

- **La fenêtre de préparation dépend du temps, pas seulement de la distance.** Sortie et bifurcation : 500 m ou 22 s (≈ 800 m à
  130 km/h) ; rond-point : 200 m ou 14 s ; enchaînement : 250 m ou 16 s ; virage : 150 m ou 11 s ; arrivée : 300 m ou 20 s ;
  plafond 1 500 m.
- **Pas de clignotement.** Une fenêtre ouverte reste ouverte jusqu'à la manœuvre, même si le conducteur freine.
- **Un seul fix médiocre ne change rien.** Il en faut trois de suite pour passer en « GPS imprécis ».
- `urgency` (0 à 1) mesure l'avancement dans la fenêtre : c'est la barre sous le bandeau.
- `side` donne le côté où se placer (type de manœuvre, sinon voies conseillées).

Ce que l'écran en fait (`feature/navigation`) :

- densité renforcée : le ruban Horizon, la prochaine limitation, la pluie et le rappel de péage s'effacent ; restent la manœuvre,
  la vitesse, la limitation courante et les zones de danger (`secondaryInfoVisible`) ;
- barre de préparation sous le bandeau, qui se remplit jusqu'au point de manœuvre (`bannerUrgency`) ;
- pastille « Serrez à droite / à gauche » sous le bandeau pour une sortie, une bifurcation ou un enchaînement, sauf si les voies
  sont dessinées (`sideHint`) ;
- pastille « GPS imprécis », distincte de « GPS perdu ».

## 3. Mode rond-point

`core/navigation/RoundaboutProgress.kt`, `feature/navigation/RoundaboutModePanel.kt`.

Le schéma sous le bandeau est vivant :

- l'entrée est toujours en bas, comme le conducteur la voit ;
- chaque sortie à laisser passer est une branche courte, placée à son angle réel sur l'anneau ; elle s'éteint une fois derrière soi ;
- la sortie à prendre est la seule branche vive, avec sa flèche ;
- un point suit le véhicule sur l'anneau ; le trajet restant est en couleur, le trajet fait en gris ;
- une seule phrase à côté : « 3 sorties à passer », puis « 2 », « 1 », puis « Prochaine sortie » sur fond orange ;
- après la sortie, le trajet passe au vert avec « Sortie prise » pendant 60 m, puis la navigation normale revient.

Captures (rendu réel des composants, Robolectric) : `docs/captures/guidage-intelligent/`.

Sources, rien d'inventé :

- angles et longueurs : tracé réel de l'anneau parcouru (`/route`) ;
- sorties intermédiaires : comptage sur la topologie réelle (`trace_attributes`), **seulement s'il donne le même rang que
  Valhalla**. Sinon seule la sortie à prendre est dessinée, et aucun décompte n'est affiché ni dit.

Robustesse au GPS : la position sur l'anneau ne recule jamais. Un saut en arrière ne rallume pas une sortie passée et ne fait pas
remonter le décompte.

Voix : avec un décompte fiable, « Quittez le rond-point » est dit dès que la sortie précédente est passée — ni avant, ni au seul
jugé d'une distance qui ne distingue pas deux sorties voisines (`RoundaboutVoice.exitGate`). Sans décompte, l'ancienne règle de
distance s'applique.

Décision : au rond-point, ce schéma 2D remplace la vue 3D de carrefour (une seule représentation à la fois). La vue 3D reste pour
les sorties, bifurcations et échangeurs. Raisons : la vue de dessus normalisée se lit plus vite qu'une perspective, et la vue 3D
n'a jamais été vue sur appareil. Le choix tient en une ligne dans `NavigationScreen.kt` (`roundaboutModeVisible`).

Changement lié : la topologie d'un carrefour est maintenant chargée même quand le réglage « vue 3D » est coupé, car le mode
rond-point en a besoin. Seul l'affichage de la vue 3D dépend du réglage. Coût : une petite requête `trace_attributes` par carrefour retenu.

Limites :

- les sorties situées après celle à prendre ne sont pas connues (hors du tracé) : l'anneau est dessiné, pas ces branches ;
- circulation à droite seulement ;
- le nombre total de sorties n'est pas affiché, pour la même raison.

## 4. Voix progressive

`core/navigation/GuidanceVoice.kt`. Quatre temps par manœuvre :

| Temps | Quand | Exemple |
|---|---|---|
| 1. Anticipation | palier lointain existant (2 km voie rapide, 600 m route, 300 m ville) | « Dans 2 kilomètres, prenez la sortie 19 vers Montpellier. » |
| 2. Placement | voie rapide 1000 → 450 m ; route 350 → 225 m ; ville 180 → 120 m | « Serrez à droite. » / « Restez sur les deux voies de droite. » |
| 3. Manœuvre | paliers proche puis maintenant, existants | « Prenez la sortie 19 à droite. » |
| 4. Confirmation | 3 s après la manœuvre | « Continuez pendant 11 kilomètres. » |

Les temps 2 et 4 se taisent quand ils n'apportent rien :

- placement : seulement si un côté est connu. Les voies réelles priment ; sans elles, seules les sorties et bretelles reçoivent
  « Serrez à … ». En ville, il faut des voies réelles. Jamais à moins de 6 s d'une autre annonce. Une fois par manœuvre ;
- confirmation : seulement si rien n'arrive avant 1,5 km et 60 s. Priorité basse : elle saute si la file vocale est occupée ;
- trottinette : aucun des deux (les annonces trottinette ont leur propre coordinateur).

## 5. Vérification

| Situation | Test | Données |
|---|---|---|
| Schéma : sorties ordonnées, écartées, cible à son angle réel | `RoundaboutModeTest` | Étoile (6e sortie) |
| Décompte cohérent avec Valhalla sur tous les ronds-points réels | `RoundaboutModeTest` | Toulouse, Muret, Nation, Étoile |
| Sans topologie : aucun décompte prétendu | `RoundaboutModeTest`, `SmartGuidanceSessionTest` | Étoile |
| Saut GPS en arrière dans l'anneau | `RoundaboutModeTest` | Étoile |
| Deux ronds-points successifs : décompte remis à zéro | `RoundaboutModeTest` | Muret |
| « Quittez le rond-point » dit une fois, après la 5e sortie | `SmartGuidanceSessionTest` | Étoile, session complète |
| Sortie d'autoroute à 110 km/h : anticipation, placement, manœuvre dans l'ordre | `SmartGuidanceSessionTest` | Toulouse, sortie 19 |
| Confirmation avant 11 km, jamais pour 60 ou 300 m | `SmartGuidanceSessionTest` | Toulouse |
| Fenêtres liées au temps, verrou, GPS incertain, recalcul | `GuidanceSituationTest` | synthétique |
| Voies : côté, nombre, silence quand rien à dire | `GuidanceSituationTest` | synthétique |
| Règles d'affichage, géométrie du schéma | `RoundaboutModeUiTest` | synthétique |
| Rendu réel jour / nuit | `GuidanceScreenshotTest` | captures PNG |

Les situations déjà couvertes avant cette passe (recalcul, route parallèle, demi-tour, tunnel, extrapolation, reprise après mort
du processus, enchaînement de ronds-points) restent dans `NavigationSession*Test`, `RouteTracker*Test`, `MotorwayGuidanceTest`.

**Non vérifié** : rien de ce document n'a roulé. Aucun téléphone n'était branché pendant la passe, il n'y a pas d'émulateur sur
ce poste. Le rendu a été regardé composant par composant, pas l'écran complet avec la carte.

Nouveau : captures JVM. `GuidanceScreenshotTest` (Robolectric 4.18.1 + Roborazzi 1.79.0, dépendances de test seulement) rend la
pile du bandeau en PNG dans `feature/navigation/build/outputs/roborazzi/`. C'est le moyen de regarder un composant sans appareil.

## 6. Phase 2 — fiabilité, données réelles, Android Auto, validation

### 6.1 Fiabilité du rond-point

`RoundaboutProgressTracker` applique quatre règles, dans l'ordre :

1. **Jamais en arrière.** Position sur l'anneau et sorties passées ne reculent pas.
2. **Une sortie n'est passée que nettement dépassée.** Marge après le nœud de la sortie : 30 % de l'imprécision du fix, entre 2 m
   et 8 m, et jamais plus de 40 % de l'écart jusqu'à la sortie suivante (sinon la bonne sortie serait annoncée trop tard).
3. **Position douteuse = décompte gelé.** Position extrapolée, ou précision moins bonne que l'écart entre deux sorties voisines
   (borné entre 12 et 25 m) : rien n'avance, la confiance passe à `LOW`, l'écran affiche « Position imprécise ». Le décompte
   rattrape d'un coup quand la position redevient sûre.
4. **Un saut doit être confirmé.** Une avancée plus grande que 2,5 fois ce que la vitesse permet (plus la précision et 8 m) est
   ignorée tant que la position suivante ne la confirme pas.

« Sortie prise » demande des preuves : position mesurée (pas extrapolée), à moins de 20 m du tracé, au moins 8 m après le nœud de
sortie (ou la moitié de l'imprécision si elle est plus grande). Avant, le schéma reste sur « Prochaine sortie ».

Voix : en confiance `LOW` et tant que la sortie n'est pas acquise, la voix ne s'appuie plus sur le décompte ; la règle de distance
reprend, et elle refuse elle-même « maintenant » sur une position extrapolée ou à plus de 30 m de précision. Une sortie acquise
le reste.

Limites connues, non traitées :

- un conducteur qui manque sa sortie et continue sur l'anneau n'est détecté que par la règle générale de sortie d'itinéraire
  (40 m pendant 5 positions, ou 100 m) : dans un grand rond-point cela prend plusieurs secondes ;
- anneaux à voies séparées (turbo-giratoires) et voie directe vers une sortie (« by-pass ») : Valhalla les décrit comme des
  bretelles ou des virages, pas comme un rond-point ; le mode rond-point ne s'y déclenche pas ;
- le suivi repose sur la projection sur l'itinéraire, pas sur un calage carte indépendant.

### 6.2 Contexte après le rond-point

À l'entrée d'un rond-point, la ligne « puis … » ne répète plus la sortie de l'anneau (déjà dans le bandeau et le schéma). Elle
montre la manœuvre qui suit la sortie, seulement si elle est à moins de 250 m (`thenAfter`). La voix l'enchaînait déjà
(« Quittez le rond-point, puis tournez à droite »).

### 6.3 Voies : la donnée existait, elle n'était pas demandée

Constat vérifié le 21 novembre 2026 sur l'instance publique : Valhalla ne renvoie `maneuvers[].lanes` que si la requête porte
`"turn_lanes": true`. Roadly ne l'envoyait pas. Sans l'option : 0 manœuvre avec voies. Avec : 4 sur 20 (Toulouse → A61), 1 sur 22
(Paris A6a → A10).

- Corrigé : `ValhallaRoutingProvider` envoie l'option. Test sur une réponse réelle enregistrée (`valhalla_lanes_toulouse.json`).
- Conséquence : le dessin des voies, « Restez sur la voie de droite » et la détection d'intersection à 3 voies sont maintenant
  alimentés, sans serveur à héberger.
- Honnêteté : une voie n'est affichée que si Valhalla la renvoie. `active` = voie conseillée, `valid` = voie permise ; sans
  donnée, rien n'est dessiné et la voix dit au plus « Serrez à droite » pour une sortie (côté de la manœuvre, pas une voie).
- Plafond réel : la carte. Environ 29 000 voies portent `turn:lanes` en France dans OpenStreetMap, contre 835 000 avec `lanes`.
  Les grands axes et échangeurs sont les mieux couverts.

Héberger Valhalla n'est donc plus nécessaire pour les voies. Les raisons restantes sont les conditions de l'instance publique :
détail, dimensionnement et architecture dans [VALHALLA-HEBERGEMENT.md](VALHALLA-HEBERGEMENT.md).

### 6.4 Android Auto

`feature/car` lit maintenant le mode rond-point :

- texte de la carte de guidage : « Encore 2 sorties à passer · Rue X », puis « Prochaine sortie · Rue X » (`roundaboutCue`) ;
- image du rond-point : branches à laisser passer (éteintes une fois passées) et position du véhicule, par pas de 20° pour ne
  pas rafraîchir l'hôte à chaque position ;
- sans décompte fiable, texte et image restent ceux d'avant : la voiture ne dit jamais autre chose que le téléphone et la voix.

Les voies passaient déjà par `setLanesImage` : elles apparaissent désormais dès que Valhalla les renvoie. Non fait : pastille
« GPS imprécis » et densité adaptative (le gabarit de navigation de l'hôte fixe la mise en page). Non vérifié sur le DHU.

### 6.5 Simulation, rejeu GPX, journal

Voir [VALIDATION-TELEPHONE.md](VALIDATION-TELEPHONE.md). Le trajet réel Toulouse (périphérique, A620, sorties, quatre ronds-points)
est rejoué de bout en bout dans la session par `DriveSimulationTest` : arrivée atteinte, aucun recalcul, tous les modes traversés.

### 6.6 Vérification ajoutée

| Situation | Test |
|---|---|
| Marge de passage selon la précision, bornée par l'écart | `RoundaboutReliabilityTest` |
| Saut GPS en avant retenu puis confirmé | `RoundaboutReliabilityTest` |
| GPS lent (une position toutes les 4 s) | `RoundaboutReliabilityTest` |
| Position imprécise ou extrapolée : gel, puis rattrapage | `RoundaboutReliabilityTest` |
| Étoile avec 4 m de bruit et 9 m de précision | `RoundaboutReliabilityTest` (session) |
| « Sortie prise » seulement avec preuve | `RoundaboutReliabilityTest` (session) |
| GPS perdu 8 s dans l'anneau | `RoundaboutReliabilityTest` (session) |
| Tous les ronds-points réels avec bruit | `RoundaboutReliabilityTest` (session) |
| Trajet simulé réaliste, lecture GPX, rejeu complet | `DriveSimulationTest` |
| Voies réelles demandées et lues | `ValhallaRoutingProviderTest` |
| Décompte et image sur Android Auto | `CarRoundaboutModeTest` |

## 7. Technologies : ce qui est gardé, et pourquoi

| Brique | Choix | Raison |
|---|---|---|
| Carte | MapLibre Native, gardé | libre (BSD), hors ligne possible, styles maîtrisés. Mapbox, HERE, TomTom et Google imposent clé, facturation à l'usage et conditions d'affichage. |
| Routage | Valhalla, gardé | libre (MIT), instructions en français, voies, `trace_attributes` pour limitations, portiques et topologie. |
| Instance | FOSSGIS publique, **décision à prendre** | suffit pour les voies. Mais usage de développement : 1 requête par seconde au plus, aucune garantie de disponibilité. |
| Données | OpenStreetMap | seule source ouverte avec voies, sorties et panneaux. |

Deux écarts de Roadly aux conditions de l'instance publique, non corrigés (ils touchent au calcul d'itinéraire, pas au guidage) :

- la comparaison d'itinéraires lance 3 requêtes `/route` en parallèle ;
- le contact du User-Agent est encore `roadly.example`.

## 8. Reste à faire

Par ordre d'effet sur le conducteur.

1. **Passe sur téléphone puis sur route**, avec la liste de `VALIDATION-TELEPHONE.md` §6. Le téléphone doit être déverrouillé.
2. **Décision sur l'instance Valhalla** : rester sur l'instance publique en respectant ses conditions (requêtes en série, contact
   réel), ou héberger (8 à 20 € par mois pour la France, chiffres non vérifiés auprès d'un hébergeur).
3. Sortie manquée dans un rond-point : détection plus rapide que la règle générale.
4. Sorties après la sortie cible et nombre total de sorties : à lire dans les tuiles vectorielles (`JunctionTileQuery`).
5. Petits ronds-points : dire « tournez à gauche » plutôt que « 3e sortie ».
6. Latence Bluetooth : ouvrir le canal audio avant la phrase et avancer le déclenchement d'autant.
7. Android Auto : passe DHU du mode rond-point ; « GPS imprécis ».
8. Circulation à gauche.
9. Inchangés : trafic (aucune source), coût des péages, navigation hors ligne complète, partage de trajet en direct, bornes de
   recharge détaillées, ponts bas, mode scooter. État détaillé dans `FEATURES.md` et `ROADLY-1.0.md`.

## 9. Phase 3 — manœuvres manquées, bifurcations, conformité, laboratoire

### 9.1 Manœuvre manquée

`core/navigation/MissedManeuverDetector.kt`.

Problème mesuré : la règle générale de sortie d'itinéraire (40 m pendant 5 positions avec un cap écarté de 50°, ou 100 m) protège
des routes parallèles, mais une bretelle quitte l'autoroute sous quelques degrés. Celui qui manque sa sortie à 110 km/h restait
« sur l'itinéraire » pendant des centaines de mètres.

Principe : près d'un point de décision, la preuve n'est pas l'écart mais sa **croissance régulière**. Il faut, réunis :

- une manœuvre proche : de 30 m avant son point à 800 m après ;
- des positions mesurées, à 30 m de précision ou mieux (une position douteuse ne compte ni pour ni contre) ;
- un écart au tracé au-dessus du bruit : 12 m, ou 1,5 fois l'imprécision ;
- qui grandit sur 3 positions de suite, d'au moins 8 m en tout, pour finir au-delà de 18 m.

Jamais sur une seule position. Un écart constant (route parallèle, voie de gauche d'une chaussée large) ne déclenche rien, un
saut qui revient non plus. Le recalcul part alors par le chemin habituel : intervalle minimal de 10 s, « Recalcul de
l'itinéraire. » dit une fois, anciennes annonces invalidées.

Couvre : sortie d'autoroute dépassée, mauvaise branche d'une bifurcation, sortie de rond-point ratée, virage non pris.
Limite : sur une chaussée à deux sens séparés prise à contresens de l'itinéraire, c'est encore la règle générale qui joue.

### 9.2 Schéma de bifurcation

`core/navigation/BranchDiagram.kt`, `feature/navigation/BranchDiagramTile.kt`.

À une sortie, une fourche ou une intersection chargée : la branche à prendre, vive et fléchée, **et les branches à ne pas prendre**,
neutres, dans l'ordre réel de gauche à droite. Une bretelle est dessinée plus fine qu'une chaussée.

- Source : branches sortantes du nœud de la manœuvre dans la topologie Valhalla. Sans topologie, ou si le nœud n'a aucune autre
  branche carrossable, rien n'est dessiné.
- Exact : le nombre de branches, leur côté, leur ordre. Schématique : l'ouverture des angles (26° au moins entre deux voisines),
  car une bretelle à 5° serait illisible — même convention qu'un panneau de présignalisation.
- Affichage : à gauche du panneau de sortie ou des voies, pendant les modes `EXIT`, `FORK` et `COMPLEX`. La vue 3D, quand elle
  est affichée, garde la priorité : le schéma est donc visible vue 3D coupée, en économie d'énergie ou sans OpenGL ES 3.
  Lequel des deux sert le mieux le conducteur reste à trancher sur route.

### 9.3 Voies contradictoires

Si les voies conseillées sont toutes du côté opposé à la manœuvre (sortie à droite, voies conseillées à gauche), la voix ne dit
aucune consigne de voie (`PositioningPolicy.conflicts`). Les voies restent dessinées telles que la carte les donne.

Niveaux de certitude des voies, tels que le code les traite :

| Donnée | Source | Écran | Voix |
|---|---|---|---|
| Voie conseillée | `active` de Valhalla | surlignée | « Restez sur la voie de… » |
| Voie permise, sans voie conseillée | `valid` seul | surlignée | « Restez sur la voie de… » |
| Voies en désaccord avec la manœuvre | les deux | dessinées | rien |
| Côté de la manœuvre, sans voies | type de manœuvre | pastille « Serrez à droite » | « Serrez à droite. » (sorties et bretelles) |
| Rien | — | rien | rien |

Aucune voie n'est estimée ou déduite de la géométrie.

### 9.4 Conformité à l'instance publique

`core/data/network/ValhallaRateLimiter.kt`, `core/routing/RouteResponseCache.kt`. Détail : `VALHALLA-HEBERGEMENT.md` §8.

- Conditions confirmées (fossgis.de, conditions d'utilisation des serveurs OSM) : une requête par seconde au plus, User-Agent
  identifiant l'application, aucune garantie de disponibilité.
- Toutes les requêtes Valhalla passent par un limiteur unique : une seule en cours, 1 s entre deux départs, seulement sur les
  hôtes `openstreetmap.de`. Priorité : recalcul en guidage, puis calcul d'itinéraire, puis le reste.
- Les trois variantes d'itinéraire partent l'une après l'autre : le résultat complet arrive environ 2 s plus tard qu'avant.
- Réponses `/route` identiques réutilisées pendant 60 s. `429` et `503` : pause du limiteur selon `Retry-After`.
- Contact du User-Agent : propriété Gradle `roadly.contact`, par défaut l'adresse du dépôt.

Conséquence à connaître : un recalcul peut attendre jusqu'à 1 s derrière une requête déjà partie.

### 9.5 Laboratoire de scénarios

`core/navigation/src/test/.../lab/` : scénarios déclaratifs joués dans le vrai moteur (`NavigationSession`), avec chronologie
seconde par seconde (position, manœuvre visée, mode, rond-point, voix). Résultats et mesures : §9.6.

### 9.6 Mesures et défauts trouvés par le laboratoire

23 scénarios. Chaque exécution écrit sa chronologie dans `core/navigation/build/guidance-lab/<scénario>.txt`.

Sortie 19 manquée, véhicule qui continue sur l'autoroute, itinéraire réel de Toulouse :

| Vitesse | Recalcul demandé (détecteur) | Seuil de 100 m de la règle générale |
|---|---|---|
| 90 km/h | 17 s, 425 m après la sortie | 25 s, 625 m |
| 110 km/h | 14 s, 427 m | 21 s, 641 m |
| 130 km/h | 12 s, 433 m | 18 s, 650 m |

Le gain est d'environ un tiers, pas davantage : sur cette sortie la bretelle longe la chaussée pendant près de 400 m avant de s'en
écarter. Tant que les deux tracés se confondent à la précision du GPS, aucune méthode fondée sur la position ne peut conclure.

Dernière annonce avant la sortie (« maintenant ») : 3,0 s avant le point de sortie à 90, 110 et 130 km/h (75, 94 et 108 m). C'est
court pour une sortie d'autoroute ; l'anticipation repose sur les annonces précédentes (2 km, puis placement entre 1 km et 450 m).
À revoir sur route.

Trois défauts du moteur révélés par le laboratoire, corrigés dans `NavigationSession` :

1. « Recalcul de l'itinéraire. » redit toutes les 10 s tant que le routeur ne répondait pas. Désormais une fois par épisode : un
   nouvel épisode ne commence qu'une fois le tracé rejoint (écart ≤ 15 m).
2. Les manœuvres du tracé abandonné continuaient d'être annoncées après un écart (« Gardez la gauche vers Labège » à 55 m du
   tracé). Désormais plus aucune annonce de manœuvre hors du tracé.
3. Guidage démarré juste avant une manœuvre : deux paliers franchis à 1 s d'écart disaient deux fois la même phrase. Désormais
   la même phrase n'est jamais redite dans les 10 s.

Non couvert par les données : une bifurcation suivie de près par une sortie (aucune dans les itinéraires enregistrés ; remplacée
par « léger virage puis sortie 31b à 230 m » et « sortie 19 puis bifurcation à 605 m »). Observation sans test : avec un tracé de
recalcul synthétique en demi-tour, le suivi s'est accroché 514 m plus loin et a annoncé un virage aussitôt ; à reproduire avec un
vrai recalcul avant de conclure. (Élucidé en phase 4, §10.1.)

## 10. Phase 4 — stabilisation

### 10.1 Saut de position à la première position d'un tracé

Cause : à la première position sur un tracé (départ ou recalcul), `RouteTracker` cherchait le point le plus proche sur **tout**
le tracé, en écartant les segments à plus de 90° du cap GPS. Si le tracé repasse près de son début dans le sens du véhicule —
demi-tour, boucle, chaussée de retour — c'est ce passage-là qui était retenu.

Reproduit sur le tracé d'essai aller-retour : véhicule au point de départ, cap inverse du premier tronçon → position accrochée
**1 210 m plus loin**, prochaine manœuvre = l'arrivée, et arrivée déclarée aussitôt quand la destination est à moins de 25 m.

Peut-il arriver sur route ? Oui : après un recalcul qui impose un demi-tour, le véhicule roule encore dans l'ancien sens ; et
sur tout itinéraire qui revient près de son point de départ.

Correction (`RouteTracker.acquire`) : si l'**origine** du tracé (ses 60 premiers mètres) est à moins de 40 m de la position, c'est
elle qui est retenue, quel que soit le cap. Sinon la recherche globale garde la main : une reprise en cours de trajet, même en
longeant le début du tracé en sens inverse, reste accrochée là où est le véhicule. Aucune limite de vitesse ou de distance n'est
ajoutée : la navigation sur autoroute n'est pas touchée.

Après la première position, les protections existantes s'appliquent déjà : fenêtre de recherche autour de la dernière position,
saut global de plus de 100 m soumis à confirmation par la position suivante.

Tests : `RouteTrackerAcquisitionTest` (5 cas : demi-tour, boucle à l'arrêt, trajet complet, reprise lointaine, position isolée
sur la chaussée de retour).

### 10.2 Délais d'annonce mesurés

`AnnouncementTimingTableTest` interroge le vrai `AnnouncementPolicy` seconde par seconde, pour 20 phases d'échantillonnage.
Secondes avant le point de manœuvre :

| km/h | Anticipation | Rappel | « Maintenant » | « Maintenant » (m) |
|---|---|---|---|---|
| 30 | 35,0 – 36,0 | 8,6 – 9,6 | 3,8 – 4,8 | 32 – 40 |
| 50 | 42,2 – 43,2 | 9,8 – 10,8 | 3,0 – 4,0 | 42 – 55 |
| 90 | 79,1 – 80,0 | 11,1 – 12,0 | 3,0 – 4,0 | 75 – 99 |
| 110 | 64,5 – 65,4 | 8,8 – 9,8 | 3,0 – 4,0 | 93 – 122 |
| 130 | 54,4 – 55,4 | 7,3 – 8,3 | 3,0 – 4,0 | 109 – 144 |

Lecture :

- « Maintenant » vise 4 s et part entre 3 et 4 s, parce que la position n'arrive qu'une fois par seconde.
- Ce n'est pas l'annonce qui laisse le temps d'agir : c'est le rappel (7 à 12 s) et, pour une sortie, la consigne de placement
  (entre 1 000 et 450 m sur voie rapide, soit 12 à 28 s à 130 km/h).
- Le point le plus serré est le rappel à 130 km/h : 7,3 s, pour un changement de voie qui en demande environ 6.

Décision : **aucun seuil n'est modifié**. Ces chiffres ne comptent ni la latence de la synthèse vocale, ni la durée de la phrase,
ni le Bluetooth, qui ne se mesurent que sur le téléphone (heure de la ligne `RoadlyGuidage` contre heure de `RoadlySpeech`).
Deux réglages à essayer sur route si la voix arrive tard : passer le rappel sur voie rapide de 300 à 400 m, et compter
« maintenant » à partir de la position suivante prévue plutôt que de la position reçue.

### 10.3 Android Auto : état réel

Rectification : le dessin du rond-point de la phase 2 (branches à laisser passer, position du véhicule) **n'a pas été écrasé**.
Il est toujours dans `CarVisuals.kt` (`drawRoundabout`). L'annonce contraire faite en fin de phase 3 venait d'une recherche sur un
mauvais mot-clé. Rien n'était à restaurer ; `feature/car` n'a pas été modifié en phases 3 et 4.

Ce que la voiture montre : texte du décompte, image du rond-point avec branches et position, voies réelles quand Valhalla les
renvoie, panneau de sortie. Ce qu'elle ne montre pas : le schéma de bifurcation, « GPS imprécis ». Non vérifié sur le DHU.

### 10.4 Schéma de bifurcation ou vue 3D

Règle en place : jamais les deux. La vue 3D a la priorité quand elle est prête ; le schéma la remplace vue 3D coupée, en économie
d'énergie, sans OpenGL ES 3, ou tant que la scène n'est pas chargée. Au rond-point, c'est toujours le schéma 2D du mode rond-point.

La comparaison visuelle des deux n'a pas pu être faite : la vue 3D est un rendu OpenGL, impossible à capturer hors appareil.
C'est le scénario 17 de `VALIDATION-TELEPHONE.md`.

### 10.5 Captures plein écran

`NavigationFullScreenScreenshotTest` rend les vraies mises en page portrait et paysage sur un fond uni (la carte MapLibre ne se
rend pas hors appareil, l'image le dit : « fond de test (pas une carte) »). 21 captures dans `docs/captures/guidage-intelligent/full_*.png` :
croisière, intersection à voies, rond-point, sortie d'autoroute, recalcul, GPS imprécis, GPS perdu, excès de vitesse, arrivée —
portrait jour et nuit, paysage nuit pour les trois plus chargées.

### 10.6 Constats des captures plein écran

Ce qui tient : aucune superposition entre le bandeau, la colonne d'actions et le bloc du bas ; la densité renforcée masque bien
le ruban Horizon et les pastilles secondaires ; le paysage tient dans les trois cas (12 dp au plus serré).

Corrigé :

- distance du panneau de sortie coupée (« 350 » sans « m ») quand le schéma de bifurcation est à côté ;
- recalcul, GPS imprécis ou perdu : la distance du bandeau était affichée comme sûre et le ruban Horizon gardait des distances
  exactes. La distance est maintenant estompée et le ruban masqué (`positionUncertain`) ;
- mode rond-point : le rang de sortie apparaissait quatre fois et la rue était tronquée. Le bandeau ne garde que la distance et
  la rue (sur deux lignes), le schéma porte le reste ;
- arrivée : compteur de vitesse avec un panneau de limitation vide, et bouton « replier » sans bandeau. Tous deux retirés hors guidage.

Non corrigé :

- mode jour : texte ambre sur blanc peu lisible (« GPS imprécis », « Zone de danger ») — couleur `warning` du thème jour à foncer ;
- excès de vitesse : pastille de zone, prochaine limitation, compteur, Horizon et panneau s'empilent sur la moitié basse ;
  la zone de danger apparaît deux fois (pastille et Horizon) ;
- bouton « replier » de 44 dp, sous les 48 dp recommandés ;
- libellé du panneau de sortie tronqué (« PROCHAINE S… ») quand le schéma est à côté.

## 11. Phase 5 — validation et stabilisation

Passe du 22 novembre 2026. Aucune fonction ajoutée : défauts reproduits puis corrigés, audits de lecture, protocole terrain
(`PROTOCOLE-TERRAIN-HONOR.md`). Rien n'a été installé, conduit ni vu sur un appareil.

### 11.1 Suivi de position

| Défaut reproduit | Cause | Correction | Test |
|---|---|---|---|
| Arrêté en face de la destination, de l'autre côté d'un terre-plein de 20 ou 30 m : 18 m de dérive GPS suffisaient à déclarer l'arrivée, demi-tour non fait | à l'arrêt le cap GPS ne filtre rien ; la fenêtre de recherche (500 m en avant) contenait la chaussée de retour, plus proche du fix | `RouteTracker.maxForwardAlong` : au pas ou à l'arrêt, un candidat au-delà de l'avance plausible (2 × vitesse × durée + précision + 30 m) est écarté, fenêtre et recherche globale | `RouteTrackerPlausibilityTest` |
| Un fix à 120 m de précision posé près de la destination déclarait l'arrivée 290 m trop tôt | l'arrivée ne regardait pas la précision | l'arrivée exige un fix à 30 m de précision au plus, comme la décision « hors itinéraire » | idem |
| À la sortie d'un tunnel de 60 s, le premier fix marquait le véhicule hors itinéraire pendant un fix | la position gardée en attente de confirmation était mesurée à 1,7 km du fix et passait par la règle des 100 m | une position en attente ne décide plus du hors-itinéraire | idem |

La borne d'avance ne joue pas dès que le véhicule roule (≥ 2,5 m/s) : le cap écarte alors les passages en sens inverse, et un
vrai saut vers l'avant reste possible. Un essai avec la borne à toute vitesse cassait quatre tests de session (positions
téléportées) : abandonné.

Vérifié sans changement : accrochage au départ (phase 4), reprise en cours de trajet, route parallèle, saut latéral d'un fix,
extrapolation plafonnée à 150 m / 10 s.

### 11.2 Recalcul et serveur d'itinéraires

| Défaut (lecture du code, puis test) | Correction |
|---|---|
| Serveur lent : délai de recalcul (10 s) = intervalle minimal (10 s), compté depuis le départ de l'essai → nouvel essai dès le fix suivant, sans fin | intervalle compté depuis la **fin** de l'essai ; 10, 10, 20, 40 puis 60 s après des échecs successifs |
| Échec du recalcul muet ; entre deux essais l'état repassait à `rerouting = false` : téléphone et voiture remontraient l'ancienne manœuvre | « Recalcul impossible pour le moment. Nouvel essai en cours. » dit une fois par écart ; l'état publié reste en recalcul tant qu'un essai est dû |
| Recalcul à l'arrêt (sans cap) : traité comme une recherche ordinaire, derrière les variantes en file, et éligible au cache | `RouteRequest.reroute` : priorité guidage et jamais de cache, avec ou sans cap |
| Une réponse qui s'étire gardait l'unique créneau du limiteur 20 s et plus : le recalcul abandonnait sans être parti | durée totale d'un appel vers l'hôte limité bornée à 15 s |
| `Retry-After` énorme : dépassement de capacité, pause nulle | valeur bornée avant la multiplication |

Non modifié : nombre de requêtes par trajet (3 pour un calcul voiture, 1 + 1 par recalcul), limiteur, cache.

### 11.3 Voix

- Perte du GPS : « Signal GPS perdu. » dit une fois, à la seconde où le badge apparaît (écran éteint, le badge ne se voit pas).
- Moteur vocal mort en cours de trajet : `tts.speak` en erreur abandonne le moteur, l'annonce suivante en crée un neuf. Avant,
  chaque annonce échouait en silence jusqu'à la fin du guidage. **Aucun test** : le moteur Android n'existe pas sur JVM.

### 11.4 Interface

- Ambre du thème clair `0xFFD99A00` → `0xFF9A6200` : contraste sur blanc 2,4:1 → 5,1:1 (texte et icônes seulement, vérifié).
- Bouton « replier le bandeau » : 44 → 48 dp.
- Non traité : empilement des panneaux en excès de vitesse, libellé du panneau de sortie tronqué à côté du schéma. À juger
  sur l'appareil.

### 11.5 Constats d'audit non corrigés

Liste complète et état : `PROTOCOLE-TERRAIN-HONOR.md` § 8. Android Auto (`feature/car`) n'a pas été touché : six défauts relevés
à la lecture y sont décrits pour l'autre chantier.

### 11.6 Vérifications

| Niveau | Résultat |
|---|---|
| Tests JVM | 1054, 0 échec, 0 ignoré |
| Compilation | `:app:assembleDebug` réussie |
| Lint | 0 erreur : `core:navigation`, `core:routing`, `core:designsystem`, `feature:navigation` |
| Captures | 21 plein écran régénérées, une relue (GPS imprécis, jour) |
| Téléphone, route, DHU, mesures | non faits |
