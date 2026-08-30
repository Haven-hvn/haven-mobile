plugins {
    id("haven.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.core.cache"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-wallet"))
    implementation("io.github.haven-hvn:foc-cache:0.1.0")
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
}