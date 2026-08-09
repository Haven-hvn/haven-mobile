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
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":foc-cache"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.androidx.datastore.preferences)
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    // Reown is temporarily stubbed — see com.reown.appkit.AppKitStubs
}