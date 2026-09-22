package haven.mobile.feature.watch

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Playback plumbing, kept out of the screen.
 *
 * Two mechanisms live here because both are lifecycle-bound and easy to get subtly wrong: connecting
 * to the playback service, and entering picture-in-picture.
 */

/**
 * Connects to [HavenPlaybackService] and queues [file], returning the controller once it is ready.
 *
 * A `MediaController` *is* a `Player`, so `PlayerView` accepts it directly — the UI does not care that
 * the actual player lives in a service. Released on dispose; the service keeps playing if the user
 * left with something playing, which is the entire point.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun rememberPlaybackController(file: File): State<MediaController?> {
    val context = LocalContext.current

    return produceState<MediaController?>(initialValue = null, file.absolutePath) {
        val token = SessionToken(context, ComponentName(context, HavenPlaybackService::class.java))
        val controller = runCatching {
            MediaController.Builder(context, token).buildAsync().await()
        }.getOrNull()

        if (controller != null) {
            // The service owns the player, so re-expanding the viewer reconnects to the same
            // playlist and position. Only queue the file when it is not already loaded — setting
            // it unconditionally would restart playback from zero on every expand.
            val uri = android.net.Uri.fromFile(file)
            if (controller.currentMediaItem?.localConfiguration?.uri != uri) {
                controller.setMediaItem(ExoMediaItem.fromUri(uri))
                controller.prepare()
                controller.playWhenReady = true
            }
        }
        value = controller

        awaitDispose {
            // Release the *connection*, not the player: the service owns that, and tearing it down
            // here would kill background playback the moment the screen rotated.
            controller?.release()
        }
    }
}

/**
 * `ListenableFuture` -> suspend, without pulling in kotlinx-coroutines-guava for one call.
 *
 * The direct executor is deliberate: the continuation resumes on whichever thread completed the
 * future, and `produceState` is already inside a coroutine that hops back for the state write.
 */
private suspend fun <T> ListenableFuture<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addListener(
        {
            runCatching { get() }.fold(
                onSuccess = { continuation.resume(it) },
                onFailure = { continuation.resumeWithException(it) },
            )
        },
        Executor { command -> command.run() },
    )
    continuation.invokeOnCancellation { cancel(false) }
}

/**
 * Keeps video playing in a floating window when the user leaves.
 *
 * Two paths, because the good one is API 31+:
 *  - 31 and above set `autoEnterEnabled`, and the system handles the transition seamlessly — no
 *    listener, no timing to get wrong, and it animates properly.
 *  - below that, the app has to ask on the way out, which is what `onUserLeaveHint` is for.
 *
 * Both are torn down on dispose so a paused or finished video does not follow the user out of the app.
 */
@Composable
internal fun EnablePictureInPicture(player: Player?, enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    if (activity == null || !enabled) return

    DisposableEffect(activity, player) {
        val params = PictureInPictureParams.Builder()
            // 16:9 matches the viewer's own aspect. A wrong ratio here letterboxes the PiP window.
            .setAspectRatio(Rational(16, 9))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setAutoEnterEnabled(true)
                }
            }
            .build()

        runCatching { activity.setPictureInPictureParams(params) }

        val leaveHint = Runnable {
            // Only take over the screen if something is actually playing.
            val playing = player?.isPlaying == true
            if (playing && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                runCatching { activity.enterPictureInPictureMode(params) }
            }
        }
        activity.addOnUserLeaveHintListener(leaveHint)

        onDispose {
            activity.removeOnUserLeaveHintListener(leaveHint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    activity.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(false).build(),
                    )
                }
            }
        }
    }
}

