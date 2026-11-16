package com.dauntehnusantara.pos.feature.auth.presentation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.activity.compose.BackHandler
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
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
import com.dauntehnusantara.pos.core.designsystem.components.AppConfirmation
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
            val menu = compact && destinations.size > 5 || LocalDensity.current.fontScale >= 1.5f || maxHeight < 480.dp
            val drawer = rememberDrawerState(DrawerValue.Closed)
            val scope = rememberCoroutineScope()
            BackHandler(enabled=menu&&drawer.isOpen){scope.launch{drawer.close()}}
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
                        DrawerIdentity(state)
                        HorizontalDivider()
                        destinations.forEach { destination ->
                            NavigationDrawerItem(label = { Text(stringResource(destination.shellLabel())) },
                                selected = selected == destination, icon = { DestinationIcon(destination) },
                                onClick = { selectedName = destination.name; scope.launch { drawer.close() } },
                                colors = NavigationDrawerItemDefaults.colors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer))
                        }
                    }
                }
            }, content = shell)
        }
    }
}

@Composable
private fun DrawerIdentity(state:AuthState) {
    val membership=requireNotNull(state.membership)
    Column(Modifier.fillMaxWidth().padding(16.dp).testTag("shell.identity"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Image(painterResource(R.drawable.brand_logo),null,Modifier.size(64.dp).testTag("shell.brand.logo"),
                colorFilter=if(MaterialTheme.colorScheme.surface.luminance()<0.5f)ColorFilter.tint(Color.White)else null)
            Text(membership.brandName,Modifier.weight(1f).semantics{heading()},style=MaterialTheme.typography.titleLarge)
        }
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.shell_active_account),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.email.isNotBlank())Text(state.email,Modifier.testTag("shell.user.email"),style=MaterialTheme.typography.bodyMedium)
            Text(stringResource(membership.role.label()),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Text(membership.outletName,style=MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ShellContextHeader(state: AuthState, switchOutlet: () -> Unit, openMenu: (() -> Unit)?) {
    val membership = requireNotNull(state.membership)
    val colors=MaterialTheme.colorScheme
    val dark=colors.surface.luminance()<0.5f
    val foreground=if(dark)colors.onPrimaryContainer else colors.onPrimary
    Surface(color=if(dark)colors.primaryContainer else colors.primary,contentColor=foreground) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp).testTag("shell.appbar"),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            if(openMenu!=null)IconButton(onClick=openMenu){Icon(painterResource(R.drawable.ic_shell_menu),stringResource(R.string.shell_open_menu))}
            else Icon(painterResource(R.drawable.ic_shell_store),null)
            val switchLabel=stringResource(R.string.auth_switch_outlet)
            val roleLabel=stringResource(membership.role.label())
            Column(Modifier.weight(1f).heightIn(min=48.dp).testTag("shell.outlet.selector")
                .clickable(enabled=!state.offline&&!state.busy,onClickLabel=switchLabel,role=androidx.compose.ui.semantics.Role.Button,onClick=switchOutlet)
                .semantics{contentDescription=switchLabel;stateDescription="${membership.outletName} · $roleLabel"},
                verticalArrangement=Arrangement.Center) {
                Text(membership.outletName,Modifier.testTag("shell.outlet.name"),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(roleLabel,style=MaterialTheme.typography.labelLarge,color=foreground.copy(alpha=0.85f))
            }
            AccessIndicator(state)
        }
    }
}

@Composable
private fun AccessIndicator(state:AuthState) {
    var details by remember{mutableStateOf(false)}
    val label=stringResource(when{state.offline->R.string.shell_access_offline;state.busy->R.string.shell_access_checking;else->R.string.shell_access_validated})
    IconButton(onClick={details=true},modifier=Modifier.testTag("shell.access").semantics{contentDescription=label}) {
        if(state.busy&&!state.offline)CircularProgressIndicator(Modifier.size(20.dp),color=LocalContentColor.current,strokeWidth=2.dp)
        else Icon(painterResource(if(state.offline)R.drawable.ic_shell_offline else R.drawable.ic_shell_verified),null,
            tint=LocalContentColor.current)
    }
    if(details)AppConfirmation(label,{details=false},actions={
        TextButton(onClick={details=false}){Text(stringResource(R.string.shell_close_status))}
    }) {
        Text(stringResource(if(state.offline)R.string.auth_offline_limits else R.string.shell_access_help))
        if(state.offline)Text(stringResource(R.string.shell_switch_online))
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
