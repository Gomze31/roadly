| Modifier le trajet sans quitter le guidage | L6 | â | Changer de destination, changer d'itinÃ©raire, ajouter un arrÃªt || Mode tournÃ©e | L6 | ð¡ | = optimisation de l'ordre ; pas encore d'enchaÃ®nement Â« arrÃªt par arrÃªt Â» avec validation || Optimisation automatique | L6 | â | Valhalla `optimized_route` (`original_index`), repli local, 3 tests || Jusqu'Ã  10 arrÃªts, rÃ©organisation manuelle | L6 | â | `StopsList`, `StopOrdering.move`, tests VM || Ajout d'arrÃªts pendant le guidage | L6 | â | Position projetÃ©e â arrÃªt â destination, bascule immÃ©diate || 12 | Gestion des arrÃªts | L6 | ð¡ | **Ajout d'un arrÃªt pendant le guidage (feuille Â« Changer d'itinÃ©raire Â» â recherche â relance via l'arrÃªt)**, rÃ©ordonner / supprimer sur l'Ã©cran d'itinÃ©raires, mode tournÃ©e = optimisation. Manque : liste des arrÃªts restants pendant le guidage, annonce Â« Ã©tape atteinte Â» || 11 | Planificateur de trajet | L6 | ð¡ | **Jusqu'Ã  10 arrÃªts, rÃ©organisation (monter/descendre/retirer), optimisation de l'ordre (Valhalla `optimized_route`, repli plus proche voisin), Â« Arriver Ã  Â» (L3)** ; itinÃ©raire multi-Ã©tapes recalculÃ©. Manque : pauses planifiÃ©es / aires sur le trajet, dÃ©part planifiÃ© avec rappel || Date de mise ÃÂ  jour affichÃÂ©e | L5 | Ã¢ÂÂ | Chaque prix porte ÃÂ« il y a X h/j ÃÂ» ; zones de vigilance : date de tÃÂ©lÃÂ©chargement + millÃÂ©sime || Bornes : puissance, connecteurs, prix kWh, disponibilitÃÂ© | L5 | Ã¢ÂÂ | IRVE consolidÃÂ© = CSV national volumineux sans API de proximitÃÂ© (vÃÂ©rifiÃÂ© 2026-10-31) ; nÃÂ©cessite un import serveur ou un dÃÂ©coupage ; disponibilitÃÂ© temps rÃÂ©el : contrat || Carburants SP95/SP98/E10/E85/Gazole/GPL, prix, comparaison | L5 | Ã¢ÂÂ | data.economie.gouv.fr `prix-des-carburants-en-france-flux-instantane-v2` (Licence Ouverte, ~10 min) ; fixture rÃÂ©elle, 2 tests |
| Prix sur la carte, dÃÂ©tour, horaires dÃÂ©taillÃÂ©s | L5 | Ã¢Â¬Â | Couche MapLibre, dÃÂ©tour via Valhalla, `horaires` JSON du flux || 8 | Stations-service | L5 | Ã°ÂÂÂ¡ | **Prix rÃÂ©els (L5)** : flux instantanÃÂ© data.economie.gouv.fr (Licence Ouverte, ~10 min), 6 carburants, date de chaque prix, 24h/24, services, tri distance / prix, carburant du profil par dÃÂ©faut, repli OSM si le flux ÃÂ©choue. Manque : prix sur la carte (couche), calcul du dÃÂ©tour, horaires dÃÂ©taillÃÂ©s (L5 suite) || Virages ÃÂ  venir | L4 | Ã¢ÂÂ | `CurveDetector` : Ã¢ÂÂ¥ 45ÃÂ° sur Ã¢ÂÂ¤ 90 m hors manÃÂuvres || Horizon (route ÃÂ  venir) | L4 | Ã°ÂÂÂ¡ | ManÃÂuvres, virages, zones ; trafic Ã¢ÂÂ || Alertes sonores, bips d'approche | L4 | Ã¢ÂÂ | Bip ÃÂ  l'entrÃÂ©e de zone (option) || Zones de vigilance | L4 | Ã¢ÂÂ | Jeu officiel ÃÂ« Radars automatiques ÃÂ» (SÃÂ©curitÃÂ© routiÃÂ¨re, data.gouv.fr, Licence Ouverte, millÃÂ©sime 2018) en cache Room ; zones de 300 m / 2 km / 4 km selon la limitation, jamais un point exact ; annonce vocale + bip ÃÂ  l'entrÃÂ©e, badge, Horizon ; France seulement (aucune donnÃÂ©e en Suisse) || 6 | Horizon | L4 | Ã°ÂÂÂ¡ | Ruban ÃÂ« route ÃÂ  venir ÃÂ» : manÃÂuvres, arrivÃÂ©e, **virages serrÃÂ©s (courbure du tracÃÂ©, L4)**, **zones de vigilance (base officielle, L4)**, ÃÂ©chelle log 0Ã¢ÂÂ5 km. Manque : ralentissements/travaux/fermetures (Ã¢ÂÂ trafic), services sur le trajet (L5), densitÃÂ© trafic (Ã¢ÂÂ) |# SILLAXE_PARITY.md ÃÂ¢ÃÂÃÂ matrice de paritÃÂÃÂ© Sillaxe ÃÂ¢ÃÂÃÂ Roadly

RÃÂÃÂ©fÃÂÃÂ©rence : https://sillaxe.app/ et la fiche App Store ÃÂÃÂ« Sillaxe : GPS & radars ÃÂÃÂ» (iPhone + CarPlay). DÃÂÃÂ©tail des fonctions vÃÂÃÂ©rifiÃÂÃÂ©es : `docs/SILLAXE-ANALYSE.md`. Sources de donnÃÂÃÂ©es et contraintes : `docs/BENCHMARK-SILLAXE.md`, `docs/API-PROVIDERS.md`.

Roadly reproduit l'**expÃÂÃÂ©rience** (ÃÂÃÂ©crans, parcours, densitÃÂÃÂ© d'information, interactions) avec son identitÃÂÃÂ© graphique propre. Aucune ressource, aucun code, aucun ÃÂÃÂ©lÃÂÃÂ©ment de marque de Sillaxe n'est repris.

