plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.security"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-haven-aol"))
    implementation(project(":core-cache"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-wallet"))
    implementation(libs.androidx.work.runtime)
}