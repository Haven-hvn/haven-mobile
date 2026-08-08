plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.wallet"
}

dependencies {
    implementation(project(":foc-cache"))
    // Reown is temporarily stubbed — see com.reown.appkit.AppKitStubs
}