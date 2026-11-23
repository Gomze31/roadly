plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
// Contact annoncé dans le User-Agent (exigé par les services publics : FOSSGIS/Valhalla, Overpass…).
// Surchargeable par -Proadly.contact=… ou `roadly.contact=…` dans gradle.properties (URL ou adresse du responsable).
// Défaut : le dépôt du projet. ASCII imprimable uniquement (valeur d'en-tête HTTP).
val roadlyContact = (findProperty("roadly.contact") as String?)?.trim()?.takeIf { it.isNotEmpty() } ?: "https://github.com/Gomze31/roadly"
require(roadlyContact.all { it.code in 0x20..0x7E && it != '"' && it != '\\' && it != '$' }) { "roadly.contact : ASCII imprimable attendu, sans guillemet, barre oblique inverse ni dollar" }
android {
    namespace = "fr.roadly.core.data"
    compileSdk = 36
    defaultConfig {
        minSdk = 29; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CONTACT", "\"$roadlyContact\"")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { buildConfig = true }
    // Tests JVM de la base (migrations, DAO) sous Robolectric.
    testOptions { unitTests { isIncludeAndroidResources = true } }
}
// Schéma Room exporté (un fichier JSON par version de la base, à conserver dans git) : référence pour les migrations.
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:radar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    api(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
}
