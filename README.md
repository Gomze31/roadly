# Roadly

Roadly est une application GPS Android native, écrite en Kotlin avec Jetpack Compose. Elle affiche une carte, suit la position de l'utilisateur, recherche des adresses et des lieux, et compare trois itinéraires en voiture (rapide, court, sans péage). La version actuelle est la **v0.1.0** : elle livre la Phase 1 de la feuille de route et le guidage pas à pas de la Phase 2 (testé en JVM et vérifié à l'arrêt sur appareil, voir « Vérification sur appareil »). Pas de trafic.

Le nom « Roadly » est provisoire. Tous les fournisseurs de données de la v1 sont gratuits et sans clé d'API.

Objectif du projet : une alternative Android à parité fonctionnelle et visuelle avec Sillaxe (iPhone). L'état de chaque fonction et de chaque écran par rapport à cette référence est tenu dans `SILLAXE_PARITY.md` ; le design system dans `docs/DESIGN-SYSTEM.md`.

Captures d'écran : à ajouter.

## Prérequis

- JDK 17 ou plus récent (le projet a été compilé avec JDK 21).
- Android SDK avec la plateforme 36 (`compileSdk` et `targetSdk` = 36).
- Un appareil ou un émulateur Android 10 ou plus (`minSdk` = 29).
- Un fichier `local.properties` à la racine contenant `sdk.dir` (voir « Configuration »).

Versions utilisées : Kotlin 2.2.10, Android Gradle Plugin 8.13.2, Gradle 8.14.3 (wrapper versionné), Compose BOM 2026.05.00 avec Material 3, Navigation Compose 2.9.4 (routes typées), Hilt 2.58, Room 2.8.5 (KSP), DataStore 1.1.7, OkHttp 4.12, kotlinx.serialization 1.8.1, MapLibre Android 11.13.5, play-services-location 21.3.0. Hilt est volontairement resté en 2.58 : la série 2.60.x exige AGP 9.

## Compilation et tests

Depuis la racine du dépôt (PowerShell sous Windows) :

```powershell
# APK de debug -> app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:assembleDebug

# Tests unitaires (les deux modules JVM purs ne sont pas couverts par testDebugUnitTest)
.\gradlew.bat testDebugUnitTest :core:model:test :core:common:test

# Tests instrumentés (DAO Room, barre de recherche Compose) : appareil ou émulateur requis
.\gradlew.bat connectedDebugAndroidTest
```

État de la vérification : **224 tests unitaires passent** (`testDebugUnitTest :core:model:test :core:common:test`, 0 échec). Les **3 tests instrumentés passent** sur le HONOR Magic 8 Pro (Android 17) : `FavoriteDaoTest` (2) et `GlassSearchBarTest` (1). Deux correctifs de configuration ont été nécessaires : `core:navigation` déclarait `AndroidJUnitRunner` sans la dépendance `androidx.test:runner` (plantage du processus de test), et `core:designsystem` héritait d'`espresso-core 3.5.0` via Compose, dont `InputManager.getInstance` n'existe plus sur Android 16+ (`espresso-core 3.7.0` forcé).

L'APK de debug pèse environ 116 Mo (121 596 756 octets), car MapLibre embarque ses bibliothèques natives pour toutes les architectures. Un App Bundle ou des découpages par ABI réduiraient fortement cette taille ; ce n'est pas fait.

### Vérification sur appareil (faite le 2026-10-30, HONOR Magic 8 Pro, Android 17)

Vérifié à l'arrêt, position réelle à Toulouse, destination Place du Capitole (6,5 km) :

- Recherche BAN/Photon, fiche lieu, trois itinéraires Valhalla, « Démarrer ».
- `NavigationService` : `isForeground=true`, `foregroundId=4201`, type `0x00000008` (location) ; notification sur le canal `roadly.navigation` (importance LOW, `ONGOING_EVENT|NO_CLEAR`, action « Arrêter », texte « Serrez à gauche dans … · 20 m · arrivée 23:59 »).
- Un appui sur la notification ramène à l'écran de guidage ; « Arrêter » depuis la notification arrête le service et retire la notification.
- Voix : moteur `com.google.android.tts` connecté, focus audio `USAGE_ASSISTANCE_NAVIGATION_GUIDANCE` demandé (`GAIN_TRANSIENT_MAY_DUCK`) puis rendu après l'annonce.
- `FLAG_KEEP_SCREEN_ON` posé pendant le guidage.
- `POST_NOTIFICATIONS` refusé : le dialogue système s'affiche, le guidage démarre quand même, aucun plantage.
- Aucun `FATAL EXCEPTION` dans logcat sur tout le parcours.

**Non vérifié** (demande un trajet réel ou une position simulée) : recalcul hors itinéraire, paliers d'annonces en roulant, arrivée, badge « GPS perdu ».

