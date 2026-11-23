# Fournisseurs de données de Roadly

Ce document décrit chaque service externe utilisé par la v1 (v0.1.0), ce que l'application lui envoie, ce qu'elle en attend, et ce qu'il faut prévoir pour une mise en production. Les URL par défaut sont définies dans `core/data/src/main/java/fr/roadly/core/data/config/ProviderConfig.kt` et peuvent être remplacées à l'exécution dans Réglages › Fournisseurs.

Tous les appels HTTP passent par un client OkHttp unique (cache de 10 Mo) qui envoie l'en-tête `User-Agent: Roadly/<version> (+https://roadly.example)`. L'adresse de contact `roadly.example` est un exemple à remplacer par un vrai contact avant toute distribution : les opérateurs de services gratuits s'en servent pour joindre les applications qui chargent leurs serveurs.

Aucune clé d'API n'est nécessaire pour les fournisseurs de ce document. Trois fonctions optionnelles utilisent une clé ou un jeton saisi par l'utilisateur : la clé Gemini de Roadly AI (Réglages, stockée chiffrée, ou `GEMINI_API_KEY` dans `local.properties`), l'URL et la clé anon Supabase de la communauté (`docs/BACKEND.md`), le jeton GitHub à portée fine des mises à jour d'un dépôt privé (`docs/UPDATES.md`). Les fournisseurs étaient joignables le 2026-10-30 (HTTP 200) lors de la rédaction de la spec ; leur disponibilité n'est garantie par personne.

## 1. OpenFreeMap (tuiles vectorielles)

