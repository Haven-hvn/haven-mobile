package haven.mobile.core.design.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import haven.mobile.core.design.HavenSpacing

/**
 * An answer, where the question comes up.
 *
 * Haven asks a newcomer to accept several unfamiliar things at once — an asset as a membership
 * card, a signature that is not a payment, content that decrypts on their own device. The rule
 * this component exists to enforce: **no term the reader could stall on is left to a glossary
 * they would have to go and find.** If a screen uses an idea, that screen explains it.
 *
 * Collapsed by default and quiet by design. Someone who already knows what a wallet is should be
 * able to ignore it entirely; someone who does not should find the answer without leaving.
 */
@Composable
fun Explain(
    question: String,
    modifier: Modifier = Modifier,
    body: String,
) {
    var expanded by rememberSaveable(question) { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // One target for the whole row: a chevron alone is a 24dp hit area, and this is
                // exactly the affordance an unsure reader is reaching for.
                .clickable { expanded = !expanded }
                .semantics(mergeDescendants = true) {}
                .animateContentSize()
                .padding(HavenSpacing.md),
            verticalArrangement = Arrangement.spacedBy(HavenSpacing.sm),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = HavenSpacing.touchTarget - HavenSpacing.md * 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(HavenSpacing.sm))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Hide answer" else "Show answer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (expanded) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(HavenSpacing.xxs))
            }
        }
    }
}
