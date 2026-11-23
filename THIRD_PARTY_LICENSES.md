# Licences des composants tiers

Ce fichier est produit par `tools/licenses/third_party_licenses.py`. Ne pas le modifier à la main : relancer le script.

Il liste les bibliothèques **livrées dans l'APK** (classpath d'exécution de la variante release de `:app`), avec la licence
déclarée par leur éditeur dans leur POM. Les outils de compilation et de test (compilateurs Room et Hilt, JUnit sous EPL-1.0,
Robolectric sous MIT, Roborazzi, Turbine, MockWebServer) ne sont pas distribués avec l'application et n'y figurent pas.

Le code de Roadly lui-même est distribué sous licence Apache 2.0 (fichier `LICENSE`).

## Composant non libre

Les bibliothèques clientes des services Google Play (`com.google.android.gms`, utilisées par le seul module `core/location`
pour la position) sont publiées sous l'« Android Software Development Kit License »
(<https://developer.android.com/studio/terms>). Ce n'est **pas** une licence libre : leur code source n'est pas publié et
leur redistribution est encadrée par Google. Conséquences :

- un APK de Roadly n'est pas composé à 100 % de logiciel libre ;
- la licence Apache 2.0 de Roadly ne s'applique pas à ces bibliothèques ;
- l'application ne peut pas entrer telle quelle dans un catalogue réservé au logiciel libre (F-Droid).

Leur remplacement par l'API de localisation d'Android est décrit dans `docs/ROADLY-1.0/DECISIONS.md` (section 4).

## Ce que ce fichier ne couvre pas

- Le code tiers embarqué **à l'intérieur** d'un artefact. La bibliothèque native de MapLibre contient ses propres
  dépendances ; leurs mentions sont publiées par le projet MapLibre Native et n'ont pas été vérifiées ici.
- Les polices : Inter et Nunito, SIL Open Font License 1.1 (`core/designsystem/LICENSE-Inter-OFL.txt`,
  `core/designsystem/licenses/Nunito-OFL.txt`).
- Les données et services en ligne (OpenStreetMap sous ODbL, OpenFreeMap, Base Adresse Nationale et IGN sous Licence
  Ouverte 2.0, etc.) : voir `NOTICE` et `docs/SERVICES.md`.

## Obligations par licence

| Licence | Obligation à la redistribution de l'APK |
|---|---|
| Apache-2.0 | Joindre le texte de la licence (`LICENSE`) et le contenu des fichiers `NOTICE` des composants |
| BSD-2-Clause, BSD-3-Clause | Reproduire la mention de droit d'auteur, la liste des conditions et l'exclusion de garantie |
| MIT | Reproduire la mention de droit d'auteur et le texte de permission |
| Android Software Development Kit License | Conditions de Google ; pas de droit de modification ni de redistribution isolée |

## Répartition

| Licence | Artefacts |
|---|---|
| Apache-2.0 | 178 |
| Android Software Development Kit License | 4 |
| BSD-2-Clause | 2 |
| BSD-3-Clause | 1 |
| MIT | 1 |
| **Total** | **186** |

## Artefacts

| Artefact | Version | Licence | Déclaration du POM |
|---|---|---|---|
| `androidx.activity:activity` | 1.10.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.activity:activity-compose` | 1.10.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.activity:activity-ktx` | 1.10.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.annotation:annotation` | 1.9.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.annotation:annotation-experimental` | 1.5.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.annotation:annotation-jvm` | 1.9.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.appcompat:appcompat` | 1.7.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.appcompat:appcompat-resources` | 1.7.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.arch.core:core-common` | 2.2.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.arch.core:core-runtime` | 2.2.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.autofill:autofill` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.car.app:app` | 1.7.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.car.app:app-projected` | 1.7.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.collection:collection` | 1.5.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.collection:collection-jvm` | 1.5.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.collection:collection-ktx` | 1.5.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose:compose-bom` | 2026.05.00 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.animation:animation` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.animation:animation-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.animation:animation-core` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.animation:animation-core-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.foundation:foundation` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.foundation:foundation-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.foundation:foundation-layout` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.foundation:foundation-layout-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.material:material-icons-core` | 1.7.8 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.material:material-icons-core-android` | 1.7.8 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.material:material-icons-extended` | 1.7.8 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.material:material-icons-extended-android` | 1.7.8 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.material:material-ripple` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.material:material-ripple-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.material3:material3` | 1.4.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.material3:material3-android` | 1.4.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime-annotation` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime-annotation-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime-retain` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime-retain-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime-saveable` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.runtime:runtime-saveable-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-geometry` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-geometry-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-graphics` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-graphics-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-text` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-text-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-tooling-preview` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-tooling-preview-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-unit` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-unit-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-util` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.compose.ui:ui-util-android` | 1.11.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.concurrent:concurrent-futures` | 1.1.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.core:core` | 1.17.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.core:core-ktx` | 1.17.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.core:core-viewtree` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.cursoradapter:cursoradapter` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.customview:customview` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.customview:customview-poolingcontainer` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-android` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-core` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-core-android` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-core-okio` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-core-okio-jvm` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-preferences` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-preferences-android` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-preferences-core` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-preferences-core-android` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.datastore:datastore-preferences-external-protobuf` | 1.1.7 | BSD-3-Clause | BSD-3-Clause |
| `androidx.datastore:datastore-preferences-proto` | 1.1.7 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.documentfile:documentfile` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.drawerlayout:drawerlayout` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.dynamicanimation:dynamicanimation` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.emoji2:emoji2` | 1.4.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.emoji2:emoji2-views-helper` | 1.4.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.fragment:fragment` | 1.8.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.graphics:graphics-path` | 1.0.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.hilt:hilt-navigation` | 1.2.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.hilt:hilt-navigation-compose` | 1.2.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.interpolator:interpolator` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.legacy:legacy-support-core-utils` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-common` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-common-java8` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-common-jvm` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-livedata` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-livedata-core` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-livedata-core-ktx` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-process` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-runtime` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-runtime-android` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-runtime-compose-android` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-runtime-ktx-android` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-android` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-compose-android` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.lifecycle:lifecycle-viewmodel-savedstate-android` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.loader:loader` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.localbroadcastmanager:localbroadcastmanager` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.media:media` | 1.6.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.navigation:navigation-common` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.navigation:navigation-common-android` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.navigation:navigation-compose` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.navigation:navigation-compose-android` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.navigation:navigation-runtime` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.navigation:navigation-runtime-android` | 2.9.4 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.print:print` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.profileinstaller:profileinstaller` | 1.4.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.resourceinspection:resourceinspection-annotation` | 1.0.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.room:room-common` | 2.8.5 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.room:room-common-jvm` | 2.8.5 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.room:room-ktx` | 2.8.5 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.room:room-runtime` | 2.8.5 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.room:room-runtime-android` | 2.8.5 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.savedstate:savedstate` | 1.3.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.savedstate:savedstate-android` | 1.3.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.savedstate:savedstate-compose` | 1.3.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.savedstate:savedstate-compose-android` | 1.3.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.savedstate:savedstate-ktx` | 1.3.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.sqlite:sqlite` | 2.6.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.sqlite:sqlite-android` | 2.6.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.sqlite:sqlite-framework` | 2.6.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.sqlite:sqlite-framework-android` | 2.6.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.startup:startup-runtime` | 1.1.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.tracing:tracing` | 1.2.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.transition:transition` | 1.6.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.vectordrawable:vectordrawable` | 1.1.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.vectordrawable:vectordrawable-animated` | 1.1.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.versionedparcelable:versionedparcelable` | 1.1.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.viewpager:viewpager` | 1.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.window:window` | 1.5.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.window:window-core` | 1.5.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `androidx.window:window-core-android` | 1.5.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `com.google.android.gms:play-services-base` | 18.5.0 | Android Software Development Kit License | Android Software Development Kit License |
| `com.google.android.gms:play-services-basement` | 18.4.0 | Android Software Development Kit License | Android Software Development Kit License |
| `com.google.android.gms:play-services-location` | 21.3.0 | Android Software Development Kit License | Android Software Development Kit License |
| `com.google.android.gms:play-services-tasks` | 18.2.0 | Android Software Development Kit License | Android Software Development Kit License |
| `com.google.code.findbugs:jsr305` | 3.0.2 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `com.google.code.gson:gson` | 2.10.1 | Apache-2.0 | Apache-2.0 |
| `com.google.dagger:dagger` | 2.58 | Apache-2.0 | Apache 2.0 |
| `com.google.dagger:dagger-lint-aar` | 2.58 | Apache-2.0 | Apache 2.0 |
| `com.google.dagger:hilt-android` | 2.58 | Apache-2.0 | Apache 2.0 |
| `com.google.dagger:hilt-core` | 2.58 | Apache-2.0 | Apache 2.0 |
| `com.google.errorprone:error_prone_annotations` | 2.11.0 | Apache-2.0 | Apache 2.0 |
| `com.google.guava:failureaccess` | 1.0.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `com.google.guava:guava` | 31.1-android | Apache-2.0 | Apache License, Version 2.0 |
| `com.google.guava:listenablefuture` | 9999.0-empty-to-avoid-conflict-with-guava | Apache-2.0 | The Apache Software License, Version 2.0 |
| `com.google.j2objc:j2objc-annotations` | 1.3 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `com.jakewharton.timber:timber` | 5.0.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `com.squareup.okhttp3:logging-interceptor` | 4.12.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `com.squareup.okhttp3:okhttp` | 4.12.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `com.squareup.okio:okio` | 3.6.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `com.squareup.okio:okio-jvm` | 3.6.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `jakarta.inject:jakarta.inject-api` | 2.0.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `javax.inject:javax.inject` | 1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `org.checkerframework:checker-qual` | 3.12.0 | MIT | The MIT License |
| `org.jetbrains:annotations` | 23.0.0 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `org.jetbrains.kotlin:kotlin-android-extensions-runtime` | 1.9.22 | Apache-2.0 | The Apache License, Version 2.0 |
| `org.jetbrains.kotlin:kotlin-parcelize-runtime` | 1.9.22 | Apache-2.0 | The Apache License, Version 2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib` | 2.2.20 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-common` | 2.2.20 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk7` | 1.9.10 | Apache-2.0 | The Apache License, Version 2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib-jdk8` | 1.9.10 | Apache-2.0 | The Apache License, Version 2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.2 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-bom` | 1.10.2 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.10.2 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm` | 1.10.2 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | 1.10.2 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-bom` | 1.8.1 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core` | 1.8.1 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-core-jvm` | 1.8.1 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.8.1 | Apache-2.0 | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json-jvm` | 1.8.1 | Apache-2.0 | Apache-2.0 |
| `org.jspecify:jspecify` | 1.0.0 | Apache-2.0 | The Apache License, Version 2.0 |
| `org.maplibre.gl:android-sdk` | 11.13.5 | BSD-2-Clause | BSD |
| `org.maplibre.gl:android-sdk-geojson` | 6.0.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `org.maplibre.gl:android-sdk-turf` | 6.0.1 | Apache-2.0 | The Apache Software License, Version 2.0 |
| `org.maplibre.gl:maplibre-android-gestures` | 0.0.4 | BSD-2-Clause | BSD |
