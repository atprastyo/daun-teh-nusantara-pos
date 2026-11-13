package com.dauntehnusantara.pos.feature.pos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class PosError { STORAGE, INVALID, CONFLICT, OVERFLOW }
data class PosUiState(val catalog: Catalog = Catalog(), val checkout: CheckoutState? = null,
    val loading: Boolean = true, val loadFailed: Boolean = false, val busy: Boolean = false,
    val error: PosError? = null, val history: List<Receipt> = emptyList())

@HiltViewModel
class PosViewModel @Inject constructor(private val observeCatalog: ObserveCatalog,
    private val observeCheckout: ObserveCheckout, private val edit: EditCart,
    private val complete: CompleteCheckout) : ViewModel() {
    private val mutableState = MutableStateFlow(PosUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var action: Job? = null
    init { retry() }
    fun retry() {
        observation?.cancel()
        observation = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, loadFailed = false) }
            try {
                combine(observeCatalog(DEMO_BRAND_ID), observeCheckout()) { catalog, checkout -> catalog to checkout }
                    .collect { (catalog, checkout) ->
                        val history = complete.receipts()
                        mutableState.update { it.copy(catalog = catalog, checkout = checkout, loading = false, loadFailed = false, history = history) }
                    }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, loadFailed = true) } }
        }
    }
    private fun mutate(block: suspend (Draft) -> Unit) {
        if (action?.isActive == true) return
        val draft = mutableState.value.checkout?.draft ?: return
        action = viewModelScope.launch {
            mutableState.update { it.copy(busy = true, error = null) }
            try {
                block(draft)
                val checkout = observeCheckout().first()
                val history = complete.receipts()
                mutableState.update { it.copy(checkout = checkout, history = history) }
            } catch (e: CancellationException) { throw e }
            catch (_: CheckoutConflict) { mutableState.update { it.copy(error = PosError.CONFLICT) } }
            catch (_: ArithmeticException) { mutableState.update { it.copy(error = PosError.OVERFLOW) } }
            catch (_: IllegalArgumentException) { mutableState.update { it.copy(error = PosError.INVALID) } }
            catch (_: Exception) { mutableState.update { it.copy(error = PosError.STORAGE) } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
    fun add(productId: String, options: List<String>, quantity: Int) {
        val catalog = mutableState.value.catalog
        mutate { draft -> edit.add(draft, CheckoutRules.line(catalog, productId, options, quantity)) }
    }
    fun quantity(line: CartLine, quantity: Int) = mutate { edit.quantity(it, line.id, quantity) }
    fun remove(line: CartLine) = mutate { edit.remove(it, line.id) }
    fun pay(payment: PaymentInput) = mutate { draft -> complete(CheckoutCommand(draft.id, draft.operationId, draft.revision, payment)) }
    fun nextSale() {
        val receipt = mutableState.value.checkout?.receipt ?: return
        mutate { complete.dismiss(receipt.id) }
    }
    fun clearError() { mutableState.update { it.copy(error = null) } }
}
