plugins {
    id("haven.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.core.cache.mirror"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-cache"))
    implementation(project(":core-arkiv"))
    implementation(project(":core-wallet"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp(libs.androidx.room.compiler)
    ksp("com.google.dagger:hilt-compiler:2.60.1")
}