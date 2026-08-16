plugins {
    `kotlin-dsl`
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:9.3.1")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    implementation("com.google.dagger:hilt-android-gradle-plugin:2.60.1")
    implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.11")
}

// NOTE: do NOT declare a `gradlePlugin { plugins { register(...) } }` block here.
// Every file under src/main/kotlin/*.gradle.kts is a *precompiled script plugin*: the
// `kotlin-dsl` plugin compiles it and registers the plugin id from the file name
// (haven.android.library.gradle.kts -> id "haven.android.library"). Registering the same
// ids manually with an `implementationClass` fails at configuration time, because the
// named class does not exist and the id is already taken.
//
// Available ids (one per file in src/main/kotlin):
//   haven.android.application, haven.android.library, haven.android.compose, haven.kotlin.library
