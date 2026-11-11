plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.domain"
}

dependencies {
    implementation(libs.kotlinx.coroutines)
    api("io.github.haven-hvn:foc-cache:0.4.2")
    // `Instant` appears in MediaItem/Attestation signatures, so consumers must be able to see
    // it. As `implementation` this compiled here but broke any module that touched createdAt.
    api(libs.kotlinx.datetime)
}