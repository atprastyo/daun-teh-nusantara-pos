package com.dauntehnusantara.pos.feature.reports.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.dauntehnusantara.pos.core.designsystem.components.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.*

@Composable
fun ReportRoute(model:ReportViewModel,expanded:Boolean,overview:Boolean=false) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    val resolver=context.contentResolver
    var launchedId by rememberSaveable { mutableStateOf<String?>(null) }
    val navigate=com.dauntehnusantara.pos.feature.auth.presentation.LocalShellNavigation.current
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        val id=launchedId;launchedId=null
        if(uri==null)model.cancelExport(id)
        else if(id!=null)model.export(id) { csv->
            requireNotNull(resolver.openOutputStream(uri,"wt")).bufferedWriter(Charsets.UTF_8).use{it.write(csv)}
        }
    }
    DisposableEffect(model,context) { onDispose {
        var owner:android.content.Context=context
        while(owner is android.content.ContextWrapper && owner !is android.app.Activity) owner=owner.baseContext
        if((owner as? android.app.Activity)?.isChangingConfigurations!=true)model.cancelExport(launchedId)
    } }
    ReportScreen(state,expanded,model::load,model::retry,export={
        model.prepareExport()?.let { ticket->
            launchedId=ticket.id
            try { picker.launch(ticket.filename) } catch(_:Exception){model.failExportLaunch(ticket.id);launchedId=null}
        }
    },session=model.session,overview=overview,pickerOutstanding=launchedId!=null,
        onPreset=model::preset,onOpenReport={navigate(com.dauntehnusantara.pos.feature.auth.domain.AccessDestination.REPORTS)})
}
@Composable
fun ManagementError(code:String?) {
    if(code==null)return
    AppErrorText(stringResource(when(code){"CONFLICT"->R.string.manage_conflict;"OFFLINE"->R.string.manage_offline;"INVALID"->R.string.manage_invalid;
        "AUTH_REQUIRED"->R.string.manage_denied;"TOO_LARGE"->R.string.manage_too_large;"EXPORT_FAILED"->R.string.export_failed;else->R.string.manage_unavailable}))
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(state:ReportState,expanded:Boolean=false,load:(String,String,String?)->Unit,retry:()->Unit,export:()->Unit,
    session:ReportSession?=null,overview:Boolean=false,pickerOutstanding:Boolean=false,
    onPreset:((ReportPreset)->Unit)?=null,onOpenReport:()->Unit={}) {
    if(state.scope!=null&&!state.allowed){ManagementError("AUTH_REQUIRED");return}
    val local=remember(state.scope?.key,state.generation){ReportSession().also { holder->state.query?.let(holder::applied) }}
    val editor=session?:local
    val draft by editor.state.collectAsState()
    val applied=state.query
    val locked=state.busy||state.exportPhase!=ReportExportPhase.NONE||pickerOutstanding
    val choices=state.choices.ifEmpty{state.report?.choices.orEmpty()}
    val outletLabel=reportOutletLabel(state.outlet,choices,state.scope?.sale?.outletId,state.outletName)
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(if(expanded)28.dp else 16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {AppScreenHeader(if(overview)"Ringkasan" else stringResource(R.string.report_title),description=stringResource(R.string.report_basis))}
        item {OutlinedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Outlet laporan · $outletLabel",style=MaterialTheme.typography.titleMedium)
            Text("${state.from} — ${state.to} · Asia/Jakarta")
            OutlinedButton(onClick={applied?.let(editor::open)},enabled=applied!=null&&!locked){Text("Ubah filter")}
        }}}
        item {ManagementError(state.error);if(state.busy||state.initializing)LinearProgressIndicator(Modifier.fillMaxWidth())}
        val report=state.report
        if(report!=null&&report.query==applied) {
            item {Text(stringResource(if(state.cached)R.string.report_cached else R.string.report_fresh,formatJakarta(report.asOfMs)),style=MaterialTheme.typography.bodySmall)}
            item {ReportMetrics(report)}
            if(report.summary.count==0L)item{AppEmptyState(stringResource(R.string.report_empty))}
            if(overview)item {Button(onClick=onOpenReport){Text("Lihat laporan")}}
            else {
                item {Text("Subtotal ${formatIdr(report.summary.subtotal)} · Diskon ${formatIdr(report.summary.discount)}",style=MaterialTheme.typography.bodyMedium)}
                if(report.days.isNotEmpty())item{Text(stringResource(R.string.report_daily),modifier=Modifier.semantics{heading()},style=MaterialTheme.typography.titleLarge)}
                items(report.days,key={"day.${it.date}"}){ReportRow(it.date.toString(),it.count,formatIdr(it.total))}
                if(report.outlets.isNotEmpty())item{Text(stringResource(R.string.report_outlets),modifier=Modifier.semantics{heading()},style=MaterialTheme.typography.titleLarge)}
                items(report.outlets,key={"outlet.${it.id}"}){ReportRow(it.name,it.count,formatIdr(it.total))}
                if(report.products.isNotEmpty())item{Text(stringResource(R.string.report_products),modifier=Modifier.semantics{heading()},style=MaterialTheme.typography.titleLarge)}
                items(report.products,key={"product.${it.id.length}:${it.id}${it.name}"}){ReportRow(it.name+" · "+it.id,it.quantity,formatIdr(it.gross),units=true)}
                item{OutlinedButton(onClick=export,enabled=!locked&&!draft.editing&&!editor.dirty(report.query)){Text(stringResource(R.string.report_export))}}
            }
        } else if(!state.busy&&!state.initializing)item {Text("Belum ada snapshot untuk filter ini. Coba lagi saat terhubung.")}
        item {if(state.exportDone)Text(stringResource(R.string.export_done))
            if(state.exportPhase==ReportExportPhase.WRITING)Text("Menyimpan CSV…")
            OutlinedButton(onClick=retry,enabled=!locked){Text(stringResource(R.string.retry))}}
    }
    if(draft.editing&&applied!=null) {
        val token=editor.generation
        val valid=state.scope?.let(editor::query)
        val known=draft.outlet==null||draft.outlet==state.scope?.sale?.outletId||draft.outlet==state.outlet||choices.any{it.id==draft.outlet}
        ModalBottomSheet(onDismissRequest={editor.guard(token){editor.cancel(applied)}}) {
            Column(Modifier.fillMaxWidth().heightIn(max=600.dp).imePadding().padding(horizontal=20.dp)) {
                Text("Filter laporan",style=MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f,false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    ReportPreset.entries.forEach { preset->TextButton(onClick={editor.guard(token){
                        if(onPreset!=null)onPreset(preset) else editor.preset(java.time.Clock.systemUTC(),preset)
                    }}){Text(when(preset){ReportPreset.TODAY->"Hari ini";ReportPreset.WEEK->"7 hari terakhir";ReportPreset.MONTH->"Bulan ini"})} }
                    AppTextField(draft.from,{value->editor.guard(token){editor.from(value.take(10))}},stringResource(R.string.report_from),Modifier.fillMaxWidth())
                    AppTextField(draft.to,{value->editor.guard(token){editor.to(value.take(10))}},stringResource(R.string.report_to),Modifier.fillMaxWidth())
                    Text("YYYY-MM-DD · Maksimal 366 tanggal · Asia/Jakarta",style=MaterialTheme.typography.bodySmall)
                    if(state.scope?.role=="OWNER") {
                        var open by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick={open=true}){Text(reportOutletLabel(draft.outlet,choices,state.scope.sale.outletId,state.outletName))}
                            DropdownMenu(open,{open=false}) {
                                DropdownMenuItem(text={Text("Semua outlet merek")},onClick={editor.guard(token){editor.outlet(null)};open=false})
                                choices.forEach { choice->DropdownMenuItem(text={Text(choice.name+if(choice.active)"" else " · nonaktif")},onClick={editor.guard(token){editor.outlet(choice.id)};open=false}) }
                            }
                        }
                    } else Text(outletLabel)
                    if(valid==null||!known)Text("Periksa tanggal dan outlet laporan.",color=MaterialTheme.colorScheme.error)
                }
                Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick={editor.guard(token){editor.cancel(applied)}}){Text("Batal")}
                    Button(onClick={editor.guard(token){if(valid!=null&&known){load(draft.from,draft.to,draft.outlet);editor.applied(valid)}}},enabled=valid!=null&&known&&!locked){Text(stringResource(R.string.report_apply))}
                }
            }
        }
    }
}
@Composable private fun ReportMetrics(report:com.dauntehnusantara.pos.feature.reports.domain.SalesReport) {
    val metrics=listOf(stringResource(R.string.report_sales) to report.summary.count.toString(),stringResource(R.string.report_total) to formatIdr(report.summary.total),stringResource(R.string.pos_cash) to formatIdr(report.summary.cash),stringResource(R.string.pos_qris) to formatIdr(report.summary.qris))
    val font=androidx.compose.ui.platform.LocalDensity.current.fontScale
    BoxWithConstraints {
        val columns=if(maxWidth>=1050.dp&&font<1.5f)4 else if(maxWidth>=540.dp&&font<1.5f)2 else 1
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {metrics.chunked(columns).forEach { row->
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{(label,value)->
                ElevatedCard(Modifier.weight(1f)){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(label,style=MaterialTheme.typography.labelLarge);Text(value,style=MaterialTheme.typography.headlineSmall)}}
            }}
        }}
    }
}
@Composable private fun ReportRow(label:String,count:Long,amount:String,units:Boolean=false) {
    val quantity="$count ${if(units)"unit" else "penjualan"}"
    AppListRow {
        if(androidx.compose.ui.platform.LocalDensity.current.fontScale>=1.5f) {
            Column(Modifier.padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(label,style=MaterialTheme.typography.bodyLarge)
                Text(quantity,style=MaterialTheme.typography.bodyMedium)
                Text(amount,style=MaterialTheme.typography.titleMedium)
            }
        } else Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(label,style=MaterialTheme.typography.bodyLarge)
                Text(quantity,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(amount,style=MaterialTheme.typography.titleMedium,modifier=Modifier.widthIn(max=200.dp))
        }
    }
}
