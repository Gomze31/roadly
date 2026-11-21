plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}
android {
    namespace = "fr.roadly.feature.car"
    compileSdk = 36
    defaultConfig { minSdk = 29 }
    testOptions { unitTests { isIncludeAndroidResources = true } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
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
    // Android Auto : bibliothèque Car App (gabarits de navigation) et hôte projeté depuis le téléphone.
    implementation(libs.car.app)
    implementation(libs.car.app.projected)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coroutines.android)
    // Icônes Material (manœuvres, actions) dessinées en bitmaps pour les gabarits de la voiture.
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
