package haven.mobile.feature.watch

/**
 * Fetch phase of staging: foc emits nothing until the piece is on disk, so the whole
 * download happens before any byte counter moves. Indeterminate LOADING — a determinate
 * 0% here would sit frozen for the entire fetch and read as a wedged decrypt.
 */
internal fun fetchStartedState(): ContentState.Working =
    ContentState.Working(UnlockStage.LOADING, progress = null)

/**
 * First bytes reached the spool: the silent fetch is over and decrypt/streaming runs
 * from here. Progress stays indeterminate when the total is unknown (Arkiv entities
 * carry no size) instead of faking a percentage.
 */
internal fun streamingState(
    fetchStage: UnlockStage,
    expectedBytes: Long?,
    written: Long,
): ContentState.Working = ContentState.Working(fetchStage, stagingFraction(expectedBytes, written))

/**
 * Progress against the *declared* size. Plaintext runs slightly smaller than ciphertext
 * (GCM tags), so this can reach 100% a beat early — better than a bar that stalls at 97%
 * because it was measuring the wrong thing. Null when the total is unknown.
 */
internal fun stagingFraction(expectedBytes: Long?, written: Long): Float? =
    expectedBytes?.takeIf { it > 0 }
        ?.let { (written.toFloat() / it.toFloat()).coerceIn(0f, 1f) }