RÃÂÃÂ¨gle absolue : **aucune fonction fictive**. Un ÃÂÃÂ©cran n'est ÃÂÃÂ« ÃÂ¢ÃÂÃÂ ÃÂÃÂ» que s'il est branchÃÂÃÂ© sur une source rÃÂÃÂ©elle ; sans source, il est ÃÂÃÂ« ÃÂ¢ÃÂÃÂ ÃÂÃÂ» et l'interface le dit (ÃÂÃÂ« BientÃÂÃÂ´t ÃÂÃÂ», ÃÂÃÂ« ÃÂ¢ÃÂÃÂ ÃÂÃÂ», explication de ce qui manque).

LÃÂÃÂ©gende des statuts :

| Statut | Sens |
|---|---|
| ÃÂ¢ÃÂÃÂ | fonctionnelle, testÃÂÃÂ©e, branchÃÂÃÂ©e sur une source rÃÂÃÂ©elle |
| ÃÂ°ÃÂÃÂÃÂ¡ | partielle (une partie des sous-fonctions, ou non vÃÂÃÂ©rifiÃÂÃÂ©e en conduite) |
| ÃÂ¢ÃÂÃÂ | bloquÃÂÃÂ©e par une source externe non configurÃÂÃÂ©e (code prÃÂÃÂªt ou interface prÃÂÃÂªte) |
| ÃÂ¢ÃÂ¬ÃÂ | non commencÃÂÃÂ©e |

Lots (section 15 de la mission) : L1 design system ÃÂÃÂ· L2 accueil/carte/recherche ÃÂÃÂ· L3 itinÃÂÃÂ©raires et navigation avancÃÂÃÂ©e ÃÂÃÂ· L4 Horizon, trafic, alertes ÃÂÃÂ· L5 carburants, bornes, parkings ÃÂÃÂ· L6 planificateur et tournÃÂÃÂ©es ÃÂÃÂ· L7 profils vÃÂÃÂ©hicules ÃÂÃÂ· L8 communautÃÂÃÂ© ÃÂÃÂ· L9 compte, statistiques, Replay ÃÂÃÂ· L10 gamification et personnalisation ÃÂÃÂ· L11 abonnement et premium ÃÂÃÂ· L12 Android Auto et finitions.

Mise ÃÂÃÂ  jour : 2026-10-30 ÃÂ¢ÃÂÃÂ audit initial puis LOT 1 (design system). Voir ÃÂÃÂ« Journal ÃÂÃÂ» en bas.

---

## 0. Audit du projet existant (2026-10-30)

PrÃÂÃÂ©servÃÂÃÂ© tel quel (ne pas dÃÂÃÂ©grader) :

- `core:navigation` : `NavigationSession`, `RouteTracker`, `AnnouncementPolicy`, `SpeedSmoother`, `NavigationService` (premier plan `location`), notification avec ÃÂÃÂ« ArrÃÂÃÂªter ÃÂÃÂ», `AndroidSpeechService` (TTS fr-FR, focus audio), `ValhallaTraceSpeedLimitProvider`.
- 224 tests unitaires JVM (`testDebugUnitTest :core:model:test :core:common:test`) et 3 tests instrumentÃÂÃÂ©s verts sur HONOR Magic 8 Pro (`FavoriteDaoTest` ÃÂÃÂ2, `GlassSearchBarTest` ÃÂÃÂ1).
- Modules (15) : `app`, `core:{model,common,designsystem,data,location,map,geocoding,routing,navigation}`, `feature:{home,search,route,settings,navigation}`.
- Fournisseurs sans clÃÂÃÂ© : BAN + Photon (gÃÂÃÂ©ocodage), Valhalla FOSSGIS (routage, fair-use), styles MapLibre (URL dans les rÃÂÃÂ©glages). Limitation de vitesse et alerte de dÃÂÃÂ©passement : code prÃÂÃÂªt, actives seulement avec un Valhalla auto-hÃÂÃÂ©bergÃÂÃÂ©.

Constats de l'audit (ÃÂÃÂ©carts avec Sillaxe) :

1. Interface : composants ÃÂÃÂ« verre ÃÂÃÂ» corrects mais gÃÂÃÂ©nÃÂÃÂ©riques (icÃÂÃÂ´nes Material brutes, panneaux empilÃÂÃÂ©s sans hiÃÂÃÂ©rarchie, pas de bandeau Horizon, pas de barre d'information de guidage, badge vitesse peu lisible, fiche lieu qui dÃÂÃÂ©borde, tracÃÂÃÂ© masquÃÂÃÂ© par les cartes d'itinÃÂÃÂ©raire).
2. Pas de bouton de signalement sur l'ÃÂÃÂ©cran de guidage, pas de bouton ÃÂÃÂ« replier ÃÂÃÂ» le bandeau, pas de boussole.
3. ÃÂÃÂcran d'itinÃÂÃÂ©raires : liste de cartes au lieu du panneau ÃÂÃÂ« Votre choix ÃÂÃÂ» (durÃÂÃÂ©e en grand, arrivÃÂÃÂ©e, distance, alternatives compactes, ÃÂÃÂ« Ajouter un arrÃÂÃÂªt ÃÂÃÂ», bouton ÃÂÃÂ« Rouler ÃÂÃÂ»).
4. Aucun des ÃÂÃÂ©crans 6 ÃÂÃÂ  25 de la mission n'existe (Horizon, signalements, stations, bornes, parkings, planificateur, arrÃÂÃÂªts, profil, classements, ligues, dÃÂÃÂ©fis, trophÃÂÃÂ©es, historique, replay, personnalisation, profils vÃÂÃÂ©hicules multiples, abonnement, notifications, assistance).
5. Pas de backend, pas de compte, pas de stockage des trajets.
6. Pas de mode paysage vÃÂÃÂ©rifiÃÂÃÂ©, pas d'Android Auto, pas de widgets, pas de raccourcis, pas de commandes vocales.

---

## 1. ÃÂÃÂcrans (25) ÃÂ¢ÃÂÃÂ ÃÂÃÂ©tat de reproduction

