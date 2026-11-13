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

data class StaffState(val scope:PullScope?=null,val roster:StaffRoster?=null,val busy:Boolean=false,val offline:Boolean=false,val error:String?=null,val saved:Boolean=false)
@HiltViewModel
class StaffViewModel @Inject constructor(private val auth:AuthRepository,private val gateway:ReportingGateway,private val staff:ManageStaff):ViewModel() {
    private val mutable=MutableStateFlow(StaffState());val state=mutable.asStateFlow();private var action:Job?=null
    init{viewModelScope.launch {auth.state.map{Triple(it.stage,it.membership,it.email to it.offline)}.distinctUntilChanged().collectLatest{(stage,_,_)->
        action?.cancel();mutable.value=StaffState(offline=auth.state.value.offline)
        if(stage==AuthStage.READY)gateway.reportingScope(false)?.let{s->mutable.update{it.copy(scope=s)};if(!auth.state.value.offline)reload()}
    }}}
    fun reload()=run{ val s=state.value.scope?:return@run;mutable.update{it.copy(roster=null)};execute{staff.load(s).also{r->mutable.update{it.copy(roster=r)}}} }
    private fun execute(block:suspend()->Unit){if(action?.isActive==true)return;action=viewModelScope.launch{
        mutable.update{it.copy(busy=true,error=null,saved=false)}
        try{if(auth.state.value.offline)throw ManagementFailure("OFFLINE");block()}
        catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it.copy(error=(e as? ManagementFailure)?.code?:"UNAVAILABLE")}}
        finally{mutable.update{it.copy(busy=false)}}
    }}
    fun member(m:StaffMember,reason:String){val s=state.value.scope?:return;execute{staff.member(s,m,reason);val fresh=staff.load(s);mutable.update{it.copy(roster=fresh,saved=true)}}}
    fun device(d:StaffDevice,reason:String){val s=state.value.scope?:return;execute{staff.device(s,d,reason);val fresh=staff.load(s);mutable.update{it.copy(roster=fresh,saved=true)}}}
}
