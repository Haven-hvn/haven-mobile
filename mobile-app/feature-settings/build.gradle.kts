plugins {
    id("haven.android.compose")
}

android {
    namespace = "haven.mobile.feature.settings"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-cache"))
    implementation(project(":core-security"))
    implementation(project(":core-wallet"))
    implementation(libs.androidx.compose.navigation)
}