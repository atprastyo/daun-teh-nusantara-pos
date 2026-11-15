package com.dauntehnusantara.pos.feature.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ReportState(val scope:PullScope?=null,val report:SalesReport?=null,val busy:Boolean=false,val cached:Boolean=true,
    val error:String?=null,val exportDone:Boolean=false,val from:String="",val to:String="",val outlet:String?=null,
    val generation:Long=0,val initializing:Boolean=false,val offline:Boolean=false,val outletName:String="",
    val choices:List<OutletChoice> = emptyList(),val exportPhase:ReportExportPhase=ReportExportPhase.NONE) {
    val query:ReportQuery? get()=scope?.let{reportQuery(it,from,to,outlet)}
    val allowed:Boolean get()=scope?.role in listOf("OWNER","MANAGER")
}
@HiltViewModel
class ReportViewModel @Inject constructor(private val auth:AuthRepository,private val gateway:ReportingGateway,
    private val reports:Reports,private val clock:Clock):ViewModel() {
    private val mutable=MutableStateFlow(ReportState(initializing=true));val state=mutable.asStateFlow()
    val session=ReportSession()
    private val reopen=MutableStateFlow(0L)
    private var epoch=0L
    private var request=0L
    private var loadJob:Job?=null
    private var exportJob:Job?=null
    private data class Prepared(val ticket:ReportExportTicket,val epoch:Long,val report:SalesReport)
    private var prepared:Prepared?=null
    init {
        viewModelScope.launch {
            auth.state.map{Triple(it.stage,it.membership,it.email)}.distinctUntilChanged().combine(reopen){context,_->context}.onEach { context ->
                clear(null)
                mutable.update{it.copy(initializing=context.first==AuthStage.READY&&context.second?.role in listOf(Role.OWNER,Role.MANAGER),offline=auth.state.value.offline)}
            }.collectLatest { context ->
                val token=epoch
                if(context.first==AuthStage.READY&&context.second?.role in listOf(Role.OWNER,Role.MANAGER)) {
                    try {
                        val s=gateway.reportingScope(false)
                        if(token==epoch&&auth.state.value.let{Triple(it.stage,it.membership,it.email)}==context) {
                            if(s==null||s.role !in listOf("OWNER","MANAGER")){clear("AUTH_REQUIRED");return@collectLatest}
                            val (from,to)=reportPreset(clock,ReportPreset.WEEK)
                            val outlet=if(s.role=="OWNER")null else s.sale.outletId
                            mutable.value=ReportState(scope=s,generation=epoch,from=from,to=to,outlet=outlet,
                                offline=auth.state.value.offline,outletName=context.second?.outletName.orEmpty())
                            load(from,to,outlet)
                        }
                    }catch(e:CancellationException){throw e}
                    catch(_:Exception){if(epoch==token)mutable.update{it.copy(initializing=false,error="UNAVAILABLE")}}
                }
            }
        }
        viewModelScope.launch {auth.state.map{it.offline}.distinctUntilChanged().collect{offline->mutable.update{it.copy(offline=offline,cached=it.cached||offline)}}}
    }
    private fun clear(error:String?) {
        epoch++;request++;loadJob?.cancel();exportJob?.cancel();prepared=null;session.clear()
        mutable.value=ReportState(generation=epoch,error=error,offline=auth.state.value.offline)
    }
    private fun current(token:Long,version:Long?=null)=epoch==token&&(version==null||request==version)
    private suspend fun validScope(source:PullScope,token:Long):Boolean {
        if(!current(token))return false
        val valid=try{gateway.reportingScope(false)==source}catch(e:CancellationException){throw e}catch(_:Exception){false}
        if(!current(token))return false
        if(!valid)clear("AUTH_REQUIRED")
        return valid
    }
    private fun validate(report:SalesReport,q:ReportQuery):SalesReport {
        require(report.query==q);ReportRules.validate(report)
        require(report.products.map{it.id to it.name}.distinct().size==report.products.size)
        return report
    }
    fun load(from:String,to:String,outlet:String?) {
        val s=state.value.scope?:return
        val q=reportQuery(s,from,to,outlet)
        val knownOutlet=outlet==null||outlet==s.sale.outletId||outlet==state.value.outlet||state.value.choices.any{it.id==outlet}
        if(q==null||!knownOutlet){mutable.update{it.copy(error="INVALID")};return}
        val token=epoch;val version=++request
        loadJob?.cancel()
        val retained=state.value.report?.takeIf{it.query==q}
        session.applied(q)
        mutable.update{it.copy(busy=true,error=null,report=retained,cached=true,from=from,to=to,outlet=outlet,exportDone=false)}
        loadJob=viewModelScope.launch {
            try {
                if(!validScope(s,token)||!current(token,version))return@launch
                // A corrupt exact cache must not prevent a valid online response; never render it.
                val saved=try{reports.cached(q)?.let{validate(it,q)}}catch(e:CancellationException){throw e}
                    catch(e:ManagementFailure){if(e.code=="AUTH_REQUIRED")throw e;null}catch(_:Exception){null}
                if(!current(token,version)||!validScope(s,token))return@launch
                if(saved!=null)mutable.update{it.copy(report=saved,choices=saved.choices)}
                if(auth.state.value.offline)throw ManagementFailure("OFFLINE")
                val fresh=validate(reports.refresh(q),q)
                if(current(token,version)&&validScope(s,token))mutable.update{it.copy(report=fresh,choices=fresh.choices,cached=false,error=null)}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){
                if(current(token,version)) {
                    if((e as? ManagementFailure)?.code=="AUTH_REQUIRED")clear("AUTH_REQUIRED")
                    else if(validScope(s,token))mutable.update{it.copy(error=(e as? ManagementFailure)?.code?:"UNAVAILABLE",cached=true)}
                }
            }finally{if(current(token,version))mutable.update{it.copy(busy=false)}}
        }
    }
    fun applyDraft(){val s=state.value.scope?:return;val d=session.state.value;if(session.query(s)==null)return;load(d.from,d.to,d.outlet)}
    fun preset(value:ReportPreset)=session.preset(clock,value)
    fun retry(){if(state.value.scope==null)reopen.update{it+1}else state.value.query?.let{load(it.from.toString(),it.to.toString(),it.outletId)}}
    fun prepareExport():ReportExportTicket? {
        val st=state.value;val report=st.report?:return null;val q=st.query?:return null
        if(st.busy||st.exportPhase!=ReportExportPhase.NONE||report.query!=q||session.dirty(q)||!st.allowed)return null
        val copy=report.copy(days=report.days.toList(),outlets=report.outlets.toList(),products=report.products.toList(),choices=report.choices.toList())
        val ticket=ReportExportTicket(UUID.randomUUID().toString(),"daun-teh-report-${q.from}-${q.to}.csv")
        prepared=Prepared(ticket,epoch,copy)
        mutable.update{it.copy(exportPhase=ReportExportPhase.PICKER,exportDone=false,error=null)}
        return ticket
    }
    fun cancelExport(id:String?) {
        if(id!=null&&prepared?.ticket?.id==id&&state.value.exportPhase==ReportExportPhase.PICKER) {
            prepared=null;mutable.update{it.copy(exportPhase=ReportExportPhase.NONE)}
        }
    }
    fun failExportLaunch(id:String) {
        if(prepared?.ticket?.id==id){cancelExport(id);mutable.update{it.copy(error="EXPORT_FAILED")}}
    }
    fun export(id:String,write:suspend(String)->Unit) {
        val capture=prepared?.takeIf{it.ticket.id==id&&it.epoch==epoch}?:return
        if(state.value.exportPhase!=ReportExportPhase.PICKER)return
        prepared=null
        val token=capture.epoch
        mutable.update{it.copy(exportPhase=ReportExportPhase.WRITING,exportDone=false,error=null)}
        exportJob=viewModelScope.launch {
            try {
                if(!validScope(capture.report.query.source,token))return@launch
                val csv=reports.export(capture.report)
                if(!current(token)||!validScope(capture.report.query.source,token))return@launch
                withContext(Dispatchers.IO){ensureActive();write(csv)}
                if(current(token)&&validScope(capture.report.query.source,token))mutable.update{it.copy(exportDone=true)}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(current(token)) {
                if((e as? ManagementFailure)?.code=="AUTH_REQUIRED")clear("AUTH_REQUIRED")
                else if(validScope(capture.report.query.source,token))mutable.update{it.copy(error="EXPORT_FAILED")}
            }}finally{if(current(token))mutable.update{it.copy(exportPhase=ReportExportPhase.NONE)}}
        }
    }
}
