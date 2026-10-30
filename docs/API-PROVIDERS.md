# Fournisseurs de données de Roadly

Ce document décrit chaque service externe utilisé par la v1 (v0.1.0), ce que l'application lui envoie, ce qu'elle en attend, et ce qu'il faut prévoir pour une mise en production. Les URL par défaut sont définies dans `core/data/src/main/java/fr/roadly/core/data/config/ProviderConfig.kt` et peuvent être remplacées à l'exécution dans Réglages › Fournisseurs.

Tous les appels HTTP passent par un client OkHttp unique (cache de 10 Mo) qui envoie l'en-tête `User-Agent: Roadly/<version> (+https://roadly.example)`. L'adresse de contact `roadly.example` est un exemple à remplacer par un vrai contact avant toute distribution : les opérateurs de services gratuits s'en servent pour joindre les applications qui chargent leurs serveurs.

Aucune clé d'API n'est utilisée. Les fournisseurs étaient joignables le 2026-10-30 (HTTP 200) lors de la rédaction de la spec ; leur disponibilité n'est garantie par personne.

## 1. OpenFreeMap (tuiles vectorielles)

- **Endpoints (par défaut)** : styles `https://tiles.openfreemap.org/styles/positron` (thème clair) et `https://tiles.openfreemap.org/styles/dark` (thème sombre). Le style est un JSON MapLibre qui référence lui-même les tuiles, les polices et les sprites.
- **Appelé par** : MapLibre Android (pas par le client OkHttp de l'application). L'application ne fournit que l'URL du style.
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

- **Endpoints** : `GET https://api-adresse.data.gouv.fr/search/` et `GET https://api-adresse.data.gouv.fr/reverse/`.
- **Paramètres utilisés** :
  - `search` : `q` (texte saisi), `limit`, et `lat` / `lon` quand la position est connue (priorité aux résultats proches).
  - `reverse` : `lon`, `lat`.
- **Réponse** : GeoJSON `FeatureCollection`. Chaque `Feature` porte `geometry.coordinates` (`[lon, lat]`) et des `properties` (libellé, ville, code postal, type). L'application les convertit en `Place`.
- **Licence** : Licence Ouverte 2.0 (Etalab). Mentionner la source.
- **Limites** : environ 50 requêtes par seconde et par IP, sans clé. Couvre uniquement la France.
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
- **Limites** : l'instance FOSSGIS est un service public en fair-use, destiné au développement. Elle ne garantit ni disponibilité ni débit. La v1 envoie trois requêtes en parallèle (une par itinéraire : rapide, court, sans péage) à chaque comparaison.
- **Prérequis de production** : Valhalla auto-hébergé (section suivante). Cette étape est aussi nécessaire pour la limitation de vitesse (`trace_attributes`, voir plus bas).
- **Alternative** : GraphHopper, OSRM.

### Valhalla `/trace_attributes` : limitation de vitesse (guidage)

- **Endpoint** : `POST <base Valhalla>/trace_attributes`, corps JSON ; implémenté par `ValhallaTraceSpeedLimitProvider` (`core:navigation`).
- **Corps de requête** :
  - `shape` : les 10 derniers fixes GPS (`{ "lat": ..., "lon": ... }`) ;
  - `costing` : `auto` ; `shape_match` : `map_snap` ; `units` : `kilometers` ;
  - `filters` : `{ "attributes": ["edge.speed_limit", "edge.names", "edge.road_class"], "action": "include" }`.
- **Réponse** : la limitation est lue dans `edges[].speed_limit` (km/h), pour l'arête désignée par le dernier `matched_points[].edge_index` (à défaut, la dernière arête). Une valeur absente ou hors de 1..254 donne « inconnue » : l'interface affiche « — », jamais une valeur devinée.
- **Règle de blocage** : l'appel n'est jamais émis si l'hôte configuré est `openstreetmap.de` ou un sous-domaine (instance publique FOSSGIS, fair-use). La limitation n'existe donc qu'avec un Valhalla auto-hébergé, configuré dans Réglages › Fournisseurs.
- **Débit** : au plus une requête toutes les 5 s pendant le guidage ; entre deux appels, la dernière valeur est conservée. En cas d'échec, la valeur est effacée (« — »).
- **Limite** : `speed_limit` n'est renseigné que si OpenStreetMap porte `maxspeed` sur la route. Le format exact de la réponse n'a pas été vérifié sur une instance réelle (les tests utilisent des réponses JSON écrites à la main).
- **Alerte de dépassement** : se déclenche seulement quand une limitation est connue (tolérance 0/5/10 km/h réglable).

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

## 8. Sources étudiées pour la Phase 3 (non intégrées)

Aucune de ces sources n'est utilisée par la v1. Leur licence, leur format et leurs conditions doivent être vérifiés avant tout développement.

| Besoin | Source | Remarques |
|---|---|---|
| Prix des carburants | `https://data.economie.gouv.fr/explore/dataset/prix-des-carburants-en-france-flux-instantane-v2/` | Données ouvertes du ministère de l'Économie, flux quasi instantané. À évaluer : format, fréquence, volume, mise en cache locale. |
| Bornes de recharge (IRVE) | `https://transport.data.gouv.fr/datasets/fichier-consolide-des-bornes-de-recharge-pour-vehicules-electriques` | Fichier consolidé des bornes, données ouvertes. Fichier volumineux à traiter hors de l'appareil ou par extraction régionale. |
| Trafic et incidents | Bison Futé / Autoroutes.fr | Conventionnement requis. Jamais de trafic simulé : sans contrat, la fonction reste « Bientôt ». |
| Péages | Données ASFA | Pas d'API publique officielle. À contractualiser avant d'afficher un montant. |
| Signalements communautaires | Supabase (backend à créer) | Modération, expiration, et zones de danger conformes à la loi française. |
