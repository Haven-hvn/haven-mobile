package haven.mobile.feature.watch

import haven.mobile.core.domain.HavenChain
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins the trade helper's pure surface: calldata layouts, amount parsing, and
 * slippage math. Calldata vectors were generated independently (Python
 * keccak + ABI encoding) so the tests guard against encoder drift.
 */
class MintClubTradeTest {

    private val token = "0x1111111111111111111111111111111111111111"
    private val recipient = "0x2222222222222222222222222222222222222222"
    private val bond = "0xc5a076cad94176c2996B32d8466Be1cE757FAa27"

    @Test
    fun `bond addresses match Mint Club V2 deployments`() {
        assertEquals(bond, MintClubTrade.bondAddress(HavenChain.BASE_MAINNET))
        assertEquals(bond, MintClubTrade.bondAddress(HavenChain.ETH_MAINNET))
        assertEquals(bond, MintClubTrade.bondAddress(HavenChain.ARBITRUM_ONE))
        assertEquals(bond, MintClubTrade.bondAddress(HavenChain.OPTIMISM_MAINNET))
        assertEquals(
            "0x8dce343A86Aa950d539eeE0e166AFfd0Ef515C0c",
            MintClubTrade.bondAddress(HavenChain.ETH_SEPOLIA),
        )
    }

    @Test
    fun `mint calldata vector`() {
        assertEquals(
            "0xf74bfe8e" +
                "0000000000000000000000001111111111111111111111111111111111111111" +
                "0000000000000000000000000000000000000000000000000de0b6b3a7640000" +
                "0000000000000000000000000000000000000000000000000000000000003039" +
                "0000000000000000000000002222222222222222222222222222222222222222",
            MintClubTrade.mintCalldata(token, BigInteger.TEN.pow(18), BigInteger("12345"), recipient),
        )
    }

    @Test
    fun `burn calldata vector`() {
        assertEquals(
            "0x5a4d5311" +
                "0000000000000000000000001111111111111111111111111111111111111111" +
                "0000000000000000000000000000000000000000000000004563918244f40000" +
                "0000000000000000000000000000000000000000000000000000000000002328" +
                "0000000000000000000000002222222222222222222222222222222222222222",
            MintClubTrade.burnCalldata(token, BigInteger.TEN.pow(18).multiply(BigInteger("5")), BigInteger("9000"), recipient),
        )
    }

    @Test
    fun `approve calldata vector`() {
        assertEquals(
            "0x095ea7b3" +
                "000000000000000000000000c5a076cad94176c2996b32d8466be1ce757faa27" +
                "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            MintClubTrade.approveCalldata(bond, MintClubTrade.MAX_UINT),
        )
    }

    @Test
    fun `buy quote calldata vector`() {
        assertEquals(
            "0x76a9864b" +
                "0000000000000000000000001111111111111111111111111111111111111111" +
                "0000000000000000000000000000000000000000000000000de0b6b3a7640000",
            MintClubTrade.buyQuoteCalldata(token, BigInteger.TEN.pow(18)),
        )
    }

    @Test
    fun `amount parsing`() {
        assertEquals(BigInteger.TEN.pow(18), MintClubTrade.parseAmount("1"))
        assertEquals(BigInteger("1500000000000000000"), MintClubTrade.parseAmount("1.5"))
        assertEquals(BigInteger("2500000000000000000"), MintClubTrade.parseAmount("2,5"))
        assertNull(MintClubTrade.parseAmount(""))
        assertNull(MintClubTrade.parseAmount("0"))
        assertNull(MintClubTrade.parseAmount("-3"))
        assertNull(MintClubTrade.parseAmount("abc"))
        assertNull(MintClubTrade.parseAmount("0.0000000000000000001"))
    }

    @Test
    fun `slippage math`() {
        val quote = BigInteger("10000")
        assertEquals(BigInteger("10100"), MintClubTrade.maxWithSlippage(quote, 100))
        assertEquals(BigInteger("9900"), MintClubTrade.minWithSlippage(quote, 100))
        assertEquals(BigInteger.ZERO, MintClubTrade.minWithSlippage(BigInteger("50"), 10_000))
    }

    @Test
    fun `quote pair decoding`() {
        val (first, second) = MintClubTrade.decodeUintPair(
            "0x" + "00".repeat(31) + "0d" + "00".repeat(31) + "2a",
        )!!
        assertEquals(BigInteger("13"), first)
        assertEquals(BigInteger("42"), second)
        assertNull(MintClubTrade.decodeUintPair("0x1234"))
    }

    @Test
    fun `unit formatting`() {
        assertEquals("1.5", MintClubTrade.formatUnits(BigInteger("1500000000000000000")))
        assertEquals("2", MintClubTrade.formatUnits(BigInteger.TEN.pow(18).multiply(BigInteger("2"))))
        assertEquals("0", MintClubTrade.formatUnits(BigInteger.ZERO))
    }
}
