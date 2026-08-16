package haven.mobile.core.arkiv

import haven.mobile.core.domain.Community
import haven.mobile.core.domain.HavenChain
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.TokenGate

interface ArkivClient {
    /**
     * Items **published by** [owner].
     *
     * Parity with `haven-dapp`'s `fetchAllVideos(ownerAddress)`. A creator sees their own uploads through
     * this, whether or not they still hold the asset they gated on.
     */
    suspend fun listMediaForOwner(
        owner: String,
        pageSize: Int = 20,
        cursor: String? = null,
    ): Result<ArkivPage<MediaItem>>

    /** Items **gated by** [gate], whoever published them. */
    suspend fun listMediaForCommunity(
        gate: TokenGate,
        pageSize: Int = 20,
        cursor: String? = null,
    ): Result<ArkivPage<MediaItem>>

    /**
     * Every distinct gate condition recorded on Arkiv, optionally narrowed to [chains].
     *
     * The right-hand side of the intersection that decides what a wallet can read: Arkiv stores what each
     * archive requires, the wallet's holdings say what it has, and the overlap is the library. Without
     * this the app can only ask about gates it already knows of — a bundled roster, or gates the wallet
     * itself published under — and a reader who holds an asset nobody hard-coded sees nothing.
     */
    suspend fun discoverGates(
        chains: Set<HavenChain> = HavenChain.mainnets.toSet(),
    ): Result<List<TokenGate>>

    /**
     * The communities this address has **published under**.
     *
     * `haven-dapp`'s own discovery, which derives gates from entities you own. Kept for parity and for
     * creators; it answers "nothing" for a reader who has never published, which is why
     * [discoverGates] exists.
     */
    suspend fun discoverUserCommunities(address: String): Result<List<Community>>

    suspend fun getMedia(id: String): Result<MediaItem?>
}
