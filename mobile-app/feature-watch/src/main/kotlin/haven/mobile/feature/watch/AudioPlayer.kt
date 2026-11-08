package haven.mobile.feature.watch

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import haven.mobile.core.design.HavenMotion
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme
import haven.mobile.core.design.component.ARTWORK_CARD_TARGET_PX
import haven.mobile.core.design.component.decodeArtworkBytes
import haven.mobile.core.design.component.label
import haven.mobile.core.design.component.rememberArtworkVisual
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.artworkFileFor
import java.io.File

/**
 * A designed "now playing" surface for audio.
 *
 * The stock Media3 `PlayerView` is a *video* controller: a hairline progress bar, a small
 * play/pause, and a letterboxed picture slot. Dropped into an 80dp strip for audio it reads as
 * plain and unfinished — which is exactly the problem this screen replaces. Audio gets its own
 * composable instead, built around what actually matters for a track:
 *
 *  - [AudioArtwork] is a compact, square, cropped cover sitting beside the title in a bounded
 *    header row, over an [ambientGround] gradient pulled from the cover's own dominant color
 *    (the design system already ships the extractor). There are no white edges, and the cover is
 *    deliberately kept small so the waveform, transport, and the album's track list below all
 *    fit on screen without scrolling. No embedded picture falls back to a designed plate, not a
 *    blank box.
 *  - [WaveformSeekBar] is a real, draggable seek control: a rounded waveform, ember for the
 *    played portion, with elapsed/remaining clocks. It mirrors the service player's position
 *    and seeks it on drag/tap — the full player is where scrubbing belongs, the mini bar is
 *    deliberately not.
 *  - [TransportControls] gives the primary action a large ember filled button, flanked by
 *    queue-aware previous/next and ±10s nudges. Every control has a real touch target and a
 *    content description.
 *
 * Video keeps the stock `PlayerView` in [PlayerViewer]; this is audio-only. Playback itself is
 * untouched — one [Player] (the MediaController to [HavenPlaybackService]) is the single owner,
 * and everything here observes or steers it.
 */

/* ── Screen ───────────────────────────────────────────────────────────────────────────────── */

@Composable
internal fun AudioPlayerScreen(
    media: MediaItem,
    controller: Player?,
    progress: PlaybackProgress,
    isPlaying: Boolean,
    isBuffering: Boolean,
    wave: List<Float>,
    artworkFile: File?,
    onSeek: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Artwork color drives the ambient wash. Decode + dominant-color sampling run off-main in
    // rememberArtworkVisual; when there is no picture we still get a neutral void ground.
    val visual = rememberArtworkVisual(artworkFile, ARTWORK_CARD_TARGET_PX)
    val ground = ambientGround(visual?.dominant)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(ground)
            .padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.md),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // A compact now-playing header: a bounded square cover beside the title, not a
            // full-width square. The old hero cover ate most of the viewport and pushed the track
            // list off-screen; this keeps the artwork present but proportionate, so the controls
            // and the tracks below are visible together without scrolling.
            Row(verticalAlignment = Alignment.CenterVertically) {
                AudioArtwork(
                    artwork = visual?.bitmap,
                    playerArt = controller,
                    itemId = media.id,
                    modifier = Modifier.size(ARTWORK_HEADER_SIZE),
                )

                Spacer(Modifier.width(HavenSpacing.md))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = media.kind.label(),
                        style = HavenTheme.text.overline,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(HavenSpacing.xs))
                    Text(
                        text = media.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(HavenSpacing.lg))

            WaveformSeekBar(
                progress = progress,
                wave = wave,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(HavenSpacing.md))

            TransportControls(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                enabled = controller != null,
                onToggle = {
                    controller?.let { player ->
                        if (player.isPlaying) player.pause() else player.play()
                    }
                },
                onReplay = { controller?.seekTo((controller.currentPosition - SEEK_NUDGE_MS).coerceAtLeast(0L)) },
                onForward = { controller?.seekTo(controller.currentPosition + SEEK_NUDGE_MS) },
                onPrevious = onSkipPrevious,
                onNext = onSkipNext,
            )
        }
    }
}

