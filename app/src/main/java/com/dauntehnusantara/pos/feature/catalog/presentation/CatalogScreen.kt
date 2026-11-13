package com.dauntehnusantara.pos.feature.catalog.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.BuildConfig
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.core.designsystem.formatIdr

@Composable
fun CatalogRoute(viewModel: CatalogViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CatalogScreen(state, viewModel::retry, viewModel::seedDemo, onBack)
}

@Composable
fun CatalogScreen(state: CatalogUiState, onRetry: () -> Unit, onSeed: () -> Unit, onBack: () -> Unit, demoEnabled: Boolean = BuildConfig.DEBUG) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val catalog = state.catalog ?: Catalog()
    val products = catalog.visibleProducts()
    LazyVerticalGrid(columns = GridCells.Adaptive(180.dp), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onBack) { Text(stringResource(R.string.catalog_back)) }
                Text(stringResource(R.string.catalog_title), style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.catalog_demo_notice))
                when {
                    state.loading -> { CircularProgressIndicator(); Text(stringResource(R.string.catalog_loading)) }
                    state.failed -> {
                        Text(stringResource(R.string.catalog_error))
                        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                    }
                    products.isEmpty() -> Text(stringResource(R.string.catalog_empty))
                }
                if (demoEnabled && !state.loading && !state.failed) {
                    Button(onClick = onSeed, enabled = !state.seeding) {
                        Text(stringResource(if (state.seeding) R.string.catalog_seeding else R.string.catalog_seed))
                    }
                }
                if (state.seedFailed) Text(stringResource(R.string.catalog_seed_error), color = MaterialTheme.colorScheme.error)
            }
        }
        if (!state.loading && !state.failed) items(products, key = { it.info.id }) { product ->
            OutlinedCard(Modifier.fillMaxWidth().clickable { selectedId = product.info.id }) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(product.info.name, style = MaterialTheme.typography.titleMedium)
                    Text(catalog.categories.first { it.info.id == product.categoryId }.info.name)
                    Text(formatIdr(product.basePriceIdr), color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.catalog_details), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
    products.find { it.info.id == selectedId }?.let { product ->
        AlertDialog(onDismissRequest = { selectedId = null },
            title = { Text(product.info.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.catalog_base_price, formatIdr(product.basePriceIdr)))
                    val groups = catalog.groupsFor(product.info.id)
                    if (groups.isEmpty()) Text(stringResource(R.string.catalog_no_modifiers))
                    groups.forEach { group ->
                        Text(group.info.name, style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.catalog_limits, group.minSelect, group.maxSelect))
                        catalog.options.filter { it.groupId == group.info.id && it.info.active }.forEach { option ->
                            Text(stringResource(R.string.catalog_option, option.info.name, formatIdr(option.priceDeltaIdr)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { selectedId = null }) { Text(stringResource(R.string.catalog_close)) } },
        )
    }
}

@Preview(name = "Catalog phone", widthDp = 393, heightDp = 852)
@Preview(name = "Catalog tablet", widthDp = 1100, heightDp = 800)
@Composable
private fun EmptyCatalogPreview() {
    DaunTehTheme { CatalogScreen(CatalogUiState(catalog = Catalog(), loading = false), {}, {}, {}) }
}
