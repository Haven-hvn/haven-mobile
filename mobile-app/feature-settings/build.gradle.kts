plugins {
    id("haven.android.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.feature.settings"
}

dependencies {
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation(project(":core-domain"))
    implementation(project(":core-design"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-cache"))
    implementation(project(":core-security"))
    implementation(project(":core-wallet"))
    implementation(libs.androidx.compose.navigation)
    implementation(libs.hilt.navigation)
}