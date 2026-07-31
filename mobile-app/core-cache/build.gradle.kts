plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.cache"
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-wallet"))
    implementation(project(":foc-cache"))
}