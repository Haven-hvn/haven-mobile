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

gradlePlugin {
    plugins {
        register("android-application") {
            id = "haven.android.application"
            implementationClass = "haven.android.application.gradle.kts"
        }
        register("android-library") {
            id = "haven.android.library"
            implementationClass = "haven.android.library.gradle.kts"
        }
        register("android-compose") {
            id = "haven.android.compose"
            implementationClass = "haven.android.compose.gradle.kts"
        }
        register("kotlin-library") {
            id = "haven.kotlin.library"
            implementationClass = "haven.kotlin.library.gradle.kts"
        }
    }
}
