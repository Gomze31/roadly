# Roadly sur Android Auto

Rédigé le 2026-11-05. Module `feature:car`, bibliothèque Car App `androidx.car.app` 1.7.0 (dernière version stable ; 1.8 en rc, 1.9 en alpha), catégorie **navigation**, niveau d'API voiture minimal **7**.

## 1. Ce que fait l'écran de la voiture

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

Pour la DHU : Android Auto › ⋮ › « Démarrer serveur unité princ. », `adb forward tcp:5277 tcp:5277`, puis `desktop-head-unit.exe`. À la première connexion, Android Auto demande sur le téléphone d'accepter la « voiture ».

## 4. Vérifié / non vérifié

| Élément | Statut |
|---|---|
| Compilation, fusion du manifeste, APK | Fait |
| Lint `feature:car` | 0 erreur. 4 avertissements : ressource privée `hosts_allowlist_sample` (usage documenté par Google), service exporté (obligatoire pour l'hôte), allocation dans `onDraw`, extensions KTX |
| Tests JVM | Correspondance des manœuvres (circulation à droite, ronds-points), distances, indications (zone traversée avant la suivante, tolérance, GPS perdu), intentions `geo:`, marges de caméra |
| Affichage réel sur la DHU | Fait (accueil, carte, suivi de position) ; recherche, itinéraire, guidage et conduite libre **non parcourus** ; en voiture : **non vérifié** |
| Sens du défilement (`onScroll`) et zoom au pincement | Convention `GestureDetector` supposée, **à vérifier sur la DHU** |
| Rendu MapLibre dans `Presentation` (SurfaceView) | Fait : la carte s'affiche. À revoir : nombreuses icônes « P » de parking dès le zoom 14 et petits points blancs (style commun) |
| Démarrage du service de premier plan depuis la voiture | Fait, téléphone déverrouillé sur l'écran d'accueil ; téléphone verrouillé : **à vérifier** |

## 5. Limites et suite

- **Pas de publication Play Store** : la catégorie navigation exige la revue de qualité de Google ; Roadly est distribué par GitHub.
- **Pas d'images de voies** : les voies de Valhalla sont rares sur l'instance publique (voir `FEATURES.md`).
- **Pas de combiné d'instruments dédié** : il n'y a pas de session `DISPLAY_TYPE_CLUSTER`, seulement le `Trip` envoyé à l'hôte.
- **Suite** :
  - P0 : passe DHU puis voiture (affichage, défilement, verrouillage du téléphone, appel entrant).
  - P1 : alerte de l'hôte (`Alert`) à l'entrée d'une zone de danger ; arrêts et alternatives depuis la voiture ; rappel de péage à l'arrivée.
