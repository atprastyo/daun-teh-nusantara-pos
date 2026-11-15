package com.dauntehnusantara.pos.feature.reports.presentation

import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import java.time.*
import kotlinx.coroutines.flow.*

val REPORT_ZONE:ZoneId=ZoneId.of("Asia/Jakarta")
enum class ReportPreset { TODAY, WEEK, MONTH }
enum class ReportExportPhase { NONE, PICKER, WRITING }
data class ReportExportTicket(val id:String,val filename:String)
data class ReportDraft(val from:String="",val to:String="",val outlet:String?=null,val editing:Boolean=false)
fun reportQuery(scope:PullScope,from:String,to:String,outlet:String?):ReportQuery? = runCatching {
    require(from.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))&&to.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")))
    ReportQuery(scope,LocalDate.parse(from),LocalDate.parse(to),outlet)
}.getOrNull()
fun reportPreset(clock:Clock,preset:ReportPreset):Pair<String,String> {
    val today=LocalDate.now(clock.withZone(REPORT_ZONE))
    return (when(preset){ReportPreset.TODAY->today;ReportPreset.WEEK->today.minusDays(6);ReportPreset.MONTH->today.withDayOfMonth(1)}).toString() to today.toString()
}
fun reportOutletLabel(outlet:String?,choices:List<OutletChoice>,currentId:String?,currentName:String):String =
    if(outlet==null)"Semua outlet merek" else choices.find{it.id==outlet}?.let{it.name+if(it.active)"" else " · nonaktif"}
        ?:if(outlet==currentId&&currentName.isNotBlank())currentName else "Outlet pilihan · $outlet"

/** Memory-only draft shared across layout/navigation; contains no authority or URI. */
class ReportSession {
    private val mutable=MutableStateFlow(ReportDraft());val state=mutable.asStateFlow()
    var generation=0L;private set
    fun clear(){generation++;mutable.value=ReportDraft()}
    fun guard(token:Long,block:()->Unit){if(token==generation)block()}
    fun applied(q:ReportQuery){mutable.value=ReportDraft(q.from.toString(),q.to.toString(),q.outletId)}
    fun open(q:ReportQuery){if(!mutable.value.editing)mutable.value=ReportDraft(q.from.toString(),q.to.toString(),q.outletId,true)}
    fun cancel(q:ReportQuery)=applied(q)
    fun from(value:String){mutable.update{if(it.editing)it.copy(from=value)else it}}
    fun to(value:String){mutable.update{if(it.editing)it.copy(to=value)else it}}
    fun outlet(value:String?){mutable.update{if(it.editing)it.copy(outlet=value)else it}}
    fun preset(clock:Clock,preset:ReportPreset){val (from,to)=reportPreset(clock,preset);mutable.update{if(it.editing)it.copy(from=from,to=to)else it}}
    fun query(scope:PullScope)=state.value.let{reportQuery(scope,it.from,it.to,it.outlet)}
    fun dirty(q:ReportQuery)=state.value.let{it.from!=q.from.toString()||it.to!=q.to.toString()||it.outlet!=q.outletId}
}
