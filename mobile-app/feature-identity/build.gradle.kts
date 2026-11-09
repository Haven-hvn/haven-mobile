plugins {
    id("haven.android.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.feature.identity"
}

dependencies {
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation(project(":core-domain"))
    implementation(project(":core-design"))
    implementation(project(":core-wallet"))
    implementation(project(":core-security"))
    implementation(libs.androidx.compose.navigation)
    implementation(libs.hilt.navigation)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines)
}