| # | ÃÂÃÂcran Sillaxe | Lot | Statut | ÃÂÃÂtat Roadly / ce qui manque |
|---|---|---|---|---|
| 1 | Accueil avec carte | L2 | ÃÂ°ÃÂÃÂÃÂ¡ | Carte, position, recherche, domicile/travail, recentrage, **boussole (L2)**, **bouton Signaler (L2, envoi L8)**, **toucher sa position ÃÂ¢ÃÂÃÂ copier/partager (L2)**, **puces Stations/Bornes/Parkings/Aires ÃÂ¢ÃÂÃÂ recherche ÃÂÃÂ  proximitÃÂÃÂ© (L2)**. Manque : carte de profil (L9), autres conducteurs (L8), couches sur la carte (L5) |
| 2 | Recherche | L2 | ÃÂ°ÃÂÃÂÃÂ¡ | BAN + Photon + coordonnÃÂÃÂ©es, rÃÂÃÂ©cents, favoris, **catÃÂÃÂ©gories ÃÂÃÂ  proximitÃÂÃÂ© (Photon `reverse` + `osm_tag`, triÃÂÃÂ©es par distance, distance affichÃÂÃÂ©e) (L2)**. Manque : horaires d'ouverture (ÃÂ¢ÃÂÃÂ non fournis par Photon), contacts/calendrier (L6), dictÃÂÃÂ©e (L12) |
| 3 | Fiche de lieu | L2 | ÃÂ°ÃÂÃÂÃÂ¡ | Distance, itinÃÂÃÂ©raire, favori, domicile/travail. Manque : horaires, tÃÂÃÂ©lÃÂÃÂ©phone, partage, ÃÂÃÂ« Enregistrer un repÃÂÃÂ¨re ÃÂÃÂ», parking ÃÂÃÂ  proximitÃÂÃÂ©, mise en page Sillaxe |
| 4 | Choix des itinÃÂÃÂ©raires | L3 | ÃÂ°ÃÂÃÂÃÂ¡ | 3 variantes, carte, coÃÂÃÂ»t carburant, panneau ÃÂÃÂ« Votre choix ÃÂÃÂ» (L1), **ÃÂÃÂ« Partir ÃÂÃÂ  ÃÂÃÂ» / ÃÂÃÂ« Arriver ÃÂÃÂ  ÃÂÃÂ» (L3)**, **puces ÃÂÃÂ©viter pÃÂÃÂ©ages / autoroutes / ferries (L3)**. Manque : montant des pÃÂÃÂ©ages (ÃÂ¢ÃÂÃÂ), trafic (ÃÂ¢ÃÂÃÂ), ÃÂÃÂ« Ajouter un arrÃÂÃÂªt ÃÂÃÂ» (L6), feux/ralentisseurs comptÃÂÃÂ©s (ÃÂ¢ÃÂÃÂ donnÃÂÃÂ©es) |
| 5 | Navigation GPS | L3 | ÃÂ°ÃÂÃÂÃÂ¡ | Bandeau repliable, voies, Horizon, barre ETA ÃÂÃÂ· min ÃÂÃÂ· km, vitesse, arrivÃÂÃÂ©e, recalcul, GPS perdu, notification, TTS, Signaler, paysage, **ÃÂÃÂ« Changer d'itinÃÂÃÂ©raire ÃÂÃÂ» en cours de trajet (L3)**. Manque : limitation (ÃÂ¢ÃÂÃÂ Valhalla auto-hÃÂÃÂ©bergÃÂÃÂ©), ajout d'arrÃÂÃÂªt (L6), autres conducteurs (L8) |
| 6 | Horizon | L4 | ÃÂ°ÃÂÃÂÃÂ¡ | Ruban ÃÂÃÂ« route ÃÂÃÂ  venir ÃÂÃÂ» livrÃÂÃÂ© (L1) : manÃÂÃÂuvres et arrivÃÂÃÂ©e rÃÂÃÂ©elles, ÃÂÃÂ©chelle log 0ÃÂ¢ÃÂÃÂ5 km. Manque : virages (courbure), ralentissements/travaux/fermetures/zones/services (ÃÂ¢ÃÂÃÂ sources) |
| 7 | Signalements | L8 | ÃÂ°ÃÂÃÂÃÂ¡ | Bouton ÃÂÃÂ« Signaler ÃÂÃÂ» en guidage + feuille des 9 types (dÃÂÃÂ©sactivÃÂÃÂ©s, explication). Manque : envoi, confirmation, invalidation, expiration, carte, points, historique, modÃÂÃÂ©ration (backend Supabase) |
| 8 | Stations-service | L5 | ÃÂ°ÃÂÃÂÃÂ¡ | Liste ÃÂÃÂ  proximitÃÂÃÂ© (OSM via Photon, L2). ÃÂ¢ÃÂÃÂ prix : data.economie.gouv.fr (flux instantanÃÂÃÂ©, Licence Ouverte) ; comparaison, dÃÂÃÂ©tour, horaires |
| 9 | Bornes ÃÂÃÂ©lectriques | L5 | ÃÂ°ÃÂÃÂÃÂ¡ | Liste ÃÂÃÂ  proximitÃÂÃÂ© (OSM, L2). ÃÂ¢ÃÂÃÂ puissance/connecteurs/prix : IRVE consolidÃÂÃÂ© transport.data.gouv.fr ; disponibilitÃÂÃÂ© temps rÃÂÃÂ©el non fournie gratuitement |
| 10 | Parkings | L5 | ÃÂ°ÃÂÃÂÃÂ¡ | Liste ÃÂÃÂ  proximitÃÂÃÂ© (OSM `amenity=parking`, L2). ÃÂ¢ÃÂÃÂ tarifs/hauteur/places : open data villes (BNLS) |
| 11 | Planificateur de trajet | L6 | ÃÂ¢ÃÂ¬ÃÂ | Jusqu'ÃÂÃÂ  10 arrÃÂÃÂªts, rÃÂÃÂ©organisation, optimisation, pauses, ÃÂÃÂ« arriver ÃÂÃÂ  ÃÂÃÂ» |
| 12 | Gestion des arrÃÂÃÂªts | L6 | ÃÂ¢ÃÂ¬ÃÂ | Ajout pendant le guidage, rÃÂÃÂ©ordonner, supprimer, mode tournÃÂÃÂ©e |
| 13 | Profil utilisateur | L9 | ÃÂ¢ÃÂ¬ÃÂ | Pseudonyme, avatar, banniÃÂÃÂ¨re, km, trajets, points, public/privÃÂÃÂ©/masquÃÂÃÂ© |
| 14 | Classements | L10 | ÃÂ¢ÃÂ¬ÃÂ | Mois, tous temps, amis (backend) |
| 15 | Ligues | L10 | ÃÂ¢ÃÂ¬ÃÂ | Ligues mensuelles, promotion/relÃÂÃÂ©gation |
| 16 | DÃÂÃÂ©fis | L10 | ÃÂ¢ÃÂ¬ÃÂ | DÃÂÃÂ©fis hebdomadaires |
| 17 | TrophÃÂÃÂ©es | L10 | ÃÂ¢ÃÂ¬ÃÂ | TrophÃÂÃÂ©es locaux (pays traversÃÂÃÂ©s, km, sÃÂÃÂ©ries) |
| 18 | Historique | L9 | ÃÂ¢ÃÂ¬ÃÂ | Trajets locaux (tracÃÂÃÂ©, date, distance, durÃÂÃÂ©e, vitesse moyenne, coÃÂÃÂ»t, arrÃÂÃÂªts, ÃÂÃÂ©vÃÂÃÂ©nements) |
| 19 | Replay | L9 | ÃÂ¢ÃÂ¬ÃÂ | Rejeu sur carte, timeline, vitesse ÃÂÃÂ0,5ÃÂ¢ÃÂÃÂÃÂÃÂ8, ÃÂÃÂ©vÃÂÃÂ©nements |
| 20 | ParamÃÂÃÂ¨tres | L2/L11 | ÃÂ°ÃÂÃÂÃÂ¡ | VÃÂÃÂ©hicule, apparence, voix et sons, alertes vitesse, prÃÂÃÂ©fÃÂÃÂ©rences d'itinÃÂÃÂ©raire, donnÃÂÃÂ©es, confidentialitÃÂÃÂ©, fournisseurs, ÃÂÃÂ  propos. Manque : organisation Sillaxe (groupes, lignes avec icÃÂÃÂ´nes), compte, notifications, autorisations, choix publicitaires, assistance |
| 21 | Personnalisation | L10 | ÃÂ¢ÃÂ¬ÃÂ | Teinte du cockpit, icÃÂÃÂ´ne alternative, avatars, silhouettes de vÃÂÃÂ©hicule |
| 22 | Profils de vÃÂÃÂ©hicules | L7 | ÃÂ°ÃÂÃÂÃÂ¡ | 1 profil (type, carburant, conso, prix). Manque : plusieurs profils, moto/taxi/PL/VUL/VE, gabarit, essieux, Crit'Air, influence sur le routage |
| 23 | Abonnement et premium | L11 | ÃÂ¢ÃÂ¬ÃÂ | Architecture gratuit/premium (`Entitlements`), ÃÂÃÂ©cran de prÃÂÃÂ©sentation, gestion |
| 24 | Centre de notifications | L11 | ÃÂ¢ÃÂ¬ÃÂ | Rappels de dÃÂÃÂ©part, pÃÂÃÂ©ages flux libre, dÃÂÃÂ©fis, amis |
| 25 | Assistance | L11 | ÃÂ¢ÃÂ¬ÃÂ | Demandes (bug / idÃÂÃÂ©e / autre), fil ÃÂÃÂ« Mes demandes ÃÂÃÂ», ÃÂÃÂ©tat du service |

