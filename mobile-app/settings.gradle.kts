pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
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

include(":maestro")
include(":build-logic")

includeBuild("../../foc-local-first-android")
includeBuild("../../ic-kotlin")
includeBuild("../../reown-kotlin-develop")