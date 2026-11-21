# Valhalla : voies de circulation et auto-hébergement

État au 2026-11-21. Document de recherche : rien n'a été déployé. Les sections 1 à 7 ont été écrites sans toucher au code ; la section 8 décrit les changements faits ensuite dans le client.

Convention : « **vérifié** » = observé ce jour (requête réelle ou lecture du code source / de la doc officielle) ; « **non vérifié** » = extrapolation, souvenir ou rapport tiers non recoupé.

## Résumé

- **L'instance publique renvoie bien les voies.** Il faut les demander : option de requête `"turn_lanes": true` sur `/route`. Roadly ne l'envoie pas, d'où le « aucune voie » constaté jusqu'ici.
- Il n'existe **aucune clé de configuration de build** pour les voies (`use_turn_lanes` n'existe pas) : les tags OSM `turn:lanes` sont toujours intégrés aux tuiles.
- L'auto-hébergement **n'est donc pas nécessaire pour avoir les voies**. Il reste justifié par d'autres raisons : conditions d'usage FOSSGIS (1 requête/s, pas de garantie de disponibilité, URL à ne pas coder en dur), limites de `trace_attributes`, maîtrise de la version.
- Le facteur limitant réel est la **donnée OSM** : environ 29 000 ways portent `turn:lanes` en France, contre 835 000 qui portent `lanes`.

## 1. Ce que renvoie réellement l'instance publique

### 1.1 Ce que Roadly envoie aujourd'hui

`core/routing/src/main/java/fr/roadly/core/routing/ValhallaRoutingProvider.kt`, `route()` (lignes 54 à 112), POST `{base}/route` :

- `locations` (lat, lon ; `heading` + `heading_tolerance: 45` sur le premier point en recalcul) ;
- `exclude_polygons` si présents ;
- `costing` : `auto`, `truck`, `motorcycle`, `taxi` (ou `pedestrian`, ou le costing trottinette) ;
- `costing_options.<costing>` : `shortest`, `use_tolls`, `use_highways`, `use_ferry`, gabarit poids lourd ;
- `units: "kilometers"`, `language: "fr-FR"` ; `elevation_interval: 30` en trottinette.

Ni `turn_lanes`, ni `directions_type`, ni `format` ne sont envoyés.

Décodage : `ValhallaDtos.kt` ligne 39 (`lanes: List<JsonElement>`), puis `ValhallaLanes.parse` (`ValhallaLanes.kt`) qui lit `directions` (masque de bits), `valid` et `active`. Le format réel (voir 1.3) est compatible : `valid` / `active` sont des masques entiers, le décodeur les traite comme « non nul = vrai ».

### 1.2 Requêtes envoyées (2026-11-21, 12:46 UTC)

7 requêtes au total vers `https://valhalla1.openstreetmap.de`, espacées de 2 s, User-Agent générique, aucune donnée personnelle.

| # | Requête | Résultat |
|---|---|---|
| 0 | `GET /status` | `version: 3.9.3-2b04fd4`, `tileset_last_modified: 1795231477` (2026-11-21 03:24 UTC), actions dont `route`, `trace_attributes`, `trace_route`, `height`, `locate` |
| 1 | `/route` Paris Porte d'Orléans → A 6a → A 10 (24,7 km), **exactement comme Roadly** (`auto`, `fr-FR`, km) | 22 manœuvres, **0 avec `lanes`** |
| 2 | idem + `"turn_lanes": true` | 22 manœuvres, **1 avec `lanes`** (voir 1.3) |
| 3 | idem + `"turn_lanes": true` + `"directions_type": "maneuvers"` | 22 manœuvres, **1 avec `lanes`**, plus d'`instruction` textuelle (réponse 9,8 ko au lieu de 17 ko) |
| 4 | idem requête 1 + `"format": "osrm"` (sans `turn_lanes`) | 22 étapes, 150 intersections, **6 intersections avec `lanes`** |
| 5 | `/route` Toulouse centre → périphérique → A 61 (26,5 km) + `"turn_lanes": true` | 20 manœuvres, **4 avec `lanes`** |
| 6 | `/trace_attributes` (`edge_walk` sur le tracé de la requête 1, filtres `edge.lane_count`, `edge.lane_connectivity`, `edge.turn_lanes`, `edge.names`, `edge.road_class`) | 149 arêtes ; `lane_count` présent sur 149/149 (1 voie : 57, 2 : 26, 3 : 55, 4 : 10, 5 : 1) ; `lane_connectivity` : 0 ; `turn_lanes` : 0 (attribut inexistant, ignoré sans erreur) |

