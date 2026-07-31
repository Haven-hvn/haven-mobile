plugins {
    id("haven.android.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.feature.watch"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-haven-aol"))
    implementation(project(":core-crypto"))
    implementation(project(":core-cache"))
    implementation(project(":core-cache-mirror"))
    implementation(libs.androidx.compose.navigation)
    implementation(libs.androidx.media3)
    implementation(libs.androidx.media3.ui)
    implementation(libs.coil)
    implementation(libs.androidx.pdf.renderer)
}
