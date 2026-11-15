package com.dauntehnusantara.pos.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.recovery.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure
import com.dauntehnusantara.pos.feature.sync.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class RecoveryPhase { NONE, CAPTURE, PICKER, WRITING }
data class RecoveryTicket(val id:String,val filename:String,val capturedAtMs:Long)
data class SettingsState(val scope:PullScope?=null,val email:String="",val outletName:String="",val offline:Boolean=false,
    val leaseExpiresMs:Long?=null,val authBusy:Boolean=false,val loading:Boolean=true,val error:String?=null,
    val phase:RecoveryPhase=RecoveryPhase.NONE,val ticket:RecoveryTicket?=null,val exportDone:Boolean=false,
    val exportConfirm:Boolean=false,val signOutConfirm:Boolean=false,val signingOut:Boolean=false,val generation:Long=0) {
    val busy:Boolean get()=phase!=RecoveryPhase.NONE||authBusy||signingOut
}
@HiltViewModel
class SettingsViewModel @Inject constructor(private val auth:AuthRepository,private val sale:SaleGateway,
    private val pull:PullGateway,private val export:ExportRecovery):ViewModel() {
    private val mutable=MutableStateFlow(SettingsState());val state=mutable.asStateFlow()
    private var epoch=0L;private var action:Job?=null
    private val reopen=MutableStateFlow(0L)
    private data class Prepared(val epoch:Long,val scope:PullScope,val ticket:RecoveryTicket,val snapshot:RecoverySnapshot)
    private var prepared:Prepared?=null
    init {
        viewModelScope.launch {
            auth.state.map{Triple(it.stage,it.membership,it.email to it.offline)}.distinctUntilChanged().combine(reopen){context,_->context}
                .onEach{invalidate()}.collectLatest { context->
                    val token=epoch
                    if(context.first==AuthStage.READY)try {
                        val source=currentScope()
                        if(current(token)&&auth.state.value.let{Triple(it.stage,it.membership,it.email to it.offline)}==context)
                            mutable.update{it.copy(scope=source,loading=false,error=if(source==null)"AUTH_REQUIRED" else null)}
                    }catch(e:CancellationException){throw e}catch(_:Exception){if(current(token))mutable.update{it.copy(loading=false,error="UNAVAILABLE")}}
                }
        }
        viewModelScope.launch {auth.state.map{it.busy}.distinctUntilChanged().collect{busy->mutable.update{it.copy(authBusy=busy)}}}
    }
    private fun current(token:Long)=epoch==token
    private fun invalidate() {
        val keepLogout=state.value.signingOut&&auth.state.value.stage==AuthStage.LOGIN
        epoch++;val old=action;prepared=null
        val a=auth.state.value
        mutable.value=SettingsState(email=a.email,outletName=a.membership?.outletName.orEmpty(),offline=a.offline,
            leaseExpiresMs=a.leaseExpiresAtMs,authBusy=a.busy,loading=a.stage==AuthStage.READY,generation=epoch)
        if(!keepLogout)old?.cancel()
    }
    private suspend fun currentScope():PullScope? {
        // Foreground validation may overlap an OS picker result. Wait; never bypass it.
        auth.state.first{!it.busy||it.stage!=AuthStage.READY}
        val a=auth.state.value;val m=a.membership?:return null
        if(a.stage!=AuthStage.READY)return null
        val s=sale.checkoutScope()?:return null
        return pull.pullScope()?.takeIf{it.sale==s&&it.sale.brandId==m.brandId&&it.sale.outletId==m.outletId&&it.role==m.role.name}
    }
    private suspend fun authorized(s:PullScope,token:Long):Boolean {
        if(!current(token))return false
        val valid=currentScope()==s
        if(!current(token))return false
        if(!valid){invalidate();mutable.update{it.copy(scope=null,loading=false,error="AUTH_REQUIRED")}}
        return valid
    }
    fun reload(){if(!state.value.busy)reopen.update{it+1}}
    fun validateAccess(){if(state.value.busy||state.value.offline)return;viewModelScope.launch{auth.refresh()}}
    fun openExport(){if(!state.value.busy&&state.value.scope!=null)mutable.update{it.copy(exportConfirm=true,signOutConfirm=false,exportDone=false,error=null)}}
    fun openSignOut(){if(!state.value.busy&&state.value.scope!=null)mutable.update{it.copy(signOutConfirm=true,exportConfirm=false)}}
    fun cancelDialog(){if(!state.value.busy)mutable.update{it.copy(exportConfirm=false,signOutConfirm=false)}}
    fun prepare() {
        val st=state.value;val source=st.scope?:return
        if(st.busy||!st.exportConfirm)return
        val token=epoch
        mutable.update{it.copy(exportConfirm=false,phase=RecoveryPhase.CAPTURE,error=null,exportDone=false)}
        action=viewModelScope.launch {
            try {
                if(!authorized(source,token))return@launch
                val snapshot=withContext(Dispatchers.IO){export.snapshot(source).also {
                    if(it.raw.toByteArray(Charsets.UTF_8).size>16*1024*1024)throw ManagementFailure("TOO_LARGE")
                }}
                if(!current(token)||!authorized(source,token))return@launch
                val ticket=RecoveryTicket(UUID.randomUUID().toString(),"daun-teh-recovery-${snapshot.capturedAtMs}.json",snapshot.capturedAtMs)
                prepared=Prepared(token,source,ticket,snapshot)
                mutable.update{it.copy(ticket=ticket,phase=RecoveryPhase.PICKER)}
            }catch(e:CancellationException){throw e}catch(e:Exception){if(current(token)) {
                val code=(e as? ManagementFailure)?.code?:"EXPORT_FAILED"
                if(code=="AUTH_REQUIRED"){invalidate();mutable.update{it.copy(scope=null,loading=false,error=code)}}
                else mutable.update{it.copy(error=code)}
            }}
            finally{if(current(token)&&state.value.phase==RecoveryPhase.CAPTURE)mutable.update{it.copy(phase=RecoveryPhase.NONE)}}
        }
    }
    fun cancelPicker(id:String?) {
        if(id!=null&&prepared?.ticket?.id==id&&state.value.phase==RecoveryPhase.PICKER){prepared=null;mutable.update{it.copy(ticket=null,phase=RecoveryPhase.NONE)}}
    }
    fun launchFailed(id:String){if(prepared?.ticket?.id==id){cancelPicker(id);mutable.update{it.copy(error="EXPORT_FAILED")}}}
    fun leave(){if(state.value.phase==RecoveryPhase.CAPTURE||state.value.phase==RecoveryPhase.PICKER||state.value.phase==RecoveryPhase.WRITING){
        val old=action;prepared=null;mutable.update{it.copy(ticket=null,phase=RecoveryPhase.NONE,exportDone=false)};epoch++;mutable.update{it.copy(generation=epoch)};old?.cancel()
    };mutable.update{it.copy(exportConfirm=false,signOutConfirm=false)}}
    fun write(id:String,write:suspend(String)->Unit) {
        val capture=prepared?.takeIf{it.ticket.id==id&&it.epoch==epoch}?:return
        if(state.value.phase!=RecoveryPhase.PICKER)return
        prepared=null;val token=epoch
        mutable.update{it.copy(ticket=null,phase=RecoveryPhase.WRITING,error=null)}
        action=viewModelScope.launch {
            try {
                if(!authorized(capture.scope,token))return@launch
                withContext(Dispatchers.IO){ensureActive();write(capture.snapshot.raw)}
                if(current(token)&&authorized(capture.scope,token))mutable.update{it.copy(exportDone=true)}
            }catch(e:CancellationException){if(current(token))mutable.update{it.copy(error="EXPORT_FAILED",exportDone=false)};throw e}
            catch(e:Exception){if(current(token))mutable.update{it.copy(error="EXPORT_FAILED")}}
            finally{if(current(token))mutable.update{it.copy(phase=RecoveryPhase.NONE)}}
        }
    }
    fun signOut() {
        val st=state.value;val source=st.scope?:return
        if(st.busy||!st.signOutConfirm)return
        val token=epoch
        mutable.update{it.copy(signingOut=true,signOutConfirm=false)}
        action=viewModelScope.launch {
            try {if(authorized(source,token))auth.signOut()}
            catch(e:CancellationException){throw e}catch(_:Exception){if(current(token))mutable.update{it.copy(error="UNAVAILABLE")}}
            finally{if(current(token))mutable.update{it.copy(signingOut=false)}}
        }
    }
}
