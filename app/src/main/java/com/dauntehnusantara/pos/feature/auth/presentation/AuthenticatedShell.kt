package com.dauntehnusantara.pos.feature.auth.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.feature.auth.domain.*
import kotlinx.coroutines.launch

val LocalShellNavigation = staticCompositionLocalOf<(AccessDestination)->Unit> { {} }

/** Presentation only: the shell cannot authorize a role, outlet, sale or sync result. */
@Composable
fun AuthenticatedShell(state: AuthState, switchOutlet: () -> Unit,
    content: @Composable (AccessDestination, Boolean) -> Unit) {
    val membership = state.membership
    if (state.stage != AuthStage.READY || membership == null) return
    key(state.email, membership.brandId, membership.outletId, membership.role.name) {
        val destinations = RoleNavigation.destinations(membership.role)
        var selectedName by rememberSaveable { mutableStateOf(destinations.first().name) }
        // A restored value is presentation state, never permission to render a route.
        val selected = destinations.firstOrNull { it.name == selectedName } ?: destinations.first()
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val compact = maxWidth < 600.dp
            val expanded = maxWidth >= 840.dp
            val menu = LocalDensity.current.fontScale >= 1.5f || maxHeight < 480.dp
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            val shell: @Composable () -> Unit = {
                Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
                    if (compact && !menu) NavigationBar(Modifier.testTag("shell.bottom"), containerColor = MaterialTheme.colorScheme.surface) {
                        destinations.forEach { destination ->
                            NavigationBarItem(selected = selected == destination, onClick = { selectedName = destination.name },
                                icon = { DestinationIcon(destination) }, label = { Text(stringResource(destination.shellLabel())) },
                                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
                        }
                    }
                }) { padding ->
                    Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                        if (!compact && !menu) NavigationRail(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).testTag("shell.rail"),
                            containerColor = MaterialTheme.colorScheme.surface) {
                            destinations.forEach { destination ->
                                NavigationRailItem(selected = selected == destination, onClick = { selectedName = destination.name },
                                    icon = { DestinationIcon(destination) }, label = { Text(stringResource(destination.shellLabel())) },
                                    colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
                            }
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            ShellContextHeader(state, switchOutlet, if (menu) ({ scope.launch { drawer.open() }; Unit }) else null)
                            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            if (state.message != null) Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { AuthMessageText(state.message) }
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                CompositionLocalProvider(LocalShellNavigation provides { target ->
                                    if(target in destinations) selectedName=target.name
                                }) { content(selected, expanded) }
                            }
                        }
                    }
                }
            }
            // Keep destination content at the same composition location when window/IME
            // or font scale changes navigation form; do not recreate in-progress fields.
            LaunchedEffect(menu) { if (!menu) drawer.close() }
            ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = menu, drawerContent = {
                if (menu) ModalDrawerSheet(Modifier.widthIn(max = 360.dp).fillMaxWidth(0.9f).testTag("shell.drawer")) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.shell_navigation), Modifier.padding(16.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        destinations.forEach { destination ->
                            NavigationDrawerItem(label = { Text(stringResource(destination.shellLabel())) },
                                selected = selected == destination, icon = { DestinationIcon(destination) },
                                onClick = { selectedName = destination.name; scope.launch { drawer.close() } },
                                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
                        }
                        TextButton(onClick = { scope.launch { drawer.close() } }) { Text(stringResource(R.string.shell_close_menu)) }
                    }
                }
            }, content = shell)
        }
    }
}

@Composable
private fun ShellContextHeader(state: AuthState, switchOutlet: () -> Unit, openMenu: (() -> Unit)?) {
    val membership = requireNotNull(state.membership)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (openMenu != null) IconButton(onClick = openMenu) { Icon(painterResource(R.drawable.ic_shell_menu), stringResource(R.string.shell_open_menu)) }
                else Icon(painterResource(R.drawable.ic_shell_store), null, tint = MaterialTheme.colorScheme.primary)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(membership.outletName, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(membership.role.label()), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = switchOutlet, enabled = !state.offline && !state.busy) {
                    Icon(painterResource(R.drawable.ic_shell_switch_outlet), stringResource(R.string.auth_switch_outlet))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(if (state.offline) R.drawable.ic_shell_offline else R.drawable.ic_shell_verified), null,
                    modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(if (state.offline) R.string.shell_access_offline else R.string.shell_access_validated),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.offline) Text(stringResource(R.string.shell_switch_online), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DestinationIcon(destination: AccessDestination) {
    Icon(painterResource(destination.shellIcon()), null)
}

@StringRes
internal fun AccessDestination.shellLabel(): Int = when (this) {
    AccessDestination.OVERVIEW -> R.string.auth_overview
    AccessDestination.POS -> R.string.cashier
    AccessDestination.HISTORY -> R.string.history
    AccessDestination.CATALOG -> R.string.shell_catalog
    AccessDestination.REPORTS -> R.string.reports
    AccessDestination.STAFF -> R.string.auth_staff
    AccessDestination.SETTINGS -> R.string.shell_settings
}

@DrawableRes
internal fun AccessDestination.shellIcon(): Int = when (this) {
    AccessDestination.OVERVIEW -> R.drawable.ic_shell_dashboard
    AccessDestination.POS -> R.drawable.ic_shell_point_of_sale
    AccessDestination.HISTORY -> R.drawable.ic_shell_receipt
    AccessDestination.CATALOG -> R.drawable.ic_shell_cafe
    AccessDestination.REPORTS -> R.drawable.ic_shell_chart
    AccessDestination.STAFF -> R.drawable.ic_shell_group
    AccessDestination.SETTINGS -> R.drawable.ic_shell_settings
}
