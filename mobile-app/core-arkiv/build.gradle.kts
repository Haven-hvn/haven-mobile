plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.arkiv"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(libs.kotlinx.serialization)
    implementation(libs.okhttp)
}