# Feuille de route de Roadly

Ce document reprend la section 11 de la spec (`docs/superpowers/specs/2026-10-30-roadly-v1-design.md`) et détaille les Phases 2 à 5 : fonctionnalités, modules impactés, prérequis externes et risques. Il s'agit d'un plan d'intention, sans dates ni estimations : rien ici n'est engagé tant que la phase n'a pas fait l'objet de sa propre spec.

Point de départ : la v0.1.0 livre la Phase 1 (carte, position, recherche, favoris, comparaison de trois itinéraires, coût carburant sur saisie utilisateur) et la Phase 2 (guidage vocal pas à pas, voir son statut ci-dessous). Le trafic et les données de stations ne sont pas là. Voir le `README.md` pour l'état exact.

Principe constant : aucune donnée de trafic, de prix, de péage ou de limitation de vitesse n'est jamais inventée ni simulée. Sans source fiable, la fonction reste « Bientôt ».

## Phase 2 : guidage

Statut : livrée le 2026-10-30 (plan `docs/superpowers/plans/2026-10-30-roadly-phase2-guidage.md`), **vérification sur appareil en attente** : 218 tests unitaires passent, mais l'installation sur le HONOR Magic 8 Pro, les tests instrumentés et le contrôle manuel du guidage n'ont pas pu être faits (téléphone non visible par ADB pendant la Task 12). Restent bloquées la limitation de vitesse et l'alerte de dépassement tant qu'aucun Valhalla auto-hébergé n'est configuré ; l'affichage des voies dépend de la réponse Valhalla (aucune voie dans les réponses FOSSGIS observées). Prochaine étape : Phase 3, après la vérification sur appareil de la Phase 2.

### Fonctionnalités

- Guidage vocal en français avec synthèse vocale (TTS) d'Android.
- Suivi d'itinéraire : projection de la position sur le tracé, manœuvre suivante, distance restante, heure d'arrivée mise à jour.
- Détection de sortie d'itinéraire et recalcul.
- Service de premier plan avec notification persistante pour continuer le guidage écran éteint ou application en arrière-plan.
- Mode conduite complet (interface dédiée, bouton « Démarrer » activé).
- Limitations de vitesse obtenues par `trace_attributes` sur un Valhalla auto-hébergé, et alerte de dépassement. Le badge de vitesse affiche « — » sans Valhalla auto-hébergé.

### Modules impactés

- `core:navigation` (nouveau) : suivi sur le tracé, annonces, recalcul, perte GPS, synthèse vocale, limitation (`trace_attributes`), service de premier plan.
- `feature:navigation` (nouveau) : écran de guidage en mode conduite.
- `core:model`, `core:routing` : voies, consignes vocales, numéros de sortie, préférences d'itinéraire.
- `core:data` : réglages voix, volume, alertes, préférences (DataStore).
- `core:location` : mode GUIDANCE 1 Hz prioritaire.
- `core:map`, `core:designsystem` : tracé parcouru, tolérance du badge de vitesse.
- `feature:route`, `feature:settings`, `app` : « Démarrer », réglages, permissions, service, retour depuis la notification.

### Prérequis externes

- Un Valhalla auto-hébergé (voir `docs/API-PROVIDERS.md`) : le serveur FOSSGIS est réservé au développement en fair-use, et la limitation de vitesse en dépend.
- Voix française disponible sur l'appareil (moteur TTS installé).
- Permissions Android : localisation précise, notification, service de premier plan de type localisation (Play Store : justification déclarée).
- Les URL en HTTP en clair sont bloquées par Android par défaut : le serveur auto-hébergé doit être en HTTPS, ou une exception réseau doit être ajoutée.

### Risques

- Consommation de batterie et fiabilité du service de premier plan selon les constructeurs (arrêt agressif des applications en arrière-plan).
- Qualité de la correspondance position / tracé (tunnels, canyons urbains) : risque de fausses sorties d'itinéraire et de recalculs répétés.
- Dépendance à un serveur Valhalla : coût et maintenance d'un VPS (≥ 4 Go de RAM pour la France), mises à jour des données.
- Calibrage du timing des annonces vocales selon la vitesse.
- Aucun test sur appareil n'a encore été exécuté pour la v1 : la base doit être validée sur terrain avant d'empiler le guidage.

## Phase 3 : trafic, incidents, stations, bornes, parkings, péages

### Fonctionnalités

- Trafic en temps réel et incidents (puces « Incidents » de l'accueil, aujourd'hui désactivées).
- Signalements communautaires : création, vote, modération, expiration automatique. Les zones de danger doivent respecter la loi française (pas d'affichage d'emplacements de contrôles de vitesse).
- Stations-service et prix des carburants (puce « Stations »), avec utilisation du prix réel dans le calcul de coût, en conservant la possibilité de saisie manuelle.
- Bornes de recharge pour véhicules électriques (puce « Bornes »).
- Parkings (puce « Parkings »).
- Péages : montant réel affiché à la place de la mention « Phase 3 ».

### Modules impactés

