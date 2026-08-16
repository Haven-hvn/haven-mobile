import java.util.Properties

plugins {
    id("haven.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "haven.mobile.core.arkiv"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Arkiv gateway base URL. Empty is a valid state: the client reports "not configured"
        // instead of throwing, so a fresh clone builds and runs against the local mirror only.
        buildConfigField(
            "String",
            "ARKIV_ENDPOINT_URL",
            "\"${localProps.getProperty("arkiv.endpointUrl", "")}\"",
        )
    }
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
