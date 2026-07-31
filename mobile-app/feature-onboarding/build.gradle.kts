plugins {
    id("haven.android.compose")
}

android {
    namespace = "haven.mobile.feature.onboarding"
}

dependencies {
    implementation(project(":core-wallet"))
    implementation(project(":core-domain"))
    implementation(libs.hilt.navigation)
}