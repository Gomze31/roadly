# SILLAXE-ANALYSE : référentiel fonctionnel de l'app GPS « Sillaxe » (iPhone + CarPlay, France)

Date de la recherche : 2026-10-30. Sources : pages publiques uniquement (site sillaxe.app + fiche App Store FR). Aucune image téléchargée, aucun code ni ressource de l'application consulté. Les citations font au plus 10 mots ; le reste est paraphrasé.

Convention : **V** = vérifié (lu sur une page), **H** = hypothèse. Sigles de sources : IDX = https://sillaxe.app/ ; PLUS = /sillaxe-plus.html ; COUV = /couverture.html ; SUP = /support.html ; CPT = /compte.html ; ETAT = /etat.html ; PRIV = /privacy.html ; CGU = /terms.html ; ML = /mentions-legales.html ; LOGIN = /login.html ; AS = fiche App Store (apps.apple.com/fr/app/sillaxe-gps-radars/id6812860288).

Les textes alternatifs des captures App Store sont vides (images en chargement différé) ; les descriptions d'écrans viennent des textes alternatifs du site et des textes des pages.

## A. Fiche produit

| Élément | Valeur | Source | Statut |
|---|---|---|---|
| Nom | « Sillaxe : GPS & radars » (sous-titre App Store : navigation, trafic, alertes) ; marque Sillaxe® | AS, IDX | V |
| Éditeur | Entrepreneur individuel, nom commercial Sillaxe, Paris 16e ; contact@sillaxe.app | ML, PRIV, AS | V |
| Plateforme | iPhone uniquement ; pas d'iPad, Mac ni Android | AS | V |
| Prérequis | iOS 18.4 ou ultérieur (iPhone XS / XR et suivants) | AS | V |
| CarPlay | Inclus gratuitement, sans abonnement | IDX, AS | V |
| Pays couverts | 13 : France, Allemagne, Andorre, Autriche, Belgique, Espagne, Italie, Luxembourg, Monaco, Pays-Bas, Portugal, Royaume-Uni, Suisse | IDX, COUV, AS | V |
| Cartes hors connexion | Proposées pour les mêmes 13 pays | COUV | V |
| Langues | Français, allemand, anglais, espagnol, italien, portugais | AS | V |
| Voix de guidage | Français : voix propre à Sillaxe, générée côté serveur ; autres langues : voix de l'iPhone | PRIV | V |
| Version / date | 1.0.2 (17 oct. 2026) ; 1.0 (3 oct.), 1.0.1 (4 oct.) | AS | V |
| Taille | 978,7 Mo (cartes embarquées probables : H) | AS | V |
| Classement / note | Navigation ; 4,7/5 sur 92 notes au 2026-10-30 | AS | V |
| Âge | 16+ ; contient publicité et contenu généré par les utilisateurs | AS | V |
| Prix | Gratuit + achats intégrés | AS | V |
| Formules Sillaxe+ | 1 mois 6,99 € ; 3 mois 18,99 € ; 6 mois 34,99 € ; 1 an 64,99 € | IDX, PLUS, AS | V |
| Essai | 7 jours au premier abonnement ; un essai par personne, carte, identité de facturation et appareil ; cartes prépayées exclues | IDX, CGU | V |
| Limites d'appareils | Compte Sillaxe+ : un iPhone à la fois (+ le site) ; abonnement App Store : jusqu'à 2 iPhone | IDX, CGU | V |
| Publicités (gratuit) | AdMob ; jamais pendant le guidage ni sur CarPlay ; personnalisées seulement avec consentement | IDX, PRIV | V |
| Achat | Site (Stripe, euros) ou in-app (Apple) | PLUS, CGU | V |
| Pays de souscription | FR, BE, LU, CH, DE, AT, NL, ES, PT, IT, UK (Andorre et Monaco absents) | PLUS, CGU | V |
| Rétractation | 14 jours | PLUS, CGU | V |
| Médiation | CM2C, Paris | CGU, ML | V |
| Sillaxe+ sans payer | 1 h par vidéo récompensée (1×/jour) ; 7 jours contre des points (1×/personne) ; 3 jours à la première montée en ligue « Aurore » ; événements « offert » | CGU, PRIV | V |

## B. Inventaire des fonctionnalités

Légende « G / + » : G = gratuit, + = Sillaxe+.

