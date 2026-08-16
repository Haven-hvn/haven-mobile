plugins {
    id("haven.android.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.feature.collections"
}

dependencies {
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation(project(":core-design"))
    implementation(project(":core-collections"))
    // SettingsRepository: the reader's per-network opt-outs apply here too.
    implementation(project(":core-cache-mirror"))
    // WalletSession appears in the ViewModel's constructor, and core-collections keeps it internal.
    implementation(project(":core-wallet"))
    implementation(libs.androidx.compose.navigation)
    implementation(libs.hilt.navigation)
}
