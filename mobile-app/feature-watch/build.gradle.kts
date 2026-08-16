plugins {
    id("haven.android.compose")
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "haven.mobile.feature.watch"
}

dependencies {
    ksp("com.google.dagger:hilt-compiler:2.60.1")
    implementation(project(":core-domain"))
    implementation(project(":core-design"))
    implementation(project(":core-haven-aol"))
    implementation(project(":core-crypto"))
    implementation(project(":core-cache"))
    implementation(project(":core-cache-mirror"))
    implementation(project(":core-wallet"))
    implementation(libs.androidx.compose.navigation)
    implementation(libs.hilt.navigation)
    // ComponentActivity, for picture-in-picture and the SAF launcher.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.media3)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.ui)
    // No image loader: viewers decode from the staged plaintext file with inSampleSize downsampling,
    // and Coil's value is a URL + disk cache — neither applies to local decrypted content.
    // DOCUMENT uses the platform android.graphics.pdf.PdfRenderer (see PdfDocument.kt) rather than
    // androidx.pdf:pdf-renderer, which is not published to Google Maven.
}
