plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.wallet"
}

dependencies {
    implementation(project(":reown-kotlin-develop"))
}