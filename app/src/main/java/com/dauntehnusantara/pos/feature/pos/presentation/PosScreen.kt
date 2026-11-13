package com.dauntehnusantara.pos.feature.pos.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.BuildConfig
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*

data class PosActions(val retry: () -> Unit = {}, val add: (String, List<String>, Int) -> Unit = { _, _, _ -> },
    val quantity: (CartLine, Int) -> Unit = { _, _ -> }, val remove: (CartLine) -> Unit = {},
    val pay: (PaymentInput) -> Unit = {}, val next: () -> Unit = {}, val clearError: () -> Unit = {})

@Composable
fun PosRoute(viewModel: PosViewModel, expanded: Boolean) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PosScreen(state, PosActions(viewModel::retry, viewModel::add, viewModel::quantity, viewModel::remove,
        viewModel::pay, viewModel::nextSale, viewModel::clearError), expanded)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosScreen(state: PosUiState, actions: PosActions, expanded: Boolean = false, demoEnabled: Boolean = BuildConfig.DEBUG) {
    var productId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCart by rememberSaveable { mutableStateOf(false) }
    var checkout by rememberSaveable { mutableStateOf(false) }
    val draft = state.checkout?.draft
    if (!demoEnabled) { Text(stringResource(R.string.pos_release_disabled), Modifier.padding(24.dp)); return }
    if (state.loadFailed) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.local_error))
            Button(onClick = actions.retry) { Text(stringResource(R.string.retry)) }
        }
        return
    }
    if (state.loading) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(); Text(stringResource(R.string.catalog_loading))
        }
        return
    }
    val receipt = state.checkout?.receipt
    if (receipt != null) {
        ReceiptContent(receipt, Modifier.fillMaxSize(), onNext = { checkout = false; showCart = false; actions.next() }, busy = state.busy, error = state.error)
        return
    }
    if (checkout && draft != null && draft.lines.isNotEmpty()) {
        BackHandler { if (!state.busy) checkout = false }
        CheckoutForm(draft, state.busy, state.error, actions.pay, { checkout = false })
        return
    }
    val cart: @Composable () -> Unit = {
        CartContent(draft, state.busy, actions, { showCart = false; checkout = true }, Modifier.fillMaxSize())
    }
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            LazyVerticalGrid(columns = GridCells.Adaptive(160.dp), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.cashier), style = MaterialTheme.typography.headlineLarge)
                        Text(stringResource(R.string.pos_demo))
                        Text(stringResource(R.string.pos_pending, state.checkout?.pendingCount ?: 0))
                        PosErrorText(state.error)
                        if (state.error != null) TextButton(onClick = actions.clearError) { Text(stringResource(R.string.pos_dismiss_error)) }
                        if (state.catalog.visibleProducts().isEmpty()) Text(stringResource(R.string.pos_empty))
                    }
                }
                if (!state.loading && !state.loadFailed) items(state.catalog.visibleProducts(), key = { it.info.id }) { product ->
                    OutlinedCard(onClick = { productId = product.info.id }, enabled = !state.busy && draft != null) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(product.info.name, style = MaterialTheme.typography.titleMedium)
                            Text(formatIdr(product.basePriceIdr))
                            Text(stringResource(R.string.pos_choose))
                        }
                    }
                }
            }
            if (!expanded) Surface(tonalElevation = 3.dp) {
                Button(onClick = { showCart = true }, enabled = draft != null && !state.busy,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Text(stringResource(R.string.pos_cart_total, draft?.lines?.sumOf { it.quantity } ?: 0, formatIdr(draft?.totalIdr ?: 0)))
                }
            }
        }
        if (expanded) OutlinedCard(Modifier.width(320.dp).fillMaxHeight().padding(8.dp)) { cart() }
    }
    if (showCart && !expanded) ModalBottomSheet(onDismissRequest = { showCart = false }) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(0.9f)) { cart() }
    }
    state.catalog.visibleProducts().find { it.info.id == productId }?.let { product ->
        ModalBottomSheet(onDismissRequest = { productId = null }) {
            ModifierPicker(state.catalog, product, state.busy) { options, quantity ->
                actions.add(product.info.id, options, quantity); productId = null
            }
        }
    }
}

