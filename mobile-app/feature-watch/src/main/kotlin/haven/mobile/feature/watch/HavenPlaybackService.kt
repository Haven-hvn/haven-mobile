package haven.mobile.feature.watch

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Playback that survives leaving the app.
 *
 * Audio previously stopped the moment Haven was backgrounded, because the player lived in a
 * composable — when the UI went away, so did playback. On Android the only way round that is for a
 * service to own the player, which also buys the system notification and lockscreen controls for free.
 *
 * The player is created here and handed to the UI through a `MediaController`, so there is exactly one
 * player: no risk of a screen and a service both holding one and fighting over the audio focus.
 *
 * Only ever plays local files — the staged, already-decrypted content — so nothing here touches the
 * network and nothing leaves the device.
 */
@OptIn(UnstableApi::class)
class HavenPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                // Handle audio focus: pause for a phone call, resume after. Doing this manually is
                // the usual source of "why is my music still playing under the call".
                /* handleAudioFocus = */ true,
            )
            // A talk or a set is long-form; pausing on an unplugged headset is what a listener expects.
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /**
     * Nothing is playing and the app is gone: stop, rather than leaving an idle foreground service and
     * a stale notification behind.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
