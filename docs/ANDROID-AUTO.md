# Roadly sur Android Auto

Rédigé le 2026-11-05. Module `feature:car`, bibliothèque Car App `androidx.car.app` 1.7.0 (dernière version stable ; 1.8 en rc, 1.9 en alpha), catégorie **navigation**, niveau d'API voiture minimal **1** (voir § 1 bis ; c'était 7 jusqu'au 2026-11-08).

## 1. Ce que fait l'écran de la voiture

Gabarits pour un hôte de niveau 7 ou plus ; en dessous, voir § 1 bis.

| Écran | Gabarit | Contenu |
|---|---|---|
| Accueil | `MapWithContentTemplate` + liste | Carte qui suit le véhicule ; guidage en cours (s'il y en a un), Rechercher, Domicile, Travail, Conduite libre, favoris, récents (dans la limite de lignes de l'hôte) ; distance depuis la position |
| Position refusée | `MessageTemplate` | Bouton « Autoriser » : la demande s'affiche sur le téléphone |
| Recherche | `SearchTemplate` | Même géocodage que le téléphone (BAN + Photon), autour du véhicule, 3 caractères minimum, 400 ms d'attente ; le lieu choisi rejoint les récents |
| Itinéraires | `MapWithContentTemplate` + liste | Mêmes variantes que le téléphone (préférences et profil du véhicule) : durée, distance, heure d'arrivée, péage ; tracés cadrés sur la carte ; une ligne = départ |
| Guidage | `NavigationTemplate` | Manœuvre (icône, distance, consigne, rue), manœuvre suivante, distance et heure d'arrivée, « recalcul » pendant le recalcul ; voix coupée / active, Arrêter ; tracé, portion parcourue atténuée, flèche de manœuvre ; arrivée et échec (Réessayer, Arrêter) |
| Conduite libre | `NavigationTemplate` sans itinéraire | Nom de la route (map matching), « Signal GPS perdu », Arrêter |

Sur la carte, dans la zone que l'hôte laisse libre :

- en bas à gauche, la limitation (panneau rond) et la vitesse, en rouge au-delà de la tolérance réglée ;
- en haut, la pastille « Zone de danger · dans 800 m » puis « encore 1,2 km », avec la limitation de la zone et « fiabilité faible » sous 60 de confiance ;
- sur le tracé, l'étendue des zones de danger : ambre devant, rouge pour celle traversée.

Chaque nouvelle zone déclenche une alerte de l'hôte pendant 8 s, avec « Fausse alerte » (masque la zone) et « OK ». L'aperçu des itinéraires indique le nombre de zones de danger de chaque variante.

Jamais de type ni de position d'équipement (R413-15, voir `docs/RADAR-SOURCES.md`).

**Une seule session** : la voiture pilote la même `NavigationSession`, la même `FreeDriveSession` et les mêmes lieux que le téléphone. Un guidage lancé sur l'un s'affiche sur l'autre, l'arrêt aussi. La voix reste celle du téléphone et sort sur les haut-parleurs de la voiture. L'hôte reçoit `navigationStarted` / `navigationEnded` et un `Trip` (combiné d'instruments). Sa demande d'arrêt (`onStopNavigation`, une autre application de navigation démarre) arrête le guidage ou la conduite libre.

**« Navigue vers… »** (Assistant, autres applications) : `geo:lat,lon` ou `geo:0,0?q=lat,lon(nom)` démarre le guidage ; `geo:0,0?q=adresse` ouvre la recherche préremplie.

## 1 bis. Niveau d'API voiture

L'hôte Android Auto et Roadly négocient un niveau d'API voiture (`CarContext.carAppApiLevel`). Si `androidx.car.app.minCarApiLevel` (manifeste de `feature:car`) dépasse le niveau de l'hôte, Android Auto **masque Roadly sans message**, y compris dans « Personnaliser le lanceur ». Une application multimédia n'est pas concernée : elle ne passe pas par cette négociation. Roadly déclare donc **1** et choisit ses gabarits au niveau négocié (`CarTemplates.level`, testé en JVM).

