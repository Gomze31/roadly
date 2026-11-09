plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

// Rendus d'écran à la demande (tests *Screenshots, Robolectric) : ./gradlew testDebugUnitTest --tests '*Screenshots*' -PscreenshotsDir=/chemin
// -ProbolectricDir=/chemin : image Android de Robolectric déjà téléchargée (réseau limité, mode hors ligne).
subprojects {
    tasks.withType<Test>().configureEach {
        val shots = project.findProperty("screenshotsDir") as String?
        if (shots != null) systemProperty("roadly.screenshots.dir", shots)
        // Sans -PscreenshotsDir, les rendus sont exclus : Robolectric téléchargerait son image Android pour rien.
        else filter { excludeTestsMatching("*Screenshots"); isFailOnNoMatchingTests = false }
        // -PzfeFile=/chemin/aires.geojson : contrôle des règles ZFE sur le vrai fichier national (test inactif sinon).
        (project.findProperty("zfeFile") as String?)?.let { systemProperty("roadly.zfe.file", it) }
        (project.findProperty("trafficFile") as String?)?.let { systemProperty("roadly.traffic.file", it) }
        (project.findProperty("roadlyLive") as String?)?.let { systemProperty("roadly.live", it) }
        (project.findProperty("robolectricDir") as String?)?.let { systemProperty("robolectric.offline", "true"); systemProperty("robolectric.dependency.dir", it) }
    }
}
