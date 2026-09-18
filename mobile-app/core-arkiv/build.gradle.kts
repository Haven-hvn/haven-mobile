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
        // Arkiv chain JSON-RPC URL, blank unless overridden in local.properties. A blank
        // BuildConfig falls back to the Tiramisu default in ArkivDiModule, so every build
        // can reach the chain; the client's "not configured" path only triggers on an
        // explicitly blank ArkivConfig (e.g. tests).
        buildConfigField(
            "String",
            "ARKIV_ENDPOINT_URL",
            "\"${localProps.getProperty("arkiv.endpointUrl", "")}\"",
        )
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation("io.github.haven-hvn:foc-cache:0.1.0")
    implementation(libs.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.datetime)
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    // Real org.json on the JVM test classpath: android.jar stubs throw RuntimeException("Stub!").
    testImplementation("org.json:json:20231013")
}