Niveaux requis (annotations `@RequiresCarApi` de `androidx.car.app:app:1.7.0`, lues avec `javap`) :

| API | Niveau |
|---|---|
| `NavigationTemplate`, `PlaceListNavigationTemplate`, `RoutePreviewNavigationTemplate`, `SearchTemplate`, `MessageTemplate`, `ListTemplate`, `NavigationManager`, `Trip`, `SurfaceCallback` (surface, zone visible) | 1 |
| `NavigationTemplate.setMapActionStrip` / `setPanModeListener`, `ConstraintManager` (limite de lignes), `SurfaceCallback.onScroll` / `onScale` | 2 |
| `PlaceListNavigationTemplate` / `RoutePreviewNavigationTemplate` : `setMapActionStrip` / `setPanModeListener` ; `Action.Builder.setFlags` | 4 |
| `Header` (et `setHeader` des gabarits de navigation), `MapController`, `MapTemplate`, `AppManager.showAlert` (`Alert`) | 5 |
| `MapWithContentTemplate`, `ListTemplate.setHeader`, `MessageTemplate.setHeader` | 7 |

Ce que fait chaque écran selon le niveau (rien n'est simulé : ce qu'un niveau ne sait pas afficher est absent) :

| Écran | Niveau ≥ 7 | Niveaux 1 à 6 |
|---|---|---|
| Accueil | inchangé (`MapWithContentTemplate` + liste) | `PlaceListNavigationTemplate` : même liste, même carte, Rechercher dans le bandeau d'actions ; bandeau de carte dès 4, `Header` dès 5 (sinon titre + icône de l'appli) ; 6 lignes au niveau 1 (pas de limite fournie par l'hôte) |
| Itinéraires | inchangé (une ligne = départ) | `RoutePreviewNavigationTemplate` : une ligne = choisir la variante (surlignée sur la carte), bouton « Démarrer » ; bandeau de carte dès 4 |
| Recherche | `SearchTemplate` | `SearchTemplate` (identique) |
| Guidage, conduite libre | `NavigationTemplate` | `NavigationTemplate` ; bandeau de carte (défilement, zoom, recentrage) dès 2 ; au niveau 1 la carte suit le véhicule, sans boutons |
| Position refusée, erreur d'itinéraire | `MessageTemplate` + `Header` | `MessageTemplate` + titre et action d'en-tête |
| Alerte de zone de danger | `Alert` de l'hôte | dès 5 ; en dessous, seulement la pastille sur la carte |

La carte (`SurfaceMapHost`, via `CarMapRenderer`) est la même à tous les niveaux : la surface est fournie par l'hôte pour tous les gabarits de navigation (déjà le cas du guidage sous `NavigationTemplate`).

## 2. Architecture

```
core:map     SurfaceMapHost : écran virtuel (VirtualDisplay) sur la surface de l'hôte + Presentation qui héberge une MapView MapLibre
             et un calque de vues ; même MapController que le téléphone (style, tracé, position, caméra) ; SurfacePadding
             place la cible dans la zone libre
core:location setCarDisplayActive : la carte de la voiture garde le GPS (≥ 1 Hz) même si le téléphone est verrouillé ou sur un autre écran
feature:car  RoadlyCarAppService (Hilt), RoadlyCarSession, CarMapRenderer (SurfaceCallback : état rejoué à chaque nouvelle surface,
             défilement, zoom, recentrage), écrans, CarHudView, CarModels (correspondances pures, testées), CarIcons
             (icônes Material du téléphone dessinées en bitmaps)
```

Le rendu suit le schéma documenté par Google (« Draw maps ») et par les SDK de carte du marché : `VirtualDisplay` créé sur la surface, `Presentation`, puis une vue de carte ordinaire. MapLibre n'a pas d'extension Android Auto officielle.

## 3. Tester

Aucun de ces essais n'a été fait le 2026-11-05 : aucun téléphone n'était branché et l'unité principale de bureau (DHU) n'était pas installée.

**Unité principale de bureau (sans voiture)** :

1. Sur le téléphone, ouvrez l'application Android Auto (Réglages › Appareils connectés › Android Auto). Touchez « Version » dix fois pour activer le mode développeur. Dans le menu ⋮ › Paramètres pour les développeurs, cochez **Sources inconnues** : Roadly n'est pas installé depuis le Play Store. Lancez ensuite « Démarrer le serveur de l'unité principale ».
2. Sur le PC, installez la DHU : `sdkmanager "extras;google;auto"`.
3. Lancez :
   ```
   adb forward tcp:5277 tcp:5277
   %LOCALAPPDATA%\Android\Sdk\extras\google\auto\desktop-head-unit.exe
   ```

Les builds débogables acceptent tout hôte. Les builds de version n'acceptent que les hôtes de la liste fournie par la bibliothèque (Android Auto, Android Automotive).

**En voiture** : même réglage « Sources inconnues ». Sans lui, Android Auto masque les applications qui ne viennent pas du Play Store.

## 3 bis. « Roadly n'apparaît pas dans Android Auto »

Réglages › **Android Auto** (sur le téléphone) affiche le diagnostic :

| Vérification | Comment Roadly la fait |
|---|---|
| La version installée contient Android Auto | Recherche du service `androidx.car.app.CarAppService` dans le paquet de Roadly (présent à partir de la release 0.1.174) |
| L'application Android Auto est installée | Paquet `com.google.android.projection.gearhead` (déclaré dans `<queries>`) |
| Installation hors Play Store | Paquet installateur ≠ `com.android.vending` → « Sources inconnues » obligatoire |
| Niveau d'API voiture | Non vérifiable depuis le téléphone. Jusqu'au 2026-11-08, Roadly exigeait le niveau 7 : un hôte qui négocie moins le masquait du lanceur sans message (cas d'une vraie voiture où Roadly manquait alors qu'il apparaissait sur la DHU 2.1). Les versions suivantes exigent le niveau 1 (§ 1 bis) |

Roadly ne peut pas lire le réglage « Sources inconnues » d'Android Auto : il affiche les 4 étapes (Version × 10, Paramètres pour les développeurs, Sources inconnues, reconnexion / Personnaliser le lanceur) et un bouton qui ouvre Android Auto (sinon sa fiche dans les réglages Android).

Constat du 2026-11-05 : les releases 0.1.174 à 0.1.177 (avec Android Auto) sont publiées et signées, mais GitHub indique 0 téléchargement de leurs APK ; la version installée sur le téléphone est donc probablement antérieure. Réglages › Mises à jour donne la version installée.

## 3 ter. Passe sur le HONOR Magic 8 Pro (2026-11-05, DHU 2.1)

Installée : 0.1.178 (par la mise à jour intégrée), Android Auto 17.9, mode développeur et « Sources inconnues » actifs. Roadly **apparaît** dans le lanceur et s'ouvre (accueil, carte, liste).

| Constat | Cause | Correction |
|---|---|---|
| Carte figée sur le planisphère, bouton de recentrage sans effet | Téléphone en arrière-plan : la demande de position de Roadly est « inactive » (pas de service de premier plan hors guidage) ; de plus la localisation du téléphone était coupée | `CarScreenPresence` : la session voiture démarre `NavigationService` (type `location`) dès `onStart` et le garde tant que l'écran voiture est affiché (notification « Roadly sur Android Auto »). Démarrage accepté par Android : processus `BTOP` (lié par l'hôte), capacité position accordée. Vérifié : la carte suit la position, téléphone sur l'écran d'accueil |
| Aucune explication quand la localisation est coupée | — | Ligne « Localisation du téléphone désactivée » en tête de l'accueil voiture (`CarLocationSwitch`, mise à jour à chaud) ; carte centrée sur le domicile (sinon le travail) tant qu'aucune position n'arrive |
| « Ouvrir Android Auto » ouvrait la fiche Infos de l'appli | Android Auto n'a pas d'activité de lancement | Intent `APPLICATION_PREFERENCES` sur le paquet d'Android Auto : ouvre ses réglages (vérifié) |
| DHU 2.0 : « Waiting for phone », lien coupé après TLS | DHU 2.0 (2022) trop ancien pour Android Auto 17.9 | DHU 2.1 : `sdkmanager --channel=3 "extras;google;auto"` |
| Carte saturée d'icônes « P » de parking | Style commun : points d'intérêt dès le zoom 14 | Carte voiture en détail réduit (`MapDetail.REDUCED`) : ni points d'intérêt, ni flèches de sens unique, ni noms de petites rues |
| Aucune marque sur l'arrivée (aperçu, guidage) | — | Repère au dernier point du tracé actif |
| Attribution « i » sous le compteur de vitesse | Même coin (bas gauche de la zone libre) | `SurfaceMapHost.bottomStartReservePx` : l'attribution passe au-dessus du compteur |
| Recherche vide : « Aucun élément » | — | Lieux récents tant que moins de 3 caractères, sinon consigne |
| « 0 zone de danger » | — | « Aucune zone de danger » |
| Petits blocs blancs carrés sur la carte | Artefacts de décodage vidéo de la DHU (macroblocs) : absents du téléphone, identiques en SurfaceView et TextureView | Aucune (à confirmer sur un vrai écran de voiture) |

