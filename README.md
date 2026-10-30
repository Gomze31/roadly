# Roadly

Roadly est une application GPS Android native, écrite en Kotlin avec Jetpack Compose. Elle affiche une carte, suit la position de l'utilisateur, recherche des adresses et des lieux, et compare trois itinéraires en voiture (rapide, court, sans péage). La version actuelle est la **v0.1.0** : elle livre la Phase 1 de la feuille de route et la partie « comparaison d'itinéraires » qui ouvre la Phase 2. Le guidage pas à pas n'existe pas encore.

Le nom « Roadly » est provisoire. Tous les fournisseurs de données de la v1 sont gratuits et sans clé d'API.

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

État de la vérification à la livraison : 74 tests unitaires passent (model 2, common 12, data 6, geocoding 17, routing 15, location 1, map 1, search 6, home 3, route 5, settings 2, app 4). Les tests instrumentés existent mais **n'ont pas été exécutés** : aucun appareil n'était connecté pendant la construction. La vérification manuelle sur appareil reste à faire.

L'APK de debug pèse environ 115 Mo, car MapLibre embarque ses bibliothèques natives pour toutes les architectures. Un App Bundle ou des découpages par ABI réduiraient fortement cette taille ; ce n'est pas fait en v1.

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
core:location          LocationRepository (Flow<LocationFix>), LocationPermissionState, impl Fused
core:map               Composable RoadlyMap (MapLibre), MapCameraController, couches position / marqueurs / tracés,
                       modes de caméra (libre, centré, suivi avec rotation)
core:geocoding         GeocodingProvider, BanGeocodingProvider, PhotonGeocodingProvider, CoordinateParser,
                       CompositeGeocoder (BAN puis Photon, fusion et déduplication)
core:routing           RoutingProvider, ValhallaRoutingProvider (requête, décodage polyline précision 6, mapping manœuvres),
                       RouteAlternativesUseCase (FASTEST, SHORTEST, NO_TOLL), FuelCostCalculator
feature:home           Écran d'accueil : carte, barre de recherche flottante, badge vitesse, recentrage, panneau inférieur
feature:search         Recherche plein écran, suggestions, récents, favoris, fiche lieu (bottom sheet)
feature:route          Comparaison d'itinéraires, tracés multiples, fiche itinéraire, estimation de coût
feature:settings       Profil véhicule, thème, données locales, confidentialité, à propos et licences
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
| Limitation de vitesse | Non disponible en v1 (affichage « — ») | Phase 2 via `trace_attributes` sur un Valhalla auto-hébergé | — |

**Avertissement.** Le serveur Valhalla de FOSSGIS et le serveur Photon de Komoot sont des services publics offerts à titre de fair-use. Ils n'offrent aucune garantie de disponibilité ni de débit et ne conviennent pas à une application distribuée à grande échelle. Pour une mise en production, il faut auto-héberger Valhalla (voir `docs/API-PROVIDERS.md`) et idéalement Photon. Roadly limite sa charge : en-tête `User-Agent: Roadly/<version> (+https://roadly.example)`, cache HTTP OkHttp de 10 Mo, debounce de 300 ms sur la recherche, une requête en vol par type.

Roadly n'invente aucune donnée GPS, routière ou tarifaire. Quand un fournisseur ne répond pas, l'interface affiche un état d'erreur explicite.

Détails par fournisseur : [`docs/API-PROVIDERS.md`](docs/API-PROVIDERS.md).

## Configuration

- `local.properties` (à la racine, hors suivi de version) : contient `sdk.dir=<chemin du SDK Android>`. Aucune clé d'API n'est nécessaire.
- Les URL des fournisseurs ont des valeurs par défaut dans `core/data/src/main/java/fr/roadly/core/data/config/ProviderConfig.kt`. Elles sont modifiables à l'exécution dans **Réglages › Fournisseurs** (stockées dans DataStore) : style de carte clair, style sombre, BAN, Photon, Valhalla. C'est ce qui permet de pointer l'application vers un Valhalla auto-hébergé sans recompiler. Utiliser une URL `https://` (reverse proxy avec TLS, par exemple Caddy) : une URL `http://` en clair est refusée par la politique réseau d'Android (cleartext).
- Si une clé devenait nécessaire plus tard, elle serait lue depuis `local.properties` à la compilation, jamais écrite dans le code.

## Ce qui fonctionne en v1

- Carte vectorielle clair/sombre, thème automatique selon le lever et le coucher du soleil calculés localement.
- Position de l'utilisateur et cap, badge de vitesse (la limitation de vitesse affiche « — » faute de source), bouton de recentrage à trois états (libre, centré, suivi).
- Adresse courante obtenue par géocodage inverse.
- Recherche : adresses (BAN), lieux et POI (Photon), coordonnées GPS en décimal ou DMS ; récents (50 maximum) ; favoris ; domicile et travail ; fiche lieu avec distance.
- Appui long sur la carte pour choisir une destination.
- Comparaison de trois itinéraires (rapide, court, sans péage) tracés sur la carte, avec heure d'arrivée.
- Heure de départ programmable (elle décale uniquement l'heure d'arrivée affichée ; il n'y a pas de prévision de trafic).
- Coût carburant, uniquement si un profil véhicule est saisi (type, carburant, consommation, prix du carburant saisi par l'utilisateur). Aucun prix par défaut n'existe.
- Mode hors ligne détecté : bandeau d'information, recherche limitée aux favoris et récents.
- Permission de localisation expliquée à l'utilisateur, avec repli vers les réglages système si elle est refusée définitivement.
- Réglages : véhicule, thème, données locales, fournisseurs, à propos et licences.

## Marqué « Bientôt » ou désactivé

- Pastilles Stations, Bornes, Parkings, Incidents de l'écran d'accueil.
- Bouton « Démarrer » d'un itinéraire (libellé « Guidage : Phase 2 »).
- Péages : le montant affiche « Phase 3 ».
- Trafic : aucun en v1.
- Limitation de vitesse : inconnue en v1.

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
- [`docs/ROADMAP.md`](docs/ROADMAP.md) : Phases 2 à 5.
