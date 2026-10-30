# Benchmark fonctionnel : Sillaxe → Roadly

Date : 2026-10-30. Source : page publique https://sillaxe.app/ (lecture du texte uniquement ; aucun visuel, nom, logo ni texte n'est repris). Objectif : aligner la feuille de route de Roadly sur l'ensemble fonctionnel d'un GPS grand public français, en précisant pour chaque fonction son état dans Roadly v1, la phase cible, la source de données réaliste et les contraintes légales.

Rappel : Sillaxe est une application iPhone + CarPlay, gratuite avec abonnement « + ». Roadly est Android (Android Auto en Phase 5). Le modèle économique n'est pas traité ici.

## 1. Tableau de correspondance

| Fonction (telle qu'annoncée) | Roadly v1 | Phase cible | Source / moyen | Contrainte |
|---|---|---|---|---|
| Guidage vocal complet, voies à prendre | Non (bouton « Guidage : Phase 2 ») | **2** | Manœuvres Valhalla (`verbal_*_instruction`, `lanes` dans `maneuvers`), TTS Android `fr-FR` | Les voies ne sont présentes que sur les routes OSM taguées `turn:lanes` |
| Vitesse + limitation de la route | Vitesse oui, limitation « — » | **2** | Valhalla `trace_attributes` (`edge.speed_limit`) sur instance auto-hébergée avec `use_admin_db` ; repli : `maxspeed` OSM via l'arête courante | L'instance publique FOSSGIS ne garantit pas `speed_limit` |
| « Horizon » : bandeau de ce qui arrive (ralentissements, manœuvres, zones) | Non | **2** (manœuvres) puis **3** (trafic, zones) | Projection de la position sur la géométrie de l'itinéraire + liste ordonnée des événements à venir | Design original Roadly (bandeau bas en verre), pas une copie |
| Comparaison d'itinéraires avec coût (péage + carburant) | 3 itinéraires, coût carburant si profil ; péages « Phase 3 » | **3** pour les péages | Péages : pas d'API publique officielle ; options : grille tarifaire ASFA à saisir/maintenir, ou `toll` des arêtes Valhalla + barème par société | Données tarifaires à contractualiser ou à maintenir manuellement |
| Trafic en direct, routes coupées évitées | Non (jamais simulé) | **3** | Bison Futé open data (DATEX II), événements Autoroutes.fr ; Valhalla accepte `exclude_locations`/`exclude_polygons` pour contourner | Trafic temps réel fluide (vitesses) = fournisseur payant (TomTom/HERE/Mapbox) ou données opérateurs |
| Prix des carburants | Non | **3** | `prix-carburants` (data.economie.gouv.fr, flux instantané, Licence Ouverte) | Mise à jour ~10 min ; stations sans prix récent à signaler |
| Bornes de recharge | Non | **3** | IRVE consolidé (transport.data.gouv.fr) ; disponibilité temps réel via opérateurs (Gireve/eMI3) | Disponibilité temps réel souvent payante |
| Stationnement à l'arrivée + retour à pied vers la voiture | Non | **3** (parkings) / **4** (retour à pied) | Parkings : OSM `amenity=parking` + open data villes (Paris, Lyon…) ; retour à pied : Valhalla `pedestrian` + position mémorisée du stationnement | Tarifs et disponibilité temps réel très hétérogènes |
| Signalements entre conducteurs | Non | **3** | Backend Supabase (table `reports`, confirmations, expiration, modération) | RGPD : séparer identité et position ; durée de vie courte |
| Zones de vigilance / « radars » | Non | **3** | Zones de danger au sens du protocole 2011-2012 (segments, jamais un point) ; source : base communautaire + données publiques de zones | **France : art. R413-15 du code de la route** — interdiction des avertisseurs de radars ; seules les zones de danger génériques sont tolérées. **Suisse : interdit** (à désactiver par pays). Allemagne : avertissement interdit en conduite |
| Ponts trop bas signalés avant l'ouvrage | Non | **3** (avec le profil véhicule hauteur) | OSM `maxheight` sur les arêtes ; Valhalla `truck` costing avec `height` évite déjà ces arêtes | Données OSM incomplètes : alerter en plus de router |
| Départ planifié pour arriver à l'heure | Heure de départ seulement (décale l'arrivée) | **4** | Calcul inverse heure d'arrivée → heure de départ + notification WorkManager | Sans trafic prédictif, estimation grossière |
| Points, récompenses, classement des km | Non | **4** (facultatif) | Backend ; points pour signalements confirmés | Anti-triche minimal, opt-in |
| Zéro publicité | Pas de publicité | — | — | — |
| Alerte zone même sans itinéraire | Non | **3** | Mode « veille » : suivi position + zones chargées autour | Service de premier plan requis ; batterie |
| Partage de trajet en direct | Non | **4** | Backend : lien temporaire, position publiée pendant le trajet uniquement | RGPD : consentement explicite, expiration automatique |
| Replay des trajets sur la carte | Non | **4** | Enregistrement local des traces (Room), lecture animée sur MapLibre | Stockage local uniquement par défaut |
| Relevé kilométrique et coût | Non | **4** | Table `trips` (distance, durée, coût estimé, carburant) + statistiques | Déjà prévu spec §12 |
| Profils poids lourd / taxi (hauteur, largeur, poids, voies réservées) | Type + carburant seulement | **4** | Valhalla `truck` costing (`height`, `width`, `weight`, `axle_load`, `hazmat`) ; voies réservées : OSM `taxi=designated` (couverture faible) | Valhalla ne gère pas nativement les voies bus/taxi en costing `auto` |
| Tournées de livraison | Non | **4** | Multi-étapes (déjà prévu) + optimisation d'ordre : Valhalla `optimized_route` | Limite raisonnable d'étapes (~50) |
| Favoris synchronisés appareil ↔ site | Local uniquement | **4** | Supabase Auth + table `favorites`, résolution de conflits par horodatage | Compte optionnel |
| Options d'apparence (teintes, icône, silhouette) | Thème clair/sombre/solaire | **5** | Teintes d'accent ; icônes adaptatives alternatives via `activity-alias` | Cosmétique |
| Couverture France + 12 pays | France (BAN) + Europe via Photon et Valhalla | **2–3** | Valhalla auto-hébergé : extraits Geofabrik par pays ; géocodage Photon | RAM/stockage serveur par pays |
| CarPlay | Sans objet | **5** = Android Auto | Android for Cars App Library (catégorie navigation) | Validation Google requise |

