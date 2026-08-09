plugins {
    id("haven.android.library")
    id("com.google.devtools.ksp")
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
    ksp(libs.androidx.room.compiler)
}