/* ── Artwork ──────────────────────────────────────────────────────────────────────────────── */

/**
 * Square, cropped cover art — no letterboxing, no white edges. The staged file is the ground
 * truth (extracted from the MP3 at staging); the live player's `artworkData` is a same-parser
 * repair when staging drew blank, matching the mini bar's two-source rule. When neither yields
 * a picture, a designed ember-tinted plate with the audio glyph stands in.
 */
@Composable
private fun AudioArtwork(
    artwork: Bitmap?,
    playerArt: Player?,
    itemId: String,
    modifier: Modifier = Modifier,
) {
    // Live-player bytes are Compose state; re-read on metadata change so a cover that arrives
    // late (some streams publish it after prepare) still fills the slot.
    var liveBytes by remember(playerArt) { mutableStateOf(playerArt?.mediaMetadata?.artworkData) }
    androidx.compose.runtime.DisposableEffect(playerArt) {
        if (playerArt == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onMediaMetadataChanged(metadata: androidx.media3.common.MediaMetadata) {
                liveBytes = metadata.artworkData
            }
        }
        playerArt.addListener(listener)
        liveBytes = playerArt.mediaMetadata.artworkData
        onDispose { playerArt.removeListener(listener) }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by androidx.compose.runtime.produceState<Bitmap?>(
        initialValue = artwork,
        artwork,
        liveBytes,
        itemId,
    ) {
        if (artwork != null) {
            value = artwork
            return@produceState
        }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            liveBytes?.let { bytes ->
                // File the player's own cover for the next open, then decode it downsampled.
                runCatching {
                    val target = artworkFileFor(context.cacheDir, itemId)
                    if (!target.exists()) {
                        target.parentFile?.mkdirs()
                        target.writeBytes(bytes)
                    }
                }
                decodeArtworkBytes(bytes, ARTWORK_CARD_TARGET_PX)
            }
        }
    }

    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = HavenSpacing.xs,
        shadowElevation = HavenElevationOverlay,
    ) {
        val art = bitmap
        if (art != null) {
            Image(
                bitmap = art.asImageBitmap(),
                contentDescription = "Album artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge),
            )
        } else {
            AudioArtworkPlaceholder()
        }
    }
}

/** Designed no-art state: an ember-tinted radial wash behind a large audio glyph. */
@Composable
private fun AudioArtworkPlaceholder() {
    val ember = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ember.copy(alpha = 0.20f),
                        ember.copy(alpha = 0.05f),
                        MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.AudioFile,
            contentDescription = "No album artwork",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(44.dp),
        )
    }
}

/* ── Seek bar ─────────────────────────────────────────────────────────────────────────────── */

/**
 * A seekable waveform progress control. Unlike the mini bar's thin progress edge, this is where
 * scrubbing belongs: tap or drag anywhere to seek, with a live elapsed/remaining clock.
 *
 * The waveform is decorative but deterministic ([waveformBuckets] derives it from the item id),
 * not a PCM analysis — the point is a bar with presence and a clear played/unplayed boundary,
 * not an accurate amplitude plot. The played bars are ember, the rest a muted surface tone, and
 * the thumb is a rounded notch at the head. Dragging suppresses the polled position locally so
 * the thumb tracks the finger instead of fighting the 500ms snapshot.
 */
