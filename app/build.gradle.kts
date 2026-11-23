import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Version : versionCode = nombre de commits (identique en local et en CI, toujours croissant sur main) ;
// surchargeable par -Proadly.versionCode / -Proadly.versionName (workflow de release).
val gitCommitCount: Int = runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }.standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)
val roadlyVersionCode = (findProperty("roadly.versionCode") as String?)?.toIntOrNull() ?: gitCommitCount

// Nom de version (REL-1). Tant que roadly.versionBase est vide (gradle.properties) : « 0.1.<code> », comme avant.
// Dès qu'il vaut N : « 1.0.<code − N> » — le commit dont le code vaut N est la 1.0.0, les suivants 1.0.1, 1.0.2…
// Le code de version, lui, ne change pas de formule : il reste strictement croissant, la mise à jour intégrée ne compare que lui.
// Cette formule est la seule : le workflow de release la lit par la tâche printRoadlyVersion au lieu de la recopier.
val roadlyVersionBase: Int? = (findProperty("roadly.versionBase") as String?)?.trim()?.takeIf { it.isNotEmpty() }?.let {
    it.toIntOrNull() ?: throw GradleException("roadly.versionBase doit être un entier (reçu : « $it »).")
}
fun defaultVersionName(code: Int): String {
    val base = roadlyVersionBase ?: return "0.1.$code"
    if (code < base) throw GradleException("roadly.versionBase ($base) dépasse le code de version ($code) : le nom de version serait négatif.")
    return "1.0.${code - base}"
}
val roadlyVersionName = (findProperty("roadly.versionName") as String?) ?: defaultVersionName(roadlyVersionCode)

// Lue par .github/workflows/release.yml : une ligne « roadlyVersion code=<n> name=<x.y.z> ».
tasks.register("printRoadlyVersion") {
    val code = roadlyVersionCode
    val name = roadlyVersionName
    doLast { println("roadlyVersion code=$code name=$name") }
}

// Signature : variables d'environnement (CI) ou local.properties (poste). Les mises à jour depuis GitHub exigent
// que l'APK publié et l'application installée soient signés avec la même clé (docs/UPDATES.md).
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun signing(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(prop)?.takeIf { it.isNotBlank() }
val keystorePath = signing("ROADLY_KEYSTORE", "roadly.keystore")
val debugSignedRelease = (findProperty("roadly.debugSignedRelease") as String?) == "true"

android {
    namespace = "fr.roadly.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "fr.roadly"
        minSdk = 29
        targetSdk = 36
        versionCode = roadlyVersionCode
        versionName = roadlyVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (keystorePath != null) create("roadly") {
            storeFile = file(keystorePath)
            storePassword = signing("ROADLY_KEYSTORE_PASSWORD", "roadly.keystore.password")
            keyAlias = signing("ROADLY_KEY_ALIAS", "roadly.key.alias")
            keyPassword = signing("ROADLY_KEY_PASSWORD", "roadly.key.password")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Jamais de clé de débogage en silence : sans la clé de publication, l'APK « release » sort NON SIGNÉ (il ne s'installe pas).
            // Pour un essai local seulement : -Proadly.debugSignedRelease=true le signe avec la clé de débogage et le marque dans son nom
            // de version ; un tel APK ne peut ni être publié ni mettre à jour une version publiée.
            val releaseKey = signingConfigs.findByName("roadly")
            signingConfig = releaseKey ?: if (debugSignedRelease) signingConfigs.getByName("debug") else null
            if (releaseKey == null && debugSignedRelease) versionNameSuffix = "-debugsigned"
        }
        // Même clé en debug si elle est configurée : une version installée depuis le poste peut être mise à jour depuis GitHub.
        debug { signingConfigs.findByName("roadly")?.let { signingConfig = it } }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:location"))
    implementation(project(":core:map"))
    implementation(project(":core:geocoding"))
    implementation(project(":core:routing"))
    implementation(project(":core:navigation"))
    implementation(project(":feature:voice"))
    implementation(libs.androidx.lifecycle.process)
    implementation(project(":feature:home"))
    implementation(project(":feature:search"))
    implementation(project(":feature:route"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:navigation"))
    implementation(project(":feature:trips"))
    implementation(project(":feature:car"))
    implementation(project(":feature:onboarding"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.serialization.json)
    implementation(libs.maplibre)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
