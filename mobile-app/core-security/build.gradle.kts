plugins {
    id("haven.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
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
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
}