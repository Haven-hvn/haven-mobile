package haven.mobile.feature.watch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.component.MediaKindGlyph
import haven.mobile.core.design.component.label
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persistent "Now Playing" bar: artwork, title, and play/pause, one tap from anywhere.
 *
 * Modelled on AntennaPod's collapsed player (`ExternalPlayerFragment`): cover slot on the
 * left, title over a stable subtitle, a play/pause button on the right, and a thin progress
 * edge along the bottom. The bar never seeks — tapping it expands the full viewer, and the
 * button steers the service player so it stays truthful when playback changes elsewhere
 * (viewer, notification, lockscreen).
 *
 * Entities carry no thumbnails (there is no `thumbnail_cid` in practice), so the kind glyph
 * stands in for AntennaPod's cover art at the same 64dp row height.
 */
@Composable
fun MiniPlayerBar(
    track: NowPlayingTrack,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller by rememberServiceController()
    val isPlaying = rememberIsPlaying(controller)
    val progress = rememberPlaybackProgress(controller)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MINI_PLAYER_HEIGHT)
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Expand player",
                        onClick = onExpand,
                    )
                    .padding(start = HavenSpacing.sm, end = HavenSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniPlayerArtwork(track = track, player = controller)
                Spacer(Modifier.width(HavenSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.kind.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(HavenSpacing.sm))
                // Nested tap target: consumed here, so it never bubbles up into the expand tap.
                if (progress.isBuffering && controller != null) {
                    Box(
                        modifier = Modifier.size(PLAY_BUTTON_SIZE),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                } else {
                    IconButton(
                        onClick = {
                            val player = controller ?: return@IconButton
                            if (player.isPlaying) player.pause() else player.play()
                        },
                        enabled = controller != null,
                        modifier = Modifier.size(PLAY_BUTTON_SIZE),
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progressFraction(progress.positionMs, progress.durationMs) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PROGRESS_EDGE_HEIGHT),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
        }
    }
}

/**
 * The cover slot: the track's embedded picture when staging found one, the kind glyph
 * otherwise. Decoded off-main and downsampled to the slot — a 3000px embedded JPEG must
 * never become a 36MB bitmap for a 48dp thumbnail.
 *
 * Two sources, in order: the file staging extracted, then the live player's own
 * `artworkData`. The second is a same-parser repair — the player surface demonstrably
 * renders art from this exact file, so when staging drew blank the bar takes ExoPlayer's
 * bytes and files them for next time instead of trusting a second parser.
 */
@Composable
private fun MiniPlayerArtwork(track: NowPlayingTrack, player: Player?) {
    val context = LocalContext.current
    val playerArt = rememberPlayerArtwork(player)
    val bitmap by produceState<Bitmap?>(initialValue = null, track.artworkPath, playerArt) {
        value = withContext(Dispatchers.IO) {
            decodeCachedArtwork(track.artworkPath)
                ?: playerArt?.let { bytes ->
                    persistPlayerArtwork(context.cacheDir, track.itemId, bytes)
                    decodeArtworkBytes(bytes, ARTWORK_TARGET_PX)
                }
        }
    }

    val art = bitmap
    if (art == null) {
        MediaKindGlyph(kind = track.kind, size = 48.dp)
    } else {
        Image(
            bitmap = art.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(MaterialTheme.shapes.small),
        )
    }
}

/**
 * Artwork off the live player, as Compose state. The player surface already renders this
 * file's cover, so these bytes are ground truth — whatever the staging-time parser made
 * of the same file is irrelevant here.
 */
@Composable
private fun rememberPlayerArtwork(player: Player?): ByteArray? {
    var art by remember(player) { mutableStateOf(player?.mediaMetadata?.artworkData) }

    DisposableEffect(player) {
        if (player == null) {
            return@DisposableEffect onDispose {}
        }
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                art = metadata.artworkData
            }
        }
        player.addListener(listener)
        art = player.mediaMetadata.artworkData
        onDispose { player.removeListener(listener) }
    }

    return art
}

/** Files one item's player-supplied cover for the next open. Null-safe no-op on failure. */
private fun persistPlayerArtwork(cacheDir: File, itemId: String, bytes: ByteArray): File? =
    runCatching {
        val target = artworkFileFor(cacheDir, itemId)
        if (!target.exists()) {
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
        }
        target
    }.getOrNull()

/** Two-pass decode: measure, pick a power-of-two sample size, then decode at that size. */
private fun decodeArtwork(file: File, targetPx: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, targetPx)
    }
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, options) }.getOrNull()
}

private fun decodeCachedArtwork(path: String?): Bitmap? =
    if (path == null) null else decodeArtwork(File(path), ARTWORK_TARGET_PX)

private fun decodeArtworkBytes(bytes: ByteArray, targetPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, targetPx)
    }
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) }.getOrNull()
}

/**
 * Largest power-of-two downsample keeping the width at or above [targetPx].
 * Pure so the memory math stays unit-tested without a device.
 */
fun sampleSizeFor(sourceWidthPx: Int, targetPx: Int): Int {
    if (sourceWidthPx <= 0 || targetPx <= 0) return 1
    var sample = 1
    while (sourceWidthPx / (sample * 2) >= targetPx) {
        sample *= 2
    }
    return sample
}

private const val ARTWORK_TARGET_PX = 144

private val MINI_PLAYER_HEIGHT = 64.dp
private val PLAY_BUTTON_SIZE = 48.dp
private val PROGRESS_EDGE_HEIGHT = 3.dp