@Composable
private fun CartContent(draft: Draft?, busy: Boolean, actions: PosActions, onCheckout: () -> Unit, modifier: Modifier) {
    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.cart), style = MaterialTheme.typography.headlineSmall)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (draft == null || draft.lines.isEmpty()) item { Text(stringResource(R.string.cart_empty)) }
            items(draft?.lines ?: emptyList(), key = { it.id }) { line ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(line.productName, style = MaterialTheme.typography.titleMedium)
                    Text(line.modifiers.joinToString { "${it.groupName}: ${it.optionName}" })
                    Text(stringResource(R.string.pos_line_price, line.quantity, formatIdr(line.unitPriceIdr), formatIdr(line.totalIdr)))
                    Row {
                        TextButton(onClick = { actions.quantity(line, line.quantity - 1) }, enabled = !busy && line.quantity > 1) { Text(stringResource(R.string.pos_minus)) }
                        TextButton(onClick = { actions.quantity(line, line.quantity + 1) }, enabled = !busy && line.quantity < CheckoutRules.MAX_QUANTITY) { Text(stringResource(R.string.pos_plus)) }
                        TextButton(onClick = { actions.remove(line) }, enabled = !busy) { Text(stringResource(R.string.pos_remove)) }
                    }
                    HorizontalDivider()
                }
            }
        }
        Text(stringResource(R.string.pos_total, formatIdr(draft?.totalIdr ?: 0)), style = MaterialTheme.typography.titleLarge)
        Button(onClick = onCheckout, enabled = !busy && draft != null && draft.lines.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pos_checkout))
        }
    }
}

@Composable
private fun ModifierPicker(catalog: Catalog, product: Product, busy: Boolean, onAdd: (List<String>, Int) -> Unit) {
    var selected by rememberSaveable(product.info.id) { mutableStateOf(emptyList<String>()) }
    var quantityText by rememberSaveable(product.info.id) { mutableStateOf("1") }
    val quantity = quantityText.toIntOrNull()
    val line = runCatching { CheckoutRules.line(catalog, product.info.id, selected, quantity ?: 0) }.getOrNull()
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(product.info.name, style = MaterialTheme.typography.headlineSmall)
            catalog.groupsFor(product.info.id).forEach { group ->
                Text(group.info.name, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.catalog_limits, group.minSelect, group.maxSelect))
                catalog.options.filter { it.groupId == group.info.id && it.info.active }.forEach { option ->
                    val checked = option.info.id in selected
                    Row(Modifier.fillMaxWidth().toggleable(value = checked, enabled = !busy, role = Role.Checkbox, onValueChange = { _ ->
                        selected = if (checked) selected - option.info.id else {
                            val base = if (group.maxSelect == 1) selected.filterNot { id -> catalog.options.any { it.info.id == id && it.groupId == group.info.id } } else selected
                            base + option.info.id
                        }
                    }).padding(vertical = 8.dp)) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Text(stringResource(R.string.catalog_option, option.info.name, formatIdr(option.priceDeltaIdr)), Modifier.padding(start = 8.dp))
                    }
                }
            }
            OutlinedTextField(quantityText, onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) quantityText = it },
                label = { Text(stringResource(R.string.pos_quantity)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !busy)
            if (line == null) Text(stringResource(R.string.pos_selection_invalid), color = MaterialTheme.colorScheme.error)
            else Text(stringResource(R.string.pos_total, formatIdr(line.totalIdr)))
        }
        Button(onClick = { onAdd(selected, requireNotNull(quantity)) }, enabled = line != null && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.pos_add))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CheckoutForm(draft: Draft, busy: Boolean, error: PosError?, onPay: (PaymentInput) -> Unit, onBack: () -> Unit) {
    var methodName by rememberSaveable(draft.id) { mutableStateOf(PaymentMethod.CASH.name) }
    var cashText by rememberSaveable(draft.id) { mutableStateOf("") }
    var verified by rememberSaveable(draft.id) { mutableStateOf(false) }
    val method = PaymentMethod.valueOf(methodName)
    val input = PaymentInput(method, if (method == PaymentMethod.CASH) cashText.toLongOrNull() else null, verified)
    val result = runCatching { CheckoutRules.payment(draft.totalIdr, input) }.getOrNull()
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text(stringResource(R.string.pos_back_cart)) }
        Text(stringResource(R.string.pos_checkout), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.pos_total, formatIdr(draft.totalIdr)), style = MaterialTheme.typography.titleLarge)
        PaymentMethod.entries.forEach { choice ->
            Row(Modifier.fillMaxWidth().selectable(selected = method == choice, enabled = !busy, role = Role.RadioButton) { methodName = choice.name }.padding(vertical = 8.dp)) {
                RadioButton(selected = method == choice, onClick = null)
                Text(stringResource(if (choice == PaymentMethod.CASH) R.string.pos_cash else R.string.pos_qris), Modifier.padding(start = 12.dp))
            }
        }
        if (method == PaymentMethod.CASH) {
            OutlinedTextField(cashText, onValueChange = { if (it.length <= 19 && it.all(Char::isDigit)) cashText = it }, enabled = !busy,
                label = { Text(stringResource(R.string.pos_tendered)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = cashText.isNotEmpty() && result == null)
            if (result == null) Text(stringResource(R.string.pos_cash_invalid))
            else Text(stringResource(R.string.pos_change, formatIdr(requireNotNull(result.changeIdr))))
        } else {
            Text(stringResource(R.string.pos_qris_notice))
            Row(Modifier.fillMaxWidth().toggleable(value = verified, enabled = !busy, role = Role.Checkbox, onValueChange = { verified = it }).padding(vertical = 8.dp)) {
                Checkbox(checked = verified, onCheckedChange = null)
                Text(stringResource(R.string.pos_qris_confirm), Modifier.padding(start = 8.dp))
            }
        }
        PosErrorText(error)
        Button(onClick = { onPay(input) }, enabled = result != null && !busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (busy) R.string.pos_saving else R.string.pos_save))
        }
        Text(stringResource(R.string.pos_demo))
    }
}