Défauts d'interface relevés, non corrigés : le tracé est partiellement masqué par les cartes d'itinéraires (`fitBounds` sans marge basse) ; le bouton « Définir comme travail » de la fiche lieu déborde sur une colonne étroite.

## Structure des modules

```
app                    Application Hilt, MainActivity, NavHost Compose, thème, gestion des permissions
core:model             Data classes pures (sans dépendance Android) : GeoPoint, BoundingBox, Place, PlaceKind,
                       Route, RouteLeg, Maneuver, ManeuverType, RouteCosting, VehicleProfile, FuelType, CostEstimate
core:common            Outcome<T> / AppError, DispatcherProvider, formatters (distance, durée, heure d'arrivée, euros),
                       ConnectivityObserver, SunTimes (calcul lever/coucher local)
core:designsystem      Tokens (couleurs, typographie Inter, espacements, formes), thème clair/sombre/auto,
                       composants Glass*, icônes de manœuvre vectorielles, CompositionLocal DrivingMode
core:data              Room (favoris, récents, profil véhicule, itinéraires sauvegardés), DataStore (préférences),
                       client HTTP OkHttp + kotlinx.serialization, ProviderConfig
core:location          LocationRepository (Flow<LocationFix>), LocationPermissionState, LocationMode (dont GUIDANCE, 1 Hz), impl Fused
core:map               Composable RoadlyMap (MapLibre), MapCameraController, couches position / marqueurs / tracés,
                       modes de caméra (libre, centré, suivi avec rotation)
core:geocoding         GeocodingProvider, BanGeocodingProvider, PhotonGeocodingProvider, CoordinateParser,
                       CompositeGeocoder (BAN puis Photon, fusion et déduplication)
core:routing           RoutingProvider, ValhallaRoutingProvider (requête, décodage polyline précision 6, mapping manœuvres),
                       RouteAlternativesUseCase (FASTEST, SHORTEST, NO_TOLL), FuelCostCalculator
core:navigation        RouteTracker, AnnouncementPolicy, NavigationSession, SpeechService (TTS fr-FR), SpeedLimitProvider,
                       NavigationService (service de premier plan)
feature:home           Écran d'accueil : carte, barre de recherche flottante, badge vitesse, recentrage, panneau inférieur
feature:search         Recherche plein écran, suggestions, récents, favoris, fiche lieu (bottom sheet)
feature:route          Comparaison d'itinéraires, tracés multiples, fiche itinéraire, estimation de coût
feature:settings       Profil véhicule, thème, voix et sons, alertes de vitesse, préférences d'itinéraire, données locales,
                       confidentialité, à propos et licences
feature:navigation     Écran de guidage en mode conduite : bandeau de manœuvre, voies, « à venir », ETA, vitesse, arrivée
```

Règles de dépendance : un module `feature:*` ne dépend que de modules `core:*`, jamais d'un autre `feature:*`. `core:model` et `core:common` sont des modules JVM purs, sans dépendance Android. `app` est le seul module qui connaît tous les features. Chaque fournisseur de données est derrière une interface Kotlin d'un module `core:*`, liée par Hilt, donc remplaçable sans toucher aux features.

## Fournisseurs de données et limites

| Besoin | Fournisseur v1 | Licence / limites | Remplaçable par |
|---|---|---|---|
| Tuiles vectorielles | OpenFreeMap, styles `positron` (clair) et `dark` (sombre) sous `https://tiles.openfreemap.org/styles/` | Gratuit, sans clé, usage commercial autorisé, pas de quota publié | MapTiler, Protomaps auto-hébergé |
| Rendu carte | MapLibre Android 11.13.5 | BSD-2 | — |
| Géocodage adresses France | Base Adresse Nationale : `https://api-adresse.data.gouv.fr/search/` et `/reverse/` | Licence Ouverte 2.0, environ 50 req/s/IP, sans clé | Nominatim, Pelias |
| Géocodage POI, villes, Europe | Photon : `https://photon.komoot.io/api/` et `/reverse` | Fair-use, aucune garantie de production | Photon auto-hébergé |
| Coordonnées saisies | Parseur local (décimal `48.85, 2.35`, DMS `48°51'N 2°21'E`) | — | — |
| Routage | Valhalla FOSSGIS : `https://valhalla1.openstreetmap.de/route` | Fair-use, développement uniquement ; en production, Valhalla Docker sur VPS | GraphHopper, OSRM |
| Position | `play-services-location` (FusedLocationProviderClient) | — | — |
| Limitation de vitesse | Valhalla `trace_attributes`, **uniquement** sur une instance auto-hébergée (jamais sur FOSSGIS : « — ») | Au plus une requête toutes les 5 s pendant le guidage | — |

