package com.dauntehnusantara.pos.feature.staff.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class StaffState(val scope:PullScope?=null,val roster:StaffRoster?=null,val busy:Boolean=false,val offline:Boolean=false,val error:String?=null,val saved:Boolean=false,
    val generation:Long=0,val blocked:Boolean=false,val outcome:StaffOutcome=StaffOutcome.NONE,val writing:Boolean=false,val outletName:String="",val creating:Boolean=false,val createError:String?=null)
@HiltViewModel
class StaffViewModel @Inject constructor(private val auth:AuthRepository,private val gateway:ReportingGateway,private val staff:ManageStaff):ViewModel() {
    private val mutable=MutableStateFlow(StaffState());val state=mutable.asStateFlow()
    val session=StaffSession()
    var pendingCreation:StaffCreation?=null;private set
    private var generation=0L;private var request=0L;private var action:Job?=null
    private val reopen=MutableStateFlow(0L)
    init {viewModelScope.launch {
        auth.state.map{Triple(it.stage,it.membership,it.email to it.offline)}.distinctUntilChanged().combine(reopen){context,_->context}
            .onEach{clear(null)}.collectLatest { context->
                val token=generation;val membership=context.second
                if(context.first==AuthStage.READY&&!auth.state.value.offline&&membership!=null&&membership.role in listOf(Role.OWNER,Role.MANAGER)) {
                    mutable.update{it.copy(busy=true)}
                    try {
                        val scope=gateway.reportingScope(false)
                        if(current(token)&&auth.state.value.let{Triple(it.stage,it.membership,it.email to it.offline)}==context) {
                            if(scope==null||scope.role!=membership?.role?.name||scope.sale.brandId!=membership.brandId||scope.sale.outletId!=membership.outletId){clear("AUTH_REQUIRED");return@collectLatest}
                            mutable.value=StaffState(scope=scope,generation=token,outletName=membership.outletName)
                            reload()
                        }
                    }catch(e:CancellationException){throw e}catch(_:Exception){if(current(token))mutable.update{it.copy(busy=false,error="UNAVAILABLE")}}
                }
            }
    }}
    private fun current(token:Long,version:Long?=null)=token==generation&&(version==null||version==request)
    private fun clear(error:String?){pendingCreation=null;generation++;request++;action?.cancel();session.clear();mutable.value=StaffState(generation=generation,offline=auth.state.value.offline,error=error)}
    private suspend fun authorized(source:PullScope,token:Long):Boolean {
        if(!current(token))return false
        if(auth.state.value.offline){clear("OFFLINE");return false}
        val scope=try{gateway.reportingScope(false)}catch(e:CancellationException){throw e}catch(_:Exception){null}
        if(!current(token))return false
        if(scope!=source){clear("AUTH_REQUIRED");return false}
        return true
    }
    private fun validate(r:StaffRoster,s:PullScope):StaffRoster {
        require(r.scope==s&&s.role in listOf("OWNER","MANAGER"))
        require(r.members.all{it.version>0&&it.role in listOf("MANAGER","CASHIER")}&&r.devices.all{it.version>0})
        require(r.members.map{it.id}.distinct().size==r.members.size&&r.devices.map{it.id}.distinct().size==r.devices.size)
        require(r.audit.size<=50&&r.audit.map{it.id}.distinct().size==r.audit.size)
        require(s.role=="OWNER"||(r.audit.isEmpty()&&r.devices.all{it.role=="CASHIER"}))
        return r
    }
    fun reload() {
        if(state.value.busy||state.value.creating||auth.state.value.busy||auth.state.value.offline)return
        val source=state.value.scope
        if(source==null){reopen.update{it+1};return}
        val token=generation;val version=++request
        session.cancel()
        mutable.update{it.copy(busy=true,error=null,saved=false)}
        action=viewModelScope.launch {
            try {
                if(!authorized(source,token))return@launch
                val fresh=validate(staff.load(source),source)
                if(current(token,version)&&authorized(source,token))mutable.update{it.copy(roster=fresh,blocked=false,outcome=StaffOutcome.NONE)}
            }catch(e:CancellationException){throw e}catch(e:Exception){if(current(token,version)) {
                if((e as? ManagementFailure)?.code=="AUTH_REQUIRED")clear("AUTH_REQUIRED")
                else if(authorized(source,token))mutable.update{it.copy(error=(e as? ManagementFailure)?.code?:"UNAVAILABLE",blocked=true)}
            }}finally{if(current(token,version))mutable.update{it.copy(busy=false)}}
        }
    }
    fun openCreation() {
        val st=state.value
        if(st.busy||st.blocked||st.offline||auth.state.value.busy||st.scope?.role !in listOf("OWNER","MANAGER")||st.roster==null)return
        if(pendingCreation==null&&st.roster.capacity?.anyAvailable(st.scope!!.role)!=true)return
        session.cancel();mutable.update{it.copy(creating=true,createError=null)}
    }
    fun closeCreation() {
        if(state.value.busy)return
        // Keep uncertain operation identity until successful readback or scope loss.
        mutable.update{it.copy(creating=false)}
    }
    fun create(command:StaffCreation) {
        val st=state.value;val source=st.scope?:return
        if(!st.creating||st.busy||auth.state.value.busy||st.offline||st.blocked)return
        val pending=pendingCreation
        if(pending==null&&st.roster?.capacity?.canCreate(command.role)!=true){mutable.update{it.copy(createError=if(st.roster?.capacity?.configured==true)"STAFF_LIMIT_REACHED" else "STAFF_LIMITS_UNCONFIGURED")};return}
        val frozen=if(pending!=null&&st.createError=="INVALID")StaffCreation(pending.operationId,pending.name,pending.email,pending.role,command.password) else pending?:command
        if(runCatching{frozen.validate(source)}.isFailure){mutable.update{it.copy(createError="INVALID")};return}
        pendingCreation=frozen
        val token=generation;val version=++request
        mutable.update{it.copy(busy=true,writing=true,createError=null)}
        action=viewModelScope.launch {
            var acknowledged=false
            try {
                if(!authorized(source,token))return@launch
                staff.create(source,frozen);acknowledged=true
                if(!current(token,version)||!authorized(source,token))return@launch
                pendingCreation=null;mutable.update{it.copy(creating=false)}
                val fresh=validate(staff.load(source),source)
                if(current(token,version)&&authorized(source,token))mutable.update{it.copy(roster=fresh,saved=true,outcome=StaffOutcome.SAVED)}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(current(token,version)) {
                val code=(e as? ManagementFailure)?.code?:"UNAVAILABLE"
                if(code=="AUTH_REQUIRED")clear(code)
                else if(authorized(source,token)) {
                    if(!acknowledged&&code in listOf("NOT_CONFIGURED","RATE_LIMIT","STAFF_LIMIT_REACHED","STAFF_LIMITS_UNCONFIGURED"))pendingCreation=null
                    mutable.update{it.copy(createError=if(acknowledged)null else code,
                    error=if(acknowledged)code else it.error,blocked=acknowledged,
                    outcome=if(acknowledged)StaffOutcome.ACK_REFRESH_FAILED else it.outcome)}
                }
            }}finally{if(current(token,version))mutable.update{it.copy(busy=false,writing=false)}}
        }
    }
    fun confirm(){val draft=session.state.value;draft.target?.let{submit(it,draft.reason)}}
    fun member(m:StaffMember,reason:String){state.value.scope?.let{submit(StaffTarget.Member(it,m),reason)}}
    fun device(d:StaffDevice,reason:String){state.value.scope?.let{submit(StaffTarget.Device(it,d),reason)}}
    private fun matches(target:StaffTarget,r:StaffRoster)=r.scope==target.scope&&when(target){
        is StaffTarget.Member->r.members.any{it==target.value}&&staffCanChange(r.scope,target.value)
        is StaffTarget.Device->r.devices.any{it.copy(deviceName=null)==target.value.copy(deviceName=null)}&&staffCanChange(r.scope,target.value)
    }
    private fun submit(target:StaffTarget,reason:String) {
        val st=state.value
        if(st.creating||st.busy||auth.state.value.busy||st.blocked||st.offline||st.scope!=target.scope)return
        if(runCatching{StaffRules.reason(reason)}.isFailure){mutable.update{it.copy(error="INVALID")};return}
        if(st.roster?.let{matches(target,it)}!=true){session.cancel();mutable.update{it.copy(error="CONFLICT",blocked=true,outcome=StaffOutcome.CONFLICT)};return}
        val token=generation;val version=++request
        mutable.update{it.copy(busy=true,writing=true,error=null,saved=false,outcome=StaffOutcome.NONE)}
        action=viewModelScope.launch {
            var dispatched=false;var acknowledged=false
            try {
                if(!authorized(target.scope,token))return@launch
                // Inspect current server version before dispatch; never invert a refreshed row.
                val fresh=validate(staff.load(target.scope),target.scope)
                if(!current(token,version)||!authorized(target.scope,token))return@launch
                if(!matches(target,fresh))throw ManagementFailure("CONFLICT")
                dispatched=true
                when(target){is StaffTarget.Member->staff.member(target.scope,target.value,reason);is StaffTarget.Device->staff.device(target.scope,target.value,reason)}
                acknowledged=true
                if(!current(token,version)||!authorized(target.scope,token))return@launch
                session.cancel()
                val readback=validate(staff.load(target.scope),target.scope)
                if(current(token,version)&&authorized(target.scope,token))mutable.update{it.copy(roster=readback,saved=true,blocked=false,outcome=StaffOutcome.SAVED)}
            }catch(e:CancellationException){
                if(current(token,version)&&dispatched){session.cancel();mutable.update{it.copy(blocked=true,error="UNAVAILABLE",outcome=if(acknowledged)StaffOutcome.ACK_REFRESH_FAILED else StaffOutcome.UNCERTAIN)}}
                throw e
            }catch(e:Exception){if(current(token,version)) {
                val code=(e as? ManagementFailure)?.code
                if(code=="AUTH_REQUIRED")clear(code)
                else if(authorized(target.scope,token)) {
                    session.cancel()
                    val outcome=when{acknowledged->StaffOutcome.ACK_REFRESH_FAILED;code=="CONFLICT"->StaffOutcome.CONFLICT;dispatched&&code !in listOf("INVALID","TOO_LARGE")->StaffOutcome.UNCERTAIN;else->StaffOutcome.NONE}
                    mutable.update{it.copy(error=code?:"UNAVAILABLE",blocked=true,outcome=outcome)}
                }
            }}finally{if(current(token,version))mutable.update{it.copy(busy=false,writing=false)}}
        }
    }
}
