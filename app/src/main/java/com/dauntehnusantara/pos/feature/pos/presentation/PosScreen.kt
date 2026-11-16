package com.dauntehnusantara.pos.feature.pos.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.BuildConfig
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.*
import com.dauntehnusantara.pos.core.designsystem.components.AppTextField
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.photos.presentation.ProductPhoto
import com.dauntehnusantara.pos.feature.checkout.domain.*
import java.util.Locale

data class PosActions(val retry: () -> Unit = {}, val add: (String, List<String>, Int) -> Unit = { _, _, _ -> },
    val quantity: (CartLine, Int) -> Unit = { _, _ -> }, val remove: (CartLine) -> Unit = {},
    val pay: (PaymentInput) -> Unit = {}, val next: () -> Unit = {}, val clearError: () -> Unit = {},
    val reconcile: () -> Unit = {}, val retryLocal: () -> Unit = {}, val retryDelivery: () -> Unit = {})

@Composable
fun PosRoute(viewModel: PosViewModel, expanded: Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PosScreen(state, PosActions(viewModel::retry, viewModel::add, viewModel::quantity, viewModel::remove,
        viewModel::pay, viewModel::nextSale, viewModel::clearError), expanded)
}

@Composable
fun PosScreen(state: PosUiState, actions: PosActions, expanded: Boolean = false, demoEnabled: Boolean = BuildConfig.DEBUG, demo: Boolean = true) {
    if (!demoEnabled) { Text(stringResource(R.string.pos_release_disabled), Modifier.padding(24.dp)); return }
    key(state.scopeKey) { PosContent(state, actions, expanded, demo) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosContent(state: PosUiState, actions: PosActions, expanded: Boolean, demo: Boolean) {
    var productId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    var checkout by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingAdd by rememberSaveable { mutableStateOf(false) }
    var addBaseline by rememberSaveable { mutableLongStateOf(0) }
    val busyNow by rememberUpdatedState(state.busy)
    val selectionSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busyNow })
    val draft = state.checkout?.draft
    val receipt = state.checkout?.receipt
    LaunchedEffect(state.added, state.busy, state.error) {
        if(awaitingAdd && state.added > addBaseline) { productId=null; awaitingAdd=false }
        else if(awaitingAdd && !state.busy && state.error!=null) awaitingAdd=false
    }
    // A locally observed receipt remains readable when recovery blocks new writes.
    if(receipt!=null && state.pending==null) {
        ReceiptContent(receipt, Modifier.fillMaxSize(), onNext = { checkout=false; showCart=false; actions.next() },
            busy=state.busy || state.syncRequired || state.recoveryRequired || state.loadFailed, error=state.error, demo=demo,
            delivery=state.checkout.deliveries[receipt.id], onRetryDelivery=actions.retryDelivery,
            notice=if(state.syncRequired||state.recoveryRequired) stringResource(if(state.recoveryRequired) R.string.recovery_required else R.string.pull_initial_required) else null)
        return
    }
    if(state.pending!=null) { UncertainCheckout(state, actions); return }
    if(state.loadFailed || state.loading || state.syncRequired || state.recoveryRequired) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("Kasir",style=MaterialTheme.typography.headlineMedium)
            if(state.loading) CircularProgressIndicator()
            Text(stringResource(when {
                state.recoveryRequired -> R.string.recovery_required
                state.syncRequired -> R.string.pull_initial_required
                state.loadFailed -> R.string.local_error
                else -> R.string.catalog_loading
            }))
            if(!state.loading) Button(onClick=actions.retry){Text(stringResource(R.string.retry))}
        };return
    }
    if(checkout && draft!=null && draft.lines.isNotEmpty()) {
        BackHandler { if(!state.busy) checkout=false }
        CheckoutForm(draft,state.busy,state.error,actions.pay,{checkout=false},demo,state.staleLineIds.isNotEmpty())
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fontScale=LocalDensity.current.fontScale
        val twoPanes=expanded && maxWidth>=900.dp && fontScale<1.5f
        if(showCart && !twoPanes) {
            BackHandler { if(!state.busy)showCart=false }
            Column(Modifier.fillMaxSize()) {
                TextButton(onClick={showCart=false},enabled=!state.busy){Text("Kembali ke menu")}
                PosErrorText(state.error)
                CartContent(draft,state.busy,actions,{checkout=true;showCart=false},Modifier.weight(1f),state.staleLineIds)
            }
        } else Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                LazyVerticalGrid(columns=GridCells.Adaptive(if(fontScale>=1.5f) 230.dp else 156.dp),modifier=Modifier.weight(1f),
                    contentPadding=PaddingValues(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    item(span={GridItemSpan(maxLineSpan)}) {
                        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Text("Kasir",style=MaterialTheme.typography.headlineLarge)
                            if(demo) Text(stringResource(R.string.pos_demo))
                            Text(stringResource(if(demo)R.string.pos_pending else R.string.pos_real_pending,state.checkout?.pendingCount?:0),style=MaterialTheme.typography.bodySmall)
                            if(state.pullFailed) {
                                PosNotice(stringResource(R.string.pull_failed))
                                TextButton(onClick=actions.retry,enabled=!state.busy){Text(stringResource(R.string.sync_retry))}
                            }
                            PosErrorText(state.error)
                            if(state.error!=null)TextButton(onClick=actions.clearError){Text(stringResource(R.string.pos_dismiss_error))}
                            AppTextField(query,{query=it},label="Cari minuman",modifier=Modifier.fillMaxWidth().testTag("pos.search"),
                                leadingIcon={Icon(Icons.Default.Search,contentDescription=null)})
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected=categoryId==null,onClick={categoryId=null},label={Text("Semua")})
                                state.catalog.categories.filter{it.info.active}.sortedBy{it.sortOrder}.forEach { category ->
                                    FilterChip(selected=categoryId==category.info.id,onClick={categoryId=category.info.id},label={Text(category.info.name)})
                                }
                            }
                            if(state.catalog.visibleProducts().isEmpty())Text(stringResource(if(demo)R.string.pos_empty else R.string.pos_real_empty))
                            else if(filteredProducts(state.catalog,query,categoryId).isEmpty()) {
                                Text("Tidak ada hasil yang cocok.");TextButton(onClick={query="";categoryId=null}){Text("Hapus pencarian/filter")}
                            }
                        }
                    }
                    items(filteredProducts(state.catalog,query,categoryId),key={it.info.id}) { product ->
                        // The photo retry remains separate from the select button; no nested click targets.
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            ProductPhoto(product.imagePath,product.info.name,Modifier.fillMaxWidth())
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(product.info.name,style=MaterialTheme.typography.titleMedium)
                                Text("Harga dasar",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatIdr(product.basePriceIdr),style=MaterialTheme.typography.titleMedium)
                                FilledTonalButton(onClick={actions.clearError();productId=product.info.id},enabled=state.canEdit&&draft!=null,
                                    modifier=Modifier.fillMaxWidth().testTag("pos.product.${product.info.id}")) { Text("Pilih minuman") }
                            }
                        }
                    }
                }
                if(!twoPanes) Surface(color=MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Button(onClick={showCart=true},enabled=draft!=null&&!state.busy,modifier=Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(stringResource(R.string.pos_cart_total,draft?.lines?.sumOf{it.quantity}?:0,formatIdr(draft?.totalIdr?:0)))
                    }
                }
            }
            if(twoPanes) Surface(Modifier.width(340.dp).fillMaxHeight().padding(12.dp),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerHighest) {
                CartContent(draft,state.busy,actions,{checkout=true;showCart=false},Modifier.fillMaxSize(),state.staleLineIds)
            }
        }
        state.catalog.visibleProducts().find{it.info.id==productId}?.let { product ->
            ModalBottomSheet(onDismissRequest={if(!state.busy){productId=null;awaitingAdd=false}},sheetState=selectionSheet) {
                ModifierPicker(state.catalog,product,state.busy,state.error,draft) { options,quantity ->
                    if(!awaitingAdd){addBaseline=state.added;awaitingAdd=true;actions.add(product.info.id,options,quantity)}
                }
            }
        }
    }
}

