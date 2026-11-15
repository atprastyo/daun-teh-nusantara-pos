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
    private val scheduler: SyncScheduler, private val sync: SyncRepository) : ViewModel() {
    private val mutable = MutableStateFlow(PosUiState(scopeKey = "locked"))
    val state = mutable.asStateFlow()
    private var repository: CheckoutRepository? = null
    private var scope: PullScope? = null
    private var generation = 0L
    private var observationVersion = 0L
    private var observation: Job? = null
    private var action: Job? = null
    init {
        viewModelScope.launch {
            auth.state.map { Triple(it.stage, it.membership, it.email) }.distinctUntilChanged().onEach {
                // Clear visible old-scope data before cancelled IO can finish. A revisited scope
                // gets a fresh generation too, so late callbacks cannot re-enter it.
                generation++
                observation?.cancel(); action?.cancel(); repository = null; scope = null
                mutable.value = PosUiState(scopeKey = "locked-$generation")
            }.collectLatest { context ->
                val token = generation
                if (context.first == AuthStage.READY) {
                    try {
                        val s = gateway.pullScope()
                        if (s != null && generation == token &&
                            auth.state.value.let { Triple(it.stage, it.membership, it.email) } == context) {
                            scope = s; repository = factory.open(s)
                            mutable.value = PosUiState(scopeKey = "${s.key}|$generation")
                            observe(s)
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { if(generation == token) mutable.update { it.copy(loading = false, loadFailed = true) } }
                }
            }
        }
    }
    private fun current(token: Long, r: CheckoutRepository) = generation == token && repository === r
    private fun observe(s: PullScope) {
        val checkout = repository ?: return
        val token = generation
        observation?.cancel()
        val observing = ++observationVersion
        observation = viewModelScope.launch {
            try {
                combine(catalog(s.sale.brandId), checkout.observe(), pull.observe(s)) { menu, cart, status -> Triple(menu, cart, status) }
                    .collect { (menu, cart, status) ->
                        val history = checkout.receipts()
                        if (current(token, checkout) && observationVersion == observing) mutable.update { it.copy(catalog = menu, checkout = cart, loading = false, loadFailed = false,
                            syncRequired = !status.initialized, pullFailed = status.error != null,
                            recoveryRequired = status.error == "REBOOTSTRAP_REQUIRED", history = history) }
                    }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { if (current(token, checkout) && observationVersion == observing) mutable.update { it.copy(loading = false, loadFailed = true) } }
        }
    }
    fun retry() {
        runCatching { scheduler.enqueue(force = true) }
        scope?.let { observe(it) }
    }
    private fun error(e: Exception) = when(e) {
        is CheckoutConflict -> PosError.CONFLICT
        is ArithmeticException -> PosError.OVERFLOW
        is IllegalArgumentException -> PosError.INVALID
        else -> PosError.STORAGE
    }
    private fun edit(block: suspend (CheckoutRepository, Draft) -> CartLine?) {
        if (action?.isActive == true || !state.value.canEdit) return
        val r = repository ?: return
        val draft = state.value.checkout?.draft ?: return
        val token = generation
        mutable.update { it.copy(busy = true, error = null) }
        action = viewModelScope.launch {
            try {
                val added = block(r, draft)
                val cart = r.observe().first()
                check(added == null || cart.draft.lines.any { it.id == added.id })
                if (current(token, r)) {
                    mutable.update { it.copy(checkout = cart, added = it.added + if(added != null) 1 else 0) }
                    scope?.let(::observe)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (current(token, r)) mutable.update { it.copy(error = error(e)) } }
            finally { if (current(token, r)) mutable.update { it.copy(busy = false) } }
        }
    }
    fun add(id: String, options: List<String>, quantity: Int) {
        val menu = state.value.catalog
        edit { r, d ->
            val line = CheckoutRules.line(menu, id, options, quantity)
            try { r.add(d, line) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // An observation error after an edit need not mean the line was absent.
                if (r.observe().first().draft.lines.none { it.id == line.id }) throw e
            }
            line
        }
    }
    fun quantity(line: CartLine, quantity: Int) = edit { r, d -> r.quantity(d, line.id, quantity); null }
    fun remove(line: CartLine) = edit { r, d -> r.remove(d, line.id); null }
    fun pay(payment: PaymentInput) {
        if (action?.isActive == true || !state.value.canEdit || state.value.staleLineIds.isNotEmpty()) return
        val draft = state.value.checkout?.draft ?: return
        val command = CheckoutCommand(draft.id, draft.operationId, draft.revision, payment)
        try { CheckoutRules.validateDraft(draft); CheckoutRules.payment(draft.totalIdr, payment) }
        catch (e: Exception) { mutable.update { it.copy(error = error(e)) }; return }
        mutable.update { it.copy(pending = command, pendingDraft = draft, retryLocalAllowed = false) }
        submit(command)
    }
    private fun submit(command: CheckoutCommand) {
        val r = repository ?: return
        val token = generation
        mutable.update { it.copy(busy = true, error = null, retryLocalAllowed = false) }
        action = viewModelScope.launch {
            try {
                r.complete(command)
                val cart = r.observe().first()
                check(cart.receipt?.operationId == command.operationId && cart.receipt.id == command.draftId)
                if (current(token, r)) {
                    mutable.update { it.copy(checkout = cart, pending = null, pendingDraft = null) }
                    scope?.let(::observe)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (current(token, r)) {
                    // Even typed conflicts may occur after persistence in a downstream step.
                    // Read back first; only a proven unsaved command can be retried.
                    resolve(r, command, token, error(e), definitive = e is CheckoutConflict || e is IllegalArgumentException || e is ArithmeticException)
                }
            } finally { if (current(token, r)) mutable.update { it.copy(busy = false) } }
        }
    }
    private suspend fun resolve(r: CheckoutRepository, command: CheckoutCommand, token: Long, error: PosError = PosError.STORAGE, definitive: Boolean = false) {
        val resolution = try { r.reconcile(command) } catch(e: CancellationException) { throw e } catch(_: Exception) { CheckoutResolution.Unknown }
        if (!current(token, r)) return
        when (resolution) {
            is CheckoutResolution.Saved -> {
                val cart = mutable.value.checkout ?: return
                mutable.update { it.copy(checkout = cart.copy(receipt = resolution.receipt), pending = null, pendingDraft = null, retryLocalAllowed = false, error = null) }
                scope?.let(::observe)
            }
            CheckoutResolution.Unsaved -> mutable.update { it.copy(pending = if(definitive) null else command,
                pendingDraft = if(definitive) null else it.pendingDraft, retryLocalAllowed = !definitive && !it.syncRequired && !it.recoveryRequired, error = error) }
            CheckoutResolution.Unknown -> mutable.update { it.copy(error = PosError.STORAGE, retryLocalAllowed = false) }
        }
    }
    fun reconcile() {
        if (action?.isActive == true) return
        val command = state.value.pending ?: return
        val r = repository ?: return
        val token = generation
        mutable.update { it.copy(busy = true, retryLocalAllowed = false) }
        action = viewModelScope.launch {
            try { resolve(r, command, token) }
            finally { if (current(token, r)) mutable.update { it.copy(busy = false) } }
        }
    }
    fun retryLocal() {
        if (action?.isActive == true || !state.value.retryLocalAllowed || state.value.syncRequired || state.value.recoveryRequired) return
        state.value.pending?.let(::submit)
    }
    fun nextSale() {
        if (action?.isActive == true || state.value.busy || state.value.pending != null || state.value.syncRequired || state.value.recoveryRequired) return
        val r = repository ?: return
        val receipt = state.value.checkout?.receipt ?: return
        val token = generation
        mutable.update { it.copy(busy = true, error = null) }
        action = viewModelScope.launch {
            try {
                r.dismissReceipt(receipt.id)
                val cart = r.observe().first()
                if(current(token,r)) { mutable.update { it.copy(checkout = cart) }; scope?.let(::observe) }
            } catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(current(token,r)) mutable.update { it.copy(error = error(e)) } }
            finally { if(current(token,r)) mutable.update { it.copy(busy = false) } }
        }
    }
    fun retryDelivery() {
        if(action?.isActive == true || state.value.pending != null) return
        val r=repository ?: return
        val s=scope ?: return
        val token=generation
        mutable.update { it.copy(busy=true, error=null) }
        action=viewModelScope.launch {
            try { sync.retry(s.sale) }
            catch(e:CancellationException){throw e}
            catch(e:Exception){if(current(token,r))mutable.update{it.copy(error=error(e))}}
            finally{if(current(token,r))mutable.update{it.copy(busy=false)}}
        }
    }
    fun clearError() { if(state.value.pending == null) mutable.update { it.copy(error = null) } }
}