---

## 2. Navigation (mission ÃÂÃÂ§4)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| GPS temps rÃÂÃÂ©el | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | FusedLocation, 1 Hz en guidage |
| Guidage vocal | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | TTS fr-FR, 3 paliers, anti-rÃÂÃÂ©pÃÂÃÂ©tition |
| Bandeau de direction | L3 | ÃÂ¢ÃÂÃÂ | ÃÂÃÂ restyler (icÃÂÃÂ´ne en tuile, distance en grand, rue) |
| Indication des voies | L3 | ÃÂ°ÃÂÃÂÃÂ¡ | DÃÂÃÂ©codage et affichage faits ; aucune voie dans les rÃÂÃÂ©ponses FOSSGIS observÃÂÃÂ©es |
| Sorties de rond-point | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | Pastille ÃÂÃÂ« 2e sortie ÃÂÃÂ», `exit_number` |
| Compteur de vitesse | L3 | ÃÂ¢ÃÂÃÂ | Vitesse brute GPS |
| Limitations de vitesse | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | `trace_attributes` ; Valhalla auto-hÃÂÃÂ©bergÃÂÃÂ© requis |
| Alertes de dÃÂÃÂ©passement | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | Bip + badge rouge ; dÃÂÃÂ©pend de la limitation |
| Zones de vigilance | L4 | ÃÂ¢ÃÂÃÂ | Base de zones ÃÂÃÂ  constituer ; France : jamais un point exact (R413-15) ; Suisse : dÃÂÃÂ©sactivÃÂÃÂ© |
| Alertes sonores, bips d'approche | L4 | ÃÂ¢ÃÂ¬ÃÂ | |
| Horizon (route ÃÂÃÂ  venir) | L4 | ÃÂ¢ÃÂ¬ÃÂ | ManÃÂÃÂuvres et virages d'abord, puis trafic/zones |
| Virages ÃÂÃÂ  venir | L4 | ÃÂ¢ÃÂ¬ÃÂ | Courbure de la polyligne |
| Ralentissements, travaux, routes fermÃÂÃÂ©es | L4 | ÃÂ¢ÃÂÃÂ | Bison FutÃÂÃÂ© DATEX II, Autoroutes.fr ; fournisseur ÃÂÃÂ  conventionner |
| Recalcul automatique | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | Non vÃÂÃÂ©rifiÃÂÃÂ© en conduite rÃÂÃÂ©elle |
| ItinÃÂÃÂ©raire alternatif en cours de trajet | L3 | ÃÂ¢ÃÂÃÂ | Feuille ÃÂÃÂ« Changer d'itinÃÂÃÂ©raire ÃÂÃÂ» : 3 variantes depuis la position projetÃÂÃÂ©e, bascule sans quitter le guidage |
| Changement de destination en guidage | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | |
| Ajout d'arrÃÂÃÂªts pendant le guidage | L6 | ÃÂ¢ÃÂ¬ÃÂ | Valhalla multi-locations dÃÂÃÂ©jÃÂÃÂ  supportÃÂÃÂ© |
| Autres conducteurs sur la carte | L8 | ÃÂ¢ÃÂ¬ÃÂ | Backend ; dÃÂÃÂ©calage ÃÂ¢ÃÂÃÂ¥ 1 km et ÃÂ¢ÃÂÃÂ¥ 90 s, dÃÂÃÂ©sactivable |
| Perte GPS | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | Extrapolation plafonnÃÂÃÂ©e, badge aprÃÂÃÂ¨s 10 s |
| ÃÂÃÂcran verrouillÃÂÃÂ© / arriÃÂÃÂ¨re-plan | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | Service de premier plan, notification |

