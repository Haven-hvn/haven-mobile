package haven.mobile.feature.watch

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Only a confirmed unavailable piece may trigger the automatic viewer exit.
 * Every other failure keeps the explicit error screen, retry control, and
 * reader-owned navigation.
 */
class UnavailableAutoReturnTest {

    @Test
    fun `unavailable failure auto-returns`() {
        assertTrue(
            shouldAutoReturnUnavailable(
                ContentState.Failed(
                    code = "PIECE_UNAVAILABLE",
                    message = "This item's content is no longer available from any provider.",
                ),
            ),
        )
    }

    @Test
    fun `wrapped provider failures auto-return`() {
        val failure = RuntimeException(
            "Fetch failed",
            cloud.filecoin.foc.cache.PieceUnavailableException("No endpoint could serve piece"),
        ).toStageFailure("This item could not be decrypted.")
        assertTrue(shouldAutoReturnUnavailable(failure))
    }

    @Test
    fun `other failures stay on screen`() {
        assertFalse(
            shouldAutoReturnUnavailable(
                ContentState.Failed(
                    code = "WALLET_NOT_CONNECTED",
                    message = "Connect a wallet to open this item.",
                ),
            ),
        )
        assertFalse(shouldAutoReturnUnavailable(ContentState.Idle))
    }
}
