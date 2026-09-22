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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
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
 * Swipe-down-to-collapse accumulator.
 *
 * Fires [onCollapse] once the downward overscroll passes [thresholdPx], then resets so one
 * gesture collapses exactly once. Anything else (upward motion, a fresh gesture) resets the
 * count. Pure so the gesture math stays unit-tested without a device.
 */
class OverscrollCollapse(
    private val thresholdPx: Float,
    private val onCollapse: () -> Unit,
) {
    private var accumulated = 0f

    fun onOverscroll(downwardPx: Float) {
        if (downwardPx <= 0f) {
            accumulated = 0f
            return
        }
        accumulated += downwardPx
        if (accumulated >= thresholdPx) {
            accumulated = 0f
            onCollapse()
        }
    }
}

/** Compose gives a `Context`, PiP needs the `Activity` behind it. */
private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