## 2. Ce que Roadly fait déjà et que la page ne met pas en avant

- Fournisseurs remplaçables à chaud (URLs dans les réglages).
- Fonctionnement sans compte, sans clé, sans télémétrie.
- Saisie de coordonnées GPS (décimal et DMS) comme destination.
- Thème solaire automatique.

## 3. Ordre proposé pour la Phase 2 (prochaine spec)

1. Moteur de suivi d'itinéraire : projection sur la polyligne, distance à la prochaine manœuvre, détection de sortie (> 40 m pendant > 5 s), recalcul.
2. Guidage vocal `fr-FR` (TTS Android) avec anticipation (500 m / 200 m / « maintenant ») et anti-répétition.
3. Service de premier plan + notification persistante ; mode conduite (tailles ×1,3, interactions minimales).
4. Bandeau « à venir » (équivalent fonctionnel d'Horizon) : manœuvres et voies, puis trafic/zones en Phase 3.
5. Limitation de vitesse + alerte de dépassement configurable (nécessite Valhalla auto-hébergé → prérequis VPS).
6. Tests sur appareil réel à chaque étape (téléphone HONOR disponible via ADB).

## 4. Points de vigilance juridiques (France)

- Avertisseurs de radars : interdits (art. R413-15). Afficher uniquement des zones de danger/vigilance génériques, sans position exacte, et désactiver la fonction dans les pays où même cela est interdit (Suisse).
- Signalements communautaires : données de position = données personnelles ; minimisation, expiration, pas de profilage.
- Partage de trajet : consentement explicite, révocable, lien à durée limitée.
- Publicité : si un jour introduite, jamais pendant la conduite (distraction) ; Roadly n'en prévoit pas.