## 3. Trafic et communautÃÂÃÂ© (ÃÂÃÂ§5)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| 9 types de signalement | L8 | ÃÂ¢ÃÂ¬ÃÂ | Accident, bouchon, obstacle, route fermÃÂÃÂ©e, travaux, mÃÂÃÂ©tÃÂÃÂ©o, nid-de-poule, feu de chantier, danger |
| CrÃÂÃÂ©ation rapide (bouton sur le guidage) | L8 | ÃÂ¢ÃÂ¬ÃÂ | Interface en L3, envoi en L8 |
| Confirmation / invalidation / expiration | L8 | ÃÂ¢ÃÂ¬ÃÂ | Supabase + Edge Function d'expiration |
| Affichage sur la carte | L8 | ÃÂ¢ÃÂ¬ÃÂ | Couche MapLibre |
| Points, historique des contributions | L8/L10 | ÃÂ¢ÃÂ¬ÃÂ | Calcul cÃÂÃÂ´tÃÂÃÂ© serveur, anti-triche |
| ModÃÂÃÂ©ration, dÃÂÃÂ©tection des incohÃÂÃÂ©rences | L8 | ÃÂ¢ÃÂ¬ÃÂ | |
| Autres utilisateurs sur la carte | L8 | ÃÂ¢ÃÂ¬ÃÂ | ReprÃÂÃÂ©sentation discrÃÂÃÂ¨te, dÃÂÃÂ©calÃÂÃÂ©e |

## 4. DonnÃÂÃÂ©es routiÃÂÃÂ¨res et services (ÃÂÃÂ§6)

| DonnÃÂÃÂ©e | Lot | Statut | Source retenue |
|---|---|---|---|
| Carburants SP95/SP98/E10/E85/Gazole/GPL, prix sur la carte, comparaison, dÃÂÃÂ©tour, horaires | L5 | ÃÂ¢ÃÂÃÂ | data.economie.gouv.fr `prix-carburants-fichier-instantane-test-ods-copie` (Licence Ouverte, ~10 min) |
| Bornes : puissance, connecteurs, prix kWh, disponibilitÃÂÃÂ© | L5 | ÃÂ¢ÃÂÃÂ | IRVE consolidÃÂÃÂ© (transport.data.gouv.fr) ; disponibilitÃÂÃÂ© : non disponible sans contrat |
| Aires de repos et de service | L5 | ÃÂ¢ÃÂ¬ÃÂ | OSM `highway=services|rest_area` (Overpass) |
| Parkings gratuits/payants | L5 | ÃÂ¢ÃÂÃÂ | OSM + BNLS |
| Ponts bas | L7 | ÃÂ¢ÃÂ¬ÃÂ | OSM `maxheight` + profil vÃÂÃÂ©hicule |
| Ralentisseurs | L4 | ÃÂ¢ÃÂ¬ÃÂ | OSM `traffic_calming` |
| PÃÂÃÂ©ages, pÃÂÃÂ©ages en flux libre | L3/L11 | ÃÂ¢ÃÂÃÂ | Pas d'API publique ; `hasToll` Valhalla seulement ; montant jamais inventÃÂÃÂ© |
| ZFE | L7 | ÃÂ¢ÃÂ¬ÃÂ | Source ÃÂÃÂ  identifier (donnÃÂÃÂ©es ZFE communales) |
| Date de mise ÃÂÃÂ  jour affichÃÂÃÂ©e | L5 | ÃÂ¢ÃÂ¬ÃÂ | Obligatoire sur chaque donnÃÂÃÂ©e affichÃÂÃÂ©e |

## 5. ItinÃÂÃÂ©raires et planification (ÃÂÃÂ§7)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| Comparaison de plusieurs itinÃÂÃÂ©raires | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | Rapide / court / sans pÃÂÃÂ©age |
| CoÃÂÃÂ»t carburant | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | Profil vÃÂÃÂ©hicule saisi ; jamais de prix par dÃÂÃÂ©faut |
| CoÃÂÃÂ»t des pÃÂÃÂ©ages | L3 | ÃÂ¢ÃÂÃÂ | PrÃÂÃÂ©sence oui/non seulement |
| DurÃÂÃÂ©e, distance, heure d'arrivÃÂÃÂ©e | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | |
| Trafic prÃÂÃÂ©vu | L4 | ÃÂ¢ÃÂÃÂ | |
| DÃÂÃÂ©part planifiÃÂÃÂ© | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | DÃÂÃÂ©cale l'heure d'arrivÃÂÃÂ©e |
| ArrivÃÂÃÂ©e ÃÂÃÂ  une heure souhaitÃÂÃÂ©e | L3 | ÃÂ¢ÃÂÃÂ | DÃÂÃÂ©part = arrivÃÂÃÂ©e ÃÂ¢ÃÂÃÂ durÃÂÃÂ©e du plus rapide (sans trafic prÃÂÃÂ©visionnel) |
| Rappel avant le dÃÂÃÂ©part planifiÃÂÃÂ© | L6 | ÃÂ¢ÃÂ¬ÃÂ | WorkManager |
| Jusqu'ÃÂÃÂ  10 arrÃÂÃÂªts, rÃÂÃÂ©organisation manuelle | L6 | ÃÂ¢ÃÂ¬ÃÂ | |
| Optimisation automatique | L6 | ÃÂ¢ÃÂ¬ÃÂ | Valhalla `optimized_route` |
| Pauses, aires, arrÃÂÃÂªts personnalisÃÂÃÂ©s | L6 | ÃÂ¢ÃÂ¬ÃÂ | |
| Mode tournÃÂÃÂ©e | L6 | ÃÂ¢ÃÂ¬ÃÂ | |
| Modifier le trajet sans quitter le guidage | L6 | ÃÂ°ÃÂÃÂÃÂ¡ | Changer de destination : fait ; arrÃÂÃÂªts : non |

