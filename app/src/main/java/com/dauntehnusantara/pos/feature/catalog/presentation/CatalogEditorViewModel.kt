package com.dauntehnusantara.pos.feature.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class CatalogEditorState(val scope:PullScope?=null,val catalog:Catalog=Catalog(),val pull:PullStatus=PullStatus(),
    val loading:Boolean=true,val offline:Boolean=false,val busy:Boolean=false,val error:String?=null,
    val pending:CatalogMutation?=null,val waitingThrough:Long?=null,val saved:Boolean=false) {
    val editable:Boolean get()=!loading&&!offline&&!busy&&pending==null&&waitingThrough==null&&pull.initialized&&pull.error==null&&pull.epoch!=null
}
@HiltViewModel
class CatalogEditorViewModel @Inject constructor(private val auth:AuthRepository,private val gateway:ReportingGateway,
    private val observe:ObserveCatalog,private val pull:PullRepository,private val edit:EditCatalog,private val scheduler:SyncScheduler):ViewModel() {
    private val mutable=MutableStateFlow(CatalogEditorState());val state=mutable.asStateFlow()
    private var observation:Job?=null;private var action:Job?=null
    private val unresolved=mutableMapOf<String,Pair<CatalogMutation?,Long?>>()
    init {viewModelScope.launch {
        auth.state.map{Triple(it.stage,it.membership,it.email to it.offline)}.distinctUntilChanged().collectLatest{(stage,_,_)->
            observation?.cancel();action?.cancel()
            val previous=state.value
            previous.scope?.let{unresolved[it.key]=previous.pending to previous.waitingThrough}
            if(stage==AuthStage.LOGIN)unresolved.clear()
            val s=if(stage==AuthStage.READY)gateway.reportingScope(false)else null
            mutable.value=CatalogEditorState(scope=s,offline=auth.state.value.offline,
                pending=s?.let{unresolved[it.key]?.first},waitingThrough=s?.let{unresolved[it.key]?.second})
            if(s!=null)observe(s)
        }
    }}
    private fun observe(scope:PullScope) {observation=viewModelScope.launch {
        try {combine(observe(scope.sale.brandId),pull.observe(scope)){c,p->c to p}.collect{(c,p)->
            mutable.update{it.copy(catalog=c,pull=p,loading=false,waitingThrough=it.waitingThrough?.takeIf{seq->p.seq<seq})}
        }}catch(e:CancellationException){throw e}catch(_:Exception){mutable.update{it.copy(loading=false,error="UNAVAILABLE")}}
    }}
    fun retrySync(){scheduler.enqueue(force=true);if(state.value.error!=null&&state.value.pending==null)state.value.scope?.let{observation?.cancel();observe(it)}}
    fun save(change:CatalogMutation) {
        if(!state.value.editable)return
        try{change.validate()}catch(_:Exception){mutable.update{it.copy(error="INVALID")};return}
        mutable.update{it.copy(pending=change,saved=false)};retryCommand()
    }
    fun retryCommand() {
        val s=state.value.scope?:return;val c=state.value.pending?:return;val epoch=state.value.pull.epoch?:return
        if(action?.isActive==true||state.value.offline)return
        action=viewModelScope.launch {
            mutable.update{it.copy(busy=true,error=null)}
            try {val ack=edit(s,epoch,c);mutable.update{it.copy(pending=null,waitingThrough=ack.throughSeq.takeIf{seq->it.pull.seq<seq},saved=true)};scheduler.enqueue(force=true)}
            catch(e:CancellationException){throw e}
            catch(e:Exception){val code=(e as? ManagementFailure)?.code?:"UNAVAILABLE"
                mutable.update{it.copy(error=code,pending=if(code in listOf("CONFLICT","INVALID","AUTH_REQUIRED","REBOOTSTRAP_REQUIRED"))null else c)}
                scheduler.enqueue(force=true)
            }finally{mutable.update{it.copy(busy=false)}}
        }
    }
}