**Avertissement.** Le serveur Valhalla de FOSSGIS et le serveur Photon de Komoot sont des services publics offerts à titre de fair-use. Ils n'offrent aucune garantie de disponibilité ni de débit et ne conviennent pas à une application distribuée à grande échelle. Pour une mise en production, il faut auto-héberger Valhalla (voir `docs/API-PROVIDERS.md`) et idéalement Photon. Roadly limite sa charge : en-tête `User-Agent: Roadly/<version> (+https://roadly.example)`, cache HTTP OkHttp de 10 Mo, debounce de 300 ms sur la recherche, une requête en vol par type.

Roadly n'invente aucune donnée GPS, routière ou tarifaire. Quand un fournisseur ne répond pas, l'interface affiche un état d'erreur explicite.

Détails par fournisseur : [`docs/API-PROVIDERS.md`](docs/API-PROVIDERS.md).

## Configuration

- `local.properties` (à la racine, hors suivi de version) : contient `sdk.dir=<chemin du SDK Android>`. Aucune clé d'API n'est nécessaire.
- Les URL des fournisseurs ont des valeurs par défaut dans `core/data/src/main/java/fr/roadly/core/data/config/ProviderConfig.kt`. Elles sont modifiables à l'exécution dans **Réglages › Fournisseurs** (stockées dans DataStore) : style de carte clair, style sombre, BAN, Photon, Valhalla. C'est ce qui permet de pointer l'application vers un Valhalla auto-hébergé sans recompiler. Utiliser une URL `https://` (reverse proxy avec TLS, par exemple Caddy) : une URL `http://` en clair est refusée par la politique réseau d'Android (cleartext).
- Si une clé devenait nécessaire plus tard, elle serait lue depuis `local.properties` à la compilation, jamais écrite dans le code.

## Ce qui fonctionne (Phase 1)

