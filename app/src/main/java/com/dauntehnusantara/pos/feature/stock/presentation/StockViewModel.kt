package com.dauntehnusantara.pos.feature.stock.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.stock.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StockState(val scope:PullScope?=null,val outlet:String="",val query:StockQuery=StockQuery(),val snapshot:StockSnapshot?=null,
    val pending:StockCommand?=null,val busy:Boolean=false,val offline:Boolean=false,val stale:Boolean=true,val error:String?=null,val saved:Boolean=false)
@HiltViewModel
class StockViewModel @Inject constructor(private val auth:AuthRepository,private val access:ReportingGateway,private val repo:StockRepository):ViewModel() {
    private val mutable=MutableStateFlow(StockState());val state=mutable.asStateFlow()
    private var generation=0L
    private var action:Job?=null
    init { viewModelScope.launch {
        auth.state.map{listOf(it.stage,it.membership,it.email,it.offline)}.distinctUntilChanged().collectLatest {
            val token=++generation;action?.cancel();mutable.value=StockState(offline=auth.state.value.offline)
            val a=auth.state.value
            if(a.stage==AuthStage.READY && a.membership?.role in listOf(Role.OWNER,Role.MANAGER)) {
                try {
                    val scope=access.reportingScope(false)?:return@collectLatest
                    if(token!=generation)return@collectLatest
                    mutable.value=StockState(scope=scope,outlet=a.membership?.outletName.orEmpty(),offline=a.offline,busy=true)
                    val pending=repo.pending(scope);val cached=repo.cached(scope,StockQuery())
                    if(token!=generation)return@collectLatest
                    mutable.update{it.copy(pending=pending,snapshot=cached,busy=false)}
                    if(!a.offline)load(scope,StockQuery(),token)
                }catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation)mutable.update{it.copy(busy=false,error=code(e))}}
            }
        }
    } }
    private fun code(e:Exception)=(e as? ManagementFailure)?.code?:"UNAVAILABLE"
    private suspend fun load(s:PullScope,q:StockQuery,token:Long) {
        if(token!=generation)return
        mutable.update{it.copy(busy=true,error=null,query=q,snapshot=if(it.query==q)it.snapshot else null,stale=true)}
        try {
            val cached=repo.cached(s,q)
            if(token==generation && cached!=null)mutable.update{it.copy(snapshot=cached)}
            val result=repo.refresh(s,q)
            if(token==generation)mutable.update{it.copy(snapshot=result,stale=false)}
        } catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation)mutable.update{it.copy(error=code(e))}}
        finally{if(token==generation)mutable.update{it.copy(busy=false)}}
    }
    fun refresh(query:StockQuery=state.value.query) {
        val current=state.value;val scope=current.scope?:return
        if(current.busy||current.offline)return
        val token=generation;mutable.update{it.copy(busy=true)};action=viewModelScope.launch{load(scope,query,token)}
    }
    fun save(command:StockCommand) {
        val current=state.value;val scope=current.scope?:return
        if(current.busy||current.offline||current.stale||current.pending!=null||auth.state.value.busy)return
        val snapshot=current.snapshot?:return
        if(command.epoch!=snapshot.epoch)return
        if(command.saleOperationId!=null) {
            val review=snapshot.reviews.find{it.operationId==command.saleOperationId&&it.productId==command.productId}?:return
            if(review.revision!=command.expectedRevision||review.quantity!=command.quantity||command.deduct&&review.balance==null)return
        } else {
            val product=snapshot.products.find{it.id==command.productId}?:return
            if(!product.active||command.expectedRevision!=product.revision)return
        }
        val token=generation
        mutable.update{it.copy(busy=true,error=null,saved=false)}
        action=viewModelScope.launch {
            try { repo.prepare(scope,command);if(token==generation)mutable.update{it.copy(pending=command)};send(scope,token) }
            catch(e:CancellationException){throw e}catch(e:Exception){if(token==generation)mutable.update{it.copy(busy=false,error=code(e))}}
        }
    }
    fun retryPending() {
        val current=state.value;val scope=current.scope?:return
        if(current.busy||current.offline||current.pending==null||auth.state.value.busy)return
        val token=generation;mutable.update{it.copy(busy=true,error=null,saved=false)}
        action=viewModelScope.launch{send(scope,token)}
    }
    private suspend fun send(scope:PullScope,token:Long) {
        try {
            repo.submit(scope)
            if(token!=generation)return
            mutable.update{it.copy(pending=null,saved=true,stale=true)}
            load(scope,state.value.query.copy(before=null),token)
        }catch(e:CancellationException){throw e}catch(e:Exception){
            if(token==generation){
                val pending=try{repo.pending(scope)}catch(_:Exception){state.value.pending}
                mutable.update{it.copy(pending=pending,busy=false,stale=true,error=code(e))}
            }
        }
    }
}
