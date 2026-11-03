package haven.mobile.core.collections

import haven.mobile.core.domain.TokenStandard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigInteger

class GateAccessCheckerTest {

    @Test
    fun `erc20 threshold scales by reported decimals`() {
        assertEquals(
            BigInteger.valueOf(25).multiply(BigInteger.TEN.pow(18)),
            requiredUnits(25.0, 18, TokenStandard.ERC20),
        )
        assertEquals(BigInteger.ONE, requiredUnits(1.0, 0, TokenStandard.ERC20))
    }

    @Test
    fun `erc20 with unreadable decimals is scaled out of reach, not down to dust`() {
        // Regression: this returned the whole-token count (1), so a 1-wei balance
        // satisfied a 1-token gate whenever the decimals() read was absent.
        assertEquals(
            BigInteger.TEN.pow(18),
            requiredUnits(1.0, null, TokenStandard.ERC20),
        )
    }

    @Test
    fun `collection with no decimals reads threshold as an item count`() {
        assertEquals(BigInteger.ONE, requiredUnits(1.0, null, TokenStandard.ERC721))
        assertEquals(BigInteger.valueOf(3), requiredUnits(3.0, null, TokenStandard.ERC1155))
    }
}
