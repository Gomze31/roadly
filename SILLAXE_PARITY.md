# SILLAXE_PARITY.md — matrice de parité Sillaxe → Roadly

Référence : https://sillaxe.app/ et la fiche App Store « Sillaxe : GPS & radars » (iPhone + CarPlay). Détail des fonctions vérifiées : `docs/SILLAXE-ANALYSE.md`. Sources de données et contraintes : `docs/BENCHMARK-SILLAXE.md`, `docs/API-PROVIDERS.md`.

Roadly reproduit l'**expérience** (écrans, parcours, densité d'information, interactions) avec son identité graphique propre. Aucune ressource, aucun code, aucun élément de marque de Sillaxe n'est repris.

Règle absolue : **aucune fonction fictive**. Un écran n'est « ✅ » que s'il est branché sur une source réelle ; sans source, il est « ⛔ » et l'interface le dit (« Bientôt », « — », explication de ce qui manque).

Légende des statuts :

| Statut | Sens |
|---|---|
| ✅ | fonctionnelle, testée, branchée sur une source réelle |
| 🟡 | partielle (une partie des sous-fonctions, ou non vérifiée en conduite) |
| ⛔ | bloquée par une source externe non configurée (code prêt ou interface prête) |
| ⬜ | non commencée |

Lots (section 15 de la mission) : L1 design system · L2 accueil/carte/recherche · L3 itinéraires et navigation avancée · L4 Horizon, trafic, alertes · L5 carburants, bornes, parkings · L6 planificateur et tournées · L7 profils véhicules · L8 communauté · L9 compte, statistiques, Replay · L10 gamification et personnalisation · L11 abonnement et premium · L12 Android Auto et finitions.

Mise à jour : 2026-10-30 — audit initial puis LOT 1 (design system). Voir « Journal » en bas.

---

## 0. Audit du projet existant (2026-10-30)

Préservé tel quel (ne pas dégrader) :

- `core:navigation` : `NavigationSession`, `RouteTracker`, `AnnouncementPolicy`, `SpeedSmoother`, `NavigationService` (premier plan `location`), notification avec « Arrêter », `AndroidSpeechService` (TTS fr-FR, focus audio), `ValhallaTraceSpeedLimitProvider`.
- 224 tests unitaires JVM (`testDebugUnitTest :core:model:test :core:common:test`) et 3 tests instrumentés verts sur HONOR Magic 8 Pro (`FavoriteDaoTest` ×2, `GlassSearchBarTest` ×1).
- Modules (15) : `app`, `core:{model,common,designsystem,data,location,map,geocoding,routing,navigation}`, `feature:{home,search,route,settings,navigation}`.
- Fournisseurs sans clé : BAN + Photon (géocodage), Valhalla FOSSGIS (routage, fair-use), styles MapLibre (URL dans les réglages). Limitation de vitesse et alerte de dépassement : code prêt, actives seulement avec un Valhalla auto-hébergé.

Constats de l'audit (écarts avec Sillaxe) :

1. Interface : composants « verre » corrects mais génériques (icônes Material brutes, panneaux empilés sans hiérarchie, pas de bandeau Horizon, pas de barre d'information de guidage, badge vitesse peu lisible, fiche lieu qui déborde, tracé masqué par les cartes d'itinéraire).
2. Pas de bouton de signalement sur l'écran de guidage, pas de bouton « replier » le bandeau, pas de boussole.
3. Écran d'itinéraires : liste de cartes au lieu du panneau « Votre choix » (durée en grand, arrivée, distance, alternatives compactes, « Ajouter un arrêt », bouton « Rouler »).
4. Aucun des écrans 6 à 25 de la mission n'existe (Horizon, signalements, stations, bornes, parkings, planificateur, arrêts, profil, classements, ligues, défis, trophées, historique, replay, personnalisation, profils véhicules multiples, abonnement, notifications, assistance).
5. Pas de backend, pas de compte, pas de stockage des trajets.
6. Pas de mode paysage vérifié, pas d'Android Auto, pas de widgets, pas de raccourcis, pas de commandes vocales.

---

## 1. Écrans (25) — état de reproduction

