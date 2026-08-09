plugins {
    id("haven.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.core.arkiv"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":foc-cache"))
    implementation(libs.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.datetime)
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
}