@Composable
private fun WaveformSeekBar(
    progress: PlaybackProgress,
    wave: List<Float>,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val played = progressFraction(progress.positionMs, progress.durationMs)
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val shown = if (scrubbing) scrubFraction else played
    val animated by animateFloatAsState(
        targetValue = shown,
        animationSpec = tween(if (scrubbing) 0 else HavenMotion.DURATION_INSTANT),
        label = "seek-progress",
    )

    val playedColor = MaterialTheme.colorScheme.primary
    val unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
    val bufferedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val duration = progress.durationMs

    Column(modifier = modifier) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(WAVEFORM_HEIGHT)
                .semantics { contentDescription = "Seek" },
        ) {
            val widthPx = constraints.maxWidth.toFloat()
            val onSeekAt: (Float) -> Unit = { x ->
                val fraction = (x / widthPx).coerceIn(0f, 1f)
                if (duration > 0L) onSeek(seekFractionToMs(fraction, duration))
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(duration) {
                        detectTapGestures { offset -> onSeekAt(offset.x) }
                    }
                    .pointerInput(duration) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                scrubbing = true
                                scrubFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                onSeek(seekFractionToMs(scrubFraction, duration))
                                scrubbing = false
                            },
                            onDragCancel = { scrubbing = false },
                        ) { change, _ ->
                            scrubFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                        }
                    },
            ) {
                drawWaveform(
                    wave = wave,
                    fraction = animated,
                    playedColor = playedColor,
                    unplayedColor = unplayedColor,
                    bufferedColor = bufferedColor,
                )
            }
        }

        Spacer(Modifier.height(HavenSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val elapsed = if (scrubbing) {
                seekFractionToMs(scrubFraction, duration)
            } else {
                progress.positionMs
            }
            Text(
                text = formatPlaybackTime(elapsed),
                style = HavenTheme.text.figure,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (duration > 0L) "-${formatPlaybackTime((duration - elapsed).coerceAtLeast(0L))}" else "--:--",
                style = HavenTheme.text.figure,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Draws rounded waveform bars plus a bright head notch at the played boundary. */
private fun DrawScope.drawWaveform(
    wave: List<Float>,
    fraction: Float,
    playedColor: Color,
    unplayedColor: Color,
    bufferedColor: Color,
) {
    if (wave.isEmpty()) return
    val count = wave.size
    val gap = WAVE_BAR_GAP.toPx()
    val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
    val step = barWidth + gap
    val centerY = size.height / 2f
    val headX = fraction * size.width

    for (i in 0 until count) {
        val left = i * step
        val amplitude = wave[i]
        val barHeight = (size.height * (0.28f + 0.72f * amplitude)).coerceAtLeast(gap)
        val top = centerY - barHeight / 2f
        // A subtle buffered under-bar gives the track depth even before it is played.
        drawRoundRect(
            color = bufferedColor,
            topLeft = Offset(left, centerY - size.height * 0.5f),
            size = Size(barWidth, size.height),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
        val color = if (left + barWidth / 2f <= headX) playedColor else unplayedColor
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
    }

    // Head marker: a short bright notch so the exact position is legible at a glance.
    if (fraction > 0f && fraction < 1f) {
        drawRoundRect(
            color = playedColor,
            topLeft = Offset((headX - WAVE_HEAD_WIDTH.toPx() / 2f).coerceAtLeast(0f), 0f),
            size = Size(WAVE_HEAD_WIDTH.toPx(), size.height),
            cornerRadius = CornerRadius(WAVE_HEAD_WIDTH.toPx() / 2f, WAVE_HEAD_WIDTH.toPx() / 2f),
        )
    }
}

/* ── Transport ────────────────────────────────────────────────────────────────────────────── */

/**
 * Primary play/pause is a large ember filled button; secondary controls are quiet icon buttons
 * with real 48dp targets. Previous/next are queue-aware when a shuffle/album queue is loaded,
 * and the ±10s nudges always address the live position. Buffering shows a spinner in place of
 * the play glyph so the button never lies about state.
 */
@Composable
private fun TransportControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onReplay: () -> Unit,
    onForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        SecondaryControl(
            icon = Icons.Default.SkipPrevious,
            description = "Previous track",
            enabled = enabled,
            onClick = onPrevious,
        )
        Spacer(Modifier.width(HavenSpacing.sm))
        SecondaryControl(
            icon = Icons.Default.Replay10,
            description = "Rewind 10 seconds",
            enabled = enabled,
            onClick = onReplay,
        )

        Spacer(Modifier.width(HavenSpacing.lg))

        FilledIconButton(
            onClick = onToggle,
            enabled = enabled && !isBuffering,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.size(PLAY_BUTTON_SIZE),
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        Spacer(Modifier.width(HavenSpacing.lg))
        SecondaryControl(
            icon = Icons.Default.Forward10,
            description = "Forward 10 seconds",
            enabled = enabled,
            onClick = onForward,
        )
        Spacer(Modifier.width(HavenSpacing.sm))
        SecondaryControl(
            icon = Icons.Default.SkipNext,
            description = "Next track",
            enabled = enabled,
            onClick = onNext,
        )
    }
}

@Composable
private fun SecondaryControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(HavenSpacing.touchTarget),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
    }
}

/* ── Pure helpers (unit-tested) ──────────────────────────────────────────────────────────── */

/**
 * Height weights for the waveform, deterministic from [seed] (the item id) so a track's bars
 * are stable across recompositions and opens. A cheap hash-driven walk produces a lively but
 * bounded 0.12..1.0 envelope — decorative, never an amplitude claim.
 */
fun waveformBuckets(seed: String, count: Int = WAVEFORM_BUCKETS): List<Float> {
    if (count <= 0) return emptyList()
    var state = seed.fold(0x9e3779b9L.toInt()) { acc, c -> acc * 31 + c.code }
    return List(count) { i ->
        state = state * 1_103_515_245 + 12_345
        val noise = ((state ushr 8) and 0xFFFF) / 65_535f
        // A slow swell keeps neighbouring bars related instead of pure static.
        val swell = 0.35f + 0.35f * kotlin.math.sin(i * 0.45f + (seed.length * 0.7f))
        (0.12f + 0.55f * noise + 0.28f * swell).coerceIn(0.12f, 1.0f)
    }.mapIndexed { i, v ->
        // Soften the very ends so the bar does not start/end on a full-height column.
        val edge = minOf(i, count - 1 - i).coerceAtMost(4) / 4f
        (v * (0.55f + 0.45f * edge)).coerceIn(0.12f, 1.0f)
    }
}

/** Map a 0..1 scrub fraction onto a duration, clamped. Unknown duration seeks nowhere (0). */
fun seekFractionToMs(fraction: Float, durationMs: Long): Long {
    if (durationMs <= 0L) return 0L
    return (fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0L, durationMs)
}

/** Darkened, low-alpha version of the cover's dominant color for the ambient ground gradient. */
fun ambientTint(dominant: Color?): Color {
    val source = dominant ?: return Color(0xFF10141C)
    return Color(
        red = (source.red * 0.30f),
        green = (source.green * 0.30f),
        blue = (source.blue * 0.30f),
        alpha = 0.55f,
    )
}

@Composable
private fun ambientGround(dominant: Color?): Brush {
    val tint = ambientTint(dominant)
    val base = MaterialTheme.colorScheme.surface
    return Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to tint,
            0.55f to blend(tint, base, 0.65f),
            1.0f to base,
        ),
    )
}

/** Linear blend between two colors, pure so the ground stays testable. */
private fun blend(from: Color, to: Color, amount: Float): Color = Color(
    red = from.red + (to.red - from.red) * amount,
    green = from.green + (to.green - from.green) * amount,
    blue = from.blue + (to.blue - from.blue) * amount,
    alpha = from.alpha + (to.alpha - from.alpha) * amount,
)

/* ── Dimensions ──────────────────────────────────────────────────────────────────────────── */

private val WAVEFORM_HEIGHT = 56.dp
private val WAVE_BAR_GAP = 3.dp
private val WAVE_HEAD_WIDTH = 3.dp
private val PLAY_BUTTON_SIZE = 72.dp
/** Compact cover in the now-playing header — proportionate, not a full-viewport hero. */
private val ARTWORK_HEADER_SIZE = 116.dp
private val HavenElevationOverlay = 8.dp
private const val WAVEFORM_BUCKETS = 48
private const val SEEK_NUDGE_MS = 10_000L
