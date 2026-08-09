plugins {
    id("haven.android.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.feature.watch"
}

dependencies {
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation(project(":core-domain"))
    implementation(project(":core-haven-aol"))
    implementation(project(":core-crypto"))
    implementation(project(":core-cache"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-wallet"))
    implementation(libs.androidx.compose.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.androidx.media3)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil)
    // androidx.pdf:pdf-renderer:1.0.0 not yet in google maven — DOCUMENT viewer uses fallback for v1 (see WatchScreen DocumentViewer)
}
