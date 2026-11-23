# Mises à jour depuis GitHub

Roadly vérifie les **releases GitHub** du dépôt `Gomze31/roadly`, télécharge l'APK de la dernière version, vérifie son empreinte SHA-256 puis lance l'installation Android. Réglages › **Mises à jour** ; vérification automatique à chaque ouverture ou retour dans l'application (au plus toutes les 30 min, jamais pendant un téléchargement ; désactivable).

## Fonctionnement

1. `GET https://api.github.com/repos/Gomze31/roadly/releases/latest` (avec le jeton si le dépôt est privé).
2. Lecture de `update.json` joint à la release : `versionCode`, `versionName`, nom de l'APK, SHA-256, `minSdk`.
3. Proposée seulement si `versionCode` > version installée (Réglages › Mises à jour affiche « Version x (code) »).
4. Téléchargement (lien signé GitHub ; le jeton n'est pas transmis à l'hôte de téléchargement), contrôle SHA-256 du fichier reçu.
5. Comparaison du certificat de signature de l'APK avec celui de l'application installée (voir « Contrôles avant l'installation »). Certificat différent : rien n'est remis à Android, Roadly affiche « réinstallation nécessaire ».
6. Installation par `PackageInstaller` : le SHA-256 est recalculé sur les octets copiés dans la session d'installation ; s'il diffère, la session est abandonnée. Android demande ensuite la confirmation. À partir d'Android 12, une fois qu'une version a été installée par Roadly lui-même, les suivantes peuvent s'installer sans confirmation.

Le `versionCode` est le **nombre de commits** (`git rev-list --count HEAD`), en local comme sur GitHub : il augmente à chaque commit sur `main`, et une compilation locale et une release du même commit ont le même code.

**Signature :** Android n'accepte une mise à jour que si l'APK est signé avec **la même clé** que l'application installée. C'est la vraie garantie de sécurité (le SHA-256 protège seulement contre un téléchargement corrompu). D'où la configuration ci-dessous.

## Contrôles avant l'installation

| Contrôle | Règle | En cas d'écart |
|---|---|---|
| Serveurs | HTTPS uniquement, et seulement `api.github.com`, `github.com`, `objects.githubusercontent.com`, `release-assets.githubusercontent.com`. Vérifié sur la première adresse et sur chaque redirection. | « Adresse de téléchargement refusée » ; rien n'est téléchargé |
| Tailles | 1 Mio pour la release et `update.json` ; 200 Mio pour l'APK (taille annoncée, `Content-Length` et octets réellement reçus) | « Fichier trop volumineux » |
| Empreinte | SHA-256 de `update.json`, vérifié après le téléchargement puis à nouveau sur les octets remis à Android | « Fichier corrompu » ou « Fichier modifié depuis sa vérification » |
| Certificat | Le signataire de l'APK doit être celui de l'application installée (une rotation de clé déclarée dans l'APK est acceptée) | « réinstallation nécessaire » ; l'APK est supprimé, l'installateur n'est jamais appelé, la version n'est plus proposée à l'ouverture |

**Ce que le contrôle du certificat garantit, et ce qu'il ne garantit pas.**

- Il compare l'APK à l'application **déjà installée**. C'est la règle qu'Android applique lui-même à l'installation : le contrôle n'ajoute pas de confiance, il évite de proposer sans fin une mise à jour vouée à l'échec et dit quoi faire.
- **Aucune clé de publication n'est épinglée** dans l'application : la clé qui signera les versions publiées n'est pas encore choisie (`docs/ROADLY-1.0/DECISIONS.md`), et `SignerCheck.PINNED_SIGNERS` est vide. Ce n'est donc pas une chaîne de confiance épinglée.
- Le SHA-256 vient de la même release que l'APK : il détecte un fichier abîmé ou remplacé en route, pas une release malveillante. Qui peut publier dans le dépôt **et** signer avec la clé de l'application installée contrôle ce qui s'installe.
- Si Android ne sait pas lire les certificats (cas non observé), l'APK est remis à Android, qui tranche. Ce cas sera refusé le jour où une clé sera épinglée.
- La lecture des certificats et la copie dans la session d'installation n'ont jamais été exécutées sur un appareil : seule leur logique est testée (JVM).

Après un redémarrage de l'application, une version refusée pour sa clé réapparaît comme disponible dans Réglages › Mises à jour ; un nouveau téléchargement la refuse à nouveau.

## Sauvegarde des favoris et des trajets

Android ne sauvegarde pas les données de Roadly (`allowBackup="false"`) : une désinstallation les efface. Avant une réinstallation (changement de clé de signature, retour à une version antérieure) :

1. Réglages › Données › **Sauvegarde** › « Exporter mes favoris et trajets » : choisir un emplacement hors de Roadly (Téléchargements, Drive…). Le fichier s'appelle `roadly-sauvegarde-AAAA-MM-JJ.json`.
2. Désinstaller, installer la nouvelle version.
3. Réglages › Données › Sauvegarde › « Importer une sauvegarde » : choisir le fichier.

| Point | Comportement |
|---|---|
| Contenu | Favoris et trajets (avec leur tracé). **Non exportés** : réglages, jeton GitHub, clés d'accès, fiches trottinette, recherches récentes, profil du véhicule |
| Import | Ajoute sans rien supprimer. Un favori de même identifiant est mis à jour ; le domicile et le travail déjà définis sur l'appareil sont conservés. Un trajet déjà présent (mêmes instants de départ et d'arrivée) n'est pas dupliqué : importer deux fois n'ajoute rien |
| Fichier refusé | Fichier qui n'est pas une sauvegarde Roadly, illisible, de plus de 32 Mio, ou écrit par une version plus récente : rien n'est modifié |
| Lignes invalides | Écartées et comptées dans le bilan ; les autres sont importées |
| Limites | 5 000 favoris, 20 000 trajets. Au-delà de 24 Mio de tracés, les trajets les plus anciens sont exportés sans leur tracé (leur nombre est affiché). Un trajet importé perd son lien avec la fiche trottinette |

L'export et l'import n'ont jamais été exécutés sur un appareil (sélecteur de fichiers Android) ; le format, les bornes et l'ajout sans perte sont testés sur la JVM.

## Mise en place (une fois)

### 1. Choisir la clé de signature

Une clé dédiée, créée et sauvegardée par le propriétaire : procédure, conséquences et sauvegarde dans [`SIGNATURE.md`](SIGNATURE.md).

**La clé de debug du PC n'est plus une option de publication** : le workflow refuse tout APK dont le certificat est `CN=Android Debug` (mots de passe publics, fichier régénérable par le SDK). Le téléphone qui porte encore une version signée en debug doit être réinstallé une fois : [`MIGRATION-HONOR.md`](MIGRATION-HONOR.md).

### 2. Secrets et variable GitHub

Les quatre secrets (`ROADLY_KEYSTORE_BASE64`, `ROADLY_KEYSTORE_PASSWORD`, `ROADLY_KEY_ALIAS`, `ROADLY_KEY_PASSWORD`) se rangent dans l'**environnement protégé `release`**, avec le propriétaire comme relecteur obligatoire. La variable `ROADLY_SIGNER_SHA256` porte l'empreinte du certificat attendu. Détail : [`SIGNATURE.md`](SIGNATURE.md), étape 3.

### 3. Même clé pour les compilations locales

Dans `local.properties` (jamais versionné) :

```
roadly.keystore=C:\\chemin\\roadly-release.jks
roadly.keystore.password=…
roadly.key.alias=roadly
roadly.key.password=…
```

Les builds debug et release locaux sont alors signés avec la clé dédiée. À réserver au poste du propriétaire : ne jamais distribuer un APK de développement.

### 4. Jeton GitHub (seulement si le dépôt est privé)

Le dépôt officiel est public : aucun jeton n'est nécessaire. Sans jeton, GitHub répond « introuvable » pour un dépôt privé. Créer un jeton *fine-grained* : GitHub › Settings › Developer settings › Personal access tokens › Fine-grained tokens › *Generate new token* :
- Repository access : *Only select repositories* → `Gomze31/roadly` ;
- Permissions : **Contents : Read-only** (rien d'autre) ;
- Expiration : au choix (à renouveler ensuite dans Roadly).

Dans Roadly : Réglages › Mises à jour › toucher la ligne de version › coller le jeton › Enregistrer. Il est chiffré par une clé du Keystore Android et n'est envoyé qu'à `api.github.com`.

## Publier une version

**Automatique :** chaque push sur `main` (hors changements limités à la documentation) publie une release. Le téléphone la propose ensuite dans Réglages › Mises à jour (ou à l'ouverture de Roadly, au plus toutes les 30 min).

Le nom de cette release vient d'une seule formule, dans `app/build.gradle.kts` (le workflow la lit par `:app:printRoadlyVersion`) :

| `roadly.versionBase` (`gradle.properties`) | Nom de version |
|---|---|
| vide (état actuel) | `0.1.<nombre de commits>` |
| entier N | `1.0.<nombre de commits − N>` : le commit dont le nombre de commits vaut N est la 1.0.0, les suivants 1.0.1, 1.0.2… |

Passer à la série 1.0 : dans le dernier commit avant la 1.0, renseigner `roadly.versionBase` avec le nombre de commits que ce commit aura sur `main` (`git rev-list --count HEAD` sur `main`, plus 1 pour un commit direct). Le `versionCode` ne change pas de formule. Ne pas pousser en plus un tag `v1.0.0` à la main : le workflow le crée.

Deux contrôles refusent une publication avant toute compilation :

- **Référence** : seul un commit de `main` est publié. Un lancement manuel depuis une autre branche, ou un tag posé sur un commit absent de `main`, échoue.
- **Version croissante** : le `versionCode` doit dépasser celui de la dernière release, et le nom doit la suivre. Un tag posé sur un commit déjà publié (même `versionCode`) échoue, comme un nom `0.1.N` après une `1.0.x`.

**Version nommée :**

```bash
git tag v0.2.0
```

```bash
git push origin v0.2.0
```

Ou : onglet Actions › *Release Android* › *Run workflow* (branche `main`) › version `0.2.0`. Le workflow publie la release `v0.2.0` avec `roadly-0.2.0.apk` et `update.json`, et génère les notes depuis les commits. Il enchaîne trois tâches :

| Tâche | Droits | Rôle |
|---|---|---|
| `build` | lecture du dépôt, aucun secret | contrôles ci-dessus, tests unitaires, APK **non signé** |
| `sign` | aucun droit sur le dépôt, seule à voir la clé, dans l'environnement protégé `release` (approbation du propriétaire) | `apksigner` du SDK signe l'APK, le vérifie, refuse une clé de debug ou un certificat autre que `ROADLY_SIGNER_SHA256`, calcule le SHA-256, écrit `update.json`, `SHA256SUMS.txt` et le bloc de vérification des notes |
| `publish` | écriture dans le dépôt, ne voit pas la clé | crée la release |

Gradle, ses plugins et les dépendances ne s'exécutent que dans `build` : ils n'ont accès ni à la clé de signature ni au droit de publier.

Les contributions (*pull requests*) ne déclenchent jamais ce workflow : elles sont vérifiées par `.github/workflows/ci.yml`, sans secret. Une fusion dans `main` le déclenche, mais rien n'est signé sans l'approbation du propriétaire dans l'environnement `release`.

`update.json` contient : `versionCode`, `versionName`, `apk`, `sha256`, `minSdk` et, depuis le 2026-11-23, `signerSha256` (empreinte du certificat, informative : l'application compare le certificat de l'APK lui-même, pas ce champ).

## Première installation

Installer une première fois l'APK de la release (téléchargé depuis GitHub sur le téléphone, ou `adb install -r roadly-0.2.0.apk`). Ensuite, tout passe par Réglages › Mises à jour. Android demandera une fois d'autoriser Roadly à installer des applications (bouton « Autoriser l'installation »).

## Dépannage

| Message | Cause | Solution |
|---|---|---|
| « dépôt privé ? Ajoutez un jeton » | Pas de jeton, ou aucune release publiée | Jeton (étape 4) ou publier une release |
| « Jeton GitHub refusé » | Jeton expiré ou révoqué | En créer un nouveau |
| « update.json absent » | Release créée à la main | Publier par le workflow |
| « pas signé avec la même clé » | APK et application installée signés différemment | Vérifier les secrets ; sinon désinstaller une fois |
| « signée avec une autre clé : réinstallation nécessaire » | Même cause, détectée avant l'installation | Exporter (Réglages › Données › Sauvegarde), désinstaller, installer, importer |
| « Adresse de téléchargement refusée » | La release renvoie vers un serveur hors de la liste GitHub | Vérifier le dépôt configuré ; si GitHub a changé d'hôte de téléchargement, l'ajouter à `GitHubHosts.ALLOWED` |
| « Fichier modifié depuis sa vérification » | Le fichier a changé entre le contrôle et l'installation | Réessayer |
| Aucune proposition alors qu'une release existe | `versionCode` de la release ≤ version installée (compilation locale plus récente) | Normal ; publier depuis un commit plus récent |
| Échec du workflow « Secret ROADLY_KEYSTORE_BASE64 absent » | Secrets non configurés, ou restés au niveau du dépôt au lieu de l'environnement `release` | Étape 2 |
| Échec du workflow « signé avec une clé de debug Android » | Les secrets contiennent la clé de debug | Créer la clé de publication : `SIGNATURE.md` |
| Échec du workflow « Variable ROADLY_SIGNER_SHA256 absente » ou « Certificat de signature inattendu » | Empreinte non déclarée, ou clé différente de celle déclarée | `SIGNATURE.md`, étape 3 ; ne jamais « corriger » la variable pour faire passer une clé inconnue |
| Téléchargement interrompu | Bouton « Annuler », écran quitté ou réseau coupé | L'état revient à « disponible », le fichier partiel est supprimé ; relancer |

## Limites

- Une compilation locale installée depuis Android Studio avec un `versionCode` supérieur à la dernière release empêche de « redescendre » : c'est le comportement d'Android.
- Pas de canal bêta : seules les releases non marquées *pre-release* sont proposées (`releases/latest`).
- Le workflow n'a pas pu être exécuté depuis l'environnement de développement : la première exécution est à surveiller dans l'onglet Actions. L'étape de signature a été essayée en local le 2026-11-23 (signature, vérification, refus de la clé de debug).
- Un téléchargement interrompu repart de zéro : pas de reprise partielle.
- Le téléchargement s'arrête si l'on quitte l'écran qui l'a lancé depuis les réglages.
