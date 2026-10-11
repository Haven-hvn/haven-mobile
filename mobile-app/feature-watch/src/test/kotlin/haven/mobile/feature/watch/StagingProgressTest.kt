package haven.mobile.feature.watch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Staging progress honesty: the fetch phase is silent (foc emits nothing until the piece
 * is on disk) and Arkiv entities carry no size, so anything determinate before bytes move
 * — or without a known total — is a stuck-at-zero bar. These lock the indeterminate states.
 */
class StagingProgressTest {

    @Test
    fun `fetch starts as indeterminate loading`() {
        assertEquals(
            ContentState.Working(UnlockStage.LOADING, progress = null),
            fetchStartedState(),
        )
    }

    @Test
    fun `unknown total never fakes a percentage`() {
        assertNull(stagingFraction(null, 0))
        assertNull(stagingFraction(null, 12_000_000))
        assertNull(stagingFraction(0, 12_000_000))
        assertNull(stagingFraction(-1, 12_000_000))
    }

    @Test
    fun `streaming keeps the fetch stage with a null fraction when size is unknown`() {
        assertEquals(
            ContentState.Working(UnlockStage.STREAMING, progress = null),
            streamingState(UnlockStage.STREAMING, null, 12_000_000),
        )
        assertEquals(
            ContentState.Working(UnlockStage.LOADING, progress = null),
            streamingState(UnlockStage.LOADING, null, 12_000_000),
        )
    }

    @Test
    fun `known total yields exact fractions`() {
        assertEquals(0f, stagingFraction(100, 0))
        assertEquals(0.25f, stagingFraction(100, 25))
        assertEquals(1f, stagingFraction(100, 100))
    }

    @Test
    fun `fraction clamps instead of overshooting`() {
        // Plaintext is slightly smaller than ciphertext, so written can pass the total.
        assertEquals(1f, stagingFraction(100, 101))
        assertEquals(0f, stagingFraction(100, -5))
    }
}
