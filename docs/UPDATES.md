# Mises à jour depuis GitHub

Roadly vérifie les **releases GitHub** du dépôt `Gomze31/roadly`, télécharge l'APK de la dernière version, vérifie son empreinte SHA-256 puis lance l'installation Android. Réglages › **Mises à jour** ; vérification automatique au lancement (au plus toutes les 12 h, désactivable).

## Fonctionnement

1. `GET https://api.github.com/repos/Gomze31/roadly/releases/latest` (avec le jeton si le dépôt est privé).
2. Lecture de `update.json` joint à la release : `versionCode`, `versionName`, nom de l'APK, SHA-256, `minSdk`.
3. Proposée seulement si `versionCode` > version installée (Réglages › Mises à jour affiche « Version x (code) »).
4. Téléchargement (lien signé GitHub ; le jeton n'est pas transmis à l'hôte de téléchargement), contrôle SHA-256.
5. Installation par `PackageInstaller` : Android demande la confirmation. À partir d'Android 12, une fois qu'une version a été installée par Roadly lui-même, les suivantes peuvent s'installer sans confirmation.

Le `versionCode` est le **nombre de commits** (`git rev-list --count HEAD`), en local comme sur GitHub : il augmente à chaque commit sur `main`, et une compilation locale et une release du même commit ont le même code.

**Signature :** Android n'accepte une mise à jour que si l'APK est signé avec **la même clé** que l'application installée. C'est la vraie garantie de sécurité (le SHA-256 protège seulement contre un téléchargement corrompu). D'où la configuration ci-dessous.

## Mise en place (une fois)

### 1. Choisir la clé de signature

**Option A — garder l'application installée (recommandée pour démarrer) :** utiliser la clé de debug du PC qui compile déjà Roadly : `%USERPROFILE%\.android\debug.keystore`, alias `androiddebugkey`, mots de passe `android`. Aucune désinstallation, aucune perte de données (favoris, trajets, fiches trottinette). Gardez une copie de ce fichier : s'il est perdu, plus aucune mise à jour ne pourra s'installer par-dessus.

**Option B — clé dédiée :**

```bash
keytool -genkeypair -v -keystore roadly-release.jks -alias roadly -keyalg RSA -keysize 4096 -validity 10000
```

L'application actuelle (signée avec la clé de debug) devra être **désinstallée une fois** avant d'installer la première version signée avec cette clé : les données locales seront perdues.

### 2. Secrets GitHub

Dépôt › Settings › Secrets and variables › Actions › *New repository secret* :

| Secret | Valeur |
|---|---|
| `ROADLY_KEYSTORE_BASE64` | le fichier de clé encodé en base64 (commande ci-dessous) |
| `ROADLY_KEYSTORE_PASSWORD` | mot de passe du keystore (`android` pour l'option A) |
| `ROADLY_KEY_ALIAS` | alias (`androiddebugkey` pour l'option A) |
| `ROADLY_KEY_PASSWORD` | mot de passe de la clé (`android` pour l'option A) |

Encodage sous Windows (PowerShell), résultat copié dans le presse-papiers :

```bash
[Convert]::ToBase64String([IO.File]::ReadAllBytes("$env:USERPROFILE\.android\debug.keystore")) | Set-Clipboard
```

### 3. Même clé pour les compilations locales (option B seulement)

Dans `local.properties` (jamais versionné) :

```
roadly.keystore=C:\\chemin\\roadly-release.jks
roadly.keystore.password=…
roadly.key.alias=roadly
roadly.key.password=…
```

Les builds debug et release locaux sont alors signés avec la clé dédiée. Avec l'option A, rien à faire : le debug local utilise déjà `debug.keystore`.

### 4. Jeton GitHub (le dépôt est privé)

Sans jeton, GitHub répond « introuvable » pour un dépôt privé. Créer un jeton *fine-grained* : GitHub › Settings › Developer settings › Personal access tokens › Fine-grained tokens › *Generate new token* :
- Repository access : *Only select repositories* → `Gomze31/roadly` ;
- Permissions : **Contents : Read-only** (rien d'autre) ;
- Expiration : au choix (à renouveler ensuite dans Roadly).

Dans Roadly : Réglages › Mises à jour › toucher la ligne de version › coller le jeton › Enregistrer. Il est chiffré par une clé du Keystore Android et n'est envoyé qu'à `api.github.com`.

## Publier une version

```bash
git tag v0.2.0
```

```bash
git push origin v0.2.0
```

Ou : onglet Actions › *Release Android* › *Run workflow* › version `0.2.0`. Le workflow lance les tests unitaires, compile l'APK signé, calcule le SHA-256, publie la release `v0.2.0` avec `roadly-0.2.0.apk` et `update.json`, et génère les notes depuis les commits.

## Première installation

Installer une première fois l'APK de la release (téléchargé depuis GitHub sur le téléphone, ou `adb install -r roadly-0.2.0.apk`), ou continuer avec la version compilée sur le PC si l'option A est utilisée. Ensuite, tout passe par Réglages › Mises à jour. Android demandera une fois d'autoriser Roadly à installer des applications (bouton « Autoriser l'installation »).

## Dépannage

| Message | Cause | Solution |
|---|---|---|
| « dépôt privé ? Ajoutez un jeton » | Pas de jeton, ou aucune release publiée | Jeton (étape 4) ou publier une release |
| « Jeton GitHub refusé » | Jeton expiré ou révoqué | En créer un nouveau |
| « update.json absent » | Release créée à la main | Publier par le workflow |
| « pas signé avec la même clé » | APK et application installée signés différemment | Vérifier les secrets ; sinon désinstaller une fois |
| Aucune proposition alors qu'une release existe | `versionCode` de la release ≤ version installée (compilation locale plus récente) | Normal ; publier depuis un commit plus récent |
| Échec du workflow « Secret ROADLY_KEYSTORE_BASE64 absent » | Secrets non configurés | Étape 2 |

## Limites

- Une compilation locale installée depuis Android Studio avec un `versionCode` supérieur à la dernière release empêche de « redescendre » : c'est le comportement d'Android.
- Pas de canal bêta : seules les releases non marquées *pre-release* sont proposées (`releases/latest`).
- Le workflow n'a pas pu être exécuté depuis l'environnement de développement : la première exécution est à surveiller dans l'onglet Actions.
