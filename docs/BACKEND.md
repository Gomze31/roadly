# Backend communautaire (LOT 8)

Roadly n'embarque aucun backend : le service communautaire est un projet **Supabase** (PostgreSQL + PostgREST) que l'exploitant crée lui-même, puis dont il saisit l'URL et la clé `anon` dans *Réglages → Communauté*. Tant que rien n'est saisi, l'application le dit clairement (feuille « Signaler » : types désactivés, message « service non configuré ») et n'envoie rien.

## Mise en place (10 min)

1. Créer un projet sur https://supabase.com (région UE).
2. SQL Editor → coller et exécuter `backend/supabase/schema.sql`.
3. Database → Extensions → activer `pg_cron`, puis exécuter la ligne `cron.schedule` commentée à la fin du script (expiration toutes les 5 min).
4. Project Settings → API : copier **Project URL** et **anon public key**.
5. Dans Roadly : Réglages → Communauté → coller les deux valeurs → Enregistrer. La ligne d'état passe à « Connecté ».

## Ce que fait le serveur (règles anti-triche, points)

- Identité : pseudonyme d'appareil (UUID généré sur le téléphone, jamais de compte ni d'e-mail). Table `profiles`.
- `create_report(kind, lat, lon, heading, device)` : durée de vie fixée **côté serveur** par type (bouchon 30 min … nid-de-poule 3 jours) ; 1 signalement / 60 s / appareil ; +2 points à l'auteur.
- `vote_report(report, confirm, device)` : une voix par appareil, interdit sur son propre signalement ; une confirmation prolonge de 15 min et crédite +1 point à l'auteur ; 3 démentis sans aucune confirmation désactivent le signalement.
- `reports_in_box(s, w, n, e)` : lecture des signalements actifs et non expirés (max 200).
- `expire_reports()` (pg_cron) : désactive les signalements expirés.
- RLS activé, aucun accès direct aux tables pour `anon` : tout passe par les fonctions `security definer`.

## Ce que fait le client

- `core:data/community` : `CommunityConfigRepository` (URL, clé, `device_id` généré une fois) et `SupabaseCommunityRepository` (appels `rpc/*` PostgREST avec `apikey` + `Authorization: Bearer <anon>`).
- Positions envoyées : seulement celle d'un signalement ou d'un vote ; aucune trace de trajet côté serveur.
- Signalements autour de la position affichés sur la carte (accueil et guidage) et projetés dans l'Horizon pendant le guidage.

## Non couvert

- Modération humaine (tableau de bord) : à faire côté Supabase (table `reports`, colonne `active`).
- Détection des signalements incohérents au-delà des règles ci-dessus (vitesse, fréquence) : à ajouter dans `create_report`.
- Autres conducteurs sur la carte : volontairement non implémenté (exige l'envoi périodique de la position).