- Carte vectorielle clair/sombre, thème automatique selon le lever et le coucher du soleil calculés localement.
- Position de l'utilisateur et cap, badge de vitesse (la limitation de vitesse affiche « — » sans Valhalla auto-hébergé), bouton de recentrage à trois états (libre, centré, suivi).
- Adresse courante obtenue par géocodage inverse.
- Recherche : adresses (BAN), lieux et POI (Photon), coordonnées GPS en décimal ou DMS ; récents (50 maximum) ; favoris ; domicile et travail ; fiche lieu avec distance.
- Appui long sur la carte pour choisir une destination.
- Comparaison de trois itinéraires (rapide, court, sans péage) tracés sur la carte, avec heure d'arrivée.
- Heure de départ programmable (elle décale uniquement l'heure d'arrivée affichée ; il n'y a pas de prévision de trafic).
- Coût carburant, uniquement si un profil véhicule est saisi (type, carburant, consommation, prix du carburant saisi par l'utilisateur). Aucun prix par défaut n'existe.
- Mode hors ligne détecté : bandeau d'information, recherche limitée aux favoris et récents.
- Permission de localisation expliquée à l'utilisateur, avec repli vers les réglages système si elle est refusée définitivement.
- Réglages : véhicule, thème, voix et sons, alertes de vitesse, préférences d'itinéraire, données locales, fournisseurs, à propos et licences.

## Guidage (Phase 2)

- « Démarrer » sur la comparaison d'itinéraires lance le guidage : suivi GPS à 1 Hz, bandeau de manœuvre (icône, distance, instruction, rue, sortie de rond-point), voies si Valhalla les fournit, bandeau « à venir » (3 manœuvres), heure d'arrivée et restant, vitesse et limitation.
- Voix française (synthèse vocale Android, sans serveur) : annonces anticipées selon la vitesse (trois paliers par classe de vitesse), anti-répétition, bouton Silence.
- Recalcul automatique hors itinéraire (au plus un toutes les 10 s), extrapolation en tunnel (plafonnée à 150 m / 10 s), badge « GPS perdu ».
- Service de premier plan avec notification persistante (instruction, distance, arrivée, « Arrêter ») ; arrêt automatique à l'arrivée.
- Réglages : Voix et sons, Alertes de vitesse (tolérance 0/5/10 km/h), Préférences d'itinéraire (éviter péages, autoroutes, ferries).

Limites : pas de trafic ; limitation de vitesse (et donc alerte de dépassement) seulement avec un Valhalla auto-hébergé ; les voies ne s'affichent que si la réponse Valhalla en contient (aucune dans les réponses FOSSGIS observées) ; changer de destination est possible, ajouter un arrêt pas encore (Phase 4) ; voix muette si aucune voix française n'est installée sur l'appareil.

## Permissions Android

| Permission | Pourquoi | Quand |
|---|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Position sur la carte et guidage | Au premier affichage de la carte |
| `POST_NOTIFICATIONS` (Android 13+) | Notification persistante du guidage | Au premier « Démarrer » ; un refus masque seulement la notification |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION` | Continuer le guidage écran éteint ou application en arrière-plan | Accordées à l'installation ; service actif uniquement pendant un guidage |

Roadly ne demande pas `ACCESS_BACKGROUND_LOCATION` : aucune localisation en arrière-plan hors guidage actif.

## Limitation de vitesse : Valhalla auto-hébergé

L'instance publique FOSSGIS n'est jamais interrogée pour la limitation (fair-use). Pour l'activer :

```powershell
mkdir valhalla_tiles
docker run -dt --name valhalla -p 8002:8002 -v "${PWD}\valhalla_tiles:/custom_files" -e tile_urls=https://download.geofabrik.de/europe/france/ile-de-france-latest.osm.pbf ghcr.io/valhalla/valhalla-scripted:latest
```

La construction des tuiles prend du temps (commencer par une région ; `europe/france-latest.osm.pbf` pour tout le pays). Exposer ensuite le port 8002 en **HTTPS** (reverse proxy, par exemple `caddy reverse-proxy --from valhalla.exemple.fr --to localhost:8002`) et saisir `https://valhalla.exemple.fr` dans **Réglages › Fournisseurs › Itinéraires (Valhalla)**. Vérification de `/trace_attributes` :

```powershell
Invoke-RestMethod -Method Post -Uri "https://valhalla.exemple.fr/trace_attributes" -ContentType "application/json" -Body '{"shape":[{"lat":48.8566,"lon":2.3522},{"lat":48.8570,"lon":2.3530}],"costing":"auto","shape_match":"map_snap","filters":{"attributes":["edge.speed_limit"],"action":"include"}}'
```

La réponse contient `edges[].speed_limit` (km/h) quand OpenStreetMap renseigne `maxspeed` ; sinon la limitation reste « — ». La commande Docker est indicative (image et variables à vérifier dans la documentation de l'image) et n'a pas été exécutée dans le cadre de cette phase.

## Marqué « Bientôt » ou désactivé

- Pastilles Stations, Bornes, Parkings, Incidents de l'écran d'accueil.
- Péages : le montant affiche « Phase 3 ».
- Trafic : aucun, ni dans le calcul d'itinéraire ni pendant le guidage.
- Limitation de vitesse : « — » tant qu'aucun Valhalla auto-hébergé n'est configuré (voir « Limitation de vitesse : Valhalla auto-hébergé »).

## Confidentialité

La position ne quitte l'appareil que vers les fournisseurs listés ci-dessus, et seulement pour les fonctions qui en ont besoin : requêtes de géocodage et de routage, ainsi que les tuiles de carte demandées à OpenFreeMap. Rien n'est stocké sur un serveur appartenant à Roadly, et l'application n'embarque aucun outil d'analyse. Favoris, récents et profil véhicule restent dans la base locale de l'appareil. La sauvegarde Android est désactivée (`allowBackup=false`) : favoris et historique ne quittent pas l'appareil.

## Écarts assumés par rapport à la spec

La spec est [`docs/superpowers/specs/2026-10-30-roadly-v1-design.md`](docs/superpowers/specs/2026-10-30-roadly-v1-design.md). Écarts de la v1 livrée :

- JUnit 4 au lieu de JUnit 5 (avec Turbine et MockWebServer).
- OkHttp utilisé directement, sans Retrofit.
- Type `Outcome<T>` (dans `core:common`) à la place de `kotlin.Result`.
- Hilt 2.58 : la série 2.60.x exige AGP 9.
- `TimePickerDialog` remplacé par un `AlertDialog` contenant un `TimePicker`.
- Les arguments de navigation sont lus depuis `SavedStateHandle` par clé, et non via `toRoute`, pour que les ViewModels restent testables sur JVM.
- Pas de repli `LocationManager` : sans services Google Play, aucune position (prévu Phase 5).
- Styles de carte `positron` et `dark` d'OpenFreeMap, au lieu du style `liberty` et du style sombre dérivé embarqué envisagés dans la spec.

## Licences

- Données cartographiques : contributeurs d'OpenStreetMap, licence ODbL.
- Tuiles : OpenFreeMap.
- Rendu : MapLibre, BSD-2.
- Adresses françaises : Base Adresse Nationale, Licence Ouverte 2.0.
- Photon (Komoot) et Valhalla (FOSSGIS) : services publics, voir leurs conditions d'usage respectives.
- Police Inter : SIL Open Font License 1.1, fichier `core/designsystem/LICENSE-Inter-OFL.txt`.

L'écran Réglages › À propos reproduit ces mentions. Le projet lui-même n'a pas encore de licence déclarée.

## Documentation complémentaire

- [`docs/API-PROVIDERS.md`](docs/API-PROVIDERS.md) : chaque API, ses endpoints, ses limites, le passage en production.
- [`docs/ROADMAP.md`](docs/ROADMAP.md) : Phase 2 livrée, Phases 3 à 5.
