import java.util.Properties

plugins {
    id("haven.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

/** Blank means "use the public default in EvmEndpoints", not "disabled". */
fun rpcField(key: String) = "\"${localProps.getProperty(key, "")}\""

android {
    namespace = "haven.mobile.core.collections"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // One endpoint per chain Haven-AOL can evaluate a gate on (see core-domain HavenChain).
        // Overrides only: the defaults are the same public endpoints haven-dapp uses, so access
        // checking works out of the box rather than hinging on a single config key.
        buildConfigField("String", "RPC_ETHEREUM", rpcField("evm.rpc.ethereum"))
        buildConfigField("String", "RPC_BASE", rpcField("evm.rpc.base"))
        buildConfigField("String", "RPC_ARBITRUM", rpcField("evm.rpc.arbitrum"))
        buildConfigField("String", "RPC_OPTIMISM", rpcField("evm.rpc.optimism"))
        buildConfigField("String", "RPC_SEPOLIA", rpcField("evm.rpc.sepolia"))
    }
}

dependencies {
    implementation(project(":core-domain"))
    implementation(project(":core-wallet"))
    implementation(libs.kotlinx.coroutines)
    implementation(libs.okhttp)
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-compiler:2.60.1")
}