- **Endpoints (par défaut)** : les styles sont embarqués dans l'application, `asset://styles/roadly-light.json` (thème clair) et `asset://styles/roadly-dark.json` (thème sombre), fichiers de `core/map/src/main/assets/styles/`. Chaque style est un JSON MapLibre qui référence les tuiles (`https://tiles.openfreemap.org/planet`), les polices et les sprites d'OpenFreeMap. Une URL `https://` de style externe reste possible dans Réglages › Fournisseurs.
- **Appelé par** : MapLibre Android (pas par le client OkHttp de l'application). L'application ne fournit que l'URI du style.
- **Paramètres** : aucun.
- **Réponse** : style JSON MapLibre/Mapbox GL, puis tuiles vectorielles.
- **Licence** : service gratuit, sans clé, usage commercial autorisé. Données : contributeurs OpenStreetMap, ODbL. L'attribution « © OpenFreeMap © contributeurs OpenStreetMap » doit rester visible (elle figure dans Réglages › À propos).
- **Limites** : aucun quota publié. Pas de garantie de niveau de service.
- **Prérequis de production** : lire les conditions d'usage du service avant une diffusion large ; envisager un soutien financier au projet ou un hébergement propre.
- **Alternative** : MapTiler (clé requise, offre payante au-delà du gratuit), ou Protomaps auto-hébergé à partir d'un fichier PMTiles.

## 2. MapLibre Android (moteur de rendu)

- **Version** : 11.13.5. Bibliothèque embarquée, pas un service.
- **Licence** : BSD-2.
- **Limite connue** : les bibliothèques natives pour toutes les architectures font l'essentiel du poids de l'APK de debug (environ 115 Mo). À traiter avec un App Bundle ou des découpages par ABI.

## 3. Base Adresse Nationale (BAN)

- **Endpoints** : `GET https://data.geopf.fr/geocodage/search/` et `GET https://data.geopf.fr/geocodage/reverse/` (service de géocodage de la Géoplateforme de l'IGN).
- **Changement d'adresse (2026-11-22)** : l'ancienne adresse `https://api-adresse.data.gouv.fr` répond encore, mais avec les en-têtes `deprecation` et `sunset` au 31 janvier 2026 et un renvoi vers la Géoplateforme. Le défaut de l'application est donc la nouvelle adresse ; un réglage enregistré avec l'ancienne adresse suit le nouveau défaut, une adresse choisie par l'utilisateur est gardée.
- **Vérification** : trois requêtes en tout le 2026-11-22 (une sur l'ancienne adresse, puis une `/search/` et une `/reverse/` sur la nouvelle) : HTTP 200, mêmes champs lus par l'application (`geometry.coordinates`, `label`, `name`, `type`, `postcode`, `city`, `id`). La documentation de l'IGN (cartes.gouv.fr, guide « Géocodage ») donne ces deux adresses, sans clé.
- **Paramètres utilisés** :
  - `search` : `q` (texte saisi), `limit`, et `lat` / `lon` quand la position est connue (priorité aux résultats proches).
  - `reverse` : `lon`, `lat`.
- **Réponse** : GeoJSON `FeatureCollection`. Chaque `Feature` porte `geometry.coordinates` (`[lon, lat]`) et des `properties` (libellé, ville, code postal, type). L'application les convertit en `Place`.
- **Licence** : Licence Ouverte 2.0 (Etalab). Mentionner la source.
- **Limites** : 50 requêtes par seconde et par IP (documentation de l'IGN et de adresse.data.gouv.fr ; au-delà, HTTP 429 puis blocage de 5 s), sans clé. Les réponses portent aussi un en-tête `x-ratelimit-limit-second: 1` dont le sens n'est pas documenté. Usage gratuit, soumis aux conditions générales de la Géoplateforme (l'IGN peut limiter ou bloquer un usage abusif). Couvre uniquement la France.
- **Prérequis de production** : respecter le quota (la recherche est déjà soumise à un debounce de 300 ms et à une seule requête en vol) ; prévoir un message clair en cas d'erreur 429 ou 5xx.
- **Alternative** : Nominatim ou Pelias (auto-hébergés pour un usage intensif).

## 4. Photon (Komoot)

- **Endpoints** : `GET https://photon.komoot.io/api/` et `GET https://photon.komoot.io/reverse`.
- **Paramètres utilisés** :
  - `api` : `q`, `lang=fr`, `limit`, et `lat` / `lon` pour favoriser les résultats proches.
  - `reverse` : `lat`, `lon`, `lang=fr`.
- **Réponse** : GeoJSON `FeatureCollection` avec `geometry.coordinates` (`[lon, lat]`) et des `properties` issues d'OpenStreetMap (nom, rue, ville, `osm_key`, `osm_value`, etc.). Le type du lieu (ville, rue, adresse, POI) est déduit des clés OSM.
- **Licence** : code sous licence Apache 2.0 ; données OpenStreetMap sous ODbL.
- **Limites** : instance publique en fair-use, sans garantie de production. Elle peut limiter ou bloquer les clients trop bavards.
- **Prérequis de production** : auto-héberger Photon (l'index de recherche se télécharge ou se construit à partir des données OpenStreetMap ; prévoir un espace disque important) ou s'appuyer sur un fournisseur commercial. Renseigner un `User-Agent` avec un contact réel.
- **Alternative** : Photon auto-hébergé, Nominatim, Pelias.

## 5. Coordonnées saisies

Pas de service : le parseur local (`CoordinateParser`, module `core:geocoding`) reconnaît les coordonnées décimales (`48.85, 2.35`) et DMS (`48°51'N 2°21'E`) et rejette les valeurs hors bornes. Le résultat s'affiche comme lieu de type « Coordonnées GPS ».

## 6. Valhalla (routage)

- **Endpoint** : `POST https://valhalla1.openstreetmap.de/route`, corps JSON.
- **Corps de requête utilisé** :
  - `locations` : liste de `{ "lat": ..., "lon": ... }` (origine, étapes éventuelles, destination).
  - `costing` : `auto`.
  - `costing_options.auto` selon l'itinéraire demandé : rapide = vide ; court = `shortest: true` ; sans péage = `use_tolls: 0`.
  - `units` : `kilometers`.
  - `language` : `fr-FR`.
- **Réponse** : JSON `trip` contenant le résumé (distance, durée) et, par tronçon, la géométrie en polyline encodée à précision 6 et la liste des manœuvres (type, instruction, longueur). L'application décode la polyline (précision 6) et fait correspondre les types de manœuvres à ses propres types.
- **Erreurs** : une réponse 400 avec un code Valhalla 170, 171 ou 442 (pas d'arête proche, pas de chemin) est traitée comme « aucun résultat » et non comme une panne.
- **Licence** : moteur sous licence MIT ; données OpenStreetMap sous ODbL.
- **Limites** : l'instance FOSSGIS est un service public en fair-use, destiné au développement. Elle ne garantit ni disponibilité ni débit. L'écran de choix demande trois itinéraires (rapide, court, sans péage ; deux quand les péages sont déjà évités), envoyés un par un par le limiteur de débit. Le démarrage direct (voix, Android Auto) n'en demande qu'un, le plus rapide. Le budget complet d'un trajet est détaillé plus bas (« Budget de requêtes par trajet »).
- **Prérequis de production** : Valhalla auto-hébergé (section suivante). Cette étape est aussi nécessaire pour le suivi de la limitation fix par fix (`trace_attributes`, voir plus bas) ; le profil du tracé, lui, fonctionne aussi sur l'instance publique.

### Valhalla `/trace_attributes` : profil du tracé (limitations, portiques en flux libre)

- **Endpoint** : `POST <base Valhalla>/trace_attributes`, corps JSON ; implémenté par `ValhallaRouteAttributesProvider` (`core/navigation/.../RouteAttributes.kt`).
- **Corps de requête** : `encoded_polyline` (tracé, précision 6), `shape_match` : `edge_walk`, `costing` : `auto`, filtre sur `edge.length`, `edge.speed_limit`, `edge.names`, `edge.end_node`, `node.type`, `admin.country_code`.
- **Débit** : une requête par tracé motorisé, y compris sur l'instance publique FOSSGIS (même volume qu'un calcul d'itinéraire) ; les 6 derniers profils sont gardés en mémoire. Aucune requête à chaque position. Deux demandes simultanées du même tracé (écran de choix puis guidage) ne font qu'une requête ; une réponse inexploitable (illisible, longueurs incohérentes, refus 4xx) n'est pas redemandée pour le même tracé ; une panne passagère n'est pas retentée en boucle (une demande = une requête).
- **Résultat** : tronçons à limitation connue et portiques de péage en flux libre (OSM `highway=toll_gantry`). Le profil est rejeté si la longueur des arêtes s'écarte de plus de 5 % de celle du tracé. Sans `maxspeed` OpenStreetMap, la limitation reste « — ».
- **Limite** : l'instance publique reste un service en fair-use, pour le développement ; non vérifié en roulant.
- **Alternative** : GraphHopper, OSRM.

### Valhalla `/trace_attributes` : limitation de vitesse fix par fix (guidage, auto-hébergé)

- **Endpoint** : `POST <base Valhalla>/trace_attributes`, corps JSON ; implémenté par `ValhallaTraceSpeedLimitProvider` (`core:navigation`).
- **Corps de requête** :
  - `shape` : les 10 derniers fixes GPS (`{ "lat": ..., "lon": ... }`) ;
  - `costing` : `auto` ; `shape_match` : `map_snap` ; `units` : `kilometers` ;
  - `filters` : `{ "attributes": ["edge.speed_limit", "edge.names", "edge.road_class"], "action": "include" }`.
- **Réponse** : la limitation est lue dans `edges[].speed_limit` (km/h), pour l'arête désignée par le dernier `matched_points[].edge_index` (à défaut, la dernière arête). Une valeur absente ou hors de 1..254 donne « inconnue » : l'interface affiche « — », jamais une valeur devinée.
- **Règle de blocage** : cet appel fix par fix n'est jamais émis si l'hôte configuré est `openstreetmap.de` ou un sous-domaine (instance publique FOSSGIS, fair-use). Sur l'instance publique, la limitation vient donc du seul profil du tracé (section précédente) ; le suivi fix par fix demande un Valhalla auto-hébergé, configuré dans Réglages › Fournisseurs.
- **Débit** : au plus une requête toutes les 5 s pendant le guidage ; entre deux appels, la dernière valeur est conservée. En cas d'échec, la valeur est effacée (« — »).
- **Limite** : `speed_limit` n'est renseigné que si OpenStreetMap porte `maxspeed` sur la route. Le format exact de la réponse n'a pas été vérifié sur une instance réelle (les tests utilisent des réponses JSON écrites à la main).
- **Alerte de dépassement** : se déclenche seulement quand une limitation est connue (tolérance 0/5/10 km/h réglable).

### Valhalla `/trace_attributes` : topologie des carrefours (vue 3D, auto-hébergé)

- **Endpoint** : `POST <base Valhalla>/trace_attributes` sur une fenêtre d'environ 1,3 km autour du carrefour ; implémenté par `ValhallaJunctionTopologyProvider` (`core/navigation/.../junction/JunctionTopologyProvider.kt`).
- **Règle de blocage** : aucune requête si l'hôte configuré est `openstreetmap.de` ou un sous-domaine (même définition que pour la limitation fix par fix, `ValhallaTraceSpeedLimitProvider.isPublicInstance`). Sur l'instance publique, la vue de carrefour se construit sans topologie (tracé et tuiles), comme elle le fait hors ligne ; le compte des sorties d'un rond-point s'appuie alors sur la seule valeur de Valhalla.
- **Débit (auto-hébergé)** : une requête par carrefour retenu, au plus trois essais espacés de 5 s en cas d'échec, deux requêtes espacées d'au moins 4 s ; les 24 dernières topologies sont gardées en mémoire.

### Overpass : ralentisseurs du tracé

- **Endpoint** : `POST https://overpass-api.de/api/interpreter` (champ de formulaire `data`) ; implémenté par `OverpassSpeedBumpProvider` (`core/navigation/.../SpeedBumps.kt`). L'URL se change dans Réglages › Fournisseurs.
- **Requête** : nœuds `traffic_calming` (dos-d'âne, plateau, coussin) des voies lentes du tracé (limitation inconnue ou ≤ 70 km/h), par paquets de 400 voies, 1 200 voies au plus par tracé.
- **Débit** : une seule requête en vol ; au moins 10 s entre la fin d'une requête et la suivante ; les voies déjà interrogées et leurs ralentisseurs sont gardés en mémoire (5 000 voies), un tracé dont toutes les voies lentes sont connues ne coûte donc aucune requête et un tracé partiellement connu ne demande que les voies manquantes ; un tracé remplacé par un plus récent pendant l'attente n'est pas demandé.
- **Retrait** : après un HTTP 429, un 5xx (dont 504) ou un délai dépassé, plus aucune requête pendant 60 s, durée doublée à chaque échec de suite (15 min au plus). Les tracés calculés entre-temps n'ont pas de ralentisseurs annoncés.
- **Limites** : l'instance publique est un service communautaire ; sa politique d'usage n'a pas été relue pour ce document (l'audit du 2026-11-22 la cite comme déconseillant l'usage en serveur d'une application). L'en-tête `Retry-After` n'est pas lu (le client HTTP commun ne le transmet pas). Après un 429, ce client ne refait plus ses deux essais (2 s puis 8 s) pour les ralentisseurs : le retrait commence dès la première réponse. Les ralentisseurs d'un tracé ne viennent que des paquets de voies qui contiennent l'une de ses voies : ceux lus pour un autre trajet sur une rue parallèle ne lui sont pas attribués ; deux rues parallèles lues dans le même paquet restent indiscernables (la réponse ne dit pas à quelle voie appartient un nœud). Les autres usages d'Overpass (mode trottinette, `core/routing/.../ScooterRouting.kt` : tags des voies d'un itinéraire, lieux autour d'un point) passent par une porte commune, `OverpassGate` (`core/data/.../network/`) : une seule requête à la fois, au moins 1 s entre deux requêtes (ces appels répondent à une demande à l'écran), aucun nouvel essai après un 429, et le même retrait de 60 s à 15 min après un 429, un 5xx ou un délai dépassé. Les variantes d'une recherche, analysées en parallèle, ne redemandent plus les voies qu'elles partagent (1 requête au lieu de 3 dans `ScooterRoutingTest`). Pendant un retrait, l'analyse aboutit sans tags (agglomération, éclairage et restrictions locales « inconnus », comme lors d'une panne) et la recherche de lieux échoue tout de suite. Les ralentisseurs gardent leur propre limiteur : les deux ne se partagent pas le créneau, mais un itinéraire trottinette ne demande pas de ralentisseurs.
- **Prérequis de production** : une instance Overpass propre ou un extrait embarqué, ou un réglage que l'utilisateur active lui-même ; à décider.

### Budget de requêtes par trajet

Les valeurs « après » sont **mesurées dans les tests sur des doublures** (faux fournisseur d'itinéraires, faux client HTTP, temps virtuel : `RequestBudgetTest`, `GuidanceActionsTest`, `RouteAlternativesUseCaseTest`), **jamais sur les serveurs réels**. Les valeurs « avant » viennent de la lecture du code précédent.

| Moment du trajet | Service | Avant | Après |
|---|---|---|---|
| Démarrage direct (voix, Android Auto) | Valhalla `/route` | 3 (2 si les péages sont évités) | 1 |
| Écran de choix des itinéraires | Valhalla `/route` | 3 (2 si les péages sont évités) | inchangé |
| Écran de choix : carte d'identité des variantes | Valhalla `/trace_attributes` | 1 par variante affichée | inchangé |
| Reprise d'un trajet interrompu depuis l'accueil | Valhalla `/route` | 3 | 1 (`HomeViewModelTest`) ; comme au démarrage direct, plus de repli sur une autre variante si le plus rapide échoue |
| Départ du guidage : profil du tracé | Valhalla `/trace_attributes` | 1, ou 2 si l'écran de choix le demandait au même moment | 1 (0 s'il est déjà en mémoire) |
| Chaque recalcul adopté | Valhalla `/route` + `/trace_attributes` | 1 + 1 | inchangé (le profil d'un nouveau tracé reste nécessaire : limitations, péages) |
| Chaque carrefour retenu, instance publique | Valhalla `/trace_attributes` | 1 (jusqu'à 3 en cas d'échec) | 0 |
| Chaque carrefour retenu, auto-hébergé | Valhalla `/trace_attributes` | 1 (jusqu'à 3 en cas d'échec) | inchangé |
| Ralentisseurs du premier tracé | Overpass | 1 à 3 selon la longueur, d'affilée | 1 à 3, espacées de 10 s |
| Ralentisseurs après un recalcul sur le même couloir | Overpass | 1 à 3 par recalcul (6 pour un tracé et cinq recalculs) | 0 (1 pour un tracé et cinq recalculs) |
| Ralentisseurs après un recalcul par d'autres rues | Overpass | 1 à 3 | 1 le plus souvent : seules les voies nouvelles sont demandées |
| Tracé suivant après un 504 | Overpass | 1 à 3 par tracé | 0 pendant le retrait |
| Tracé suivant après un 429 | Overpass | 1 à 3 par tracé | 0 pendant le retrait ; le 429 lui-même coûte 1 requête (3 avant) |
| Conduite libre, instance publique | Valhalla `/trace_attributes` | espacées selon `RoadQueryPolicy.PUBLIC` (20 s) | inchangé |

### Valhalla auto-hébergé : `docker-compose.yml` indicatif

Fichier d'exemple à adapter (non testé dans ce dépôt). Il suppose une machine Linux avec Docker, au moins 4 Go de RAM libres, et de l'espace disque pour les données de la France (extraction Geofabrik) et les tuiles de routage générées.

```yaml
services:
  valhalla:
    image: ghcr.io/valhalla/valhalla:latest   # ou gisops/valhalla
    container_name: valhalla
    ports:
      - "8002:8002"
    volumes:
      - ./custom_files:/custom_files
    restart: unless-stopped
```

Préparation des données : placer `france-latest.osm.pbf` (téléchargé depuis `https://download.geofabrik.de/europe/france.html`) dans le dossier `custom_files`. Selon l'image choisie, la construction des tuiles se fait au premier démarrage ou via une commande dédiée ; suivre la documentation de l'image retenue. Prévoir ensuite une mise à jour périodique du fichier `.osm.pbf` et la reconstruction des tuiles.

Sécurité : le port 8002 n'est pas authentifié. Le placer derrière un reverse proxy avec TLS et, si besoin, une authentification ou une restriction d'adresses, plutôt que de l'exposer tel quel sur Internet. Android bloque le trafic HTTP en clair par défaut, et la v1 ne déclare aucune exception réseau (`usesCleartextTraffic` ou configuration de sécurité réseau absents du manifeste). Une URL `http://<ip>:8002` est donc refusée par la politique cleartext d'Android (l'application affiche alors que le service est indisponible) : exposer Valhalla en HTTPS derrière un reverse proxy avec TLS.

### Pointer l'application vers ce serveur

1. Ouvrir Réglages › Fournisseurs.
2. Remplacer l'URL Valhalla par l'URL `https://` du reverse proxy avec TLS (par exemple Caddy devant le port 8002). Une URL `http://` en clair est refusée par la politique cleartext d'Android. Ne pas ajouter `/route` : l'application l'ajoute.
3. Lancer une comparaison d'itinéraires. En cas d'échec, l'écran affiche l'état d'erreur du fournisseur.

La même méthode vaut pour BAN, Photon et les styles de carte.

## 7. Position

`play-services-location` (FusedLocationProviderClient). Ce n'est pas un service réseau de Roadly. Prérequis : les Google Play Services doivent être présents sur l'appareil ; il n'y a pas de repli `LocationManager` en v1 (prévu Phase 5), donc sans eux aucune position n'est disponible.

## 8. Sources étudiées pour la Phase 3

Hors les prix des carburants et les signalements (intégrés depuis), aucune de ces sources n'est utilisée. Leur licence, leur format et leurs conditions doivent être vérifiés avant tout développement.

| Besoin | Source | Remarques |
|---|---|---|
| Prix des carburants | `https://data.economie.gouv.fr/explore/dataset/prix-des-carburants-en-france-flux-instantane-v2/` | **Intégré** : `OpenDataFuelStationProvider` (`core/data/.../fuel/FuelStationProvider.kt`), requête de proximité sur l'API `explore/v2.1`, Licence Ouverte, sans clé ; repli sur les lieux OSM si le flux échoue. |
| Bornes de recharge (IRVE) | `https://transport.data.gouv.fr/datasets/fichier-consolide-des-bornes-de-recharge-pour-vehicules-electriques` | Fichier consolidé des bornes, données ouvertes. Fichier volumineux à traiter hors de l'appareil ou par extraction régionale. |
| Trafic et incidents | Bison Futé / Autoroutes.fr | Conventionnement requis. Jamais de trafic simulé : sans contrat, la fonction reste « Bientôt ». |
| Péages | Données ASFA | Pas d'API publique officielle. À contractualiser avant d'afficher un montant. |
| Signalements communautaires | Supabase (projet à créer par l'exploitant) | **Client intégré**, inactif tant que l'URL et la clé anon ne sont pas saisies (Réglages › Communauté) ; schéma dans `backend/supabase/schema.sql`, `docs/BACKEND.md`. Modération, expiration, et zones de danger conformes à la loi française. |
