package haven.mobile.feature.watch

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.component.MediaKindGlyph
import haven.mobile.core.design.component.label

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
                MediaKindGlyph(kind = track.kind, size = 48.dp)
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

private val MINI_PLAYER_HEIGHT = 64.dp
private val PLAY_BUTTON_SIZE = 48.dp
private val PROGRESS_EDGE_HEIGHT = 3.dp
