plugins {
    id("haven.android.library")
}

android {
    namespace = "haven.mobile.core.domain"
}

dependencies {
    api("io.github.haven-hvn:foc-cache:0.1.0")
    // `Instant` appears in MediaItem/Attestation signatures, so consumers must be able to see
    // it. As `implementation` this compiled here but broke any module that touched createdAt.
    api(libs.kotlinx.datetime)
}