Coordonnées utilisées : Paris `48.8205,2.3255` → `48.6820,2.2360` ; Toulouse `43.6065,1.4510` → `43.4600,1.5700`.

### 1.3 Ce qui revient exactement

Requête 2, manœuvre type 23 « Gardez la droite pour rester sur A 10/E 05/E 50/L'Aquitaine… » :

```json
"lanes": [{"directions":16},{"directions":32,"active":32},{"directions":32,"active":32}]
```

Requête 5 (Toulouse) :

```json
"Boulevard de la Gare" (type 24) : [{"directions":8,"active":8},{"directions":64},{"directions":64}]
"A 61 / Périphérique"  (type 18) : [{"directions":2},{"directions":2},{"directions":64,"active":64}]
"A 61/E 09"            (type 24) : [{"directions":2,"active":2},{"directions":2,"active":2},{"directions":32},{"directions":32}]
sortie (type 20)                 : [{"directions":2},{"directions":2},{"directions":2},{"directions":32,"active":32}]
```

Requête 4 (format OSRM), dans `routes[0].legs[].steps[].intersections[].lanes` :

```json
[{"indications":["slight left"],"valid":false,"active":false},
 {"indications":["slight right"],"valid_indication":"slight right","valid":true,"active":true},
 {"indications":["slight right"],"valid_indication":"slight right","valid":true,"active":true}]
```

### 1.4 Conclusions factuelles

