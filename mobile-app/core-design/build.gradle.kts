plugins {
    id("haven.android.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
}

android {
    namespace = "haven.mobile.core.design"
}

dependencies {
    // Domain types leak into component signatures on purpose (MediaKind badge,
    // cache-status chip), so they are `api` rather than `implementation`.
    api(project(":core-domain"))
}
