import java.util.Properties

plugins {
    id("haven.android.application")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "haven.mobile.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "haven.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "WALLET_PROJECT_ID", "\"${localProps.getProperty("wallet.projectId", "")}\"")
        buildConfigField("String", "HAVEN_AOL_CANISTER_ID", "\"${localProps.getProperty("haven.aol.canisterId", "")}\"")
        buildConfigField("String", "HAVEN_AOL_IC_HOST", "\"${localProps.getProperty("haven.aol.icHost", "https://ic0.app")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
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
    implementation(project(":feature-onboarding"))
    implementation(project(":feature-library"))
    implementation(project(":feature-watch"))
    implementation(project(":feature-community"))
    implementation(project(":feature-settings"))
    implementation(project(":core-security"))
    implementation(platform("com.reown:android-bom:1.6.14"))
    implementation("com.reown:android-core")
    implementation("com.reown:appkit")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material:1.7.3")
    implementation("androidx.navigation:navigation-compose:2.8.6")
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.hilt.navigation)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.work.runtime)
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}