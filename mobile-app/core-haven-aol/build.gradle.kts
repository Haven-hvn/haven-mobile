import java.util.Properties

plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "haven.mobile.core.haven.aol"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "HAVEN_AOL_CANISTER_ID", "\"${localProps.getProperty("haven.aol.canisterId", "")}\"")
        buildConfigField("String", "HAVEN_AOL_IC_HOST", "\"${localProps.getProperty("haven.aol.icHost", "https://ic0.app")}\"")
    }

    buildFeatures {
        compose = false
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-wallet"))
    implementation(project(":core-crypto"))
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("dev.ic.kotlin:ic-agent:0.1.0-SNAPSHOT")
    implementation("dev.ic.kotlin:ic-candid:0.1.0-SNAPSHOT")
    implementation(libs.kotlinx.coroutines)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines)
}