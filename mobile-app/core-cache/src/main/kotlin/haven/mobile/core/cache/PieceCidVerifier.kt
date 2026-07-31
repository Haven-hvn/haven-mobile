package haven.mobile.core.cache

fun interface PieceCidVerifier {
    suspend fun verify(pieceCid: String, bytes: ByteArray): Boolean
}