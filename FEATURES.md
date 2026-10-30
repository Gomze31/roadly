# FEATURES.md — statut des fonctionnalités Roadly

Référentiel : `docs/SILLAXE-ANALYSE.md` (fonctions vérifiées sur les pages publiques de Sillaxe) et `docs/BENCHMARK-SILLAXE.md` (sources de données et contraintes). Statuts : **non commencée** · **en cours** · **fonctionnelle** · **bloquée** (dépend d'une source externe non configurée). « Palier Sillaxe » = gratuit (G) ou Sillaxe+ (+) chez Sillaxe ; Roadly ne reproduit pas le modèle économique, l'information sert à prioriser.

Mise à jour : 2026-10-30 (fin de la v1, branche `feat/roadly-v1`).

## 1. Navigation et guidage

| Fonction | Palier | Statut Roadly | Phase | Notes |
|---|---|---|---|---|
| Position GPS en direct, cap, vitesse | G | fonctionnelle | 1 | FusedLocation ; 1 Hz carte, 2 Hz suivi |
| Orientation de la carte (suivi cap) | G | fonctionnelle | 1 | Mode FOLLOW |
| Calcul d'itinéraires (3 variantes) | G | fonctionnelle | 1 | Valhalla ; rapide / court / sans péage |
| Guidage vocal français | G | non commencée | 2 | TTS Android `fr-FR`, anti-répétition |
| Bandeau de manœuvre (prochaine instruction, distance) | G | non commencée | 2 | Manœuvres Valhalla déjà décodées |
| Affichage des voies | G | non commencée | 2 | `lanes` Valhalla si `turn:lanes` OSM |
| Ronds-points avec sortie | G | non commencée | 2 | `roundabout_exit_count` déjà mappé |
| Suivi d'itinéraire, détection de sortie, recalcul | G | non commencée | 2 | Projection sur polyligne |
| Temps/distance restants, heure d'arrivée en guidage | G | non commencée | 2 | ETA statique déjà en comparaison |
| Limitation de vitesse de la route | G | bloquée | 2 | Nécessite Valhalla auto-hébergé (`trace_attributes`) ; « — » affiché |
| Alerte sonore de dépassement (option) | G | non commencée | 2 | Dépend de la limitation |
| Limitation abaissée par pluie (option) | G | non commencée | 3 | Dépend météo |
| Changer de destination / ajouter un arrêt en guidage | G | non commencée | 2 | |
| Jusqu'à 10 arrêts réordonnables | G | non commencée | 4 | Valhalla multi-locations déjà supporté côté provider |
| Départ planifié (heure de départ) | G | fonctionnelle | 1 | Décale seulement l'heure d'arrivée |
| « Arriver à l'heure » + rappel avant départ | G | non commencée | 4 | WorkManager |
| Mode paysage | G | en cours | 1 | Compose s'adapte ; non vérifié visuellement |
| Thème clair / sombre / automatique | G | fonctionnelle | 1 | Auto solaire (position) + système |
| Mode Silence | G | non commencée | 2 | Avec le TTS |
| Import GPX | ? | non commencée | 5 | |
| Guidage allégé hors connexion | G | non commencée | 5 | Cache tuiles MapLibre + itinéraire mémorisé |
| ZFE / Crit'Air | ? | non commencée | 3 | Source à identifier |
| Tunnels (perte GPS) | G | non commencée | 2 | Extrapolation sur la polyligne |

## 2. Horizon (route à venir)

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Bandeau « à venir » : manœuvres, virages | G | non commencée | 2 | Design original Roadly (verre, bas d'écran) |
| Ralentissements, bouchons, incidents dans le bandeau | G | bloquée | 3 | Dépend trafic |
| Services (stations, aires) dans le bandeau | G | bloquée | 3 | Dépend POI |
| Zones de vigilance dans le bandeau | G | bloquée | 3 | Dépend base de zones + cadre légal |

## 3. Recherche, lieux, favoris

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Recherche adresse France (BAN) | G | fonctionnelle | 1 | |
| Recherche POI / villes / Europe (Photon) | G | fonctionnelle | 1 | POI après les adresses BAN : tri à améliorer |
| Coordonnées GPS (décimal, DMS) | G | fonctionnelle | 1 | |
| Récents (50), favoris, domicile, travail | G | fonctionnelle | 1 | Local (Room) |
| Fiche lieu : distance, itinéraire, favori, domicile/travail | G | fonctionnelle | 1 | Retour visuel après « Définir » à ajouter |
| Appui long sur la carte → destination | G | fonctionnelle | 1 | |
| Toucher sa position → adresse copiable/partageable | G | en cours | 1 | Adresse affichée ; copie/partage non faits |
| Horaires d'ouverture des lieux | G | non commencée | 3 | OSM `opening_hours` via Photon/Overpass |
| Adresses des contacts, lieux de rendez-vous | G | non commencée | 4 | Permissions contacts/calendrier |
| Commande vocale (Assistant / App Actions) | G | non commencée | 5 | |
| Favoris synchronisés (web, multi-appareils) | + | non commencée | 4 | Supabase |

## 4. Itinéraires et coûts

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Comparaison visuelle 3 itinéraires sur carte | G | fonctionnelle | 1 | |
| Coût carburant (profil véhicule saisi) | G | fonctionnelle | 1 | Jamais de prix par défaut |
| Prix des péages | G | bloquée | 3 | Pas d'API publique ; barème à maintenir |
| Itinéraire « économique » | G | non commencée | 3 | Valhalla : coût combiné distance/péage/conso |
| Trafic pris en compte dans le calcul | G | bloquée | 3 | Fournisseur trafic |
| Itinéraire adapté au gabarit (hauteur/largeur/poids) | + | non commencée | 4 | Valhalla `truck` |

## 5. Trafic et incidents

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Bouchons, accidents, travaux, fermetures (sources officielles) | G | bloquée | 3 | Bison Futé DATEX II, Autoroutes.fr ; aucun trafic simulé |
| Routes coupées évitées | G | bloquée | 3 | `exclude_polygons` Valhalla |
| Re-routage sur bouchon | G | bloquée | 3 | |
| Météo sur la carte | G | bloquée | 3 | Météo-France / Open-Meteo |
| ETA par apprentissage collectif | G | non commencée | 5 | Backend, opt-in |

## 6. Zones de vigilance

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Zones de vigilance (France, jamais un point exact) | G | bloquée | 3 | Base de zones à constituer ; art. R413-15 |
| Alertes visuelles / vocales / sonores anticipées | G/+ | non commencée | 3 | |
| Limitation de la zone | + | bloquée | 3 | |
| Alerte sans itinéraire | + | non commencée | 3 | Service premier plan |
| Désactivation par pays (Suisse) | — | non commencée | 3 | Géofence pays |

## 7. Stations, bornes, parkings, aires, ponts, ralentisseurs

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Stations-service + prix (6 carburants) | G | bloquée | 3 | data.economie.gouv.fr flux instantané ; puce « Bientôt » en v1 |
| Bornes de recharge (puissance, prix kWh si connu) | G | bloquée | 3 | IRVE transport.data.gouv.fr |
| Parkings (tarifs, hauteur, horaires) | G | bloquée | 3 | OSM + open data villes |
| Aires de repos et de service | G | non commencée | 3 | OSM `highway=services|rest_area` |
| Ponts bas | G/+ | non commencée | 3 | OSM `maxheight` + profil véhicule |
| Ralentisseurs | G | non commencée | 3 | OSM `traffic_calming` |

## 8. Planificateur et tournées

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Multi-destinations (10), réorganisation | G | non commencée | 4 | |
| Optimisation de tournée | + | non commencée | 4 | Valhalla `optimized_route` |
| Pauses planifiées, aires | G | non commencée | 4 | |
| Heure d'arrivée souhaitée | G | non commencée | 4 | |

## 9. Stationnement et retour à pied

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Parking proche de la destination | G | bloquée | 3 | |
| Chemin à pied jusqu'à la destination | G | non commencée | 4 | Valhalla `pedestrian` |
| Retrouver son véhicule (position mémorisée) | G | non commencée | 4 | Local |

## 10. Communauté

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Signalements (accident, bouchon, obstacle, route fermée, nid-de-poule, feu de chantier, travaux, ralentisseur, chaussée dégradée) | G | bloquée | 3 | Backend Supabase à créer |
| Confirmation / démenti, expiration | G | bloquée | 3 | |
| Points communautaires | G | non commencée | 4 | |
| Autres conducteurs sur la carte (décalés) | G | non commencée | 5 | Vie privée |

## 11. Gamification

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Profil, km parcourus, temps, trajets | G | non commencée | 4 | Table `trips` locale |
| Classement, ligues mensuelles, défis, trophées, avatars | G | non commencée | 4 | Backend ; facultatif |

## 12. Fonctions « + »

| Fonction | Palier | Statut | Phase | Notes |
|---|---|---|---|---|
| Profils véhicule PL / taxi / dimensions | + | non commencée | 4 | |
| Partage de trajet en direct | + | non commencée | 4 | Supabase + page web |
| Rappel péage flux libre | + | non commencée | 4 | Géofences des portiques (données à constituer) |
| Replay des trajets | + | non commencée | 4 | Traces locales |
| Historique des dépenses | + | non commencée | 4 | |
| Personnalisation (teintes, icône, silhouettes) | + | non commencée | 5 | |

## 13. Paramètres

| Réglage | Statut | Notes |
|---|---|---|
| Apparence (clair / sombre / auto) | fonctionnelle | |
| Véhicule (type, carburant, conso, prix) | fonctionnelle | Dimensions : Phase 4 |
| Fournisseurs (URLs) | fonctionnelle | Validation des URLs ; HTTPS requis |
| Données locales (effacer historique / favoris) | fonctionnelle | |
| Confidentialité (texte) | fonctionnelle | |
| À propos / licences | fonctionnelle | |
| Voix et sons, volume, mode Silence | non commencée | Phase 2 |
| Alertes de vitesse / de vigilance | non commencée | Phase 2 / 3 |
| Préférences d'itinéraire (péages, autoroutes, ferries) | non commencée | Phase 2 (options Valhalla) |
| Domicile / travail dans les réglages | en cours | Définis via la fiche lieu ; pas d'écran dédié |
| Compte, abonnement, assistance | non commencée | Phase 4 (Supabase) |

## 14. Plateforme

| Fonction | Statut | Notes |
|---|---|---|
| APK debug installable | fonctionnelle | ~115 Mo (toutes ABI) ; App Bundle en Phase 5 |
| Tests unitaires | fonctionnelle | 83 tests |
| Tests instrumentés | en cours | Lancés sur HONOR Magic 8 Pro |
| Service de premier plan / notification | non commencée | Phase 2 |
| Widgets, Android Auto | non commencée | Phase 5 |
| Backend Supabase | non commencée | Phase 3 |
