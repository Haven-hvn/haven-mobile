plugins {
    id("haven.android.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.feature.onboarding"
}

dependencies {
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation(project(":core-wallet"))
    implementation(project(":core-domain"))
    implementation(project(":core-design"))
    implementation(platform("com.reown:android-bom:1.6.14"))
    implementation("com.reown:android-core")
    implementation("com.reown:appkit")
    implementation(libs.androidx.compose.navigation)
    implementation(libs.hilt.navigation)
}