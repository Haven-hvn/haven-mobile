package haven.mobile.core.design.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme
import haven.mobile.core.domain.ContentCacheStatus
import haven.mobile.core.domain.MediaKind

/**
 * Trust and residency marks.
 *
 * Every one of these encodes state a user makes a decision on — is this playable offline, can
 * I believe who published it, which network holds it — so each is colour *plus* icon *plus*
 * text. Colour alone fails for the ~8% of men with a colour vision deficiency, and these are
 * exactly the signals you cannot afford to have misread.
 */

/** Cache residency. Reads as information, never as a tappable control. */
@Composable
fun CacheStatusChip(
    status: ContentCacheStatus,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val accents = HavenTheme.accents
    val (tint, icon, label) = when (status) {
        ContentCacheStatus.CACHED -> Triple(accents.cacheCached, Icons.Default.CloudDone, "Offline")
        ContentCacheStatus.PARTIAL -> Triple(accents.cacheFetching, Icons.Default.Downloading, "Partial")
        ContentCacheStatus.EXPIRED -> Triple(accents.cacheExpired, Icons.Default.Schedule, "Expired")
        ContentCacheStatus.UNCACHED -> Triple(accents.cacheMissing, Icons.Default.CloudOff, "Not cached")
    }
    HavenStatusChip(
        tint = tint,
        icon = icon,
        label = label,
        semantics = "Cache status: $label",
        compact = compact,
        modifier = modifier,
    )
}
/** The three attestation states. Unverified and Failed are never collapsed into one look. */
@Composable
fun AttestationBadge(
    state: AttestationState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val accents = HavenTheme.accents
    val (tint, icon, label) = when (state) {
        AttestationState.VERIFIED -> Triple(accents.attestVerified, Icons.Default.Verified, "Verified")
        // "Unsigned" rather than "Unverified": the publisher simply did not attach a signature, and
        // the latter word reads as an accusation to someone who does not know the difference.
        AttestationState.UNVERIFIED -> Triple(accents.attestUnverified, Icons.Default.RemoveCircleOutline, "Unsigned")
        AttestationState.FAILED -> Triple(accents.attestFailed, Icons.Default.Warning, "Failed check")
    }
    HavenStatusChip(
        tint = tint,
        icon = icon,
        label = label,
        semantics = when (state) {
            AttestationState.VERIFIED -> "Signed by the publisher and verified on this device"
            AttestationState.UNVERIFIED -> "The publisher did not sign this item"
            AttestationState.FAILED -> "Signature check failed — treat this item as untrusted"
        },
        compact = compact,
        modifier = modifier,
    )
}

enum class AttestationState { VERIFIED, UNVERIFIED, FAILED }

/** Which public network a piece lives on. Dot only — the hue *is* the information. */
@Composable
fun ProtocolDot(
    protocol: Protocol,
    modifier: Modifier = Modifier,
) {
    val accents = HavenTheme.accents
    val tint = when (protocol) {
        Protocol.ARKIV -> accents.protocolArkiv
        Protocol.ICP -> accents.protocolIcp
        Protocol.EVM -> accents.protocolEvm
        Protocol.FILECOIN -> accents.protocolFilecoin
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .background(tint, CircleShape)
            .clearAndSetSemantics { contentDescription = "Network: ${protocol.label}" },
    )
}

enum class Protocol(val label: String) {
    ARKIV("Arkiv"),
    ICP("Internet Computer"),
    EVM("EVM"),
    FILECOIN("Filecoin"),
}

/**
 * Per-kind glyph in a tonal container. Same size everywhere so lists stay on a grid, and the
 * shape differs per kind so the icon is not the only cue at a glance.
 */
@Composable
fun MediaKindGlyph(
    kind: MediaKind,
    modifier: Modifier = Modifier,
    size: Dp = HavenSpacing.glyph,
) {
    val shape: RoundedCornerShape = when (kind) {
        MediaKind.VIDEO -> RoundedCornerShape(12.dp)
        MediaKind.AUDIO -> RoundedCornerShape(percent = 50)
        MediaKind.IMAGE -> RoundedCornerShape(4.dp)
        MediaKind.DOCUMENT -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomEnd = 12.dp, bottomStart = 4.dp)
        MediaKind.FILE -> RoundedCornerShape(8.dp)
    }
    Surface(
        modifier = modifier.size(size),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = kind.icon(),
                contentDescription = kind.label(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}

fun MediaKind.icon(): ImageVector = when (this) {
    MediaKind.VIDEO -> Icons.Default.Videocam
    MediaKind.AUDIO -> Icons.Default.AudioFile
    MediaKind.IMAGE -> Icons.Default.Image
    MediaKind.DOCUMENT -> Icons.Default.Article
    MediaKind.FILE -> Icons.Default.InsertDriveFile
}

fun MediaKind.label(): String = when (this) {
    MediaKind.VIDEO -> "Video"
    MediaKind.AUDIO -> "Audio"
    MediaKind.IMAGE -> "Image"
    MediaKind.DOCUMENT -> "Document"
    MediaKind.FILE -> "File"
}

/**
 * Addresses and piece CIDs. Truncated in the middle rather than the end, because the tail of
 * a hash is what people actually check against a block explorer.
 */
@Composable
fun MonoIdentifier(
    value: String,
    modifier: Modifier = Modifier,
    head: Int = 6,
    tail: Int = 4,
    full: Boolean = false,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val shown = if (full) value else truncateMiddle(value, head, tail)
    Text(
        text = shown,
        style = HavenTheme.text.mono,
        color = color,
        maxLines = if (full) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.clearAndSetSemantics {
            // Read the whole value to a screen reader; the ellipsis is a visual affordance.
            contentDescription = value
        },
    )
}

/** `0x1234…cdef` — visible for identification, short enough for a 76dp row. */
fun truncateMiddle(value: String, head: Int = 6, tail: Int = 4): String {
    if (head < 0 || tail < 0) return value
    if (value.length <= head + tail + 1) return value
    return value.take(head) + "\u2026" + value.takeLast(tail)
}

/**
 * Shared chip body for every state mark in the app.
 *
 * Public so features can add their own marks (collection access, for one) without rebuilding the
 * geometry. Colour is always paired with an icon and a word: colour alone fails for the ~8% of men
 * with a colour vision deficiency, and these are the signals a reader makes decisions on.
 */
@Composable
fun HavenStatusChip(
    tint: Color,
    icon: ImageVector,
    label: String,
    semantics: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier.clearAndSetSemantics { contentDescription = semantics },
        shape = RoundedCornerShape(percent = 50),
        color = tint.copy(alpha = 0.14f),
        border = BorderStroke(HavenSpacing.hairline, tint.copy(alpha = 0.38f)),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) HavenSpacing.sm else HavenSpacing.md,
                vertical = if (compact) 3.dp else HavenSpacing.xs + 1.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(if (compact) 12.dp else 14.dp),
            )
            Spacer(Modifier.width(HavenSpacing.xs + 1.dp))
            Text(
                text = label,
                style = if (compact) HavenTheme.text.monoSmall else HavenTheme.text.mono,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