/**
 * Connects to [HavenPlaybackService] without queuing anything, for UI that observes or steers
 * playback from outside the viewer — the shell's mini bar. Multiple controllers on one session
 * are cheap; each is just a connection, released on dispose.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun rememberServiceController(): State<MediaController?> {
    val context = LocalContext.current

    return produceState<MediaController?>(initialValue = null) {
        val token = SessionToken(context, ComponentName(context, HavenPlaybackService::class.java))
        val controller = runCatching {
            MediaController.Builder(context, token).buildAsync().await()
        }.getOrNull()
        value = controller

        awaitDispose {
            controller?.release()
        }
    }
}

/**
 * Play state as Compose state, so a play/pause button recomposes when playback changes from
 * anywhere — the viewer, the mini bar, the notification, the lockscreen.
 */
@Composable
internal fun rememberIsPlaying(player: Player?): Boolean {
    var isPlaying by remember(player) { mutableStateOf(player?.isPlaying == true) }

    DisposableEffect(player) {
        if (player == null) {
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlaying = player.isPlaying
            }
        }
        player.addListener(listener)
        isPlaying = player.isPlaying
        onDispose { player.removeListener(listener) }
    }

    return isPlaying
}

/**
 * Position snapshot for the mini player, in the spirit of AntennaPod's collapsed player: the bar
 * shows a thin progress edge rather than a seekbar, so it needs position/duration but never
 * seeks from here — tapping expands the full viewer for that.
 *
 * Media3 pushes state changes but not a position tick, so the snapshot refreshes on every
 * player event plus a half-second poll while something is playing. Stale reads are harmless:
 * the worst case is a progress edge a beat behind.
 */
data class PlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
    val isBuffering: Boolean,
)

@Composable
internal fun rememberPlaybackProgress(player: Player?): PlaybackProgress {
    var progress by remember(player) {
        mutableStateOf(PlaybackProgress(positionMs = 0L, durationMs = 0L, isBuffering = false))
    }

    DisposableEffect(player) {
        if (player == null) {
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onEvents(p: Player, events: Player.Events) {
                progress = p.readProgress()
            }
        }
        player.addListener(listener)
        progress = player.readProgress()
        onDispose { player.removeListener(listener) }
    }

    val playing = rememberIsPlaying(player)
    LaunchedEffect(player, playing) {
        // Poll only while playing; a paused bar is static, so there is nothing to refresh.
        // Each write to `progress` recomposes on its own — no extra tick state needed.
        while (isActive && playing && player != null) {
            delay(PROGRESS_POLL_MS)
            progress = player.readProgress()
        }
    }

    return progress
}

private fun Player.readProgress(): PlaybackProgress {
    val position = currentPosition.coerceAtLeast(0L)
    val duration = duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
    return PlaybackProgress(
        positionMs = position,
        durationMs = duration,
        isBuffering = playbackState == Player.STATE_BUFFERING,
    )
}

/**
 * Fraction for the progress edge, 0..1. Unknown or zero duration yields 0 rather than NaN —
 * the bar renders empty instead of flashing full.
 */
fun progressFraction(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L || positionMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

/**
 * Compact clock label (`m:ss`, or `h:mm:ss` past the hour). Unknown/negative time renders as
 * `--:--` so the bar never shows a bogus `0:00` for a stream with no duration yet.
 */
fun formatPlaybackTime(ms: Long): String {
    if (ms < 0L) return "--:--"
    val totalSeconds = ms / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private const val PROGRESS_POLL_MS = 500L

/**
 * Drag-to-dismiss settle decision for the viewer.
 *
 * Release past [thresholdPx] collapses; so does a fast downward fling even from a short drag
 * (velocity is in px/s, positive downward). Anything else — short slow drags, upward flings,
 * zero offset — springs back. Pure so the gesture math stays unit-tested without a device.
 */
fun shouldCollapseOnRelease(offsetPx: Float, thresholdPx: Float, velocityPxPerSec: Float): Boolean {
    if (offsetPx <= 0f) return false
    return offsetPx >= thresholdPx || velocityPxPerSec >= DISMISS_FLING_PX_PER_SEC
}

private const val DISMISS_FLING_PX_PER_SEC = 1_800f

/** Compose gives a `Context`, PiP needs the `Activity` behind it. */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