1. La phrase « l'instance publique ne renvoie pas de voies » est **fausse en l'état** : elle les renvoie quand `turn_lanes: true` est demandé (vérifié, Valhalla 3.9.3).
2. `directions_type` ne change rien aux voies (il ne retire que les textes).
3. Format natif : `lanes` n'est écrit que pour l'arête **qui précède** la manœuvre, et seulement si elle a **plus d'une voie taguée** (code `src/tyr/route_serializer_valhalla.cc`, fonction `turn_lanes`, condition `turn_lanes_size() > 1`). D'où 1 manœuvre sur 22 à Paris, 4 sur 20 à Toulouse.
4. Format OSRM : les voies sont attachées à chaque intersection, y compris en section courante, donc plus nombreuses (6 contre 1 sur le même trajet). Mais ce format change toute la structure de réponse (pas d'instructions verbales Valhalla telles que Roadly les utilise).
5. `trace_attributes` ne donne **pas** les flèches de voies : seulement `edge.lane_count`. `edge.lane_connectivity` existe mais dépend de relations OSM `connectivity`, absentes sur le trajet testé. Il n'y a pas d'attribut `edge.turn_lanes` (vérifié dans `src/baldr/attributes_controller.cc` et `src/tyr/trace_serializer.cc`).

## 2. Ce qu'il faut pour avoir les voies

### 2.1 Côté requête (suffisant)

| Élément | Nom exact | Source |
|---|---|---|
| Option de requête `/route` | `"turn_lanes": true` | doc officielle « Turn-by-turn API reference », table « Other request options » |
| Champ de réponse | `trip.legs[].maneuvers[].lanes[]` avec `directions`, `valid` (optionnel), `active` (optionnel) | même doc |
| Version minimale | Valhalla 3.6.0 (CHANGELOG : « ADDED: Turn lane information for valhalla serializer », PR #5078) | CHANGELOG |
| Alternative sans option | `"format": "osrm"` → `steps[].intersections[].lanes` | requête 4 |

Masques `directions` (doc officielle, identiques à `ValhallaLanes.kt`) : 0 vide, 1 aucun, 2 tout droit, 4 gauche serrée, 8 gauche, 16 légère gauche, 32 légère droite, 64 droite, 128 droite serrée, 256 demi-tour, 512 rabattement à gauche, 1024 rabattement à droite.

`valid` = voie utilisable au début de la manœuvre (d'autres changements de voie peuvent suivre) ; `active` = meilleure voie, sans changement supplémentaire.

### 2.2 Côté build (rien à activer)

- **Aucune clé de configuration** ne contrôle les voies. Le script officiel `scripts/valhalla_build_config` ne contient aucune clé contenant « lane » (vérifié par recherche dans le fichier). `mjolnir.data_processing.use_turn_lanes` **n'existe pas**.
- Les clés réelles de `mjolnir.data_processing` sont notamment `use_admin_db`, `use_direction_on_ways`, `scan_tar` ; celles de `mjolnir` : `include_driveways`, `include_construction`, `include_bicycle`, `include_pedestrian`, `include_driving`, `include_platforms`. Aucune ne touche aux voies.
- Les tags lus à l'import sont `turn:lanes`, `turn:lanes:forward`, `turn:lanes:backward` (`src/mjolnir/pbfgraphparser.cc`), enrichis par `graphenhancer.cc`, puis copiés dans la réponse par `src/thor/triplegbuilder.cc`.
- FOSSGIS ne désactive rien : la preuve est la requête 2. La configuration exacte de leur serveur (matériel, cadence de reconstruction) est **non vérifiée** ; je n'ai pas trouvé de rôle Valhalla dans leur dépôt Ansible public. Seule observation : le jeu de tuiles datait du jour même (03:24 UTC).

### 2.3 Ce qui doit changer dans Roadly pour afficher les voies

Changement minimal, sans serveur :

- `ValhallaRoutingProvider.route()` : ajouter `put("turn_lanes", true)` à côté de `put("language", "fr-FR")` (ligne 111, branche véhicule). Inutile pour le piéton ; à décider pour la trottinette.
- Mettre à jour les tests du fournisseur et la mention « voies rares / absentes sur l'instance publique » dans `docs/ANDROID-AUTO.md` (ligne 142) et les documents qui la reprennent.
- Vérifier sur appareil que `LaneGuidanceView` et `CarVisuals` reçoivent bien des voies sur un trajet réel (**non vérifié** : seul le JSON a été observé).

## 3. Dimensionnement et coûts

### 3.1 Chiffres sourcés

| Donnée | Valeur | Nature |
|---|---|---|
| Extrait Geofabrik France | 5,12 Go (`france-latest.osm.pbf`, mis à jour le 2026-11-21) | vérifié (en-tête HTTP) |
| Extrait Geofabrik Europe | 35,51 Go (`europe-latest.osm.pbf`, 2026-11-20) | vérifié (en-tête HTTP) |
| Build planète complet | « près de 24 heures sur une machine 16 cœurs / 64 Go », tout activé sauf trafic historique | mainteneur Valhalla, issue #5099 (février 2025) |
| Archive de tuiles planète | 111 Go, avec admins et fuseaux, sans altitude | rapport communautaire, issue #2585 (2020) |
| Build Amérique du Nord (PBF 13,6 Go) | plus de 91 Go de fichiers produits pendant le build ; 5 Go de RAM = build bloqué plus de 22 h ; « 16 Go suffisent largement », le CPU compte plus que la RAM | utilisateur + mainteneur de l'image Docker, discussion docker-valhalla #129 (décembre 2023) |
| Service d'une petite région | 1 cœur / 1,5 Go insuffisant depuis la 3.3 ; 2 cœurs / 3 Go suffisent | rapport communautaire, discussion docker-valhalla #90 (2023) |
| Altitude monde entier | « environ 1,6 To » | doc officielle (`concepts/elevation.md`) |
| Défauts de l'image `valhalla-scripted` | `build_admins=True`, `build_time_zones=True`, `build_elevation=False`, `build_tar=True`, `server_threads=nproc` | doc officielle (`docker/README.md`) |

Je n'ai trouvé **aucun chiffre publié** pour un build France ou Europe précisément. Le tableau suivant est donc une extrapolation.

### 3.2 Tableau France / Europe (extrapolation, **non vérifié**)

Hypothèses : coût proportionnel à la taille du PBF ; archive de tuiles ≈ 2 × le PBF (ratio planète 2020) ; fichiers temporaires ≈ 7 × le PBF (ratio Amérique du Nord) ; tuile d'altitude 1°×1° = 25,9 Mo non compressée (3601 × 3601 × 2 octets).

| | France seule | Europe |
|---|---|---|
| PBF source (vérifié) | 5,1 Go | 35,5 Go |
| Archive `valhalla_tiles.tar` | ~10 Go | ~70 Go |
| Disque pendant le build (PBF + temporaires + tar) | ~50 Go | ~350 Go |
| RAM pour le build | 8 Go minimum, 16 Go confortable | 32 Go minimum, 64 Go confortable |
| Durée de build sur 16 cœurs | ~1 h 30 | ~10 h |
| Durée de build sur 4 vCPU de VPS | 4 à 8 h | irréaliste (plus de 24 h) |
| Altitude (SRTM / Tilezen, non compressé) | ~3 à 4 Go (≈ 120 à 150 tuiles) | ~40 à 50 Go |
| Base admin + fuseaux horaires | quelques centaines de Mo, quelques minutes | ~1 à 2 Go |
| RAM pour servir | 4 Go minimum, 8 Go confortable | 16 Go minimum, 32 à 64 Go pour tenir l'archive en cache |
| vCPU pour servir (usage personnel) | 2 | 4 |
| Disque pour servir (tar + altitude + marge) | 30 Go | 150 Go |
| Coût mensuel indicatif | VPS 4 vCPU / 8 Go : 8 à 20 € | serveur dédié 64 Go / NVMe 500 Go : 40 à 80 € |

Notes :

- **RAM de service** : l'archive `tile_extract` est projetée en mémoire (mmap) ; Valhalla fonctionne avec moins de RAM que la taille de l'archive, mais les premières requêtes sur une zone lisent le disque. Un SSD/NVMe est nécessaire.
- **Altitude** : utile à Roadly uniquement pour le profil d'altitude trottinette (`elevation_interval`) et l'action `/height`. Sans elle, le serveur répond mais sans altitudes. Ajouter l'altitude après coup impose de reconstruire les tuiles (doc Docker).
- **Admin** : nécessaire pour le côté de conduite, les règles par pays et les pénalités de frontière. **Fuseaux** : nécessaires pour le routage dépendant de l'heure. Les deux sont activés par défaut dans l'image.
- **Tarifs** : ordres de grandeur de mémoire, non relevés ce jour chez un hébergeur. À confirmer avant décision.
- Le build peut se faire **ailleurs** (PC personnel avec Docker) puis l'archive être copiée sur un petit VPS : c'est ce qui rend la France tenable sur un VPS à moins de 10 €.

### 3.3 Mises à jour

- Geofabrik régénère ses extraits chaque jour (vérifié par les dates ci-dessus).
- Valhalla **ne sait pas appliquer de différentiels OSM** aux tuiles : chaque mise à jour est un **rebuild complet** à partir d'un nouveau PBF (aucun mécanisme incrémental dans la doc de build ni dans l'image Docker ; absence constatée, pas d'affirmation officielle trouvée).
- Cadence raisonnable pour un usage personnel : hebdomadaire. Construire dans un second dossier, puis remplacer l'archive et redémarrer le conteneur (coupure de quelques secondes).

## 4. Architecture proposée

Uniquement si la décision d'auto-héberger est prise (voir section 6). France seule.

```
Roadly (HTTPS, en-tête X-Api-Key)
   → reverse proxy (Caddy ou nginx, port 443, certificat Let's Encrypt, limite de débit)
      → conteneur valhalla-scripted, écoute 127.0.0.1:8002 uniquement
         → /custom_files : valhalla_tiles.tar, valhalla.json, admin_data, timezone_data, elevation_data
```

### 4.1 Build et service (image officielle)

L'ancienne image `gis-ops/docker-valhalla` est archivée ; son code a été repris en amont sous `ghcr.io/valhalla/valhalla-scripted` (vérifié dans les deux README).

```bash
mkdir -p /srv/valhalla/custom_files

docker run -dt --name valhalla \
  --restart unless-stopped \
  -p 127.0.0.1:8002:8002 \
  -v /srv/valhalla/custom_files:/custom_files \
  -e tile_urls=https://download.geofabrik.de/europe/france-latest.osm.pbf \
  -e build_admins=True \
  -e build_time_zones=True \
  -e build_elevation=True \
  -e build_tar=True \
  -e server_threads=4 \
  ghcr.io/valhalla/valhalla-scripted:latest
```

- `-p 127.0.0.1:8002:8002` : le port n'est pas exposé sur Internet. Valhalla n'a **aucune authentification intégrée**.
- Épingler une version plutôt que `latest` en production (nom exact des tags à lire sur la page du paquet : **non vérifié**).
- Si le build est tué par manque de mémoire, baisser `server_threads` (doc officielle).

Vérification après build :

```bash
curl -s http://127.0.0.1:8002/status
curl -s http://127.0.0.1:8002/route --data '{"locations":[{"lat":43.6065,"lon":1.4510},{"lat":43.4600,"lon":1.5700}],"costing":"auto","language":"fr-FR","units":"kilometers","turn_lanes":true}'
```

Le second appel doit renvoyer 4 manœuvres avec `lanes` (référence : requête 5 de la section 1).

### 4.2 Clés de `custom_files/valhalla.json`

Générées par l'image ; à relire, pas à écrire à la main. Noms vérifiés dans `scripts/valhalla_build_config` :

| Clé | Rôle |
|---|---|
| `mjolnir.tile_extract` | chemin de l'archive de tuiles servie en mmap |
| `mjolnir.tile_dir` | dossier des tuiles (build) |
| `mjolnir.admin` | base SQLite des limites administratives |
| `mjolnir.timezone` | base SQLite des fuseaux |
| `mjolnir.concurrency` | nombre de threads du build |
| `mjolnir.data_processing.use_admin_db` | utiliser la base admin à l'enrichissement (défaut `true`) |
| `mjolnir.data_processing.scan_tar` | préchauffer le cache disque au chargement de l'archive |
| `additional_data.elevation` | dossier des tuiles d'altitude |
| `httpd.service.listen` | `tcp://*:8002` |
| `httpd.service.timeout_seconds` | délai maximal d'une requête (défaut `-1`, à borner) |
| `loki.actions` | liste des actions autorisées (retirer celles que Roadly n'utilise pas) |
| `service_limits.<costing>.max_distance`, `max_locations` | bornes anti-abus |

Actions utilisées par Roadly : `route`, `optimized_route`, `trace_attributes`. Il n'y a **aucune clé pour les voies** (section 2.2).

### 4.3 Reverse proxy HTTPS et clé

Exemple nginx (limite de débit intégrée ; Caddy fait le HTTPS automatiquement mais sa limite de débit demande un module tiers) :

```nginx
limit_req_zone $binary_remote_addr zone=valhalla:10m rate=5r/s;

server {
    listen 443 ssl http2;
    server_name valhalla.example.org;
    # certificats Let's Encrypt (certbot)

    client_max_body_size 1m;

    location / {
        if ($http_x_api_key != "CLE_LONGUE_ALEATOIRE") { return 401; }
        limit_req zone=valhalla burst=20 nodelay;
        proxy_pass http://127.0.0.1:8002;
        proxy_read_timeout 30s;
    }
}
```

Limites à connaître :

- Une clé embarquée dans un APK **s'extrait**. Elle écarte les robots, pas un attaquant motivé. Ne jamais la mettre dans le dépôt Git (public) : la saisir dans les réglages de l'application, ou l'injecter à la compilation depuis un secret.
- Compléter par un pare-feu (ports 22 et 443 seulement), fail2ban, et des bornes `service_limits`.
- Les journaux du proxy contiennent des positions : prévoir rotation courte et anonymisation.

### 4.4 Mise à jour hebdomadaire

```bash
#!/bin/sh
# /srv/valhalla/update.sh — cron : 0 3 * * 1
set -e
cd /srv/valhalla
mkdir -p build
wget -q -O build/france-latest.osm.pbf https://download.geofabrik.de/europe/france-latest.osm.pbf
cp -r custom_files/elevation_data build/ 2>/dev/null || true

docker run --rm --name valhalla-build \
  -v /srv/valhalla/build:/custom_files \
  -e serve_tiles=False -e force_rebuild=True \
  -e build_elevation=True -e server_threads=4 \
  ghcr.io/valhalla/valhalla-scripted:latest

mv build/valhalla_tiles.tar custom_files/valhalla_tiles.tar
docker restart valhalla
curl -sf http://127.0.0.1:8002/status > /dev/null
```

Script **non testé** (écrit d'après la doc de l'image : `serve_tiles`, `force_rebuild`, `use_tiles_ignore_pbf`). Points à valider au premier essai : nom exact de l'archive produite, réutilisation des bases admin/fuseaux, espace disque doublé pendant le build.

### 4.5 Ce qui doit changer dans Roadly pour pointer vers le serveur

| Fichier | Changement |
|---|---|
| `core/data/src/main/java/fr/roadly/core/data/config/ProviderConfig.kt` ligne 23 | `valhallaBaseUrl = "https://valhalla1.openstreetmap.de"` est la valeur par défaut. Soit la remplacer, soit la laisser et saisir l'URL dans Réglages → Fournisseurs. |
| `core/data/.../config/ProviderConfigRepository.kt` | la surcharge utilisateur existe déjà (clé DataStore `provider.valhalla`). Aucun changement pour l'URL. |
| `ProviderConfig` + `ProviderConfigRepository` + `SettingsScreen` | **nouveau champ** clé d'API Valhalla (il n'en existe pas aujourd'hui). |
| `core/data/.../network/ApiClient.kt` | `postJson(url, body, headers)` accepte déjà des en-têtes : y passer `X-Api-Key`. |
| Appelants Valhalla | passer l'en-tête partout : `ValhallaRoutingProvider` (`/route`, `/optimized_route`), `ScooterRouting` (`/trace_attributes`), `FreeDrive.kt` (`ValhallaRoadSnapshotProvider`), `ValhallaTraceSpeedLimitProvider`, fournisseurs de topologie de carrefour et d'attributs d'itinéraire. |
| `ValhallaTraceSpeedLimitProvider.isPublicInstance` (ligne 97) | rien à faire : tout hôte hors `openstreetmap.de` bascule seul en politique `SELF_HOSTED` (limitations de vitesse par `trace_attributes` actives). |
| `ValhallaRoutingProvider.route()` | `put("turn_lanes", true)` (section 2.3), indépendant de l'hébergement. |

### 4.6 Points de conformité si Roadly reste sur l'instance publique

Constatés en lisant les conditions FOSSGIS et la doc Valhalla :

- **1 requête par seconde au maximum.** `RouteAlternativesUseCase` lance trois `/route` en parallèle (rapide, court, sans péage) : à espacer ou à réduire.
- **User-Agent identifiant l'application** exigé, avec un contact joignable. Roadly envoie `Roadly/<version> (+https://roadly.example)` (`NetworkModule.kt` ligne 31) : l'URL est fictive.
- La doc Valhalla demande aux applications publiées de se signaler dans les GitHub Discussions et d'envoyer un en-tête `X-Client-Id`.
- FOSSGIS recommande explicitement de **ne pas coder en dur** l'URL de ses services dans une application, n'offre **aucune garantie de disponibilité** (un seul serveur par service) et n'autorise l'usage commercial que s'il n'est pas une part substantielle de l'offre.

## 5. Alternatives pour les données de voies

| Piste | Constat | Contrainte |
|---|---|---|
| **OSM `turn:lanes` (la source de tout le reste)** | France, taginfo Geofabrik du 2026-11-20 : `turn:lanes` 28 936 ways, `turn:lanes:forward` 8 448, `turn:lanes:backward` 7 261, `lanes` 835 221, sur 12 507 309 ways `highway`. | ODbL. Couverture concentrée sur les grands axes et carrefours urbains ; répartition par type de route : voir le tableau sous cette table. |
| **Tuiles vectorielles OpenMapTiles** | La couche `transportation` n'a **aucun attribut de voies** (vérifié dans `layers/transportation/transportation.yaml` : ni `lanes` ni `turn:lanes`). | Il faudrait générer ses propres tuiles avec un schéma modifié. |
| **Valhalla format OSRM** | `"format":"osrm"` donne `steps[].intersections[].lanes` (`indications`, `valid`, `active`), plus dense que le format natif (6 contre 1 sur le trajet test). | Autre structure de réponse ; instructions verbales Valhalla à re-vérifier. |
| **OSRM** | Voies dans `steps[].intersections[].lanes` (`indications`, `valid`). | BSD ; serveur de démonstration en usage raisonnable ; pas de textes français intégrés à la réponse (bibliothèque séparée) — **non vérifié** ce jour. |
| **GraphHopper** | Pas de guidage de voies dans la réponse de l'API open source, à ma connaissance — **non vérifié**. | Apache 2.0 pour le moteur ; API hébergée payante. |
| **Overpass (déjà utilisé par Roadly)** | Peut lire `turn:lanes` des ways d'un carrefour. | Usage raisonnable ; il faut recalculer soi-même quelles voies sont valides. |
| **HERE** | Guidage de voies complet. | Payant au volume ; conditions interdisant le stockage et le mélange avec d'autres cartes (détails **non vérifiés**). |
| **TomTom** | Guidage de voies complet. | Payant ; mêmes réserves (**non vérifié**). |
| **Mapbox** | `intersections[].lanes` + `bannerInstructions`. | Payant au-delà du palier gratuit ; les conditions imposent l'affichage sur une carte Mapbox (**non vérifié** ce jour), incompatible avec MapLibre + styles Roadly. |

Répartition de `turn:lanes` par type de route en France (taginfo Geofabrik, données du 2026-11-20, vérifié) :

| `highway` | Ways au total | Avec `turn:lanes` | Avec `turn:lanes:forward` | Avec `turn:lanes:backward` |
|---|---|---|---|---|
| `motorway` | 31 508 | 4 975 (16 %) | — | — |
| `motorway_link` | 26 040 | 646 (2,5 %) | — | — |
| `trunk` | 30 396 | 5 041 (17 %) | — | — |
| `primary` | 275 194 | 8 719 (3,2 %) | 4 131 | 3 640 |
| `secondary` | 450 422 | 5 157 (1,1 %) | 2 549 | 2 218 |
| `tertiary` | 628 469 | 1 806 (0,3 %) | 972 | 738 |

Les pourcentages comptent des tronçons (ways), pas des kilomètres ni des carrefours : ils situent l'ordre de grandeur, pas la part des manœuvres couvertes.

## 6. Décisions à prendre par le propriétaire

1. **Activer `turn_lanes: true` tout de suite ?** Une ligne dans `ValhallaRoutingProvider`, sans serveur. Recommandé : c'est le seul blocage réel constaté.
2. **Rester en format natif ou passer au format OSRM** pour avoir les voies en section courante ? Le natif suffit pour « quelle voie avant la manœuvre » ; l'OSRM demande de réécrire le décodage de la réponse.
3. **Auto-héberger ou non ?** Ce n'est plus une question de voies. Les vraies raisons : 1 requête/s, disponibilité non garantie, URL à ne pas coder en dur, limitations de vitesse par `trace_attributes` bridées sur l'instance publique.
4. Si oui : **France seule ou Europe ?** France = VPS à moins de 20 €/mois ; Europe = serveur dédié, 40 à 80 €/mois (chiffres à confirmer).
5. **Avec altitude ?** Nécessaire seulement pour le profil trottinette.
6. **Où construire les tuiles ?** Sur le VPS (plus gros, plus cher) ou sur un PC personnel puis copie de l'archive.
7. **Clé d'API** : saisie dans les réglages, ou injectée à la compilation. Jamais dans le dépôt.
8. **Conformité immédiate sur l'instance publique** : User-Agent réel, en-tête `X-Client-Id`, signalement de l'application, espacement des trois requêtes d'alternatives.
9. **Contribuer à OSM** : la couverture `turn:lanes` (≈ 29 000 ways en France) est le plafond de toute solution libre.

## 7. Sources

Vérifiées le 2026-11-21 :

- Instance publique : `https://valhalla1.openstreetmap.de/status`, `/route`, `/trace_attributes` (requêtes de la section 1)
- API de calcul d'itinéraire, option `turn_lanes` et objet `lanes` : https://github.com/valhalla/valhalla/blob/master/docs/docs/api/route/api-reference.md
- API de map matching, attributs `edge.*` : https://github.com/valhalla/valhalla/blob/master/docs/docs/api/map-matching.md
- API `/status` : https://github.com/valhalla/valhalla/blob/master/docs/docs/api/status.md
- Sérialisation des voies : https://github.com/valhalla/valhalla/blob/master/src/tyr/route_serializer_valhalla.cc
- Attributs de trace : https://github.com/valhalla/valhalla/blob/master/src/tyr/trace_serializer.cc et https://github.com/valhalla/valhalla/blob/master/src/baldr/attributes_controller.cc
- Lecture des tags `turn:lanes` : https://github.com/valhalla/valhalla/blob/master/src/mjolnir/pbfgraphparser.cc
- Clés de configuration : https://github.com/valhalla/valhalla/blob/master/scripts/valhalla_build_config
- CHANGELOG (3.6.0, PR #5078 ; 3.9.3) : https://github.com/valhalla/valhalla/blob/master/CHANGELOG.md
- Image Docker officielle et variables d'environnement : https://github.com/valhalla/valhalla/blob/master/docker/README.md
- Ancienne image archivée : https://github.com/gis-ops/docker-valhalla
- Altitude (1,6 To monde) : https://github.com/valhalla/valhalla/blob/master/docs/docs/concepts/elevation.md
- Serveur de démonstration, `X-Client-Id` : https://valhalla.github.io/valhalla/
- Durée du build planète (16 cœurs / 64 Go, ~24 h) : https://github.com/valhalla/valhalla/issues/5099
- Taille de l'archive planète (111 Go, 2020) : https://github.com/valhalla/valhalla/issues/2585
- Build Amérique du Nord, mémoire : https://github.com/gis-ops/docker-valhalla/discussions/129
- Spécifications minimales de service : https://github.com/gis-ops/docker-valhalla/discussions/90
- Conditions d'usage FOSSGIS : https://fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen
- Extraits Geofabrik : https://download.geofabrik.de/europe/france.html et https://download.geofabrik.de/europe.html
- Taginfo France : https://taginfo.geofabrik.de/europe:france/keys/turn:lanes
- Schéma OpenMapTiles, couche `transportation` : https://github.com/openmaptiles/openmaptiles/blob/master/layers/transportation/transportation.yaml

Non consultées ce jour (affirmations marquées « non vérifié ») : documentation OSRM, GraphHopper, HERE, TomTom, Mapbox ; grilles tarifaires des hébergeurs ; configuration du serveur FOSSGIS.

## 8. Conformité du client

Ajout du 2026-11-21, postérieur aux sections 1 à 7 : le code a été modifié (les points de la section 4.6 marqués « à espacer » et « URL fictive » sont traités). Vérifié par tests unitaires en temps virtuel ; **non vérifié** sur appareil ni contre l'instance publique (aucune requête envoyée pour ce travail).

Conditions relues ce jour sur https://fossgis.de/arbeitsgruppen/osm-server/nutzungsbedingungen : pour `routing.openstreetmap.de` et `valhalla1.openstreetmap.de`, « Maximal eine Anfrage pro Sekunde » ; User-Agent valide identifiant l'application (celui d'une bibliothèque ne suffit pas) ; un seul serveur par service, aucune garantie de disponibilité.

### 8.1 Ce que le client garantit

| Garantie | Mécanisme |
|---|---|
| Au plus une requête par seconde vers `*.openstreetmap.de`, une seule en vol à la fois | `ValhallaRateLimiter` (`core/data/.../network/`), unique pour tout le processus, appliqué dans `ApiClient` : tous les appelants Valhalla passent par `ApiClient`, aucun ne peut le contourner |
| Serveur auto-hébergé non bridé | le limiteur ne s'applique qu'aux hôtes `openstreetmap.de` et à leurs sous-domaines ; toute autre URL saisie dans Réglages → Fournisseurs garde le comportement précédent |
| Le recalcul ne reste pas derrière l'arrière-plan | file à trois rangs : recalcul avec cap (`GUIDANCE`), puis `/route` et `/optimized_route` demandés par l'utilisateur (`INTERACTIVE`), puis `trace_attributes` (`BACKGROUND`) |
| Plus de rafale de trois `/route` | `RouteAlternativesUseCase` dépose les trois variantes dans l'ordre (rapide, court, sans péage) ; le limiteur les envoie une par une |
| Pas de requête répétée pour la même recherche | `RouteResponseCache` : réponse `/route` réutilisée 60 s si l'URL et le corps sont identiques (6 entrées, réponses réussies seulement, jamais un recalcul avec cap) |
| Repli en cas de saturation | HTTP 429 → `AppError.RateLimited`, 503 → `AppError.ProviderUnavailable` (types existants) ; `Retry-After` (secondes ou date) met tout le limiteur en pause ; sans cet en-tête, pause de 5 s ; au-delà de 10 s de pause restante, les requêtes échouent tout de suite sans toucher le serveur |
| User-Agent identifiable | `Roadly/<version> (+<contact>)`, contact lu à la compilation (`BuildConfig.CONTACT` de `core:data`) |

Limites connues :

- L'espacement est mesuré entre deux **départs** de requête côté client ; la gigue du réseau peut rapprocher deux arrivées côté serveur de quelques dizaines de millisecondes.
- La priorité joue entre requêtes en attente : une requête déjà partie n'est pas interrompue. Un recalcul attend donc au pire la fin de la requête en vol plus le reste de la seconde.
- Sur l'instance publique, les trois variantes arrivent en un peu plus de 2 s au lieu d'être simultanées. `RouteAlternativesUseCase.progressive()` les livre une par une, mais les écrans appellent encore la forme bloquante : à brancher dans `RouteViewModel` et `CarRoutePreviewScreen` pour afficher le plus rapide sans attendre les deux autres.
- Le limiteur vaut pour un processus. Deux appareils du même foyer derrière la même adresse IP ne se coordonnent pas.
- Les requêtes d'arrière-plan (`trace_attributes` par variante, par carrefour, en conduite libre) partagent la même seconde : en trottinette ou avec la vue 3D, l'analyse des variantes arrive plus tard qu'avant.

### 8.2 Ce qui reste à la décision du propriétaire

1. **Contact du User-Agent.** Valeur par défaut : `https://github.com/Gomze31/roadly` (le dépôt, même privé, sert d'identifiant). Pour annoncer une adresse joignable : `roadly.contact=…` dans `gradle.properties` ou `-Proadly.contact=…` à la compilation (ASCII, sans guillemet). Aucune adresse personnelle n'a été mise dans le code.
2. **Signalement de l'application et en-tête `X-Client-Id`** demandés par la doc Valhalla (section 4.6) : non faits.
3. **URL de l'instance publique codée comme valeur par défaut** (`ProviderConfig.DEFAULT`) : inchangée, alors que FOSSGIS le déconseille. Tant qu'elle reste, une diffusion large de l'application n'est pas compatible avec ces conditions ; l'auto-hébergement (section 4) règle ce point.
4. **Usage commercial** : autorisé seulement s'il ne constitue pas une part substantielle de l'offre.
5. **Volume d'arrière-plan** : le limiteur plafonne le débit, il ne réduit pas le nombre de requêtes `trace_attributes` par trajet. Les réduire sur l'instance publique reste un choix de fonctionnalités (`core/navigation`).
