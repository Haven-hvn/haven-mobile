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
    namespace = "haven.mobile.core.wallet"
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        buildConfigField("String", "WALLET_PROJECT_ID", "\"${localProps.getProperty("wallet.projectId", "")}\"")
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation("io.github.haven-hvn:foc-cache:0.1.0")
    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx.datastore.preferences)
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation(platform("com.reown:android-bom:1.6.14"))
    implementation("com.reown:android-core")
    implementation("com.reown:appkit")
    implementation("com.jakewharton.timber:timber:5.0.1")

    testImplementation("org.json:json:20231013")
}