## 6. Profils de vÃÂÃÂ©hicules (ÃÂÃÂ§8)

| Fonction | Lot | Statut | Notes |
|---|---|---|---|
| Voiture / moto / taxi / PL / VUL / VE | L7 | ÃÂ°ÃÂÃÂÃÂ¡ | `VehicleType` = CITY, SEDAN, SUV, VAN ; ÃÂÃÂ  ÃÂÃÂ©tendre |
| Hauteur, largeur, longueur, poids, essieux | L7 | ÃÂ¢ÃÂ¬ÃÂ | Valhalla `truck` costing (`height`, `width`, `length`, `weight`, `axle_load`) |
| Consommation, type de carburant | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ | |
| Crit'Air | L7 | ÃÂ¢ÃÂ¬ÃÂ | |
| Influence rÃÂÃÂ©elle sur les itinÃÂÃÂ©raires | L7 | ÃÂ¢ÃÂ¬ÃÂ | Seulement avec `truck` costing et donnÃÂÃÂ©es OSM |
| Restrictions PL, pÃÂÃÂ©ages PL | L7 | ÃÂ¢ÃÂÃÂ | PÃÂÃÂ©ages PL : pas de source |

## 7. Premium (ÃÂÃÂ§9)

| Fonction | Lot | Statut |
|---|---|---|
| Architecture gratuit/premium (`Entitlements`, flags testables) | L11 | ÃÂ¢ÃÂ¬ÃÂ |
| Absence de publicitÃÂÃÂ© | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ (Roadly n'a pas de publicitÃÂÃÂ©) |
| Zones de vigilance complÃÂÃÂ¨tes, alertes sans trajet | L4 | ÃÂ¢ÃÂÃÂ |
| Profils vÃÂÃÂ©hicules avancÃÂÃÂ©s | L7 | ÃÂ¢ÃÂ¬ÃÂ |
| ItinÃÂÃÂ©raires temps rÃÂÃÂ©el longs trajets | L4 | ÃÂ¢ÃÂÃÂ |
| Mode tournÃÂÃÂ©e | L6 | ÃÂ¢ÃÂ¬ÃÂ |
| Partage de trajet en direct | L9 | ÃÂ¢ÃÂ¬ÃÂ |
| Rappels pÃÂÃÂ©ages flux libre | L11 | ÃÂ¢ÃÂÃÂ (gÃÂÃÂ©ofences des portiques ÃÂÃÂ  constituer) |
| Replay | L9 | ÃÂ¢ÃÂ¬ÃÂ |
| Synchronisation favoris, domicile, travail | L9 | ÃÂ¢ÃÂ¬ÃÂ |
| Historique kilomÃÂÃÂ©trique, coÃÂÃÂ»t des dÃÂÃÂ©placements | L9 | ÃÂ¢ÃÂ¬ÃÂ |
| Couleurs, icÃÂÃÂ´nes, avatars, silhouettes | L10 | ÃÂ¢ÃÂ¬ÃÂ |

## 8. Gamification (ÃÂÃÂ§10)

| Fonction | Lot | Statut |
|---|---|---|
| Points, rÃÂÃÂ©compenses | L10 | ÃÂ¢ÃÂ¬ÃÂ (serveur, anti-triche) |
| Classement mensuel / gÃÂÃÂ©nÃÂÃÂ©ral | L10 | ÃÂ¢ÃÂ¬ÃÂ |
| Ligues mensuelles | L10 | ÃÂ¢ÃÂ¬ÃÂ |
| DÃÂÃÂ©fis hebdomadaires | L10 | ÃÂ¢ÃÂ¬ÃÂ |
| TrophÃÂÃÂ©es | L10 | ÃÂ¢ÃÂ¬ÃÂ |
| Profil public, amis | L10 | ÃÂ¢ÃÂ¬ÃÂ |
| Km parcourus, bilan mensuel | L9 | ÃÂ¢ÃÂ¬ÃÂ |
| Avatars, banniÃÂÃÂ¨res, rÃÂÃÂ©compenses ÃÂÃÂ  dÃÂÃÂ©bloquer | L10 | ÃÂ¢ÃÂ¬ÃÂ |

## 9. Android (ÃÂÃÂ§11)

| Fonction | Lot | Statut |
|---|---|---|
| Android Auto (catÃÂÃÂ©gorie navigation) | L12 | ÃÂ¢ÃÂ¬ÃÂ |
| App Shortcuts (domicile, travail, rechercher, signaler) | L12 | ÃÂ¢ÃÂ¬ÃÂ |
| Widgets | L12 | ÃÂ¢ÃÂ¬ÃÂ |
| Notifications (guidage) | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ |
| Commandes vocales (App Actions : maison, travail, adresse, accident, destination) | L12 | ÃÂ¢ÃÂ¬ÃÂ |
| Intents (geo:, google.navigation:, partage) | L12 | ÃÂ¢ÃÂ¬ÃÂ |
| Partage natif | L2 | ÃÂ¢ÃÂÃÂ (fiche lieu : copier l'adresse, partager) |
| Mode paysage | L3 | ÃÂ°ÃÂÃÂÃÂ¡ (accueil, itinÃÂÃÂ©raires, guidage disposÃÂÃÂ©s en deux colonnes ; guidage vÃÂÃÂ©rifiÃÂÃÂ© sur appareil) |
| Permissions | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ |
| Localisation en arriÃÂÃÂ¨re-plan, service persistant | ÃÂ¢ÃÂÃÂ | ÃÂ¢ÃÂÃÂ |

## 10. Replay et historique (ÃÂÃÂ§12)

| Fonction | Lot | Statut |
|---|---|---|
| Enregistrement du tracÃÂÃÂ© (Room `trips`, `trip_points`, `trip_events`) | L9 | ÃÂ¢ÃÂ¬ÃÂ |
| Date, distance, durÃÂÃÂ©e, vitesse moyenne, coÃÂÃÂ»t, arrÃÂÃÂªts, ÃÂÃÂ©vÃÂÃÂ©nements | L9 | ÃÂ¢ÃÂ¬ÃÂ |
| Replay : carte, timeline, vitesse de lecture, ÃÂÃÂ©vÃÂÃÂ©nements | L9 | ÃÂ¢ÃÂ¬ÃÂ |

## 11. QualitÃÂÃÂ© (ÃÂÃÂ§14)

| Exigence | Statut |
|---|---|
| Aucune fonction fictive, aucun bouton inactif | ÃÂ¢ÃÂÃÂ (puces ÃÂÃÂ« BientÃÂÃÂ´t ÃÂÃÂ» dÃÂÃÂ©sactivÃÂÃÂ©es et marquÃÂÃÂ©es) |
| Aucune donnÃÂÃÂ©e simulÃÂÃÂ©e | ÃÂ¢ÃÂÃÂ |
| Tous les ÃÂÃÂ©crans testÃÂÃÂ©s | ÃÂ°ÃÂÃÂÃÂ¡ (ViewModels testÃÂÃÂ©s ; ÃÂÃÂ©crans : 1 test Compose) |
| Compilation stable | ÃÂ¢ÃÂÃÂ |

---

## Journal

- 2026-10-30 ÃÂ¢ÃÂÃÂ Audit complet, crÃÂÃÂ©ation de ce fichier.
- 2026-10-31 ÃÂ¢ÃÂÃÂ **LOT 1 livrÃÂÃÂ©** : design system (`docs/DESIGN-SYSTEM.md`) ÃÂ¢ÃÂÃÂ tokens violet/orange, surfaces opaques et verre, `Overline`/`StatValue`, boutons Primary/Brand/Secondary/Danger, boutons ronds, tuiles d'icÃÂÃÂ´ne, `NavInfoBar`, `HorizonStrip` (ÃÂÃÂ©chelle log, 3 tests), `ListRow`/`ToggleRow`/`SegmentedControl`, `RoadlyTopBar`, `RouteHeadline`/`RouteOptionRow`, teintes de cockpit (`CockpitTint`) prÃÂÃÂªtes pour L10 ; halo + liserÃÂÃÂ© du tracÃÂÃÂ© MapLibre ; `fitBounds` ÃÂÃÂ  marges distinctes. ÃÂÃÂcrans rÃÂÃÂ©alignÃÂÃÂ©s sur la rÃÂÃÂ©fÃÂÃÂ©rence : accueil (tuiles Domicile/Travail, recentrage rond marque, catÃÂÃÂ©gories ÃÂÃÂ« BientÃÂÃÂ´t ÃÂÃÂ»), recherche (catÃÂÃÂ©gories ÃÂÃÂ« BientÃÂÃÂ´t ÃÂÃÂ», favoris/rÃÂÃÂ©cents en cartes, fiche lieu avec tuiles d'action + copier/partager rÃÂÃÂ©els), itinÃÂÃÂ©raires (en-tÃÂÃÂªte destination / ÃÂÃÂ« Partir de ÃÂÃÂ», panneau ÃÂÃÂ« Votre choix ÃÂÃÂ» : durÃÂÃÂ©e en grand, arrivÃÂÃÂ©e, distance, puces, alternatives compactes, ÃÂÃÂ« Ajouter un arrÃÂÃÂªt ÃÂÃÂ» BientÃÂÃÂ´t, ÃÂÃÂ« Rouler ÃÂÃÂ»), guidage (bandeau repliable, bouton Signaler ÃÂ¢ÃÂÃÂ feuille des 9 types dÃÂÃÂ©sactivÃÂÃÂ©s avec explication, voix, destination, compteur + panneau de limitation, Horizon alimentÃÂÃÂ© par les manÃÂÃÂuvres rÃÂÃÂ©elles, barre ETA ÃÂÃÂ· min ÃÂÃÂ· km ÃÂÃÂ· recentrer ÃÂÃÂ· arrÃÂÃÂªter, **mode paysage**), rÃÂÃÂ©glages (groupes, lignes ÃÂÃÂ  tuiles d'icÃÂÃÂ´ne, sÃÂÃÂ©lecteurs segmentÃÂÃÂ©s, fournisseurs repliÃÂÃÂ©s). Build `assembleDebug` vert, **230 tests unitaires** verts. **VÃÂÃÂ©rifiÃÂÃÂ© sur HONOR Magic 8 Pro** (ÃÂÃÂ  l'arrÃÂÃÂªt, Toulouse ÃÂ¢ÃÂÃÂ Place du Capitole) : accueil, recherche, fiche lieu, itinÃÂÃÂ©raires, guidage portrait et paysage, feuille ÃÂÃÂ« Signaler ÃÂÃÂ» ; aucun `FATAL EXCEPTION` dans logcat.
  - ÃÂÃÂcrans 1, 2, 3, 4, 5, 20 : passent de ÃÂÃÂ« gÃÂÃÂ©nÃÂÃÂ©rique ÃÂÃÂ» ÃÂÃÂ  ÃÂÃÂ« fidÃÂÃÂ¨le ÃÂÃÂ» graphiquement ; leur statut fonctionnel reste ÃÂ°ÃÂÃÂÃÂ¡ (voir tableau 1).
  - ÃÂÃÂcran 6 (Horizon) : composant livrÃÂÃÂ© et affichÃÂÃÂ© en guidage avec manÃÂÃÂuvres + arrivÃÂÃÂ©e ; trafic/zones/services restent ÃÂ¢ÃÂÃÂ (L4).
  - ÃÂÃÂcran 7 (Signalements) : interface de saisie livrÃÂÃÂ©e, envoi ÃÂ¢ÃÂÃÂ (L8).
- 2026-10-31 ÃÂ¢ÃÂÃÂ **LOT 2 livrÃÂÃÂ©** (accueil, carte, recherche) : `NearbyPoiProvider` / `PhotonNearbyPoiProvider` (Photon `reverse` + `radius` + `osm_tag`, fixture rÃÂÃÂ©elle, 2 tests) ; recherche : puces Stations / Bornes / Parkings / Aires / Restaurants rÃÂÃÂ©elles (lieux OSM triÃÂÃÂ©s par distance, distance par ligne, ÃÂÃÂ©tats ÃÂÃÂ« position inconnue ÃÂÃÂ» et ÃÂÃÂ« aucun lieu ÃÂÃÂ», pied de carte rappelant l'absence de prix/disponibilitÃÂÃÂ©), route `SearchRoute(category)` ; accueil : boussole animÃÂÃÂ©e (apparaÃÂÃÂ®t quand la carte est tournÃÂÃÂ©e, remet le nord), bouton Signaler (feuille partagÃÂÃÂ©e dans `core:designsystem`), position courante ÃÂ¢ÃÂÃÂ feuille Copier/Partager (adresse + coordonnÃÂÃÂ©es + lien OSM), puces de catÃÂÃÂ©gories branchÃÂÃÂ©es sur la recherche, ÃÂÃÂ« Incidents ÃÂÃÂ» reste ÃÂÃÂ« BientÃÂÃÂ´t ÃÂÃÂ». 235 tests verts ; vÃÂÃÂ©rifiÃÂÃÂ© sur HONOR : stations ÃÂÃÂ  630 m, parkings ÃÂÃÂ  730 m, feuille position, bouton Signaler.
- 2026-10-31 ÃÂ¢ÃÂÃÂ **LOT 3 livrÃÂÃÂ©** (itinÃÂÃÂ©raires et navigation avancÃÂÃÂ©e) : mode ÃÂÃÂ« Arriver ÃÂÃÂ  ÃÂÃÂ» (sÃÂÃÂ©lecteur Partir ÃÂÃÂ  / Arriver ÃÂÃÂ , dÃÂÃÂ©part dÃÂÃÂ©duit du plus rapide, arrivÃÂÃÂ©es recalÃÂÃÂ©es, test `ArrivalShiftTest`) ; puces d'options ÃÂÃÂ©viter pÃÂÃÂ©ages / autoroutes / ferries sur le panneau ÃÂÃÂ« Votre choix ÃÂÃÂ» (persistÃÂÃÂ©es, appliquÃÂÃÂ©es aux 3 variantes et au recalcul) ; en guidage, bouton ÃÂÃÂ« ItinÃÂÃÂ©raires ÃÂÃÂ» ÃÂ¢ÃÂÃÂ feuille ÃÂÃÂ« Changer d'itinÃÂÃÂ©raire ÃÂÃÂ» (3 variantes depuis la position projetÃÂÃÂ©e, arrivÃÂÃÂ©e, pÃÂÃÂ©age, bascule immÃÂÃÂ©diate du guidage ; test VM). 238 tests verts ; vÃÂÃÂ©rifiÃÂÃÂ© sur HONOR (bascule vers ÃÂÃÂ« Le plus court ÃÂÃÂ» : 26 min ÃÂÃÂ· 4,6 km).
- 2026-10-31 Ã¢ÂÂ **LOT 4 livrÃÂ©** (Horizon, alertes) : `CurveDetector` (virages serrÃÂ©s par courbure, hors manÃÂuvres) ; zones de vigilance depuis le jeu officiel ÃÂ« Radars automatiques ÃÂ» (data.gouv.fr, Licence Ouverte, 3 350 enregistrements, millÃÂ©sime 2018) tÃÂ©lÃÂ©chargÃÂ© au premier lancement et tous les 30 jours (Room v2, migration), reconstituÃÂ©es en zones de 300 m / 2 km / 4 km (jamais un point exact), annonce vocale + bip ÃÂ  l'entrÃÂ©e, badge avec limitation, Horizon ; pluie Open-Meteo (toutes les 10 min) avec option ÃÂ« limitation abaissÃÂ©e par pluie ÃÂ» (R413-2) ; rÃÂ©glages ÃÂ« Alertes ÃÂ» (zones, bip, mise ÃÂ  jour manuelle avec compteur et date, pluie). Trafic en direct, travaux, fermetures : toujours Ã¢ÂÂ (aucune source gratuite ; Bison FutÃÂ© ÃÂ  conventionner). 254 tests verts ; vÃÂ©rifiÃÂ© sur HONOR (3 350 zones chargÃÂ©es, Horizon avec virage + zone sur Toulouse Ã¢ÂÂ Montgiscard).
- 2026-10-31 â **LOT 5 (partie carburants) livrÃ©** : `FuelStationProvider` / `OpenDataFuelStationProvider` (flux instantanÃ© du ministÃ¨re de l'Ãconomie, Licence Ouverte, requÃªte de proximitÃ© ODSQL `within_distance`, fixture rÃ©elle), modÃ¨le `FuelStation`/`FuelPrice` (seuls les prix publiÃ©s, avec leur date) ; recherche Â« Stations Â» : carburant du profil par dÃ©faut, puces de carburant, tri distance / prix, prix par ligne, Â« â Â» si non publiÃ©, repli OSM si le flux Ã©choue ; fiche station : tous les prix avec fraÃ®cheur, 24h/24, services, source. Bornes (IRVE) : pas d'API de proximitÃ© gratuite â reste â (liste OSM seulement) ; parkings : tarifs â. 258 tests verts ; vÃ©rifiÃ© sur HONOR (Balma : Gazole 2,499 â¬ il y a 41 h, E85 0,869 â¬).
- 2026-10-31 — **LOT 6 livré** (planificateur, arrêts, tournées) : `RouteAlternativesUseCase` avec étapes ; `RoutingProvider.optimizeOrder` (Valhalla `/optimized_route`, repli `StopOrdering.nearestNeighbour`, 5 tests) ; écran d'itinéraires : liste d'arrêts numérotée (≤ 10) avec monter / descendre / retirer, « Ajouter un arrêt » → recherche en mode « choisir un arrêt » (lieu rendu par le `savedStateHandle` de l'entrée de pile), « Optimiser l'ordre » ; guidage : « Ajouter un arrêt » depuis la feuille « Changer d'itinéraire », relance via l'arrêt. 2 tests VM. Vérifié sur HONOR (2 arrêts Balma + Montgiscard : 58 min · 54 km, puces et optimisation visibles). Restent ⬜ : pauses planifiées, rappel de départ, enchaînement arrêt par arrêt.
