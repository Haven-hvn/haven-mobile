package haven.mobile.core.collections

import haven.mobile.core.domain.TokenStandard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger

class GateAccessCheckerTest {

    @Test
    fun `erc20 threshold is already in base units and not rescaled by decimals`() {
        assertEquals(
            BigInteger.valueOf(1_000_000),
            requiredUnits(1_000_000.0, 6, TokenStandard.ERC20),
        )
        assertEquals(BigInteger.ONE, requiredUnits(1.0, 0, TokenStandard.ERC20))
    }

    @Test
    fun `erc20 with unreadable decimals still uses base units without rescaling`() {
        assertEquals(
            BigInteger.valueOf(1_000_000),
            requiredUnits(1_000_000.0, null, TokenStandard.ERC20),
        )
    }

    @Test
    fun `collection with no decimals reads threshold as an item count`() {
        assertEquals(BigInteger.ONE, requiredUnits(1.0, null, TokenStandard.ERC721))
        assertEquals(BigInteger.valueOf(3), requiredUnits(3.0, null, TokenStandard.ERC1155))
    }
}
