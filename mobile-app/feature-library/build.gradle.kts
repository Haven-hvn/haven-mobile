plugins {
    id("haven.android.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.feature.library"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-arkiv"))
    implementation(project(":core-wallet"))
    implementation(libs.androidx.compose.navigation)
}