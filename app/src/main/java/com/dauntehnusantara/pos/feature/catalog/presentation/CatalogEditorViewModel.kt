package com.dauntehnusantara.pos.feature.catalog.presentation

import com.dauntehnusantara.pos.feature.catalog.photos.domain.*
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
    val photoCommand:PhotoCommand?=null,val photoBusy:Boolean=false,val photoError:String?=null,
    val pending:CatalogMutation?=null,val waitingThrough:Long?=null,val saved:Boolean=false,
    val completedOperationId:String?=null,val refreshRequired:Boolean=false,val refreshing:Boolean=false,val refreshBaseline:Long?=null) {
    val editable:Boolean get()=(scope==null||scope.role in listOf("OWNER","MANAGER"))&&!refreshRequired&&!refreshing&&!loading&&!offline&&!busy&&!photoBusy&&photoCommand==null&&pending==null&&waitingThrough==null&&pull.initialized&&pull.error==null&&pull.epoch!=null
}
@HiltViewModel
class CatalogEditorViewModel @Inject constructor(private val auth:AuthRepository,private val gateway:ReportingGateway,
    private val observe:ObserveCatalog,private val pull:PullRepository,private val edit:EditCatalog,private val scheduler:SyncScheduler,private val photos:ProductPhotoRepository,private val photoGateway:ProductPhotoGateway,private val processor:ProductPhotoProcessor):ViewModel() {
    val editor = CatalogEditorSession()
    private val mutable=MutableStateFlow(CatalogEditorState());val state=mutable.asStateFlow()
    private var observation:Job?=null;private var action:Job?=null
    private val pendingPhotos=mutableMapOf<String,Triple<PhotoCommand?,ByteArray?,Boolean>>()
    private var pickerTarget:Pair<PullScope,Product>?=null
    private var pickerDelivery:Job?=null
    private var photoBytes:ByteArray?=null
    private var uploaded=false
    private data class Work(val pending:CatalogMutation?,val waiting:Long?,val saved:Boolean,val completed:String?,val refreshRequired:Boolean)
    private val unresolved=mutableMapOf<String,Work>()
    init {viewModelScope.launch {
        auth.state.map{Triple(it.stage,it.membership,it.email to it.offline)}.distinctUntilChanged().collectLatest{(stage,_,_)->
            val previous=state.value
            previous.scope?.let{unresolved[it.key]=Work(previous.pending,previous.waitingThrough,previous.saved,previous.completedOperationId,previous.refreshRequired)}
            previous.scope?.let{pendingPhotos[it.key]=Triple(previous.photoCommand,photoBytes,uploaded)}
            // Remove old-scope display data before waiting for slow/cancelled IO.
            observation?.cancel();action?.cancel()
            mutable.value=CatalogEditorState(offline=auth.state.value.offline)
            if(stage==AuthStage.LOGIN){editor.clear();unresolved.clear();pendingPhotos.clear();pickerTarget=null;pickerDelivery?.cancel()}
            observation?.join();action?.join()
            photoBytes=null;uploaded=false
            val s=if(stage==AuthStage.READY)gateway.reportingScope(false)?.takeIf{it.role in listOf("OWNER","MANAGER")}else null
            s?.let{editor.bindScope(it.key);photoBytes=pendingPhotos[it.key]?.second;uploaded=pendingPhotos[it.key]?.third?:false}
            mutable.value=CatalogEditorState(scope=s,offline=auth.state.value.offline,photoCommand=s?.let{pendingPhotos[it.key]?.first},
                pending=s?.let{unresolved[it.key]?.pending},waitingThrough=s?.let{unresolved[it.key]?.waiting},
                saved=s?.let{unresolved[it.key]?.saved}?:false,completedOperationId=s?.let{unresolved[it.key]?.completed},
                refreshRequired=s?.let{unresolved[it.key]?.refreshRequired}?:false)
            if(s!=null)observe(s)
        }
    }}
    private fun observe(scope:PullScope) {observation=viewModelScope.launch {
        try {combine(observe(scope.sale.brandId),pull.observe(scope)){c,p->c to p}.collect{(c,p)->
            val current=state.value
            val fresh=current.refreshing && p.initialized && p.error==null && p.epoch!=null &&
                p.lastSuccessMs!=null && (current.refreshBaseline==null || p.lastSuccessMs>current.refreshBaseline)
            // Subscribe after the committed pull checkpoint; don't pair an old catalog emission
            // with the new cursor and then label it refreshed.
            val appliedWaiting=current.waitingThrough?.let{p.seq>=it && p.initialized && p.error==null}?:false
            val catalog=if(fresh||appliedWaiting)observe(scope.sale.brandId).first()else c
            updateScope(scope){it.copy(catalog=catalog,pull=p,loading=false,waitingThrough=it.waitingThrough?.takeIf{seq->p.seq<seq||p.error!=null||!p.initialized},
                refreshing=if(fresh)false else it.refreshing,
                refreshRequired=if(fresh)false else it.refreshRequired,
                error=if(fresh && it.error in listOf("CONFLICT","UNAVAILABLE"))null else it.error,
                photoError=if(fresh && it.photoError=="CONFLICT")null else it.photoError)}
        }}catch(e:CancellationException){throw e}catch(_:Exception){updateScope(scope){it.copy(loading=false,refreshing=false,error="UNAVAILABLE")}}
    }}
    private fun updateScope(scope:PullScope,change:(CatalogEditorState)->CatalogEditorState){
        mutable.update{if(it.scope==scope)change(it)else it}
    }
    private suspend fun appliedCatalog(scope:PullScope,through:Long):Catalog? {
        val p=state.value.pull
        return if(p.initialized&&p.error==null&&p.seq>=through)observe(scope.sale.brandId).first()else null
    }
    fun retrySync(){
        val current=state.value
        if(current.busy||current.photoBusy||current.offline)return
        // An unresolved command is retried only through its original immutable command.
        if(current.pending==null&&current.photoCommand==null)mutable.update{it.copy(refreshing=true,refreshBaseline=it.pull.lastSuccessMs)}
        scheduler.enqueue(force=true)
        if(current.error!=null&&current.pending==null)current.scope?.let{observation?.cancel();observe(it)}
    }
    fun save(change:CatalogMutation) {
        if(!state.value.editable||state.value.scope==null)return
        try{change.validate()}catch(_:Exception){mutable.update{it.copy(error="INVALID")};return}
        mutable.update{it.copy(pending=change.copy(groupIds=change.groupIds.toList()),saved=false)};retryCommand()
    }
    fun retryCommand() {
        val s=state.value.scope?:return;val c=state.value.pending?:return;val epoch=state.value.pull.epoch?:return
        if(action?.isActive==true||state.value.offline)return
        action=viewModelScope.launch {
            updateScope(s){it.copy(busy=true,error=null)}
            try {
                val ack=edit(s,epoch,c)
                val applied=appliedCatalog(s,ack.throughSeq)
                updateScope(s){it.copy(catalog=applied?:it.catalog,pending=null,waitingThrough=ack.throughSeq.takeIf{applied==null},saved=true,completedOperationId=c.operationId)}
                scheduler.enqueue(force=true)
            }
            catch(e:CancellationException){throw e}
            catch(e:Exception){val code=(e as? ManagementFailure)?.code?:"UNAVAILABLE"
                if(code=="CONFLICT"&&state.value.scope==s)editor.conflict()
                updateScope(s){it.copy(error=code,refreshRequired=it.refreshRequired||code=="CONFLICT",pending=if(code in listOf("CONFLICT","INVALID","AUTH_REQUIRED","REBOOTSTRAP_REQUIRED"))null else c)}
                scheduler.enqueue(force=true)
            }finally{updateScope(s){it.copy(busy=false)}}
        }
    }
    fun beginPhotoPick(product:Product):Boolean {
        if(!state.value.editable)return false
        pickerTarget=requireNotNull(state.value.scope) to product
        return true
    }
    fun photoPicked(uri:String?) {
        val target=pickerTarget;pickerTarget=null
        if(uri==null||target==null)return
        pickerDelivery?.cancel()
        pickerDelivery=viewModelScope.launch {
            val ready=withTimeoutOrNull(30000){state.first{it.scope==target.first&&it.editable}}
            if(ready!=null)pickPhoto(target.second,uri)
            else if(state.value.scope==target.first)mutable.update{it.copy(photoError="AUTH_REQUIRED")}
        }
    }
    private fun pickPhoto(product:Product,uri:String) {
        if(!state.value.editable)return
        val source=state.value.scope?:return
        action=viewModelScope.launch {
            updateScope(source){it.copy(photoBusy=true,photoError=null)}
            try {
                val bytes=processor.prepare(uri)
                if(state.value.scope!=source)return@launch
                photoBytes=bytes;uploaded=false
                updateScope(source){it.copy(photoCommand=PhotoCommand(productId=product.info.id,expectedVersion=product.imageVersion,
                    path=PhotoRules.newPath(product.info.brandId,product.info.id)))}
            }catch(e:CancellationException){throw e}catch(_:Exception){updateScope(source){it.copy(photoError="INVALID_IMAGE")}}
            finally{updateScope(source){it.copy(photoBusy=false)}}
            if(state.value.photoCommand!=null)sendPhoto()
        }
    }
    fun removePhoto(product:Product) {
        if(!state.value.editable)return
        photoBytes=null;uploaded=true
        mutable.update{it.copy(photoCommand=PhotoCommand(productId=product.info.id,expectedVersion=product.imageVersion,path=null),photoError=null)}
        retryPhoto()
    }
    fun retryPhoto(){if(action?.isActive==true||state.value.offline)return;action=viewModelScope.launch{sendPhoto()}}
    private suspend fun sendPhoto() {
        val s=state.value.scope?:return;val c=state.value.photoCommand?:return;val epoch=state.value.pull.epoch?:return
        updateScope(s){it.copy(photoBusy=true,photoError=null,saved=false)}
        try {
            c.path?.let { if(!uploaded){photos.upload(s,it,requireNotNull(photoBytes));uploaded=true} }
            val ack=photoGateway.setPhoto(s,epoch,c)
            val applied=appliedCatalog(s,ack.throughSeq)
            photoBytes=null;uploaded=false
            updateScope(s){it.copy(catalog=applied?:it.catalog,photoCommand=null,waitingThrough=ack.throughSeq.takeIf{applied==null},saved=true)}
            scheduler.enqueue(force=true)
        }catch(e:CancellationException){throw e}catch(e:Exception){
            val code=(e as? ManagementFailure)?.code?:"UNAVAILABLE"
            val terminal=code in listOf("CONFLICT","INVALID","AUTH_REQUIRED","REBOOTSTRAP_REQUIRED")
            if(terminal){photoBytes=null;uploaded=false}
            updateScope(s){it.copy(photoError=code,refreshRequired=it.refreshRequired||code=="CONFLICT",photoCommand=if(terminal)null else c)}
            scheduler.enqueue(force=true)
        }finally{updateScope(s){it.copy(photoBusy=false)}}
    }

}
