plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.domain"
}

dependencies {
    api(project(":foc-cache"))
    implementation(libs.kotlinx.datetime)
}