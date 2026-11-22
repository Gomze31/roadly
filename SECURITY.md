# Sécurité

## Signaler une vulnérabilité

Merci de ne pas ouvrir d'*issue* publique pour une faille. Utilisez le signalement privé de GitHub : onglet **Security › Report a vulnerability** du dépôt. Décrivez la version de Roadly, les étapes pour reproduire et l'impact. Une réponse est donnée dès que possible ; la correction est publiée dans une release, puis la faille est décrite ici.

## Versions officielles

Une version officielle de Roadly vient des releases du dépôt officiel et porte le certificat de signature publié dans
[`docs/SIGNATURE.md`](docs/SIGNATURE.md), qui donne aussi les commandes pour le vérifier. Une compilation faite par un tiers
porte un autre certificat : Android refuse de l'installer par-dessus une version officielle.

Aucun contributeur n'a accès à la clé de signature. Le code d'une *pull request* est vérifié par un workflow sans secret
(`.github/workflows/ci.yml`) ; la signature se fait dans un environnement protégé, après l'approbation du propriétaire
(`.github/workflows/release.yml`).

## Audit du 2026-11-16 (corrections publiées dans la version 0.1.206)

Audit de l'ensemble de l'application : mise à jour intégrée, composants exposés aux autres applications, réseau, stockage local, service communautaire, chaîne de publication, dépendances et données envoyées. Les failles du service communautaire ont été reproduites sur PostgreSQL 16 avec le rôle `anon` de Supabase, avant et après correction.

### Corrigé

| Gravité | Zone | Problème | Correction |
|---|---|---|---|
| Élevée | Service communautaire | Sans compte, il suffisait d'inventer des identifiants d'appareil pour publier des faux signalements en rafale (50 d'affilée), gonfler ses points (+100 par auto-confirmation) ou faire disparaître un vrai signalement (3 démentis) | Limites par adresse IP (HMAC salé, 24 h), votes des appareils de moins d'une heure sans effet sur les points ni la désactivation, un point par votant (`backend/supabase/schema.sql`) |
| Élevée | Service communautaire | Chaque signalement renvoyé contenait l'identifiant d'appareil de son auteur : suivi des lieux signalés par une même personne, usurpation de son identité | Identifiant jamais renvoyé ; champ `mine` calculé par le serveur ; l'application l'utilise |
| Moyenne | Service communautaire | Fonctions sans `search_path` fixé, exécutables par `PUBLIC` ; lecture et classement en échec pour `anon` ; lecture du monde entier possible | `security definer` + `search_path` fixé, droits accordés fonction par fonction, boîte de lecture bornée à 1° |
| Moyenne | Publication (GitHub Actions) | La version saisie au lancement manuel était recopiée dans le script shell avant sa vérification (injection de commande pour qui peut lancer le workflow) ; actions référencées par une étiquette déplaçable ; clé de signature décodée avant les tests | Saisie passée par variable d'environnement, actions épinglées à un commit, clé décodée juste avant la signature |
| Faible | Vie privée | Le transfert d'appareil à appareil d'Android 12+ ignore `allowBackup=false` : favoris, trajets et réglages pouvaient être copiés | `data_extraction_rules.xml` exclut tout, sauvegarde et transfert |
| Faible | Liens `geo:` / `google.navigation:` (ouvrables depuis une page web) | Coordonnées hors limites acceptées, libellés et recherches de longueur arbitraire | Bornes vérifiées, textes limités à 200 caractères, URI à 2 000 |
| Faible | Mise à jour intégrée | Le jeton GitHub aurait accompagné une adresse de fichier pointant hors de l'API GitHub | Jeton envoyé seulement à l'hôte de l'API (même schéma, même port) |
| Info | Documentation | La liste des services qui reçoivent une position était incomplète | Tableau à jour dans [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md#confidentialité) |

### Vérifié, sans problème

- **Mise à jour intégrée** : HTTPS vers l'API GitHub uniquement, release la plus récente seulement, version strictement supérieure exigée (pas de retour en arrière), empreinte SHA-256 obligatoire et vérifiée, fichier dans le stockage privé de l'application, installation par session `PackageInstaller` ; Android refuse un APK signé par une autre clé. Jeton GitHub chiffré par une clé AES-GCM du Keystore Android, jamais journalisé ni placé dans une URL.
- **Clé de signature** : absente du dépôt, fournie par les secrets GitHub Actions et effacée en fin de publication.
- **Composants exposés** : seuls l'activité principale (liens `geo:`), le widget et le service Android Auto sont exportés ; un lien ouvre au plus l'écran d'itinéraire, sans démarrer le guidage ; le récepteur d'installation n'est pas exporté ; toutes les `PendingIntent` sont explicites (immuables, sauf celle du programme d'installation qui doit l'être).
- **Android Auto** : en version publiée, seuls les hôtes de la liste de la bibliothèque (Android Auto, Android Automotive) peuvent se connecter.
- **Réseau** : trafic en clair refusé (politique Android par défaut), aucune désactivation de la vérification TLS, journal HTTP seulement dans les versions de développement, requêtes construites par des constructeurs (pas de concaténation de texte libre dans les URL ni dans le JSON).
- **Journaux** : aucune position, adresse ni jeton.
- **Dépendances** : versions récentes (OkHttp 4.12, MapLibre 11.13, Car App 1.7, Room 2.8…), sans vulnérabilité connue relevée à la date de l'audit.

### Limites connues

- Service communautaire sans compte : un attaquant disposant de nombreuses adresses IP peut encore créer des appareils, plus lentement. Étape suivante : connexion anonyme Supabase et vérification d'intégrité de l'appareil.
- La protection par adresse IP suppose que le déploiement Supabase transmette `cf-connecting-ip` (voir [`docs/BACKEND.md`](docs/BACKEND.md)).
- Les versions de développement peuvent être signées avec la clé de publication si elle est configurée sur le poste : ne jamais distribuer un APK de développement.
- Les services publics (Valhalla, Photon, Overpass, Open-Meteo) reçoivent les positions nécessaires à leur fonction ; pour une confidentialité maximale, les auto-héberger.
