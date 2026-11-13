package com.dauntehnusantara.pos.feature.pos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class AuthenticatedPosViewModel @Inject constructor(private val auth: AuthRepository, private val gateway: PullGateway,
    private val factory: AuthenticatedCheckoutFactory, private val catalog: ObserveCatalog, private val pull: PullRepository,
    private val scheduler: SyncScheduler) : ViewModel() {
    private val mutable = MutableStateFlow(PosUiState())
    val state = mutable.asStateFlow()
    private var repository: CheckoutRepository? = null
    private var scope: PullScope? = null
    private var observation: Job? = null
    private var action: Job? = null
    init {
        viewModelScope.launch {
            auth.state.map { Triple(it.stage, it.membership, it.email) }.distinctUntilChanged().collectLatest { (stage, _, _) ->
                observation?.cancel(); action?.cancel(); repository = null; scope = null; mutable.value = PosUiState()
                if (stage == AuthStage.READY) {
                    scope = gateway.pullScope()
                    scope?.let { s -> repository = factory.open(s); observe(s) }
                }
            }
        }
    }
    private fun observe(s: PullScope) {
        val checkout = repository ?: return
        observation?.cancel()
        observation = viewModelScope.launch {
            try {
                combine(catalog(s.sale.brandId), checkout.observe(), pull.observe(s)) { menu, cart, status -> Triple(menu, cart, status) }
                    .collect { (menu, cart, status) ->
                        val history = checkout.receipts()
                        mutable.update { it.copy(catalog = menu, checkout = cart, loading = false, loadFailed = false,
                            syncRequired = !status.initialized, pullFailed = status.error != null, recoveryRequired = status.error == "REBOOTSTRAP_REQUIRED", history = history) }
                    }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.update { it.copy(loading = false, loadFailed = true) } }
        }
    }
    fun retry() { scheduler.enqueue(force = true); scope?.let { observe(it) } }
    private fun mutate(block: suspend (CheckoutRepository, Draft) -> Unit) {
        if (action?.isActive == true) return
        val checkout = repository ?: return
        val draft = state.value.checkout?.draft ?: return
        action = viewModelScope.launch {
            mutable.update { it.copy(busy = true, error = null) }
            try { block(checkout, draft) }
            catch (e: CancellationException) { throw e }
            catch (_: CheckoutConflict) { mutable.update { it.copy(error = PosError.CONFLICT) } }
            catch (_: ArithmeticException) { mutable.update { it.copy(error = PosError.OVERFLOW) } }
            catch (_: IllegalArgumentException) { mutable.update { it.copy(error = PosError.INVALID) } }
            catch (_: Exception) { mutable.update { it.copy(error = PosError.STORAGE) } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun add(id: String, options: List<String>, quantity: Int) = mutate { r, d -> r.add(d, CheckoutRules.line(state.value.catalog, id, options, quantity)) }
    fun quantity(line: CartLine, quantity: Int) = mutate { r, d -> r.quantity(d, line.id, quantity) }
    fun remove(line: CartLine) = mutate { r, d -> r.remove(d, line.id) }
    fun pay(payment: PaymentInput) = mutate { r, d -> r.complete(CheckoutCommand(d.id, d.operationId, d.revision, payment)) }
    fun nextSale() { state.value.checkout?.receipt?.let { receipt -> mutate { r, _ -> r.dismissReceipt(receipt.id) } } }
    fun clearError() { mutable.update { it.copy(error = null) } }
}