internal fun filteredProducts(catalog:Catalog,query:String,categoryId:String?):List<Product> = catalog.visibleProducts().filter {
    (categoryId==null||it.categoryId==categoryId)&&it.info.name.lowercase(Locale.forLanguageTag("id")).contains(query.trim().lowercase(Locale.forLanguageTag("id")))
}

@Composable
internal fun CartContent(draft:Draft?,busy:Boolean,actions:PosActions,onCheckout:()->Unit,modifier:Modifier,stale:Set<String>) {
    Column(modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Keranjang",style=MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(draft==null||draft.lines.isEmpty())item{Text(stringResource(R.string.cart_empty))}
            items(draft?.lines.orEmpty(),key={it.id}){line ->
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(line.productName,style=MaterialTheme.typography.titleMedium)
                    if(line.modifiers.isNotEmpty())Text(line.modifiers.joinToString{"${it.groupName}: ${it.optionName}"},style=MaterialTheme.typography.bodySmall)
                    Text("${line.quantity} × ${formatIdr(line.unitPriceIdr)}",style=MaterialTheme.typography.bodyMedium)
                    Text(formatIdr(line.totalIdr),style=MaterialTheme.typography.titleMedium)
                    if(line.id in stale)Text("Katalog berubah. Hapus lalu pilih ulang.",color=MaterialTheme.colorScheme.error)
                    Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick={actions.quantity(line,line.quantity-1)},enabled=!busy&&line.quantity>1,
                            modifier=Modifier.testTag("pos.minus.${line.id}").semanticsLabel("Kurangi ${line.productName}"),contentPadding=PaddingValues(12.dp)){Text("−")}
                        OutlinedButton(onClick={actions.quantity(line,line.quantity+1)},enabled=!busy&&line.quantity<CheckoutRules.MAX_QUANTITY,
                            modifier=Modifier.semanticsLabel("Tambah jumlah ${line.productName}"),contentPadding=PaddingValues(12.dp)){Text("+")}
                        TextButton(onClick={actions.remove(line)},enabled=!busy){Text("Hapus",color=MaterialTheme.colorScheme.error)}
                    }
                    HorizontalDivider()
                }
            }
        }
        Text(stringResource(R.string.pos_total,formatIdr(draft?.totalIdr?:0)),style=MaterialTheme.typography.headlineSmall)
        Button(onClick=onCheckout,enabled=!busy&&draft!=null&&draft.lines.isNotEmpty()&&stale.isEmpty(),modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.pos_checkout))}
    }
}
@Composable
fun LocalHistoryRoute(viewModel: PosViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!BuildConfig.DEBUG) { Text(stringResource(R.string.pos_release_disabled), Modifier.padding(24.dp)); return }
    HistoryContent(state, viewModel::retry)
}
@Composable
fun HistoryContent(state: PosUiState, retry: () -> Unit, demo: Boolean = true) {
    key(state.scopeKey) {
        val session = remember { com.dauntehnusantara.pos.feature.history.presentation.HistorySession() }
        com.dauntehnusantara.pos.feature.history.presentation.HistoryScreen(
            com.dauntehnusantara.pos.feature.history.presentation.HistoryState(
                scopeKey=state.scopeKey,receipts=state.history,deliveries=state.checkout?.deliveries.orEmpty(),
                loading=state.loading,loadFailed=state.loadFailed,allowed=true,demo=demo,
                pull=com.dauntehnusantara.pos.feature.sync.domain.PullStatus(initialized=!state.syncRequired,
                    error=if(state.recoveryRequired) "REBOOTSTRAP_REQUIRED" else if(state.pullFailed) "UNAVAILABLE" else null)),
            session,retry)
    }
}

@Preview(name = "POS phone", widthDp = 393, heightDp = 852)
@Composable private fun PhonePreview() { DaunTehTheme { PosScreen(PosUiState(loading = false), PosActions()) } }
@Preview(name = "POS tablet", widthDp = 1100, heightDp = 800)
@Composable private fun TabletPreview() { DaunTehTheme { PosScreen(PosUiState(loading = false), PosActions(), expanded = true) } }