| # | Écran Sillaxe | Lot | Statut | État Roadly / ce qui manque |
|---|---|---|---|---|
| 1 | Accueil avec carte | L2 | 🟡 | Carte, position, recherche, domicile/travail, recentrage, **boussole (L2)**, **bouton Signaler (L2, envoi L8)**, **toucher sa position → copier/partager (L2)**, **puces Stations/Bornes/Parkings/Aires → recherche à proximité (L2)**. Manque : carte de profil (L9), autres conducteurs (L8), couches sur la carte (L5) |
| 2 | Recherche | L2 | 🟡 | BAN + Photon + coordonnées, récents, favoris, **catégories à proximité (Photon `reverse` + `osm_tag`, triées par distance, distance affichée) (L2)**. Manque : horaires d'ouverture (⛔ non fournis par Photon), contacts/calendrier (L6), dictée (L12) |
| 3 | Fiche de lieu | L2 | 🟡 | Distance, itinéraire, favori, domicile/travail. Manque : horaires, téléphone, partage, « Enregistrer un repère », parking à proximité, mise en page Sillaxe |
| 4 | Choix des itinéraires | L3 | 🟡 | 3 variantes, carte, coût carburant, panneau « Votre choix » (L1), **« Partir à » / « Arriver à » (L3)**, **puces éviter péages / autoroutes / ferries (L3)**. Manque : montant des péages (⛔), trafic (⛔), « Ajouter un arrêt » (L6), feux/ralentisseurs comptés (⛔ données) |
| 5 | Navigation GPS | L3 | 🟡 | Bandeau repliable, voies, Horizon, barre ETA · min · km, vitesse, arrivée, recalcul, GPS perdu, notification, TTS, Signaler, paysage, **« Changer d'itinéraire » en cours de trajet (L3)**. Manque : limitation (⛔ Valhalla auto-hébergé), ajout d'arrêt (L6), autres conducteurs (L8) |
| 6 | Horizon | L4 | 🟡 | Ruban « route à venir » livré (L1) : manœuvres et arrivée réelles, échelle log 0–5 km. Manque : virages (courbure), ralentissements/travaux/fermetures/zones/services (⛔ sources) |
| 7 | Signalements | L8 | 🟡 | Bouton « Signaler » en guidage + feuille des 9 types (désactivés, explication). Manque : envoi, confirmation, invalidation, expiration, carte, points, historique, modération (backend Supabase) |
| 8 | Stations-service | L5 | 🟡 | Liste à proximité (OSM via Photon, L2). ⛔ prix : data.economie.gouv.fr (flux instantané, Licence Ouverte) ; comparaison, détour, horaires |
| 9 | Bornes électriques | L5 | 🟡 | Liste à proximité (OSM, L2). ⛔ puissance/connecteurs/prix : IRVE consolidé transport.data.gouv.fr ; disponibilité temps réel non fournie gratuitement |
| 10 | Parkings | L5 | 🟡 | Liste à proximité (OSM `amenity=parking`, L2). ⛔ tarifs/hauteur/places : open data villes (BNLS) |
| 11 | Planificateur de trajet | L6 | ⬜ | Jusqu'à 10 arrêts, réorganisation, optimisation, pauses, « arriver à » |
| 12 | Gestion des arrêts | L6 | ⬜ | Ajout pendant le guidage, réordonner, supprimer, mode tournée |
| 13 | Profil utilisateur | L9 | ⬜ | Pseudonyme, avatar, bannière, km, trajets, points, public/privé/masqué |
| 14 | Classements | L10 | ⬜ | Mois, tous temps, amis (backend) |
| 15 | Ligues | L10 | ⬜ | Ligues mensuelles, promotion/relégation |
| 16 | Défis | L10 | ⬜ | Défis hebdomadaires |
| 17 | Trophées | L10 | ⬜ | Trophées locaux (pays traversés, km, séries) |
| 18 | Historique | L9 | ⬜ | Trajets locaux (tracé, date, distance, durée, vitesse moyenne, coût, arrêts, événements) |
| 19 | Replay | L9 | ⬜ | Rejeu sur carte, timeline, vitesse ×0,5–×8, événements |
| 20 | Paramètres | L2/L11 | 🟡 | Véhicule, apparence, voix et sons, alertes vitesse, préférences d'itinéraire, données, confidentialité, fournisseurs, à propos. Manque : organisation Sillaxe (groupes, lignes avec icônes), compte, notifications, autorisations, choix publicitaires, assistance |
| 21 | Personnalisation | L10 | ⬜ | Teinte du cockpit, icône alternative, avatars, silhouettes de véhicule |
| 22 | Profils de véhicules | L7 | 🟡 | 1 profil (type, carburant, conso, prix). Manque : plusieurs profils, moto/taxi/PL/VUL/VE, gabarit, essieux, Crit'Air, influence sur le routage |
| 23 | Abonnement et premium | L11 | ⬜ | Architecture gratuit/premium (`Entitlements`), écran de présentation, gestion |
| 24 | Centre de notifications | L11 | ⬜ | Rappels de départ, péages flux libre, défis, amis |
| 25 | Assistance | L11 | ⬜ | Demandes (bug / idée / autre), fil « Mes demandes », état du service |