- `feature:home` : activation des puces et des couches associées.
- `core:map` : couches de marqueurs, regroupement (clustering) des points.
- `core:data` : cache local des stations, bornes et parkings, nouveaux clients HTTP, éventuel client Supabase.
- `core:routing` : intégration du trafic dans les temps (l'heure de départ programmable ne décale aujourd'hui que l'heure d'arrivée), montant des péages dans `RouteOption`, recherche « sur le trajet ».
- `core:model` : types de lieux et d'événements (station, borne, parking, incident, signalement).
- Nouveaux modules probables : `core:traffic`, `core:community`, `feature:poi` (noms indicatifs, à décider dans la spec de la phase).
- `feature:settings` : préférences de signalements, de confidentialité et de carburant.

### Prérequis externes

- Trafic : fournisseur à contractualiser (Bison Futé / Autoroutes.fr nécessitent un conventionnement). Jamais de trafic simulé.
- Prix des carburants : jeu de données du ministère de l'Économie (`data.economie.gouv.fr`, flux instantané v2).
- Bornes : fichier consolidé IRVE de `transport.data.gouv.fr`.
- Péages : données ASFA, sans API publique officielle ; accord à obtenir avant d'afficher un montant.
- Signalements : backend Supabase à créer et à exploiter, avec politique de modération et conditions d'utilisation.
- Détail des sources : `docs/API-PROVIDERS.md`, section 8.

### Risques

- Dépendance contractuelle et coût du fournisseur de trafic : sans accord, la fonction ne peut pas exister.
- Qualité et fraîcheur des données ouvertes (prix, bornes) ; volumétrie du fichier IRVE.
- Conformité juridique des signalements de zones de danger en France.
- Modération, spam et abus sur les signalements communautaires ; données personnelles liées aux comptes ou aux positions envoyées (nouvelle politique de confidentialité à écrire, car la v1 ne stocke rien côté serveur).
- Absence de données péages fiables : risque d'afficher un montant faux, à éviter en gardant « montant : Phase 3 » tant que la source n'est pas sûre.

## Phase 4 : planification et suivi

### Fonctionnalités

- Planificateur multi-étapes (waypoints) avec réordonnancement.
- Pauses planifiées sur un trajet long.
- Mode tournée (enchaînement d'arrêts).
- Statistiques et historique des trajets.
- Profils (plusieurs véhicules ou usages).
- Synchronisation des données entre appareils.

### Modules impactés

- `feature:route` : édition de trajets à plusieurs étapes.
- `core:routing` : routage avec étapes (`RouteRequest` accepte déjà une liste de `waypoints`) ; optimisation d'ordre pour le mode tournée.
- `core:data` : tables Room pour trajets enregistrés, historique, profils ; couche de synchronisation.
- `feature:settings` : gestion des profils, export et suppression des données.
- Nouveaux modules probables : `feature:trips`, `feature:stats` (noms indicatifs).

### Prérequis externes

- Un backend de synchronisation (par exemple Supabase, déjà envisagé en Phase 3) avec authentification.
- Un moteur capable d'optimiser des tournées si le mode tournée dépasse le simple enchaînement (Valhalla propose `optimized_route` ; à valider sur la version déployée).

### Risques

- Vie privée : l'historique des trajets est une donnée sensible ; le stockage, le chiffrement et la suppression doivent être spécifiés avant le développement.
- Conflits de synchronisation entre appareils.
- Coût de calcul et limites de l'instance de routage pour les tournées de nombreux points.
- Migrations de base Room à soigner (profils et historique modifient le schéma).

## Phase 5 : fiabilisation et diffusion

### Fonctionnalités

- Campagne de tests sur appareils réels (plusieurs constructeurs et versions d'Android, de 10 à la plus récente).
- Optimisation de la batterie.
- Comportement hors ligne : cartes et recherche disponibles sans réseau (aujourd'hui, seul le mode dégradé existe : bandeau, recherche limitée aux favoris et récents).
- Android Auto.
- Publication sur le Play Store.

### Modules impactés

- `core:map` et `core:data` : tuiles et données hors ligne, gestion du stockage.
- `core:routing` : routage hors ligne, ce qui impose un moteur embarqué ou un autre mécanisme (à étudier ; non prévu en v1).
- `core:location` : réglages d'énergie.
- Nouveau module probable pour Android Auto (`auto` ou équivalent, à décider).
- `app` : réduction de la taille de l'APK (App Bundle, découpage par ABI), signature de release, obfuscation et règles ProGuard, `network_security_config`.

### Prérequis externes

- Parc d'appareils de test, ou service de test sur appareils.
- Compte développeur Google Play, politique de confidentialité publiée, déclarations de permissions (localisation, service de premier plan).
- Pour Android Auto : respect des règles de la catégorie « navigation » (Car App Library) et validation par Google.
- Contact réel dans l'en-tête `User-Agent` et fournisseurs de production en place (Valhalla et idéalement Photon auto-hébergés).

### Risques

- Les tests instrumentés de la v1 (DAO Room, barre de recherche Compose) n'ont jamais été lancés sur un appareil : la dette de vérification s'accumule si elle n'est pas traitée avant les phases suivantes.
- Le routage hors ligne est un chantier lourd (poids des données, moteur) et peut ne pas rentrer dans le périmètre.
- Taille de l'APK (environ 115 Mo en debug) et exigences de la Play Console.
- Validation Android Auto potentiellement longue.
- Le nom « Roadly » est provisoire : vérifier sa disponibilité (marque, Play Store) avant la publication.
