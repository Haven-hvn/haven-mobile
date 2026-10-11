package haven.mobile.core.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme
import haven.mobile.core.domain.MediaItem
import haven.mobile.core.domain.MediaKind
import java.io.File

/**
 * The two ways a `MediaItem` is presented. Library and Community render the same object, so they
 * render it the same way — a list row and a grid card, defined once here.
 *
 * The row carries what a reader can act on: what it is, how big, how recent, and whether it
 * will open without a connection. The card is the visual layout — full-bleed art, glass
 * title strip, status chips — with the facts left to the row. The optional `caption` line
 * carries the library wording (`My contribution · Members`, see `LibraryLabels`).
 * **No piece CIDs, no owner addresses, no chain names.** Those are storage
 * internals; an earlier revision printed a `bafkzcib…` hash under every title, which made a media
 * library read like a debugging console. Identifiers live behind a details affordance on the
 * viewer, for the rare moment somebody genuinely needs one.
 */

/**
 * Dense list row. 76dp, one tap target, no nested clickables.
 *
 * `selected` is a visual state only: non-null draws a leading checkbox whose tap is the
 * row's own click (selection mode), so the row stays a single tap target.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaRow(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    attestation: AttestationState? = null,
    selected: Boolean? = null,
    /** Library caption (`My contribution · Members`); null hides it. */
    caption: String? = null,
    /** Long-press action (library: enters selection with the item checked); null disables. */
    onLongClick: (() -> Unit)? = null,
    /** True once the unlock key is held — shows the Unlocked chip. */
    keyReady: Boolean = false,
    /** Extracted cover-art slot file; null (or an empty slot) falls back to the kind glyph. */
    artworkFile: File? = null,
) {
    val pressModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HavenSpacing.rowHeight)
            .then(pressModifier)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected != null) {
            Checkbox(checked = selected, onCheckedChange = null)
            Spacer(Modifier.width(HavenSpacing.sm))
        }
        ArtworkImage(file = artworkFile, kind = item.kind)
        Spacer(Modifier.width(HavenSpacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (caption != null) {
                Spacer(Modifier.height(HavenSpacing.xxs))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(HavenSpacing.xxs))
            Text(
                text = item.summaryLine(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(HavenSpacing.sm))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(HavenSpacing.xs),
        ) {
            CacheStatusChip(status = item.contentCacheStatus, compact = true)
            if (keyReady) {
                KeyStatusChip(compact = true)
            }
            if (attestation != null) {
                AttestationBadge(state = attestation, compact = true)
            }
        }
    }
}

/**
 * Grid card. Full-bleed square art with a glass title strip over the bottom edge — the grid
 * is the visual layout, so the face carries the title and status chips only, and the list
 * row keeps the caption, summary line, and full facts.
 *
 * The glass reflects the cover: its scrim is a gradient of the artwork's own dominant
 * color, darkened for contrast, so each card is tinted by its album. Items without art
 * get the same layout over the kind plate with a neutral scrim.
 *
 * `selected` mirrors the row: a non-null value overlays a checkbox while the card itself
 * stays the single tap target.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    attestation: AttestationState? = null,
    selected: Boolean? = null,
    /** Long-press action (library: enters selection with the item checked); null disables. */
    onLongClick: (() -> Unit)? = null,
    /** True once the unlock key is held — shows the Unlocked chip. */
    keyReady: Boolean = false,
    /** Extracted cover-art slot file; null (or an empty slot) falls back to the kind plate. */
    artworkFile: File? = null,
) {
    val pressModifier = if (onLongClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier.clickable(onClick = onClick)
    }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(pressModifier)
            .semantics(mergeDescendants = true) {},
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(HavenSpacing.hairline, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        ) {
            val visual = rememberArtworkVisual(artworkFile, ARTWORK_CARD_TARGET_PX)
            val art = visual?.bitmap
            if (art == null) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = item.kind.icon(),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            } else {
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // Glass: transparent at the top so the art shows through, settling into the
            // cover's own darkened color behind the title.
            val scrim = scrimBase(visual?.dominant)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.44f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, scrim.copy(alpha = 0.92f)),
                        ),
                    )
                    .padding(HavenSpacing.md),
                contentAlignment = Alignment.BottomStart,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(HavenSpacing.sm),
                contentAlignment = Alignment.TopEnd,
            ) {
                CacheStatusChip(status = item.contentCacheStatus, compact = true)
            }
            // Selection wins the corner while it lasts; otherwise the status chips live here.
            if (selected != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(HavenSpacing.xs),
                    contentAlignment = Alignment.TopStart,
                ) {
                    Checkbox(checked = selected, onCheckedChange = null)
                }
            } else if (keyReady || attestation != null) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(HavenSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(HavenSpacing.xs),
                ) {
                    if (keyReady) {
                        KeyStatusChip(compact = true)
                    }
                    if (attestation != null) {
                        AttestationBadge(state = attestation, compact = true)
                    }
                }
            }
        }
    }
}

/**
 * The one subtitle used everywhere: kind, runtime, size, age. Identical in the row and the card so a
 * reader switching layouts is not re-learning the screen.
 *
 * Runtime comes first after the kind because it is the fact people actually decide on — "is this a
 * two-minute clip or an hour" is a different question from "how many megabytes".
 */
fun MediaItem.summaryLine(): String = listOfNotNull(
    kind.label(),
    durationLabel(),
    byteLabel(),
    RelativeTime.format(createdAt),
).joinToString(" \u00b7 ")

/** Runtime, for the kinds that have one. */
fun MediaItem.durationLabel(): String? = durationSeconds
    ?.takeIf { it > 0 && (kind == MediaKind.VIDEO || kind == MediaKind.AUDIO) }
    ?.let { formatDuration(it) }

/**
 * `4:07`, `1:02:33`. Never zero-pads the leading unit, always pads the rest — the convention every
 * player uses, and the reason `12:05` cannot be mistaken for twelve hours.
 */
fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds < 0) return "—"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.pad()}:${seconds.pad()}"
    } else {
        "$minutes:${seconds.pad()}"
    }
}

private fun Long.pad(): String = if (this < 10) "0$this" else toString()

/**
 * Human byte size. Binary units, because the cache quota is measured in them — a "2 GiB" quota
 * that fills up after 1.86 "GB" of content is a support ticket.
 */
fun MediaItem.byteLabel(): String? = sizeBytes?.let { formatBytes(it) }

fun formatBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (value >= 100.0) {
        "${value.toLong()} ${units[unitIndex]}"
    } else {
        val rounded = (value * 10).toLong() / 10.0
        "$rounded ${units[unitIndex]}"
    }
}