---

## 2. Navigation (mission §4)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| GPS temps réel | — | ✅ | FusedLocation, 1 Hz en guidage |
| Guidage vocal | — | ✅ | TTS fr-FR, 3 paliers, anti-répétition |
| Bandeau de direction | L3 | ✅ | À restyler (icône en tuile, distance en grand, rue) |
| Indication des voies | L3 | 🟡 | Décodage et affichage faits ; aucune voie dans les réponses FOSSGIS observées |
| Sorties de rond-point | — | ✅ | Pastille « 2e sortie », `exit_number` |
| Compteur de vitesse | L3 | ✅ | Vitesse brute GPS |
| Limitations de vitesse | — | ⛔ | `trace_attributes` ; Valhalla auto-hébergé requis |
| Alertes de dépassement | — | ⛔ | Bip + badge rouge ; dépend de la limitation |
| Zones de vigilance | L4 | ⛔ | Base de zones à constituer ; France : jamais un point exact (R413-15) ; Suisse : désactivé |
| Alertes sonores, bips d'approche | L4 | ⬜ | |
| Horizon (route à venir) | L4 | ⬜ | Manœuvres et virages d'abord, puis trafic/zones |
| Virages à venir | L4 | ⬜ | Courbure de la polyligne |
| Ralentissements, travaux, routes fermées | L4 | ⛔ | Bison Futé DATEX II, Autoroutes.fr ; fournisseur à conventionner |
| Recalcul automatique | — | ✅ | Non vérifié en conduite réelle |
| Itinéraire alternatif en cours de trajet | L3 | ✅ | Feuille « Changer d'itinéraire » : 3 variantes depuis la position projetée, bascule sans quitter le guidage |
| Changement de destination en guidage | — | ✅ | |
| Ajout d'arrêts pendant le guidage | L6 | ⬜ | Valhalla multi-locations déjà supporté |
| Autres conducteurs sur la carte | L8 | ⬜ | Backend ; décalage ≥ 1 km et ≥ 90 s, désactivable |
| Perte GPS | — | ✅ | Extrapolation plafonnée, badge après 10 s |
| Écran verrouillé / arrière-plan | — | ✅ | Service de premier plan, notification |

## 3. Trafic et communauté (§5)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| 9 types de signalement | L8 | ⬜ | Accident, bouchon, obstacle, route fermée, travaux, météo, nid-de-poule, feu de chantier, danger |
| Création rapide (bouton sur le guidage) | L8 | ⬜ | Interface en L3, envoi en L8 |
| Confirmation / invalidation / expiration | L8 | ⬜ | Supabase + Edge Function d'expiration |
| Affichage sur la carte | L8 | ⬜ | Couche MapLibre |
| Points, historique des contributions | L8/L10 | ⬜ | Calcul côté serveur, anti-triche |
| Modération, détection des incohérences | L8 | ⬜ | |
| Autres utilisateurs sur la carte | L8 | ⬜ | Représentation discrète, décalée |

## 4. Données routières et services (§6)

| Donnée | Lot | Statut | Source retenue |
|---|---|---|---|
| Carburants SP95/SP98/E10/E85/Gazole/GPL, prix sur la carte, comparaison, détour, horaires | L5 | ⛔ | data.economie.gouv.fr `prix-carburants-fichier-instantane-test-ods-copie` (Licence Ouverte, ~10 min) |
| Bornes : puissance, connecteurs, prix kWh, disponibilité | L5 | ⛔ | IRVE consolidé (transport.data.gouv.fr) ; disponibilité : non disponible sans contrat |
| Aires de repos et de service | L5 | ⬜ | OSM `highway=services|rest_area` (Overpass) |
| Parkings gratuits/payants | L5 | ⛔ | OSM + BNLS |
| Ponts bas | L7 | ⬜ | OSM `maxheight` + profil véhicule |
| Ralentisseurs | L4 | ⬜ | OSM `traffic_calming` |
| Péages, péages en flux libre | L3/L11 | ⛔ | Pas d'API publique ; `hasToll` Valhalla seulement ; montant jamais inventé |
| ZFE | L7 | ⬜ | Source à identifier (données ZFE communales) |
| Date de mise à jour affichée | L5 | ⬜ | Obligatoire sur chaque donnée affichée |