Parcours vérifié sur la DHU (2026-11-05, soir), téléphone **verrouillé** : accueil, recherche (clavier, résultats), aperçu des deux variantes, guidage (manœuvre, suivante, arrivée, Arrêter), conduite libre, retour à l'accueil. Service de premier plan démarré et position active téléphone verrouillé. Non vérifié : zoom au pincement et défilement (les taps de la DHU sont décalés près du bord droit), alertes de zone, recalcul, vraie voiture. Le classement des résultats de recherche (« gare » : Teulat avant Toulouse-Matabiau) vient du géocodeur commun au téléphone.

Pour la DHU : Android Auto › ⋮ › « Démarrer serveur unité princ. », `adb forward tcp:5277 tcp:5277`, puis `desktop-head-unit.exe`. À la première connexion, Android Auto demande sur le téléphone d'accepter la « voiture ».

## 4. Vérifié / non vérifié

| Élément | Statut |
|---|---|
| Compilation, fusion du manifeste, APK | Fait |
| Lint `feature:car` | 0 erreur. 4 avertissements : ressource privée `hosts_allowlist_sample` (usage documenté par Google), service exporté (obligatoire pour l'hôte), allocation dans `onDraw`, extensions KTX |
| Tests JVM | Correspondance des manœuvres (circulation à droite, ronds-points), distances, indications (zone traversée avant la suivante, tolérance, GPS perdu), intentions `geo:`, marges de caméra |
| Affichage réel sur la DHU | Fait : accueil, recherche, aperçu, guidage, conduite libre, téléphone verrouillé ; en voiture : **non vérifié** |
| Sens du défilement (`onScroll`) et zoom au pincement | Convention `GestureDetector` supposée, **à vérifier sur la DHU** |
| Rendu MapLibre dans `Presentation` (SurfaceView) | Fait : la carte s'affiche, détail réduit |
| Démarrage du service de premier plan depuis la voiture | Fait, téléphone déverrouillé puis verrouillé |

## 5. Limites et suite

- **Pas de publication Play Store** : la catégorie navigation exige la revue de qualité de Google ; Roadly est distribué par GitHub.
- **Pas d'images de voies** : les voies de Valhalla sont rares sur l'instance publique (voir `FEATURES.md`).
- **Pas de combiné d'instruments dédié** : il n'y a pas de session `DISPLAY_TYPE_CLUSTER`, seulement le `Trip` envoyé à l'hôte.
- **Suite** :
  - P0 : passe DHU puis voiture (affichage, défilement, verrouillage du téléphone, appel entrant).
  - P1 : alerte de l'hôte (`Alert`) à l'entrée d'une zone de danger ; arrêts et alternatives depuis la voiture ; rappel de péage à l'arrivée.

## Photos aériennes

Le bouton à côté de « Rechercher », sur l'accueil de la voiture, bascule la carte entre le plan et les photos aériennes de l'IGN (même réglage que sur le téléphone, Réglages › Navigation). Les photos sont assombries quand l'écran de la voiture passe en mode nuit. Hors de France, le plan reste affiché.
