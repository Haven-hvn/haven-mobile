plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    id("org.jetbrains.kotlin.kapt") version "2.2.21" apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.5" apply false
    id("com.google.dagger.hilt.android") version "2.60.1" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