## 5. Itinéraires et planification (§7)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| Comparaison de plusieurs itinéraires | — | ✅ | Rapide / court / sans péage |
| Coût carburant | — | ✅ | Profil véhicule saisi ; jamais de prix par défaut |
| Coût des péages | L3 | ⛔ | Présence oui/non seulement |
| Durée, distance, heure d'arrivée | — | ✅ | |
| Trafic prévu | L4 | ⛔ | |
| Départ planifié | — | ✅ | Décale l'heure d'arrivée |
| Arrivée à une heure souhaitée | L3 | ✅ | Départ = arrivée − durée du plus rapide (sans trafic prévisionnel) |
| Rappel avant le départ planifié | L6 | ⬜ | WorkManager |
| Jusqu'à 10 arrêts, réorganisation manuelle | L6 | ⬜ | |
| Optimisation automatique | L6 | ⬜ | Valhalla `optimized_route` |
| Pauses, aires, arrêts personnalisés | L6 | ⬜ | |
| Mode tournée | L6 | ⬜ | |
| Modifier le trajet sans quitter le guidage | L6 | 🟡 | Changer de destination : fait ; arrêts : non |

## 6. Profils de véhicules (§8)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| Voiture / moto / taxi / PL / VUL / VE | L7 | 🟡 | `VehicleType` = CITY, SEDAN, SUV, VAN ; à étendre |
| Hauteur, largeur, longueur, poids, essieux | L7 | ⬜ | Valhalla `truck` costing (`height`, `width`, `length`, `weight`, `axle_load`) |
| Consommation, type de carburant | — | ✅ | |
| Crit'Air | L7 | ⬜ | |
| Influence réelle sur les itinéraires | L7 | ⬜ | Seulement avec `truck` costing et données OSM |
| Restrictions PL, péages PL | L7 | ⛔ | Péages PL : pas de source |

## 7. Premium (§9)

