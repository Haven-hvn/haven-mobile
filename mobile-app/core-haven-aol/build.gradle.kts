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

android {
    testOptions {
        unitTests.all {
            it.failOnNoDiscoveredTests = false
        }
    }
}

tasks.withType<Test> {
    failOnNoDiscoveredTests.set(false)
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-wallet"))
    implementation(project(":core-crypto"))
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation("io.github.haven-hvn:ic-agent:0.1.0")
    implementation("io.github.haven-hvn:ic-kotlin:0.1.0")
    implementation(libs.kotlinx.coroutines)
    implementation(libs.okhttp)
    // Failures here are reported to the reader in plain language; the detail goes to the log.
    implementation("com.jakewharton.timber:timber:5.0.1")
    testImplementation(libs.junit)
    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines)
    testImplementation(libs.kotlinx.datetime)
}