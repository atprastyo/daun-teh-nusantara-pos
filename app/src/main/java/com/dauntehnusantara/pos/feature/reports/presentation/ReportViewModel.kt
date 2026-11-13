package com.dauntehnusantara.pos.feature.reports.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ReportState(val scope:PullScope?=null,val report:SalesReport?=null,val busy:Boolean=false,val cached:Boolean=true,
    val error:String?=null,val exportDone:Boolean=false,val from:String="",val to:String="",val outlet:String?=null)
@HiltViewModel
class ReportViewModel @Inject constructor(private val auth:AuthRepository,private val gateway:ReportingGateway,private val reports:Reports,clock:Clock):ViewModel() {
    private val mutable=MutableStateFlow(ReportState());val state=mutable.asStateFlow()
    private var action:Job?=null
    private var prepared:SalesReport?=null
    private val selections=mutableMapOf<String,Triple<String,String,String?>>()
    private val today=LocalDate.now(clock.withZone(ZoneId.of("Asia/Jakarta")))
    init { viewModelScope.launch {
        auth.state.map { Triple(it.stage,it.membership,it.email to it.offline) }.distinctUntilChanged().collectLatest { (stage,_,_) ->
            action?.cancel();mutable.value=ReportState()
            if(stage==AuthStage.LOGIN){selections.clear();prepared=null}
            if(stage==AuthStage.LOCKED)prepared=null
            if(stage==AuthStage.READY)gateway.reportingScope(false)?.let { s->
                if(prepared?.query?.source!=s)prepared=null
                val selection=selections[s.key]?:Triple(today.minusDays(6).toString(),today.toString(),if(s.role=="OWNER")null else s.sale.outletId)
                mutable.value=ReportState(scope=s,from=selection.first,to=selection.second,outlet=selection.third)
                load(mutable.value.from,mutable.value.to,mutable.value.outlet)
            }
        }
    } }
    fun load(from:String,to:String,outlet:String?) {
        val s=state.value.scope?:return
        val q=try { ReportQuery(s,LocalDate.parse(from),LocalDate.parse(to),outlet) }catch(_:Exception){mutable.update{it.copy(error="INVALID")};return}
        selections[s.key]=Triple(from,to,outlet)
        action?.cancel();action=viewModelScope.launch {
            mutable.update{it.copy(busy=true,error=null,report=null,cached=true,from=from,to=to,outlet=outlet,exportDone=false)}
            try {
                val saved=reports.cached(q);mutable.update{it.copy(report=saved)}
                if(auth.state.value.offline)throw ManagementFailure("OFFLINE")
                val fresh=reports.refresh(q);mutable.update{it.copy(report=fresh,cached=false)}
            }catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it.copy(error=(e as? ManagementFailure)?.code?:"UNAVAILABLE")}}
            finally { mutable.update{it.copy(busy=false)} }
        }
    }
    fun retry()=load(state.value.from,state.value.to,state.value.outlet)
    fun prepareExport():String? {
        if(state.value.busy)return null
        val report=state.value.report?:return null
        prepared=report
        return "daun-teh-report-${report.query.from}-${report.query.to}.csv"
    }
    fun export(write:suspend (String)->Unit) {
        val report=prepared
        prepared=null
        if(report==null){mutable.update{it.copy(error="EXPORT_FAILED")};return}
        if(state.value.busy)return
        action=viewModelScope.launch {
            mutable.update{it.copy(busy=true,error=null,exportDone=false)}
            try { val csv=reports.export(report);withContext(Dispatchers.IO){write(csv)};mutable.update{it.copy(exportDone=true)} }
            catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it.copy(error=(e as? ManagementFailure)?.code?:"EXPORT_FAILED")}}
            finally { mutable.update{it.copy(busy=false)} }
        }
    }
}