| Fonction | Lot | Statut |
|---|---|---|
| Architecture gratuit/premium (`Entitlements`, flags testables) | L11 | ⬜ |
| Absence de publicité | — | ✅ (Roadly n'a pas de publicité) |
| Zones de vigilance complètes, alertes sans trajet | L4 | ⛔ |
| Profils véhicules avancés | L7 | ⬜ |
| Itinéraires temps réel longs trajets | L4 | ⛔ |
| Mode tournée | L6 | ⬜ |
| Partage de trajet en direct | L9 | ⬜ |
| Rappels péages flux libre | L11 | ⛔ (géofences des portiques à constituer) |
| Replay | L9 | ⬜ |
| Synchronisation favoris, domicile, travail | L9 | ⬜ |
| Historique kilométrique, coût des déplacements | L9 | ⬜ |
| Couleurs, icônes, avatars, silhouettes | L10 | ⬜ |

## 8. Gamification (§10)

| Fonction | Lot | Statut |
|---|---|---|
| Points, récompenses | L10 | ⬜ (serveur, anti-triche) |
| Classement mensuel / général | L10 | ⬜ |
| Ligues mensuelles | L10 | ⬜ |
| Défis hebdomadaires | L10 | ⬜ |
| Trophées | L10 | ⬜ |
| Profil public, amis | L10 | ⬜ |
| Km parcourus, bilan mensuel | L9 | ⬜ |
| Avatars, bannières, récompenses à débloquer | L10 | ⬜ |

## 9. Android (§11)

| Fonction | Lot | Statut |
|---|---|---|
| Android Auto (catégorie navigation) | L12 | ⬜ |
| App Shortcuts (domicile, travail, rechercher, signaler) | L12 | ⬜ |
| Widgets | L12 | ⬜ |
| Notifications (guidage) | — | ✅ |
| Commandes vocales (App Actions : maison, travail, adresse, accident, destination) | L12 | ⬜ |
| Intents (geo:, google.navigation:, partage) | L12 | ⬜ |
| Partage natif | L2 | ✅ (fiche lieu : copier l'adresse, partager) |
| Mode paysage | L3 | 🟡 (accueil, itinéraires, guidage disposés en deux colonnes ; guidage vérifié sur appareil) |
| Permissions | — | ✅ |
| Localisation en arrière-plan, service persistant | — | ✅ |

## 10. Replay et historique (§12)

| Fonction | Lot | Statut |
|---|---|---|
| Enregistrement du tracé (Room `trips`, `trip_points`, `trip_events`) | L9 | ⬜ |
| Date, distance, durée, vitesse moyenne, coût, arrêts, événements | L9 | ⬜ |
| Replay : carte, timeline, vitesse de lecture, événements | L9 | ⬜ |

## 11. Qualité (§14)

| Exigence | Statut |
|---|---|
| Aucune fonction fictive, aucun bouton inactif | ✅ (puces « Bientôt » désactivées et marquées) |
| Aucune donnée simulée | ✅ |
| Tous les écrans testés | 🟡 (ViewModels testés ; écrans : 1 test Compose) |
| Compilation stable | ✅ |

---

## Journal

- 2026-10-30 — Audit complet, création de ce fichier.
- 2026-10-31 — **LOT 1 livré** : design system (`docs/DESIGN-SYSTEM.md`) — tokens violet/orange, surfaces opaques et verre, `Overline`/`StatValue`, boutons Primary/Brand/Secondary/Danger, boutons ronds, tuiles d'icône, `NavInfoBar`, `HorizonStrip` (échelle log, 3 tests), `ListRow`/`ToggleRow`/`SegmentedControl`, `RoadlyTopBar`, `RouteHeadline`/`RouteOptionRow`, teintes de cockpit (`CockpitTint`) prêtes pour L10 ; halo + liseré du tracé MapLibre ; `fitBounds` à marges distinctes. Écrans réalignés sur la référence : accueil (tuiles Domicile/Travail, recentrage rond marque, catégories « Bientôt »), recherche (catégories « Bientôt », favoris/récents en cartes, fiche lieu avec tuiles d'action + copier/partager réels), itinéraires (en-tête destination / « Partir de », panneau « Votre choix » : durée en grand, arrivée, distance, puces, alternatives compactes, « Ajouter un arrêt » Bientôt, « Rouler »), guidage (bandeau repliable, bouton Signaler → feuille des 9 types désactivés avec explication, voix, destination, compteur + panneau de limitation, Horizon alimenté par les manœuvres réelles, barre ETA · min · km · recentrer · arrêter, **mode paysage**), réglages (groupes, lignes à tuiles d'icône, sélecteurs segmentés, fournisseurs repliés). Build `assembleDebug` vert, **230 tests unitaires** verts. **Vérifié sur HONOR Magic 8 Pro** (à l'arrêt, Toulouse → Place du Capitole) : accueil, recherche, fiche lieu, itinéraires, guidage portrait et paysage, feuille « Signaler » ; aucun `FATAL EXCEPTION` dans logcat.
  - Écrans 1, 2, 3, 4, 5, 20 : passent de « générique » à « fidèle » graphiquement ; leur statut fonctionnel reste 🟡 (voir tableau 1).
  - Écran 6 (Horizon) : composant livré et affiché en guidage avec manœuvres + arrivée ; trafic/zones/services restent ⛔ (L4).
  - Écran 7 (Signalements) : interface de saisie livrée, envoi ⛔ (L8).
- 2026-10-31 — **LOT 2 livré** (accueil, carte, recherche) : `NearbyPoiProvider` / `PhotonNearbyPoiProvider` (Photon `reverse` + `radius` + `osm_tag`, fixture réelle, 2 tests) ; recherche : puces Stations / Bornes / Parkings / Aires / Restaurants réelles (lieux OSM triés par distance, distance par ligne, états « position inconnue » et « aucun lieu », pied de carte rappelant l'absence de prix/disponibilité), route `SearchRoute(category)` ; accueil : boussole animée (apparaît quand la carte est tournée, remet le nord), bouton Signaler (feuille partagée dans `core:designsystem`), position courante → feuille Copier/Partager (adresse + coordonnées + lien OSM), puces de catégories branchées sur la recherche, « Incidents » reste « Bientôt ». 235 tests verts ; vérifié sur HONOR : stations à 630 m, parkings à 730 m, feuille position, bouton Signaler.
- 2026-10-31 — **LOT 3 livré** (itinéraires et navigation avancée) : mode « Arriver à » (sélecteur Partir à / Arriver à, départ déduit du plus rapide, arrivées recalées, test `ArrivalShiftTest`) ; puces d'options éviter péages / autoroutes / ferries sur le panneau « Votre choix » (persistées, appliquées aux 3 variantes et au recalcul) ; en guidage, bouton « Itinéraires » → feuille « Changer d'itinéraire » (3 variantes depuis la position projetée, arrivée, péage, bascule immédiate du guidage ; test VM). 238 tests verts ; vérifié sur HONOR (bascule vers « Le plus court » : 26 min · 4,6 km).
