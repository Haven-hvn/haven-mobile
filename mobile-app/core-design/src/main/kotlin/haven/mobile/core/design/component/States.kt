package haven.mobile.core.design.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import haven.mobile.core.design.HavenMotion
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme

/**
 * The three states every screen has to answer for, built once.
 *
 * Empty, loading and error are visually distinct on purpose: a spinner where an empty state
 * belongs reads as "still working" and users wait for something that is never coming.
 */

/**
 * Nothing to show, and that is a valid outcome. Always names the reason and offers the one
 * action that would change it.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HavenSpacing.xl, vertical = HavenSpacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Spacer(Modifier.height(HavenSpacing.lg))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(HavenSpacing.sm))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(HavenSpacing.xl))
            Button(
                onClick = onAction,
                modifier = Modifier.height(HavenSpacing.touchTarget),
            ) {
                Text(actionLabel)
            }
        }
    }
}

/**
 * Something failed. Shows the stable error code alongside the message — the codes are the
 * shared vocabulary between this screen, the Settings error log and a support conversation.
 */
@Composable
fun ErrorState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    code: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HavenSpacing.xl, vertical = HavenSpacing.xxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(HavenSpacing.md))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(HavenSpacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (code != null) {
            Spacer(Modifier.height(HavenSpacing.md))
            Text(
                text = code,
                style = HavenTheme.text.monoSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(HavenSpacing.xl))
            Button(onClick = onRetry, modifier = Modifier.height(HavenSpacing.touchTarget)) {
                Text("Try again")
            }
        }
    }
}

/**
 * Loading. Skeletons of the real layout rather than a centred spinner, so the screen does not
 * jump when content lands and the wait reads as shorter than it is.
 */
@Composable
fun LibrarySkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 6,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HavenSpacing.gutter, vertical = HavenSpacing.md),
        verticalArrangement = Arrangement.spacedBy(HavenSpacing.md),
    ) {
        repeat(rows) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(width = HavenSpacing.glyph, height = HavenSpacing.glyph, radius = 12.dp)
                Spacer(Modifier.width(HavenSpacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(HavenSpacing.sm)) {
                    SkeletonBlock(width = 180.dp, height = 14.dp)
                    SkeletonBlock(width = 110.dp, height = 11.dp)
                }
            }
        }
    }
}

/** One shimmering placeholder block. */
@Composable
fun SkeletonBlock(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    radius: Dp = 6.dp,
) {
    val accents = HavenTheme.accents
    val transition = rememberInfiniteTransition(label = "skeleton")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(HavenMotion.DURATION_SKELETON, easing = HavenMotion.standard),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "sheen",
    )
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .background(
                // Stops must stay strictly ascending, so the sheen never reaches the ends.
                brush = Brush.horizontalGradient(
                    0f to accents.skeleton,
                    progress.coerceIn(0.06f, 0.94f) to accents.skeletonSheen,
                    1f to accents.skeleton,
                ),
                shape = RoundedCornerShape(radius),
            ),
    )
}
