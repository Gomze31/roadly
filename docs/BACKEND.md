# Backend communautaire (LOT 8)

Roadly n'embarque aucun backend : le service communautaire est un projet **Supabase** (PostgreSQL + PostgREST) que l'exploitant crée lui-même, puis dont il saisit l'URL et la clé `anon` dans *Réglages → Communauté*. Tant que rien n'est saisi, l'application le dit clairement (feuille « Signaler » : types désactivés, message « service non configuré ») et n'envoie rien.

## Mise en place (10 min)

1. Créer un projet sur https://supabase.com (région UE).
2. SQL Editor → coller et exécuter `backend/supabase/schema.sql`.
3. Database → Extensions → activer `pg_cron`, puis exécuter la ligne `cron.schedule` commentée à la fin du script (expiration toutes les 5 min).
4. Project Settings → API : copier **Project URL** et **anon public key**.
5. Dans Roadly : Réglages → Communauté → coller les deux valeurs → Enregistrer. La ligne d'état passe à « Connecté ».

## Mettre à jour une installation existante

Ré-exécuter `backend/supabase/schema.sql` dans le SQL Editor : le script est ré-exécutable, garde les données et remplace les fonctions. Les versions de Roadly antérieures à 0.1.205 restent compatibles (elles comparent l'auteur elles-mêmes tant que le serveur le renvoie ; le nouveau schéma ne le renvoie plus, leurs propres signalements ne sont alors plus marqués comme tels).

## Sécurité (audit du 2026-11-16)

Le schéma précédent, testé sur PostgreSQL 16 avec le rôle `anon` de Supabase, laissait passer : 50 faux signalements d'affilée en inventant des identifiants d'appareil (la limite d'une minute était par identifiant), +100 points par auto-confirmation sous de faux identifiants, la désactivation d'un vrai signalement par 3 identifiants inventés, et l'identifiant d'appareil de l'auteur dans chaque signalement renvoyé (suivi des lieux signalés par un même appareil, usurpation de son identité). La lecture (`reports_in_box`) et le classement échouaient en outre pour `anon` (`permission denied`).

Protections actuelles :

- l'identifiant d'appareil n'est jamais renvoyé ; `mine` dit au demandeur si un signalement est le sien ;
- limites par adresse IP, en plus des limites par appareil : 5 nouveaux appareils / jour, 1 signalement / 15 s et 30 / h, 200 votes / h. L'adresse n'est jamais stockée : seulement un HMAC salé (sel tiré à l'installation), effacé après 24 h ;
- un appareil de moins d'une heure peut voter, mais son vote ne crédite aucun point et ne compte pas pour désactiver un signalement ; une confirmation ne crédite qu'une fois par votant ;
- lecture bornée à une boîte de 1° de côté ;
- fonctions `security definer` à `search_path` fixé ; exécution retirée à `PUBLIC`, accordée à `anon` pour les cinq fonctions d'API seulement.

L'adresse IP est lue dans `cf-connecting-ip` (posé par Cloudflare devant Supabase), sinon `x-real-ip`, sinon le premier élément de `x-forwarded-for` (qu'un client peut choisir). Vérifier sur son déploiement quel en-tête arrive (`select current_setting('request.headers', true)` depuis une fonction de test) avant de compter sur ces limites.

Limites restantes : sans compte, un attaquant disposant de nombreuses adresses IP peut encore créer des appareils ; les limites rendent cela lent et coûteux sans l'empêcher. Une connexion anonyme Supabase (`auth.uid()`) avec vérification d'intégrité de l'appareil (Play Integrity) serait l'étape suivante.

## Ce que fait le serveur (règles anti-triche, points)

- Identité : pseudonyme d'appareil (UUID aléatoire généré sur le téléphone, jamais de compte ni d'e-mail ; il sert de secret et n'est jamais renvoyé par le serveur). Table `profiles`.
- `create_report(kind, lat, lon, heading, device)` : durée de vie fixée **côté serveur** par type (bouchon 30 min … nid-de-poule 3 jours) ; 1 signalement / 60 s / appareil, et les limites par IP ci-dessus ; +2 points à l'auteur.
- `vote_report(report, confirm, device)` : une voix par appareil, interdit sur son propre signalement, 60 votes / h ; une confirmation prolonge de 15 min et, venant d'un appareil de plus d'une heure, crédite +1 point à l'auteur (une fois par votant) ; 3 démentis d'appareils de plus d'une heure, sans aucune confirmation, désactivent le signalement.
- `reports_in_box(s, w, n, e, p_device)` : lecture des signalements actifs et non expirés (max 200, boîte d'au plus 1°), sans identifiant d'auteur, avec `mine`.
- `expire_reports()` (pg_cron) : désactive les signalements expirés et efface les empreintes d'IP de plus de 24 h.
- RLS activé, aucun accès direct aux tables pour `anon` : tout passe par les fonctions `security definer`.

## Ce que fait le client

- `core:data/community` : `CommunityConfigRepository` (URL, clé, `device_id` généré une fois) et `SupabaseCommunityRepository` (appels `rpc/*` PostgREST avec `apikey` + `Authorization: Bearer <anon>`).
- Positions envoyées : seulement celle d'un signalement ou d'un vote ; aucune trace de trajet côté serveur.
- Signalements autour de la position affichés sur la carte (accueil et guidage) et projetés dans l'Horizon pendant le guidage.

## Non couvert

- Modération humaine (tableau de bord) : à faire côté Supabase (table `reports`, colonne `active`).
- Détection des signalements incohérents au-delà des règles ci-dessus (vitesse, fréquence) : à ajouter dans `create_report`.
- Autres conducteurs sur la carte : volontairement non implémenté (exige l'envoi périodique de la position).
