plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.attestation"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-haven-aol"))
}