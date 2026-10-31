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
6. Pas de mode paysage vérifié, pas d'Android Auto, pas de widgets, pas de raccourcis, pas de commandes vocales. *(Depuis : paysage L3, widget / raccourcis / intents L12 ; Android Auto toujours ⬜.)*

---

## 1. Écrans (25) — état de reproduction

| # | Écran Sillaxe | Lot | Statut | État Roadly / ce qui manque |
|---|---|---|---|---|
| 1 | Accueil avec carte | L2 | 🟡 | Carte, position, recherche, domicile/travail, recentrage, **boussole (L2)**, **bouton Signaler (L2, envoi L8)**, **toucher sa position → copier/partager (L2)**, **puces Stations/Bornes/Parkings/Aires → recherche à proximité (L2)**. Manque : carte de profil (L9), autres conducteurs (L8), couches sur la carte (L5) |
| 2 | Recherche | L2 | 🟡 | BAN + Photon + coordonnées, récents, favoris, **catégories à proximité (Photon `reverse` + `osm_tag`, triées par distance, distance affichée) (L2)**. Manque : horaires d'ouverture (⛔ non fournis par Photon), contacts/calendrier (L6), dictée (L12) |
| 3 | Fiche de lieu | L2 | 🟡 | Distance, itinéraire, favori, domicile/travail. Manque : horaires, téléphone, partage, « Enregistrer un repère », parking à proximité, mise en page Sillaxe |
| 4 | Choix des itinéraires | L3 | 🟡 | 3 variantes, carte, coût carburant, panneau « Votre choix » (L1), **« Partir à » / « Arriver à » (L3)**, **puces éviter péages / autoroutes / ferries (L3)**. Manque : montant des péages (⛔), trafic (⛔), « Ajouter un arrêt » (L6), feux/ralentisseurs comptés (⛔ données) |
| 5 | Navigation GPS | L3 | 🟡 | Bandeau repliable, voies, Horizon, barre ETA · min · km, vitesse, arrivée, recalcul, GPS perdu, notification, TTS, Signaler, paysage, **« Changer d'itinéraire » en cours de trajet (L3)**. Manque : limitation (⛔ Valhalla auto-hébergé), ajout d'arrêt (L6), autres conducteurs (L8) |
| 6 | Horizon | L4 | 🟡 | Ruban « route à venir » : manœuvres, arrivée, **virages serrés (courbure du tracé, L4)**, **zones de vigilance (base officielle, L4)**, échelle log 0–5 km. Manque : ralentissements/travaux/fermetures (⛔ trafic), services sur le trajet (L5), densité trafic (⛔) |# SILLAXE_PARITY.md — matrice de parité Sillaxe → Roadly
| 7 | Signalements | L8 | 🟡 | **Client complet (L8)** : feuille des 9 types (accueil et guidage), envoi à la position avec le cap, signalements actifs sur la carte (icône ambre, rafraîchis toutes les 60 s) et dans l'Horizon pendant le guidage, retour « envoyé / limité / impossible ». **Backend fourni** (`backend/supabase/schema.sql`, `docs/BACKEND.md`) : expiration par type côté serveur, 1 signalement/min/appareil, votes (une voix par appareil, jamais sur le sien), 3 démentis désactivent, points serveur (+2 auteur, +1 par confirmation), RLS. L'exploitant crée le projet Supabase et saisit URL + clé anon dans Réglages → Communauté ; sinon la feuille reste inactive. Manque : écran de confirmation / démenti sur un signalement touché, historique des contributions (L9/L10), modération humaine (tableau de bord Supabase) |
| 8 | Stations-service | L5 | 🟡 | **Prix réels (L5)** : flux instantané data.economie.gouv.fr (Licence Ouverte, ~10 min), 6 carburants, date de chaque prix, 24h/24, services, tri distance / prix, carburant du profil par défaut, repli OSM si le flux échoue. Manque : prix sur la carte (couche), calcul du détour, horaires détaillés (L5 suite) |
| 9 | Bornes électriques | L5 | 🟡 | Liste à proximité (OSM, L2). ⛔ puissance/connecteurs/prix : IRVE consolidé transport.data.gouv.fr ; disponibilité temps réel non fournie gratuitement |
| 10 | Parkings | L5 | 🟡 | Liste à proximité (OSM `amenity=parking`, L2). ⛔ tarifs/hauteur/places : open data villes (BNLS) |
| 11 | Planificateur de trajet | L6 | 🟡 | **Jusqu'à 10 arrêts, réorganisation (monter/descendre/retirer), optimisation de l'ordre (Valhalla `optimized_route`, repli plus proche voisin), « Arriver à » (L3)** ; itinéraire multi-étapes recalculé. Manque : pauses planifiées / aires sur le trajet, départ planifié avec rappel |
| 12 | Gestion des arrêts | L6 | 🟡 | **Ajout d'un arrêt pendant le guidage (feuille « Changer d'itinéraire » → recherche → relance via l'arrêt)**, réordonner / supprimer sur l'écran d'itinéraires, mode tournée = optimisation. Manque : liste des arrêts restants pendant le guidage, annonce « étape atteinte » |
| 13 | Profil utilisateur | L10 | 🟡 | **Écran « Mon profil » (L10)** : km / trajets / temps locaux, 8 trophées locaux avec progression, pseudonyme d'appareil et points serveur quand le service communautaire est configuré (rpc `get_profile`). Manque : avatar, bannière, visibilité publique/privée (backend) |
| 14 | Classements | L10 | 🟡 | **Classement général par points (rpc `leaderboard`, L10)**, affiché dans « Mon profil » dès que le service est configuré ; sinon ligne inactive explicite. Manque : classement mensuel, amis, ligues |
| 15 | Ligues | L10 | ⬜ | Ligues mensuelles, promotion/relégation |
| 16 | Défis | L10 | ⬜ | Défis hebdomadaires |
| 17 | Trophées | L10 | ⬜ | Trophées locaux (pays traversés, km, séries) |
| 18 | Historique | L9 | 🟡 | **Enregistrement local des trajets guidés (`TripRecorder`, Room v4) : tracé échantillonné 20 m, date, distance, durée, vitesse max/moyenne, coût carburant, arrivé/interrompu ; écran « Mes trajets » avec bilan du mois et totaux (L9)**. Manque : arrêts et événements rencontrés dans le trajet, export |
| 19 | Replay | L9 | 🟡 | **Rejeu sur la carte : tracé, marqueur interpolé, timeline, lecture ×1/×2/×4/×8, « Y retourner » (L9)**. Manque : événements sur la timeline |
| 20 | Paramètres | L2/L11 | 🟡 | Groupes avec icônes : conduite, trajets, **Roadly+ et assistance (L11)**, apparence, personnalisation, voix et sons, alertes, itinéraires, données, confidentialité, communauté, fournisseurs, à propos. Manque : compte, notifications, autorisations ; choix publicitaires sans objet (pas de publicité) |
| 21 | Personnalisation | L10 | 🟡 | **Teinte du cockpit (5 teintes, appliquée à l'app et au guidage, L10)**. Manque : icône alternative, avatars, silhouettes de véhicule |
| 22 | Profils de véhicules | L7 | 🟡 | **Types citadine / berline / SUV / utilitaire / moto / taxi / poids lourd, gabarit (hauteur, largeur, longueur, poids, essieux), Crit'Air (L7)** ; costing Valhalla `truck` / `motorcycle` / `taxi` avec le gabarit, appliqué aux 3 variantes, au recalcul et aux alternatives. Manque : plusieurs profils enregistrés, péages PL (⛔), ZFE (⛔ source) |
| 23 | Abonnement et premium | L11 | 🟡 | **Architecture gratuit / Roadly+ (`Entitlements`, `Feature.plus`, `BillingProvider`, L11)** : écran « Roadly+ » (palier, fonctions incluses / Roadly+, état réel de la boutique). Sans boutique intégrée tout reste inclus et aucun bouton d'achat n'est affiché. Manque : implémentation Google Play Billing (produit à créer par l'exploitant, `docs/PREMIUM.md`), essai, gestion de l'abonnement |
| 24 | Centre de notifications | L11 | ⬜ | Aucune notification à centraliser aujourd'hui (pas de rappel de départ, de péage flux libre ⛔, de défi ni d'ami) ; écran sans objet tant que ces sources n'existent pas |
| 25 | Assistance | L11 | 🟡 | **Demandes bug / idée / autre : partage Android d'un rapport pré-rempli (type, version, appareil), canal au choix de l'utilisateur (L11)**. Fil « Mes demandes » : ⬜ (exige un compte). État du service : voir Réglages → Fournisseurs |

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
| Zones de vigilance | L4 | ✅ | Jeu officiel « Radars automatiques » (Sécurité routière, data.gouv.fr, Licence Ouverte, millésime 2018) en cache Room ; zones de 300 m / 2 km / 4 km selon la limitation, jamais un point exact ; annonce vocale + bip à l'entrée, badge, Horizon ; France seulement (aucune donnée en Suisse) |
| Alertes sonores, bips d'approche | L4 | ✅ | Bip à l'entrée de zone (option) |
| Horizon (route à venir) | L4 | 🟡 | Manœuvres, virages, zones ; trafic ⛔ |
| Virages à venir | L4 | ✅ | `CurveDetector` : ≥ 45° sur ≤ 90 m hors manœuvres |
| Ralentissements, travaux, routes fermées | L4 | ⛔ | Bison Futé DATEX II, Autoroutes.fr ; fournisseur à conventionner |
| Recalcul automatique | — | ✅ | Non vérifié en conduite réelle |
| Itinéraire alternatif en cours de trajet | L3 | ✅ | Feuille « Changer d'itinéraire » : 3 variantes depuis la position projetée, bascule sans quitter le guidage |
| Changement de destination en guidage | — | ✅ | |
| Ajout d'arrêts pendant le guidage | L6 | ✅ | Position projetée → arrêt → destination, bascule immédiate |
| Autres conducteurs sur la carte | L8 | ⬜ | Backend ; décalage ≥ 1 km et ≥ 90 s, désactivable |
| Perte GPS | — | ✅ | Extrapolation plafonnée, badge après 10 s |
| Écran verrouillé / arrière-plan | — | ✅ | Service de premier plan, notification |

## 3. Trafic et communauté (§5)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| 9 types de signalement | L8 | ✅ | `ReportKind` (modèle + enum SQL) |
| Création rapide (bouton sur le guidage) | L8 | ✅ | Accueil et guidage ; position + cap ; 4 tests dépôt |
| Confirmation / invalidation / expiration | L8 | 🟡 | Serveur : `vote_report`, `expire_reports` (pg_cron) ; client : `vote()` prêt, écran de vote à faire |
| Affichage sur la carte | L8 | ✅ | Couche `roadly-reports`, toutes les 60 s |
| Points, historique des contributions | L8/L10 | 🟡 | Points calculés côté serveur (`profiles.points`), affichés dans « Mon profil » (L10) ; historique des contributions : ⬜ |
| Modération, détection des incohérences | L8 | 🟡 | Anti-spam 60 s, 3 démentis ; modération humaine via Supabase |
| Autres utilisateurs sur la carte | L8 | ⬜ | Représentation discrète, décalée |

## 4. Données routières et services (§6)

| Donnée | Lot | Statut | Source retenue |
|---|---|---|---|
| Carburants SP95/SP98/E10/E85/Gazole/GPL, prix, comparaison | L5 | ✅ | data.economie.gouv.fr `prix-des-carburants-en-france-flux-instantane-v2` (Licence Ouverte, ~10 min) ; fixture réelle, 2 tests |
| Prix sur la carte, détour, horaires détaillés | L5 | ⬜ | Couche MapLibre, détour via Valhalla, `horaires` JSON du flux |
| Bornes : puissance, connecteurs, prix kWh, disponibilité | L5 | ⛔ | IRVE consolidé = CSV national volumineux sans API de proximité (vérifié 2026-10-31) ; nécessite un import serveur ou un découpage ; disponibilité temps réel : contrat |
| Aires de repos et de service | L5 | ⬜ | OSM `highway=services|rest_area` (Overpass) |
| Parkings gratuits/payants | L5 | ⛔ | OSM + BNLS |
| Ponts bas | L7 | ⬜ | OSM `maxheight` + profil véhicule |
| Ralentisseurs | L4 | ⬜ | OSM `traffic_calming` |
| Péages, péages en flux libre | L3/L11 | ⛔ | Pas d'API publique ; `hasToll` Valhalla seulement ; montant jamais inventé |
| ZFE | L7 | ⬜ | Source à identifier (données ZFE communales) |
| Date de mise à jour affichée | L5 | ✅ | Chaque prix porte « il y a X h/j » ; zones de vigilance : date de téléchargement + millésime |

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
| Jusqu'à 10 arrêts, réorganisation manuelle | L6 | ✅ | `StopsList`, `StopOrdering.move`, tests VM |
| Optimisation automatique | L6 | ✅ | Valhalla `optimized_route` (`original_index`), repli local, 3 tests |
| Pauses, aires, arrêts personnalisés | L6 | ⬜ | |
| Mode tournée | L6 | 🟡 | = optimisation de l'ordre ; pas encore d'enchaînement « arrêt par arrêt » avec validation |
| Modifier le trajet sans quitter le guidage | L6 | ✅ | Changer de destination, changer d'itinéraire, ajouter un arrêt |

## 6. Profils de véhicules (§8)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| Voiture / moto / taxi / PL / VUL / VE | L7 | ✅ | `VehicleType` + MOTORCYCLE, TAXI, TRUCK ; VE = carburant ÉLECTRIQUE |
| Hauteur, largeur, longueur, poids, essieux | L7 | ✅ | `VehicleDimensions`, Room v3, costing `truck` (`height`, `width`, `length`, `weight`, `axle_count`) ; seules les valeurs saisies sont envoyées |
| Consommation, type de carburant | — | ✅ | |
| Crit'Air | L7 | 🟡 | Saisie et mémorisée ; aucune restriction ZFE appliquée (source ⛔) |
| Influence réelle sur les itinéraires | L7 | ✅ | Poids lourd (gabarit, interdictions PL OSM), moto, taxi via le costing Valhalla ; test de requête |
| Restrictions PL, péages PL | L7 | ⛔ | Péages PL : pas de source |

## 7. Premium (§9)

| Fonction | Lot | Statut |
|---|---|---|
| Architecture gratuit/premium (`Entitlements`, flags testables) | L11 | ✅ | `Feature` (11 fonctions, 5 marquées Roadly+), `Entitlements.includes` (tout inclus sans boutique), `BillingProvider` / `NoBillingProvider`, 5 tests |
| Achat, essai, restauration (Google Play Billing) | L11 | ⬜ | Interface prête (`purchase`, `restore`, `StoreState`) ; produit Play à créer par l'exploitant (`docs/PREMIUM.md`) |
| Absence de publicité | — | ✅ (Roadly n'a pas de publicité) |
| Zones de vigilance complètes, alertes sans trajet | L4 | ⛔ |
| Profils véhicules avancés | L7 | ✅ | Marqués Roadly+ (`Feature.VEHICLE_ADVANCED`) |
| Itinéraires temps réel longs trajets | L4 | ⛔ |
| Mode tournée | L6 | 🟡 | = optimisation de l'ordre ; pas encore d'enchaînement « arrêt par arrêt » avec validation |
| Partage de trajet en direct | L9 | ⬜ |
| Rappels péages flux libre | L11 | ⛔ (géofences des portiques à constituer) |
| Replay | L9 | ✅ | Marqué Roadly+ (`Feature.REPLAY`) |
| Synchronisation favoris, domicile, travail | L9 | ⬜ |
| Historique kilométrique, coût des déplacements | L9 | ⬜ |
| Couleurs, icônes, avatars, silhouettes | L10 | ⬜ |

## 8. Gamification (§10)

| Fonction | Lot | Statut |
|---|---|---|
| Points, récompenses | L10 | 🟡 | Points serveur affichés ; récompenses à débloquer : ⬜ |
| Classement mensuel / général | L10 | 🟡 | Général ✅ (rpc `leaderboard`) ; mensuel ⬜ |
| Ligues mensuelles | L10 | ⬜ | Logique serveur à écrire (documenté dans `docs/BACKEND.md`) |
| Défis hebdomadaires | L10 | ⬜ | Logique serveur à écrire |
| Trophées | L10 | ✅ | 8 trophées locaux (km cumulés, trajets, trajet le plus long), `Trophies.compute`, 2 tests |
| Profil public, amis | L10 | ⬜ | Backend : visibilité et relations à concevoir |
| Km parcourus, bilan mensuel | L9 | ✅ | « Mes trajets » et « Mon profil » |
| Avatars, bannières, récompenses à débloquer | L10 | ⬜ |

## 9. Android (§11)

| Fonction | Lot | Statut |
|---|---|---|
| Android Auto (catégorie navigation) | L12 | ⬜ | `androidx.car.app` absent du cache Gradle hors ligne ; marche à suivre dans `docs/ANDROID.md` (service `CarAppService`, `NavigationTemplate`, déclaration `com.google.android.gms.car.application`) |
| App Shortcuts (domicile, travail, rechercher, signaler) | L12 | 🟡 | **Raccourcis statiques domicile / travail / rechercher (L12)** ; domicile ou travail non défini → recherche + message. « Signaler » : ⬜ (exige le service communautaire et une position) |
| Widgets | L12 | ✅ | Widget 4×1 « Roadly » (domicile, travail, rechercher), jour / nuit, `RoadlyWidgetProvider` (L12) |
| Notifications (guidage) | — | ✅ |
| Commandes vocales (App Actions : maison, travail, adresse, accident, destination) | L12 | 🟡 | « Navigue vers X » de l'Assistant arrive par `google.navigation:q=X` → recherche pré-remplie ou itinéraire direct si coordonnées (L12). Pas de capacités App Actions déclarées (exigent une fiche Play) |
| Intents (geo:, google.navigation:, partage) | L12 | ✅ | `geo:lat,lon`, `geo:lat,lon?q=`, `geo:0,0?q=`, `google.navigation:q=` (`ExternalIntents`, 4 tests) ; partage sortant ✅ (fiche lieu, position, assistance) |
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
- 2026-10-31 — **LOT 4 livré** (Horizon, alertes) : `CurveDetector` (virages serrés par courbure, hors manœuvres) ; zones de vigilance depuis le jeu officiel « Radars automatiques » (data.gouv.fr, Licence Ouverte, 3 350 enregistrements, millésime 2018) téléchargé au premier lancement et tous les 30 jours (Room v2, migration), reconstituées en zones de 300 m / 2 km / 4 km (jamais un point exact), annonce vocale + bip à l'entrée, badge avec limitation, Horizon ; pluie Open-Meteo (toutes les 10 min) avec option « limitation abaissée par pluie » (R413-2) ; réglages « Alertes » (zones, bip, mise à jour manuelle avec compteur et date, pluie). Trafic en direct, travaux, fermetures : toujours ⛔ (aucune source gratuite ; Bison Futé à conventionner). 254 tests verts ; vérifié sur HONOR (3 350 zones chargées, Horizon avec virage + zone sur Toulouse → Montgiscard).
- 2026-10-31 — **LOT 5 (partie carburants) livré** : `FuelStationProvider` / `OpenDataFuelStationProvider` (flux instantané du ministère de l'Économie, Licence Ouverte, requête de proximité ODSQL `within_distance`, fixture réelle), modèle `FuelStation`/`FuelPrice` (seuls les prix publiés, avec leur date) ; recherche « Stations » : carburant du profil par défaut, puces de carburant, tri distance / prix, prix par ligne, « — » si non publié, repli OSM si le flux échoue ; fiche station : tous les prix avec fraîcheur, 24h/24, services, source. Bornes (IRVE) : pas d'API de proximité gratuite → reste ⛔ (liste OSM seulement) ; parkings : tarifs ⛔. 258 tests verts ; vérifié sur HONOR (Balma : Gazole 2,499 € il y a 41 h, E85 0,869 €).
- 2026-10-31 — **LOT 6 livré** (planificateur, arrêts, tournées) : `RouteAlternativesUseCase` avec étapes ; `RoutingProvider.optimizeOrder` (Valhalla `/optimized_route`, repli `StopOrdering.nearestNeighbour`, 5 tests) ; écran d'itinéraires : liste d'arrêts numérotée (≤ 10) avec monter / descendre / retirer, « Ajouter un arrêt » → recherche en mode « choisir un arrêt » (lieu rendu par le `savedStateHandle` de l'entrée de pile), « Optimiser l'ordre » ; guidage : « Ajouter un arrêt » depuis la feuille « Changer d'itinéraire », relance via l'arrêt. 2 tests VM. Vérifié sur HONOR (2 arrêts Balma + Montgiscard : 58 min · 54 km, puces et optimisation visibles). Restent ⬜ : pauses planifiées, rappel de départ, enchaînement arrêt par arrêt.
- 2026-10-31 — **LOT 7 livré** (profils de véhicules) : types moto / taxi / poids lourd ajoutés, gabarit (`VehicleDimensions`) et Crit'Air (Room v3, migration 2→3, mapping tolérant aux anciennes lignes, 2 tests) ; Valhalla : costing `truck` / `motorcycle` / `taxi` avec seulement les dimensions saisies (2 tests), profil transmis au calcul initial, au recalcul (`NavigationSession.start(…, vehicle)`), aux alternatives et à l'ajout d'arrêt ; réglages : chips de types, champs de gabarit (PL / utilitaire), vignette Crit'Air, notes honnêtes (péages PL et ZFE sans source). 268 tests verts.
- 2026-10-31 — **LOT 8 livré** (communauté) : schéma Supabase `backend/supabase/schema.sql` (profils pseudonymes, signalements, votes, fonctions `create_report` / `vote_report` / `reports_in_box` / `expire_reports`, TTL par type, anti-spam, points serveur, RLS) + `docs/BACKEND.md` ; client `CommunityConfigRepository` (URL, clé anon, identifiant d'appareil) et `SupabaseCommunityRepository` (PostgREST rpc, erreurs métier mappées, 4 tests) ; feuille « Signaler » active dès que le service est configuré (accueil + guidage), retour visuel, signalements sur la carte (couche + icône) et dans l'Horizon (projection sur le tracé) ; Réglages → Communauté (URL, clé, état, déconnexion). Aucune donnée fictive : sans projet Supabase saisi, rien n'est envoyé ni affiché. 272 tests verts.
- 2026-10-31 — **LOT 9 (historique, Replay) livré, compte reporté** : `TripRecorder` branché sur la course (début, fixes échantillonnés 20 m, fin arrivée/arrêt, trajets < 200 m ignorés), Room v4 (`trips`, migration 3→4), `TripRepository` + codec des points (2 tests), coût d'après le profil ; module `feature:trips` : « Mes trajets » (bilan du mois, totaux, liste, suppression) et Replay (tracé, marqueur interpolé, timeline, ×1–×8, « Y retourner » ; 2 tests), entrée Réglages → Trajets. Compte / synchronisation : ⬜ (exige le backend L8 configuré ; à faire en L10/L11). 276 tests verts. Non vérifié sur appareil avec un vrai trajet (exige de rouler).
- 2026-10-31 — **LOT 10 livré** (gamification, personnalisation) : teinte du cockpit (5 teintes `CockpitTint`, préférence DataStore, appliquée au thème de l'app et au guidage, Réglages → Personnalisation) ; modèle `CommunityProfile` / `LeaderboardEntry` / `Trophies` (8 trophées locaux calculés depuis l'historique, 2 tests) ; `CommunityRepository.profile()` / `leaderboard()` (rpc `get_profile`, `leaderboard` ajoutées au schéma Supabase, 1 test) ; écran « Mon profil » (bouton sur l'accueil + Réglages) : stats locales, trophées avec progression, points et classement serveur seulement si le service est configuré, sinon mention explicite. Ligues, défis, avatars, profil public : ⬜ (serveur). 282 tests verts ; vérifié sur HONOR (profil, trophées, classement inactif sans backend). Réparation de l'encodage de ce fichier (mojibake accumulé depuis le LOT 3, rangées de la matrice remises en place).
- 2026-10-31 — **LOT 11 livré** (abonnement, premium, assistance) : modèle `Feature` / `Tier` / `Entitlements` (règle : tout inclus tant qu'aucune boutique n'est configurée ; les fonctions `plus` exigent `PLUS` dès qu'une boutique existe ; 3 tests), `BillingProvider` (`store`, `tier`, `purchase`, `restore`) avec `NoBillingProvider` livré et `DefaultEntitlementsRepository` (2 tests) ; écran « Roadly+ » (palier, fonctions incluses / Roadly+, boutique : « Achat indisponible — Google Play Billing non intégré », boutons d'achat et de restauration rendus seulement quand la boutique est prête) ; écran « Assistance » (bug / idée / autre → partage Android d'un rapport pré-rempli ; fil des demandes « Bientôt » sans action) ; groupe Réglages → « Roadly+ et assistance ». Centre de notifications : ⬜ sans objet (aucune source de notification). Intégration Play : `docs/PREMIUM.md`. 287 tests verts ; vérifié sur HONOR (Réglages, Roadly+, Assistance, feuille de partage).
- 2026-10-31 — **LOT 12 livré** (Android) : `ExternalIntents` (actions domicile / travail / recherche, `geo:` et `google.navigation:` → destination directe si coordonnées, sinon recherche pré-remplie ; 4 tests) traitées par `MainActivity` → `RoadlyNavHost` (`LaunchViewModel` lit domicile / travail ; non défini → recherche + message) ; filtres d'intent `geo` et `google.navigation` (Assistant « Navigue vers… ») ; raccourcis statiques `res/xml/shortcuts.xml` ; widget 4×1 `RoadlyWidgetProvider` (RemoteViews, jour / nuit) ; `SearchRoute.query`. Android Auto : ⬜ — `androidx.car.app` indisponible hors ligne, marche à suivre `docs/ANDROID.md`. 291 tests verts ; vérifié sur HONOR : `geo:43.6045,1.444?q=Capitole` → itinéraire « Capitole », `google.navigation:q=Gare Matabiau` → recherche pré-remplie avec résultats BAN, raccourci Domicile sans domicile → recherche + message ; 3 raccourcis et le fournisseur de widget enregistrés par le système.
