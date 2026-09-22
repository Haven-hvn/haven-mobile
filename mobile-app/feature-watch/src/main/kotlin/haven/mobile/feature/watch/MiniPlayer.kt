package haven.mobile.feature.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * The bar is a remote view, not a player — play state and toggling go through the service
 * connection, so the button stays truthful when playback changes elsewhere (viewer,
 * notification, lockscreen). Tapping anywhere else re-expands the full viewer; playback
 * itself never moves, which is what makes collapse/expand seamless.
 */
@Composable
fun MiniPlayerBar(
    track: NowPlayingTrack,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller by rememberServiceController()
    val isPlaying = rememberIsPlaying(controller)

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MINI_PLAYER_HEIGHT)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Expand player",
                    onClick = onExpand,
                )
                .padding(horizontal = HavenSpacing.md, vertical = HavenSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaKindGlyph(kind = track.kind, size = 40.dp)
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
                    text = if (isPlaying) "Now playing · ${track.kind.label()}" else "Paused · ${track.kind.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(HavenSpacing.sm))
            // Nested tap target: consumed here, so it never bubbles up into the expand tap.
            IconButton(
                onClick = {
                    val player = controller ?: return@IconButton
                    if (player.isPlaying) player.pause() else player.play()
                },
                enabled = controller != null,
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

private val MINI_PLAYER_HEIGHT = 64.dp
