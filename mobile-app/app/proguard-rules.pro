# Haven Mobile — R8/ProGuard rules
#
# `isMinifyEnabled` is false for now, but this file is referenced by the release build type in
# both app/build.gradle.kts and the haven.android.application convention plugin, so it has to
# exist or the release variant fails to configure. The rules below are what shrinking will need
# the day it is switched on, written now while the reasons are fresh.

# ── Kotlin / coroutines ──────────────────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── Reflection-driven serialization of domain payloads ───────────────────────────────────────
# Arkiv responses are parsed with org.json rather than reflection, but kotlinx-serialization is
# on the classpath for future use; its generated serializers must survive.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Room ─────────────────────────────────────────────────────────────────────────────────────
# Entities and DAOs are referenced by generated code and by name in queries.
-keep class haven.mobile.core.cache.mirror.MediaMirrorEntity { *; }
-keep interface haven.mobile.core.cache.mirror.MediaDao { *; }
-dontwarn androidx.room.paging.**

# ── Hilt / Dagger ────────────────────────────────────────────────────────────────────────────
# Hilt ships its own consumer rules; these cover the injected constructors R8 cannot see used.
-keepclasseswithmembernames,includedescriptorclasses class * {
    @javax.inject.Inject <init>(...);
}

# ── Media3 ───────────────────────────────────────────────────────────────────────────────────
# ExoPlayer instantiates renderers and extractors reflectively by class name.
-keep class androidx.media3.exoplayer.** { *; }
-dontwarn androidx.media3.**

# ── WalletConnect / Reown ────────────────────────────────────────────────────────────────────
# Relay payloads are Moshi/Gson-shaped models resolved by name at runtime.
-keep class com.reown.** { *; }
-dontwarn com.reown.**
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}

# ── ic-kotlin (Candid / agent transport) ─────────────────────────────────────────────────────
# Candid encode/decode walks sealed-class hierarchies; keep the value model intact.
-keep class dev.ic.kotlin.candid.** { *; }
-dontwarn dev.ic.kotlin.**

# ── foc-cache ────────────────────────────────────────────────────────────────────────────────
-keep class cloud.filecoin.foc.cache.** { *; }
-dontwarn cloud.filecoin.foc.cache.**

# ── OkHttp ───────────────────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**

# ── Crash triage ─────────────────────────────────────────────────────────────────────────────
# No third-party crash reporter in v1 (requirements §6), so line numbers are the only thing
# standing between a user-reported stack trace and a fix.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
