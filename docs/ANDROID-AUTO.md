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
- en haut, la pastille « Zone de danger · dans 800 m » puis « encore 1,2 km », avec la limitation de la zone et « fiabilité faible » sous 60 de confiance.

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

## 4. Vérifié / non vérifié

| Élément | Statut |
|---|---|
| Compilation, fusion du manifeste, APK | Fait |
| Lint `feature:car` | 0 erreur. 4 avertissements : ressource privée `hosts_allowlist_sample` (usage documenté par Google), service exporté (obligatoire pour l'hôte), allocation dans `onDraw`, extensions KTX |
| Tests JVM | Correspondance des manœuvres (circulation à droite, ronds-points), distances, indications (zone traversée avant la suivante, tolérance, GPS perdu), intentions `geo:`, marges de caméra |
| Affichage réel sur la DHU ou en voiture | **Non vérifié** |
| Sens du défilement (`onScroll`) et zoom au pincement | Convention `GestureDetector` supposée, **à vérifier sur la DHU** |
| Rendu MapLibre dans `Presentation` (SurfaceView) | **À vérifier** ; repli possible : `textureMode(true)` dans `SurfaceMapHost` |
| Démarrage du service de premier plan depuis la voiture, téléphone verrouillé | **À vérifier** ; un refus du système n'arrête pas le guidage (journalisé) |

## 5. Limites et suite

- **Pas de publication Play Store** : la catégorie navigation exige la revue de qualité de Google ; Roadly est distribué par GitHub.
- **Pas d'images de voies** : les voies de Valhalla sont rares sur l'instance publique (voir `FEATURES.md`).
- **Pas de combiné d'instruments dédié** : il n'y a pas de session `DISPLAY_TYPE_CLUSTER`, seulement le `Trip` envoyé à l'hôte.
- **Suite** :
  - P0 : passe DHU puis voiture (affichage, défilement, verrouillage du téléphone, appel entrant).
  - P1 : alerte de l'hôte (`Alert`) à l'entrée d'une zone de danger ; arrêts et alternatives depuis la voiture ; rappel de péage à l'arrivée.
