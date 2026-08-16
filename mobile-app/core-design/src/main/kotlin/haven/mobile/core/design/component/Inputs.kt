package haven.mobile.core.design.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import haven.mobile.core.design.HavenSpacing
import haven.mobile.core.design.HavenTheme

/**
 * Inputs and settings primitives.
 *
 * Settings was the worst offender before this refactor: sliders, switches and destructive
 * dialogs were rebuilt per item with fully-qualified inline calls and `remember` state living
 * inside `LazyColumn` items — where it is silently discarded on scroll. These take their state
 * from the caller, so a dialog cannot lose track of whether it is open.
 */

/** Search. `singleLine`, search IME action, and a clear button once there is something to clear. */
@Composable
fun HavenSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
    onSearch: () -> Unit = {},
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HavenSpacing.touchTarget + 8.dp),
        placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        textStyle = MaterialTheme.typography.bodyMedium,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
    )
}

/**
 * Group heading. Uppercase mono eyebrow plus a hairline — the one print idiom that survives
 * the trip to mobile, because it separates groups without spending 32dp on a heading.
 */
@Composable
fun SectionHeader(
    label: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = HavenSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label.uppercase(),
                style = HavenTheme.text.overline,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            trailing()
        }
        HorizontalDivider(
            thickness = HavenSpacing.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

/** Label / value / control row. The value is mono so numbers line up down the column. */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    value: String? = null,
    control: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = HavenSpacing.touchTarget)
            .padding(vertical = HavenSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (value != null) {
            Spacer(Modifier.width(HavenSpacing.md))
            Text(
                text = value,
                style = HavenTheme.text.figure,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        control()
    }
}

/** Switch row. The whole row is not clickable on purpose — the switch is the target, and a
 *  row-wide tap on a destructive toggle like "clear on disconnect" is too easy to hit. */
@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    SettingRow(title = title, supporting = supporting, modifier = modifier) {
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Slider row with the live value in the label rather than a floating bubble.
 *
 * `onValueChangeFinished` is what commits: dragging a quota slider that writes to DataStore on
 * every frame is a hundred disk writes per gesture.
 */
@Composable
fun SettingSliderRow(
    title: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    steps: Int = 0,
    supporting: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = valueLabel,
                style = HavenTheme.text.figure,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (supporting != null) {
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
            steps = steps,
            modifier = Modifier
                .fillMaxWidth()
                .height(HavenSpacing.touchTarget),
        )
    }
}

/**
 * Destructive confirmation. Red text, never a red fill: a filled red button is the most
 * visually dominant thing on screen, which is backwards for the option you hope nobody picks.
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    color = if (destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
