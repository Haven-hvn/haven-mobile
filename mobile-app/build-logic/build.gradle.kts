plugins {
    `kotlin-dsl`
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
