# Contribuer à Roadly

Merci de votre intérêt. Roadly est un GPS Android libre ; les corrections, les idées et les nouvelles sources de données
ouvertes sont bienvenues.

## Avant de commencer

- **Un bug ou une idée** : ouvrez une *issue* avec le modèle proposé. Pour un changement important, discutez-en dans une
  *issue* avant d'écrire du code.
- **Une faille de sécurité** : jamais en *issue* publique. Voir [`SECURITY.md`](SECURITY.md).
- **Aucune donnée personnelle** dans une *issue* ou une *pull request* : pas de trace GPS de vos trajets, pas de capture
  montrant votre domicile, pas de jeton. Une trace fabriquée doit dire qu'elle l'est.

## La règle du projet

**Aucune fonction fictive.** Une fonction n'existe que si elle repose sur une source réelle et vérifiable. Sans donnée,
l'écran le dit. Une contribution qui invente une valeur, une limite de vitesse ou un état sera refusée.

## Préparer son poste

Prérequis : JDK 17, SDK Android 36, un fichier `local.properties` contenant `sdk.dir=…`. Aucune clé d'API n'est nécessaire.

```bash
git clone https://github.com/Gomze31/roadly.git
cd roadly
./gradlew :app:assembleDebug
```

Guide complet (modules, fournisseurs, configuration) : [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).
Vue d'ensemble : [`ARCHITECTURE.md`](ARCHITECTURE.md).

## Proposer un changement

1. Créez une branche depuis `main`.
2. Écrivez le test avant ou avec le code. Un correctif de bug vient avec un test qui échouait avant lui.
3. Lancez les vérifications que la CI lancera :

   ```bash
   ./gradlew testDebugUnitTest :core:model:test :core:common:test :core:scooter:test :core:radar:test
   ```

   ```bash
   ./gradlew :app:assembleRelease
   ```

4. Si vous ajoutez ou mettez à jour une bibliothèque : `python tools/licenses/third_party_licenses.py`, puis joignez le
   fichier `THIRD_PARTY_LICENSES.md` mis à jour. Seules les licences Apache-2.0, BSD et MIT passent sans discussion.
5. Ouvrez une *pull request* avec le modèle proposé. Décrivez ce qui a été testé, et ce qui ne l'a pas été.

## Ce qui est attendu d'une contribution

- Le code et ses commentaires sont en français, comme le reste du projet ; les noms de tests sont en anglais.
- Un changement fait une chose. Pas de remaniement sans rapport dans la même *pull request*.
- Un schéma Room modifié vient avec sa migration, son fichier de schéma exporté et un test de migration.
- Un nouveau service en ligne vient avec : sa licence, ses limites d'usage, ce qu'il reçoit de l'utilisateur, une limite
  de débit côté application, et une ligne dans [`docs/SERVICES.md`](docs/SERVICES.md) et [`PRIVACY.md`](PRIVACY.md).
- Ce qui n'a été vérifié que sur la JVM est dit tel quel. Rien n'est annoncé « testé sur téléphone » sans l'avoir été.

## Revue et fusion

- La CI (tests JVM, compilation release, contrôle des licences) doit être verte. Elle s'exécute sans aucun secret.
- Toute *pull request* est relue par un mainteneur avant fusion ; les fichiers de `.github/` et de signature exigent la
  revue du propriétaire ([`.github/CODEOWNERS`](.github/CODEOWNERS)).
- **Une fusion n'est pas une publication.** Les versions officielles sont signées par le propriétaire du projet, après son
  approbation explicite ([`docs/SIGNATURE.md`](docs/SIGNATURE.md)). Aucun contributeur n'a accès à la clé.

## Licence de vos contributions

Roadly est distribué sous licence [Apache 2.0](LICENSE). En proposant une contribution, vous acceptez qu'elle soit
distribuée sous cette licence (article 5 de la licence), et vous affirmez en avoir le droit : c'est votre travail, ou un
travail dont la licence le permet et que vous signalez.

Signez vos commits avec `git commit -s` : la ligne `Signed-off-by` vaut acceptation du
[Developer Certificate of Origin](https://developercertificate.org/).

## Compilations personnelles

Vous pouvez compiler et distribuer votre propre version, dans le respect de la licence. Elle sera signée par votre clé :
Android ne l'installera pas par-dessus une version officielle, et elle ne doit pas se présenter comme une version
officielle de Roadly. Voir [`docs/PUBLICATION.md`](docs/PUBLICATION.md), section « Nom et logo ».
