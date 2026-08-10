pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("com.android.application") version "9.3.1"
        id("com.android.library") version "9.3.1"
        id("org.jetbrains.kotlin.android") version "2.3.21"
        id("org.jetbrains.kotlin.jvm") version "2.3.21"
        id("com.google.devtools.ksp") version "2.3.11"
        id("com.google.dagger.hilt.android") version "2.60.1"
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "haven-mobile"

include(":app")
include(":core-domain")
include(":core-wallet")
include(":core-haven-aol")
include(":core-arkiv")
include(":core-crypto")
include(":core-attestation")
include(":core-cache")
include(":core-cache-mirror")
include(":core-security")
include(":feature-onboarding")
include(":feature-library")
include(":feature-watch")
include(":feature-community")
include(":feature-settings")

includeBuild("build-logic")
include(":foc-cache")
project(":foc-cache").projectDir = File("../../foc-local-first-android/foc-cache")

includeBuild("../../ic-kotlin")
// includeBuild("../../reown-kotlin-develop") // Reown AppKit via Maven Central when online; offline uses local AppKit wrapper (see core-wallet AppKit.kt)