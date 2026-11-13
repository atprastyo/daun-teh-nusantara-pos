package com.dauntehnusantara.pos.feature.pos.presentation

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.formatIdr
import com.dauntehnusantara.pos.feature.auth.domain.AccessDestination

@Composable
fun AuthenticatedPosRoute(model: AuthenticatedPosViewModel, destination: AccessDestination, expanded: Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    when (destination) {
        AccessDestination.POS -> PosScreen(state, PosActions(model::retry, model::add, model::quantity, model::remove,
            model::pay, model::nextSale, model::clearError), expanded, demoEnabled = true, demo = false)
        AccessDestination.HISTORY -> HistoryContent(state, model::retry, demo = false)
        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.headlineMedium) }
            if (state.loading) item { CircularProgressIndicator() }
            if (state.loadFailed || state.pullFailed || state.syncRequired) item {
                Text(stringResource(if (state.recoveryRequired) R.string.recovery_required else R.string.pull_initial_required)); Button(onClick = model::retry) { Text(stringResource(R.string.sync_retry)) }
            }
            if (!state.loading && state.catalog.visibleProducts().isEmpty()) item { Text(stringResource(R.string.pos_real_empty)) }
            items(state.catalog.visibleProducts(), key = { it.info.id }) { product ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                    Text(product.info.name, style = MaterialTheme.typography.titleMedium); Text(formatIdr(product.basePriceIdr))
                    state.catalog.groupsFor(product.info.id).forEach { group ->
                        Text(group.info.name)
                        state.catalog.options.filter { it.groupId == group.info.id && it.info.active }.forEach { Text("${it.info.name} · ${formatIdr(it.priceDeltaIdr)}") }
                    }
                } }
            }
        }
    }
}