### B1. Navigation et guidage

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Guidage vocal + bandeau de direction | G | PLUS, AS | V | Voix claire, bandeau lisible d'un coup d'œil |
| Voies à prendre | G | IDX, COUV | V | Absent si marquage absent ou ambigu |
| Ronds-points dessinés avec la sortie | G | AS (1.0.1) | V | |
| Vitesse + limitation | G | IDX, AS | V | Compteur redessiné, limitation bien en vue |
| Son optionnel de dépassement | G | AS (1.0.2) | V | |
| Limitation abaissée en cas de pluie (option) | G | AS | V | |
| Limitations lues dans le sens de circulation | G | AS (1.0.1) | V | |
| Changer de destination / ajouter un arrêt en guidage | G | AS | V | |
| Jusqu'à 10 arrêts, ordre au choix | G | AS | V | Réordonnables aussi dans CarPlay |
| Départ planifié (heure ou « arriver à l'heure ») | G | IDX, AS | V | Rappel avant de partir ; ajout au calendrier |
| Recherche d'adresse France jusqu'au numéro | G | PLUS, AS | V | Apple + Base Adresse Nationale ; horaires des lieux |
| Adresses des contacts, lieux de rendez-vous (48 h) | G | AS, PRIV | V | Désactivés au départ ; traitement local |
| Adresse dictée (CarPlay) | G | IDX, PRIV | V | Micro actif seulement au toucher |
| Siri : maison, travail, adresse, signaler un accident | G | AS (1.0.1) | V | |
| Repères personnels (appui long) | G | AS | V | Enregistrer un lieu ou y aller |
| Boussole ; toucher sa position = adresse copiable | G | AS (1.0.2) | V | |
| Appui long sur l'icône de l'app | G | AS | V | Partager sa position ou rechercher |
| Mode paysage | G | AS (1.0.2) | V | |
| Carte clair / sombre / automatique | G | AS | V | Sombre au coucher du soleil |
| Mode Silence ; réglages « Voix et sons », « Apparence » | G | AS | V | |
| Import GPX | ? | PRIV | V (existence) / H (palier) | |
| Guidage allégé hors connexion | G | CGU, COUV | V | Si le trajet est déjà chargé |
| ZFE : alerte Crit'Air | ? | AS (1.0.2) | V / H (palier) | |
| Numéros de route et noms de rue sur le tracé | G | AS | V | |
| Feux, stops, ralentisseurs visibles | G | AS | V | Feux comptés dès le choix d'itinéraire |
| Tunnels (balises mémorisées) | G | PRIV | V | Stocké sur l'appareil |

### B2. Horizon

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Horizon : « la route qui vient » | G | IDX, AS | V | Zone en bas de l'écran de guidage : virages, ralentissements, services, manœuvres, bouchons, feux, incidents, zones de vigilance |
| Halo des zones de vigilance dans Horizon | G | PLUS, CGU | V | |
| Horizon dans CarPlay | G | IDX | V | |

### B3. Trafic et incidents

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Trafic en direct, bouchons et fermetures | G | PLUS | V | TomTom pour certains itinéraires (ML) |
| Routes coupées évitées d'office | G | PLUS, AS | V | |
| Travaux pris en compte à l'heure de passage | G | AS | V | Jeux de données chantiers par ville/région |
| Re-routage si bouchon | G | AS | V | |
| Heure d'arrivée corrigée par apprentissage | G | AS, PRIV | V | Temps par portion ; jugée imprécise par un avis |
| « Intelligence collective » | G | PLUS, PRIV | V | Actif par défaut, désactivable ; tronçons et vitesses agrégés |
| Itinéraire « temps réel » sur longs trajets | + | PLUS | V | |
| Météo sur la carte (option) | G | PRIV | V | Apple Weather, position arrondie à 1 km |
| Protection anti-pluie deux-roues | G | PLUS | V | Non détaillé |

### B4. Zones de vigilance

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Annonce des zones pendant le guidage (écran + voix) | G | IDX, CGU, AS | V | |
| Limitation de la zone, bips d'approche | + | PLUS, AS | V | |
| Alerte sans itinéraire (app ouverte) | + | PLUS | V | |
| Avis communautaire (confirmer / démentir) | G | PRIV | V | Conservé 90 jours |
| France | | AS | V | Jamais l'emplacement exact d'un contrôle, seulement des zones |
| Hors France | + | AS | V | Radars affichés et annoncés |
| Suisse | | IDX, PLUS, AS | V | Rien n'est signalé (loi) |

### B5. Itinéraires et coûts

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Comparaison de plusieurs itinéraires | G | IDX | V | Trois propositions : durée, distance, trafic |
| Coût réel (péage + carburant) par itinéraire | G | IDX, AS | V | Carburant selon carnet de consommation (H) |
| Itinéraire adapté au gabarit | + | IDX, PLUS | V | Ponts trop bas évités |
| Relevé kilométrique et coût carburant | + | PLUS | V | |

### B6. Stations, bornes, parkings, aires, ponts, ralentisseurs

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Stations-service avec prix du carburant choisi | G | AS | V | data.economie.gouv.fr (FR) + équivalents ES/IT/PT/AT/BE/LU/UK |
| Prix proposés par la communauté | G | PRIV | V | 7 jours ; prix retenu 30 jours |
| Info « paiement en espèces » | G | PRIV | V | |
| Outre-mer : prix maximal officiel | G | AS | V | |
| Stations dans CarPlay (prix, détour, fermeture) | G | AS | V | |
| Bornes (puissance, prix au kWh) | G | AS | V | IRVE (FR), Chargy / Eco-Movement (LU), OFEN (CH) |
| Parkings (gratuit/payant, disponibilités) | G | AS, ML | V | Indigo, Saemes, Strasbourg, Aix-Marseille, Luxembourg |
| Aires de repos et de service | G | AS | V | |
| Ponts trop bas signalés avant l'ouvrage | G | IDX, PLUS | V | Évités avec Sillaxe+ |
| Ralentisseurs annoncés | G | AS | V | |
| « Parking Sense » (stationnement + recharge à l'arrivée) | G | PLUS, CGU | V | Sans garantie de disponibilité |

### B7. Planificateur et tournées

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Planificateur de pauses | G | PLUS, AS | V | Aires, arrêts, heure au choix |
| Mode tournée (ordre optimal, arrêt par arrêt) | + | PLUS, AS | V | Calcul côté serveur |
| Départ planifié | G | B1 | V | |

### B8. Stationnement et retour à pied

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Stationnement proposé à l'arrivée | G | IDX | V | |
| Chemin à pied jusqu'à la porte | G | IDX, AS | V | |
| Retour à la voiture à pied | G | IDX, PLUS | V | Position en arrière-plan pendant ce retour |
| Mémorisation de la place (photo, adresse) | G | PRIV | V | Sur l'appareil seul |
| Fin de trajet par activité de mouvement | G | PRIV | V | Autorisation distincte |

### B9. Communauté et signalements

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Accident, bouchon, obstacle, route fermée, météo | G | AS | V | |
| Nids-de-poule, feux de chantier | G | AS (1.0.2) | V | Nid-de-poule : quelques jours au plus |
| Depuis CarPlay / Siri | G | IDX, AS | V | |
| Expiration automatique | G | PRIV | V | Minutes à heures selon le type |
| Confirmation / démenti | G | PRIV | V | |
| « Limite affichée fausse ? » | G | PRIV | V | 10 conducteurs de confiance ou validation équipe ; mise à jour hebdomadaire |
| Autres conducteurs sur la carte | G | AS, PRIV | V | Décalés ≥ 1 km en arrière, 1 min 30, désactivable |
| Support intégré | G | SUP, AS | V | Fil, 2 photos max |

### B10. Gamification

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Points (signalements confirmés, avis) | G | AS, PRIV | V | Remis à zéro après 90 jours d'inactivité |
| Récompenses : avatars, bannières, teintes, silhouettes | G | AS | V | |
| Vidéo récompensée | G | PRIV, CGU | V | |
| Classement km (mois, tous temps) | G | AS, PRIV | V | Pseudonyme ; profil public/privé/masqué |
| Ligues du mois | G | AS, PRIV | V | « Aurore », « Soleil » cités |
| Défis de la semaine | G | AS, PRIV | V | |
| Trophées | G | AS | V | Pays traversés (local) |
| Amis (suivi à sens unique) | G | AS, PRIV | V | Notification de dépassement |
| Bilan du mois partageable | G | AS, PRIV | V | |
| Prime de fidélité (série de jours) | G | PRIV | V | |
| Thèmes de saison | G | AS | V | Halloween, Néon, Noël |
| Anti-triche des ligues | G | PRIV | V | Positions simulées comptées |

### B11. Partage de trajet

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Partage de trajet en direct | + | IDX, PLUS | V | |
| Liens nommés, révocables | + | CPT, CGU | V | « Mon espace » |
| Contenu partagé | + | PRIV | V | Position, cap, vitesse, ETA, départ, destination ; page web avec carte |
| Notification de départ ; « Envoyer ma position » | + | PRIV | V | |
| Partage de position instantanée | G | AS | V | |

### B12. Replay, historique, dépenses

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Historique des trajets | G | PLUS | V | Sur l'appareil |
| Replay | + | IDX, PLUS | V | |
| Relevé kilométrique + coût | + | PLUS | V | |
| Carnet de consommation | ? | PRIV | V / H | |

### B13. Profils véhicule

| Fonction | G / + | Source | Statut | Détails observables |
|---|---|---|---|---|
| Poids lourd | + | PLUS, AS | V | Hauteur, largeur, poids, ponts, pauses, interdictions, péages PL, registre |
| Taxi | + | PLUS, AS | V | Voies réservées, stations |
| Dimensions | + | IDX, PLUS | V | Hauteur, largeur, poids |
| Deux-roues | G | PLUS | V (indice) | |

### B14. Péage en flux libre

| Fonction | G / + | Source | Statut |
|---|---|---|---|
| Rappel daté de paiement après portique (France) | + | IDX, PLUS | V |

### B15. Favoris et synchronisation

| Fonction | G / + | Source | Statut |
|---|---|---|---|
| Favoris locaux (étoile) | G | AS | V |
| Domicile / travail / favoris synchronisés (web + nouvel iPhone) | + | IDX, CPT | V |
| Destinations récentes (locales) | G | CPT | V |

### B16. Personnalisation

| Fonction | G / + | Source | Statut |
|---|---|---|---|
| Teinte du cockpit | + | PLUS | V |
| Icône alternative | + | PLUS | V |
| Avatars | + | PLUS | V |
| Silhouettes de véhicule | + | PLUS | V |

### B17. Compte et web

| Fonction | G / + | Source | Statut |
|---|---|---|---|
| Compte pseudonyme automatique | G | IDX, PRIV | V |
| Connexion e-mail sans mot de passe (lien ou code 6 chiffres, 20 min) | G | LOGIN, IDX | V |
| Mon espace : Vue d'ensemble, Mes lieux, Appareils et partage, Mes données | + | CPT | V |
| Support web ; État du service | G | SUP, ETAT | V |

## C. Écrans et parcours

Textes alternatifs du site :
1. Guidage : prochaine manœuvre, vitesse, limitation et Horizon sur le même écran (V). H : bandeau de manœuvre en haut, vitesse et limitation en incrustation, Horizon en bas.
2. Choix d'itinéraire : trois propositions avec durée, distance et trafic (V).
3. Guidage en ville de nuit, Horizon en bas de l'écran (V : thème sombre, Horizon en bas).
4. Zone de vigilance signalée dans Horizon (V).
5. Replay : trajet rejoué sur la carte (V).

Écrans décrits dans les textes :
- Écran d'arrivée : stationnement, chemin à pied, vidéo récompensée, publicité page entière (gratuit).
- Sélecteur d'itinéraires : une annonce parmi les itinéraires (gratuit).
- Accueil : boussole ; toucher sa position affiche l'adresse exacte.
- « Mes points », Classement (mois / tous temps, ligues, amis, défis, trophées, bilan).
- CarPlay : carte déplaçable, stations devant soi, arrêts réordonnables, micro, autres conducteurs.
- Réglages : abonnement, Voix et sons, Apparence, Légal, Choix publicitaires, Mentions & confidentialité, Supprimer mon compte, Améliorer les routes ensemble, Personnaliser mon profil.
- Mon espace web : Vue d'ensemble (abonnement), Mes lieux, Appareils et partage (connexions, liens de suivi), Mes données (export JSON, suppression par code).
- Support : types bug / idée / autre, titre, détail, 2 photos, fil « Mes demandes ».
- État du service : Service, Données de navigation, Guidage, Partage de trajet, Signalements.

Couleurs : aucune valeur citée (thème clair/sombre/automatique, thèmes de saison, teintes du cockpit).

## D. Paramètres mentionnés

Améliorer les routes ensemble ; adresses des contacts ; lieux des rendez-vous ; météo sur la carte ; aide au stationnement ; limitation abaissée quand il pleut ; son de dépassement ; clair / sombre / automatique ; Silence ; Voix et sons ; Apparence ; visible des autres conducteurs ; profil public / privé / masqué ; notifications ; choix publicitaires ; autorisations (position y compris arrière-plan, activité, micro, reconnaissance vocale, contacts, calendrier, notifications) ; profil de véhicule (voiture, PL, taxi), motorisation ; préférences d'arrivée ; départ planifié ; pauses ; teinte, icône, avatars, silhouettes ; vignette Crit'Air ; unités (milles au Royaume-Uni).

## E. Données et services évoqués

Cités : OpenStreetMap (ODbL), cartes hors connexion 13 pays, Apple MapKit / MapKit JS (page de suivi), recherche Apple ; BAN, IGN ; trafic TomTom + Bison Futé, DiaLog, NDW, Verkeerscentrum Vlaanderen, DGT, Madrid, Catalogne, MobiData BW, VIZ Berlin, National Highways, TfL, Wales, Wien, Salzburg, Genève, Bologne, Nantes, Bordeaux ; carburants : ministère de l'Économie (FR), ES, IT, PT, E-Control (AT), Statbel (BE), STATEC (LU), enseignes UK ; bornes IRVE, Chargy / Eco-Movement, OFEN ; parkings : Base nationale des lieux de stationnement, Indigo, Saemes, Strasbourg, Aix-Marseille, Luxembourg ; météo : Météo-France, Apple Weather ; hébergement OVH, Cloudflare ; Stripe ; Brevo ; AdMob.

Non précisé : moteur de routage ; source des limitations hors OSM ; source des radars / zones ; base des ponts bas ; source Crit'Air / ZFE ; prix DE/NL/CH/AD/MC ; couverture recherche hors France ; fréquences de mise à jour (limitations : hebdomadaire).

## F. Confidentialité et conditions notables

- Rien n'est vendu, pas de profil commercial ; pas de cookies publicitaires sur le site.
- Position envoyée au serveur toutes les 15 à 30 s pour les signalements proches, sans conservation ; aucun historique côté serveur.
- Position en arrière-plan : navigation, 2 min après la fin, retour à pied, CarPlay.
- Compte pseudonyme d'appareil ; suppression après 24 mois d'inactivité.
- Suppression du compte : dans l'app ou sur le web (code 6 chiffres) ; export JSON.
- Conservations : signalements minutes–heures ; avis 90 jours ; support 180 jours ; ligues 13 mois ; journaux IP 14 jours ; sauvegardes 14 jours.
- Anti-abus : empreintes carte / e-mail pour l'essai, App Attest, exclusions automatiques contestables.
- Hors UE : hébergement, Cloudflare, cartographie, reconnaissance vocale, notifications, e-mails, paiement, AdMob (décision d'adéquation ou clauses types).

## G. Points juridiques (formulation Sillaxe)

- France : jamais l'emplacement exact d'un contrôle ; seules des « zones de vigilance ».
- Hors France : radars avec Sillaxe+ « là où la loi permet ».
- Suisse : aucun avertissement.
- L'utilisateur reste responsable (amendes, points) ; la signalisation réelle prévaut ; ETA et distances sont des estimations ; ne pas manipuler l'app en roulant.
- Abonnement : essai 7 j, résiliation en ligne, rétractation 14 j, préavis 30 j avant changement défavorable.

## H. Inconnues et hypothèses

1. H : Horizon = ruban sous la carte avec pictos (manœuvres, bouchons, services).
2. H : base de radars communautaire et/ou publique, non précisée.
3. H : Crit'Air / ZFE gratuit.
4. H : routage propriétaire (cartes hors ligne, OSM, trafic TomTom).
5. H : import GPX, carnet de consommation, deux-roues gratuits.
6. H : autres profils véhicule (VUL, camping-car) non cités.
7. V : aucune version Android ; H : Android Auto = équivalent naturel de CarPlay.
8. Inconnu : noms/ordre des ligues, barème des points, nombre de teintes/icônes/silhouettes, contenu d'admin.html, fréquence de mise à jour des zones et cartes.
9. Constat : avis du 20 oct. — ETA et trafic communautaire imprécis (peu d'utilisateurs) ; une route signalée fermée à tort.

## I. URLs lues (toutes HTTP 200)

https://sillaxe.app/ · /sillaxe-plus.html · /couverture.html · /support.html · /compte.html · /etat.html · /privacy.html · /terms.html · /mentions-legales.html · /login.html · https://apps.apple.com/fr/app/sillaxe-gps-radars/id6812860288. Non lu : /admin.html (session requise).
