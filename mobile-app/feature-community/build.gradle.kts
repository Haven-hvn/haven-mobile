plugins {
    id("haven.android.compose")
}

android {
    namespace = "haven.mobile.feature.community"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-arkiv"))
    implementation(project(":core-attestation"))
    implementation(project(":core-haven-aol"))
    implementation(libs.androidx.compose.navigation)
}