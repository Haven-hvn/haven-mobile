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
    compileSdk = 37

    defaultConfig {
        applicationId = "haven.mobile"
        minSdk = 26
        targetSdk = 34
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
    // The app hosts the Hilt component, so it aggregates every @Module on the classpath and its
    // generated code references the bound types by name. Those types therefore have to be on this
    // module's compile classpath — including the ones the app never touches itself (the cache
    // facade, the mirror, the collections repository). Omitting them fails at codegen with an
    // "cannot access class" that points at generated sources rather than at the cause.
    implementation(project(":core-domain"))
    implementation(project(":core-design"))
    implementation(project(":core-wallet"))
    implementation(project(":core-haven-aol"))
    implementation(project(":core-arkiv"))
    implementation(project(":core-collections"))
    implementation(project(":core-crypto"))
    implementation(project(":core-attestation"))
    implementation(project(":core-cache"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-security"))
    implementation(project(":feature-onboarding"))
    implementation(project(":feature-library"))
    implementation(project(":feature-watch"))
    implementation(project(":feature-community"))
    implementation(project(":feature-collections"))
    implementation(project(":feature-settings"))
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
    // Icons.Default.{VideoLibrary, Groups, BugReport} in Routes.kt live in the extended set. The
    // compose convention plugin adds this for feature modules; :app uses the application convention,
    // so it needs the dependency explicitly.
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("com.google.accompanist:accompanist-navigation-material:0.32.0")
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.navigation)
    implementation(libs.hilt.navigation)
    implementation("androidx.appcompat:appcompat:1.7.0")
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