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
    // The reader's communities come from what the wallet holds, not from what it published.
    implementation(project(":core-collections"))
    implementation(project(":core-wallet"))
    implementation("io.github.haven-hvn:foc-cache:0.1.0")
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp(libs.androidx.room.compiler)
    ksp("com.google.dagger:hilt-compiler:2.60.1")
}