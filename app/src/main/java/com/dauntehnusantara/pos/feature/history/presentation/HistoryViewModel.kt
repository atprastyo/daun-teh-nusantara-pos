package com.dauntehnusantara.pos.feature.history.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class HistoryViewModel @Inject constructor(private val auth: AuthRepository, private val gateway: PullGateway,
    private val factory: AuthenticatedCheckoutFactory, private val pull: PullRepository,
    private val scheduler: SyncScheduler, private val clock: Clock) : ViewModel() {
    private val mutable = MutableStateFlow(HistoryState(today=historyToday(clock)))
    val state = mutable.asStateFlow()
    val session = HistorySession()
    private val reopen = MutableStateFlow(0L)
    private var repository: CheckoutRepository? = null
    private var scope: PullScope? = null
    private var generation = 0L
    private var watchVersion = 0L
    private var observation: Job? = null
    private var refreshJob: Job? = null
    init {
        viewModelScope.launch {
            auth.state.map { Triple(it.stage,it.membership,it.email) }.distinctUntilChanged().combine(reopen){context,_->context}.onEach {
                generation++; watchVersion++
                observation?.cancel(); refreshJob?.cancel(); repository=null; scope=null
                session.clear()
                mutable.value=HistoryState(scopeKey="locked-$generation",today=historyToday(clock),offline=auth.state.value.offline,loading=false)
            }.collectLatest { context ->
                val token=generation
                if(context.first==AuthStage.READY && context.second?.role==Role.CASHIER) {
                    mutable.update{it.copy(loading=true)}
                    try {
                        val s=gateway.pullScope()
                        if(s!=null && s.role==Role.CASHIER.name && generation==token &&
                            auth.state.value.let{Triple(it.stage,it.membership,it.email)}==context) {
                            scope=s; repository=factory.open(s)
                            mutable.value=HistoryState(scopeKey="${s.key}|$token",allowed=true,today=historyToday(clock),offline=auth.state.value.offline)
                            observe(s,requireNotNull(repository))
                        } else if(generation==token)mutable.update{it.copy(loading=false,loadFailed=true)}
                    } catch(e:CancellationException){throw e}
                    catch(_:Exception){if(generation==token)mutable.update{it.copy(loading=false,loadFailed=true)}}
                }
            }
        }
        viewModelScope.launch { auth.state.map{it.offline}.distinctUntilChanged().collect { offline -> mutable.update{it.copy(offline=offline)} } }
        viewModelScope.launch {
            while(isActive) {
                delay(historyTickDelay(clock))
                mutable.update{it.copy(today=historyToday(clock))}
                session.reconcile(mutable.value)
            }
        }
    }
    private fun current(token:Long,r:CheckoutRepository)=generation==token && repository===r
    private suspend fun validate(s:PullScope,r:CheckoutRepository,token:Long):Boolean {
        if(!current(token,r))return false
        val valid=try{gateway.pullScope()==s}catch(e:CancellationException){throw e}catch(_:Exception){false}
        if(!current(token,r))return false
        if(!valid) {
            generation++;watchVersion++;observation?.cancel()
            repository=null;scope=null;session.clear()
            mutable.value=HistoryState(scopeKey="locked-$generation",loading=false,loadFailed=true,today=historyToday(clock))
        }
        return valid
    }
    private fun publish(cart:CheckoutState,receipts:List<Receipt>,status:PullStatus) {
        mutable.update{it.copy(receipts=receipts,deliveries=cart.deliveries,pull=status,loading=false,loadFailed=false,today=historyToday(clock))}
        session.reconcile(mutable.value)
    }
    private fun observe(s:PullScope,r:CheckoutRepository) {
        val token=generation;val watch=++watchVersion
        observation?.cancel()
        observation=viewModelScope.launch {
            try {
                combine(r.observe(),pull.observe(s)){cart,status->cart to status}.collect { (cart,status) ->
                    val receipts=r.receipts()
                    if(current(token,r)&&watchVersion==watch&&validate(s,r,token))publish(cart,receipts,status)
                }
            } catch(e:CancellationException){throw e}
            catch(_:Exception){if(current(token,r)&&watchVersion==watch&&validate(s,r,token))mutable.update{it.copy(loading=false,loadFailed=true)}}
        }
    }
    private suspend fun read(s:PullScope,r:CheckoutRepository,token:Long):PullStatus {
        if(!validate(s,r,token))throw CancellationException("History scope changed")
        val cart=r.observe().first()
        val receipts=r.receipts()
        val status=pull.observe(s).first()
        if(!validate(s,r,token))throw CancellationException("History scope changed")
        if(current(token,r))publish(cart,receipts,status)
        return status
    }
    fun refresh() {
        if(repository==null) { reopen.update{it+1};return }
        if(refreshJob?.isActive==true || !state.value.allowed)return
        val r=repository?:return;val s=scope?:return;val token=generation
        // Stop older same-scope reads before publishing the request's fresh snapshot.
        watchVersion++;observation?.cancel()
        mutable.update{it.copy(refreshing=true,refresh=HistoryRefresh.NONE)}
        refreshJob=viewModelScope.launch {
            var localRead=false
            try {
                val localStatus=read(s,r,token);localRead=true
                if(!current(token,r))return@launch
                if(auth.state.value.offline || localStatus.error=="REBOOTSTRAP_REQUIRED") {
                    mutable.update{it.copy(refresh=HistoryRefresh.LOCAL)}
                } else {
                    check(gateway.pullScope()==s)
                    // Await actual bounded pull completion, not a scheduling callback or old timestamp.
                    val more=pull.sync()
                    if(!current(token,r))return@launch
                    val result=read(s,r,token)
                    val outcome=when {
                        result.error!=null -> HistoryRefresh.FAILED
                        more -> HistoryRefresh.MORE
                        result.initialized && result.lastSuccessMs!=null -> HistoryRefresh.PULL
                        else -> HistoryRefresh.FAILED
                    }
                    mutable.update{it.copy(refresh=outcome)}
                    if(more)runCatching{scheduler.enqueue()}
                }
            } catch(e:CancellationException){throw e}
            catch(_:Exception){if(current(token,r)&&validate(s,r,token))mutable.update{it.copy(loading=false,loadFailed=it.loadFailed||!localRead,refresh=HistoryRefresh.FAILED)}}
            finally {
                if(current(token,r)) {
                    mutable.update{it.copy(refreshing=false)}
                    observe(s,r)
                }
            }
        }
    }
}
