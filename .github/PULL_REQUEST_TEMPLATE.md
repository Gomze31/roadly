## Objet

<!-- Ce que change cette pull request, et pourquoi. Lien vers l'issue : « Corrige #… ». -->

## Vérifications

<!-- Dites ce qui a réellement été exécuté. « Non testé » est une réponse acceptable ; une affirmation fausse ne l'est pas. -->

- [ ] `./gradlew testDebugUnitTest :core:model:test :core:common:test :core:scooter:test :core:radar:test` passe
- [ ] `./gradlew :app:assembleRelease` passe
- [ ] Un test couvre le changement (pour un bug : il échouait avant le correctif)
- [ ] Essayé sur un téléphone : oui / non. Si oui, modèle et version d'Android :

## Points de contrôle

- [ ] Aucune valeur inventée : chaque donnée affichée a une source réelle
- [ ] Aucun secret, aucune trace GPS personnelle, aucune capture d'un lieu privé
- [ ] Schéma Room modifié : migration, schéma exporté et test de migration joints
- [ ] Bibliothèque ajoutée ou mise à jour : `THIRD_PARTY_LICENSES.md` régénéré
- [ ] Nouveau service en ligne ou nouvelle donnée envoyée : `docs/SERVICES.md` et `PRIVACY.md` mis à jour
- [ ] Commits signés (`git commit -s`), contribution sous licence Apache 2.0

## Ce qui n'est pas couvert

<!-- Ce que vous n'avez pas pu vérifier, les limites connues. -->
