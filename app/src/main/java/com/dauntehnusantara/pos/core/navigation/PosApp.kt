package com.dauntehnusantara.pos.core.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.feature.pos.presentation.*
import com.dauntehnusantara.pos.feature.catalog.presentation.CatalogRoute
import com.dauntehnusantara.pos.feature.catalog.presentation.CatalogViewModel
import com.dauntehnusantara.pos.core.designsystem.*
import com.dauntehnusantara.pos.feature.foundation.presentation.*

private enum class Destination(val route: String, @param:StringRes val label: Int, val icon: ImageVector) {
    Cashier("cashier", R.string.cashier, Icons.Default.ShoppingCart),
    History("history", R.string.history, Icons.Default.DateRange),
    Reports("reports", R.string.reports, Icons.Default.Info),
    Settings("settings", R.string.settings, Icons.Default.Settings),
}

@Composable
fun PosApp(viewModel: FoundationViewModel, catalogViewModel: CatalogViewModel, posViewModel: PosViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PosShell(state, viewModel::retry,
        cashierContent = { layout -> PosRoute(posViewModel, layout == WindowLayout.Expanded) },
        historyContent = { LocalHistoryRoute(posViewModel) },
        catalogContent = { onBack -> CatalogRoute(catalogViewModel, onBack) })
}

@Composable
fun PosShell(storageState: StorageState, onRetry: () -> Unit, cashierContent: (@Composable (WindowLayout) -> Unit)? = null, historyContent: (@Composable () -> Unit)? = null, catalogContent: (@Composable (() -> Unit) -> Unit)? = null) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val current = (entry?.destination?.route ?: Destination.Cashier.route).let { if (it == "catalog") Destination.Settings.route else it }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val layout = windowLayout(maxWidth.value.toInt())
        val compact = layout == WindowLayout.Compact
        val navigate: (Destination) -> Unit = { destination ->
            controller.navigate(destination.route) {
                popUpTo(controller.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        Scaffold(
            bottomBar = {
                if (compact) NavigationBar {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = current == destination.route,
                            onClick = { navigate(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
            },
        ) { padding ->
            Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                if (!compact) NavigationRail {
                    Destination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = current == destination.route,
                            onClick = { navigate(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.label)) },
                        )
                    }
                }
                NavHost(controller, startDestination = Destination.Cashier.route, modifier = Modifier.weight(1f)) {
                    composable("catalog") { catalogContent?.invoke { controller.popBackStack() } }
                    Destination.entries.forEach { destination ->
                        composable(destination.route) {
                            when {
                                destination == Destination.Cashier && cashierContent != null -> cashierContent(layout)
                                destination == Destination.History && historyContent != null -> historyContent()
                                else -> PlaceholderScreen(destination.label, storageState, onRetry, Modifier.fillMaxSize(),
                                    if (destination == Destination.Settings && catalogContent != null) ({ controller.navigate("catalog") { launchSingleTop = true } }) else null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(@StringRes title: Int, state: StorageState, onRetry: () -> Unit, modifier: Modifier = Modifier, onCatalog: (() -> Unit)? = null) {
    Column(modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
        Text(stringResource(R.string.foundation), style = MaterialTheme.typography.labelLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    StorageState.Loading -> {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.local_loading))
                    }
                    StorageState.Ready -> Text(stringResource(R.string.local_ready))
                    StorageState.Error -> {
                        Text(stringResource(R.string.local_error))
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                }
                Text(stringResource(R.string.offline_note))
            }
        }
        if (onCatalog != null) Button(onClick = onCatalog) { Text(stringResource(R.string.catalog_title)) }
        Text(stringResource(R.string.placeholder))
    }
}

@Preview(name = "Phone", widthDp = 393, heightDp = 852, showBackground = true)
@Preview(name = "Tablet", widthDp = 1280, heightDp = 800, showBackground = true)
@Preview(name = "Split screen", widthDp = 700, heightDp = 800, showBackground = true)
@Preview(name = "Large text", widthDp = 393, heightDp = 852, fontScale = 1.5f)
@Composable
private fun FoundationPreview() {
    DaunTehTheme { PosShell(StorageState.Ready, {}) }
}