@Composable
private fun PosErrorText(error: PosError?) {
    if (error != null) Text(stringResource(when (error) {
        PosError.CONFLICT -> R.string.pos_conflict
        PosError.INVALID -> R.string.pos_invalid
        PosError.OVERFLOW -> R.string.pos_overflow
        PosError.STORAGE -> R.string.pos_storage_error
    }), color = MaterialTheme.colorScheme.error, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
fun ReceiptContent(receipt: Receipt, modifier: Modifier = Modifier, onNext: (() -> Unit)? = null, busy: Boolean = false, error: PosError? = null) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PosErrorText(error)
        Text(stringResource(R.string.pos_receipt), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.pos_local_saved))
        Text(receipt.id)
        Text(formatJakarta(receipt.createdAtUtcMs))
        receipt.lines.forEach { line ->
            Text(line.productName, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pos_line_price, line.quantity, formatIdr(line.unitPriceIdr), formatIdr(line.totalIdr)))
            line.modifiers.forEach { Text(stringResource(R.string.pos_receipt_modifier, it.groupName, it.optionName, formatIdr(it.priceDeltaIdr))) }
        }
        HorizontalDivider()
        Text(stringResource(R.string.pos_total, formatIdr(receipt.totalIdr)), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(if (receipt.payment.method == PaymentMethod.CASH) R.string.pos_cash else R.string.pos_qris))
        receipt.payment.tenderedIdr?.let { Text(stringResource(R.string.pos_paid, formatIdr(it))) }
        receipt.payment.changeIdr?.let { Text(stringResource(R.string.pos_change, formatIdr(it))) }
        if (receipt.payment.method == PaymentMethod.QRIS_MANUAL) Text(stringResource(R.string.pos_qris_manual_receipt))
        Text(stringResource(R.string.pos_demo))
        if (onNext != null) Button(onClick = onNext, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pos_next)) }
    }
}

@Composable
fun LocalHistoryRoute(viewModel: PosViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!BuildConfig.DEBUG) { Text(stringResource(R.string.pos_release_disabled), Modifier.padding(24.dp)); return }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text(stringResource(R.string.history), style = MaterialTheme.typography.headlineLarge) }
        item { Text(stringResource(R.string.pos_local_history)) }
        if (state.loading) item { CircularProgressIndicator() }
        if (state.loadFailed) item {
            Text(stringResource(R.string.local_error))
            Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
        }
        if (!state.loading && !state.loadFailed && state.history.isEmpty()) item { Text(stringResource(R.string.pos_no_receipts)) }
        items(state.history, key = { it.id }) { receipt ->
            OutlinedCard(onClick = { selected = receipt.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formatJakarta(receipt.createdAtUtcMs)); Text(formatIdr(receipt.totalIdr)); Text(receipt.id)
                }
            }
        }
    }
    state.history.find { it.id == selected }?.let { receipt ->
        AlertDialog(onDismissRequest = { selected = null }, confirmButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.catalog_close)) } },
            text = { ReceiptContent(receipt, Modifier.heightIn(max = 500.dp)) })
    }
}

@Preview(name = "POS phone", widthDp = 393, heightDp = 852)
@Composable private fun PhonePreview() { DaunTehTheme { PosScreen(PosUiState(loading = false), PosActions()) } }
@Preview(name = "POS tablet", widthDp = 1100, heightDp = 800)
@Composable private fun TabletPreview() { DaunTehTheme { PosScreen(PosUiState(loading = false), PosActions(), expanded = true) } }
