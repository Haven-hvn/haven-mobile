plugins {
    id("haven.android.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.feature.onboarding"
}

dependencies {
    implementation(project(":core-wallet"))
    implementation(project(":core-domain"))
    implementation(libs.androidx.compose.navigation)
    implementation(libs.hilt.navigation)
}