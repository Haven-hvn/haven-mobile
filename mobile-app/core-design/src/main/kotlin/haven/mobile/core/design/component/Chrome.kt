package haven.mobile.core.design.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import haven.mobile.core.design.HavenSpacing

/**
 * App chrome.
 *
 * One top bar and one navigation bar for the whole product. Before this refactor each screen
 * drew its own header inline, so titles sat at three different sizes — and two of the five
 * screens had no way to navigate anywhere at all.
 *
 * Mobile-native structure on purpose: bottom navigation within thumb reach rather than the
 * web's sticky top nav, `Scaffold` so window insets and snackbars are solved once, and a real
 * back affordance on detail screens instead of a "Back" button at the end of a scrolling list.
 */

/** A destination in the bottom bar. */
data class HavenDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector = icon,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HavenTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
fun HavenNavigationBar(
    destinations: List<HavenDestination>,
    currentRoute: String?,
    onSelect: (HavenDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HorizontalDivider(
            thickness = HavenSpacing.hairline,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
        ) {
            destinations.forEach { destination ->
                val selected = currentRoute == destination.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onSelect(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.icon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(destination.label, maxLines = 1) },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

/**
 * Screen shell. Handles insets, the snackbar host, and the 2dp activity line that reports a
 * background refresh without taking over the content area the way a full-screen spinner does.
 *
 * `content` receives the inset padding rather than having it pre-applied, so a screen can put
 * a `LazyColumn`'s bottom inset into `contentPadding` and keep the list scrolling edge-to-edge
 * under the navigation bar.
 */
@Composable
fun HavenScreen(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState? = null,
    busy: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        topBar = {
            Column {
                topBar()
                if (busy) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        },
        bottomBar = bottomBar,
        snackbarHost = {
            if (snackbarHostState != null) SnackbarHost(hostState = snackbarHostState)
        },
        content = content,
    )
}
