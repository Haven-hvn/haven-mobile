plugins {
    id("haven.android.compose")
}

android {
    namespace = "haven.mobile.feature.library"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-arkiv"))
}