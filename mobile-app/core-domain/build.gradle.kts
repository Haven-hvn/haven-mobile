plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.domain"
}

dependencies {
    implementation(project(":foc-cache"))
    implementation(libs.kotlinx.datetime)
}