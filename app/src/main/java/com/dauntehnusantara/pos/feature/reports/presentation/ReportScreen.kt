package com.dauntehnusantara.pos.feature.reports.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.*

@Composable
fun ReportRoute(model:ReportViewModel,expanded:Boolean) {
    val state by model.state.collectAsStateWithLifecycle();val resolver=LocalContext.current.contentResolver
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")){uri->
        if(uri!=null)model.export { csv->requireNotNull(resolver.openOutputStream(uri,"wt")).bufferedWriter(Charsets.UTF_8).use{it.write(csv)} }
    }
    ReportScreen(state,expanded,model::load,model::retry) { model.prepareExport()?.let { name->picker.launch(name) } }
}
@Composable
fun ManagementError(code:String?) {
    if(code==null)return
    Text(stringResource(when(code){"CONFLICT"->R.string.manage_conflict;"OFFLINE"->R.string.manage_offline;"INVALID"->R.string.manage_invalid;
        "AUTH_REQUIRED"->R.string.manage_denied;"TOO_LARGE"->R.string.manage_too_large;"EXPORT_FAILED"->R.string.export_failed;else->R.string.manage_unavailable}),color=MaterialTheme.colorScheme.error)
}
@Composable
fun ReportScreen(state:ReportState,expanded:Boolean=false,load:(String,String,String?)->Unit,retry:()->Unit,export:()->Unit) {
    var from by rememberSaveable(state.scope?.key,state.from){mutableStateOf(state.from)}
    var to by rememberSaveable(state.scope?.key,state.to){mutableStateOf(state.to)}
    var outlet by rememberSaveable(state.scope?.key,state.outlet){mutableStateOf(state.outlet)}
    var choicesOpen by remember {mutableStateOf(false)}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item {Text(stringResource(R.string.report_title),style=MaterialTheme.typography.headlineMedium)}
        item {Text(stringResource(R.string.report_basis))}
        item {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(from,{if(it.length<=10)from=it},label={Text(stringResource(R.string.report_from))},enabled=!state.busy)
                OutlinedTextField(to,{if(it.length<=10)to=it},label={Text(stringResource(R.string.report_to))},enabled=!state.busy)
                if(state.scope?.role=="OWNER")Box {
                    OutlinedButton(onClick={choicesOpen=true},enabled=!state.busy){Text(state.report?.choices?.find{it.id==outlet}?.name?:stringResource(R.string.report_all_outlets))}
                    DropdownMenu(choicesOpen,{choicesOpen=false}) {
                        DropdownMenuItem(text={Text(stringResource(R.string.report_all_outlets))},onClick={outlet=null;choicesOpen=false})
                        state.report?.choices?.forEach{c->DropdownMenuItem(text={Text(c.name)},onClick={outlet=c.id;choicesOpen=false})}
                    }
                }
                Button(onClick={load(from,to,outlet)},enabled=!state.busy&&state.scope!=null){Text(stringResource(R.string.report_apply))}
            }
        }
        item {ManagementError(state.error);if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())}
        val report=state.report
        if(report!=null) {
            item {
                Text("${report.query.from} — ${report.query.to} · Asia/Jakarta")
                Text(stringResource(if(state.cached)R.string.report_cached else R.string.report_fresh,formatJakarta(report.asOfMs)))
            }
            item {
                if(expanded)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
                    ReportMetric(stringResource(R.string.report_sales),report.summary.count.toString(),Modifier.weight(1f))
                    ReportMetric(stringResource(R.string.report_total),formatIdr(report.summary.total),Modifier.weight(1f))
                    ReportMetric(stringResource(R.string.pos_cash),formatIdr(report.summary.cash),Modifier.weight(1f))
                    ReportMetric(stringResource(R.string.pos_qris),formatIdr(report.summary.qris),Modifier.weight(1f))
                } else Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    ReportMetric(stringResource(R.string.report_sales),report.summary.count.toString())
                    ReportMetric(stringResource(R.string.report_total),formatIdr(report.summary.total))
                    Text("${stringResource(R.string.pos_cash)}: ${formatIdr(report.summary.cash)}")
                    Text("${stringResource(R.string.pos_qris)}: ${formatIdr(report.summary.qris)}")
                }
            }
            if(report.summary.count==0L)item{Text(stringResource(R.string.report_empty))}
            if(report.days.isNotEmpty())item{Text(stringResource(R.string.report_daily),style=MaterialTheme.typography.titleMedium)}
            items(report.days,key={"day.${it.date}"}){Text("${it.date} · ${it.count} · ${formatIdr(it.total)}")}
            if(report.outlets.isNotEmpty())item{Text(stringResource(R.string.report_outlets),style=MaterialTheme.typography.titleMedium)}
            items(report.outlets,key={"outlet.${it.id}"}){Text("${it.name} · ${it.count} · ${formatIdr(it.total)}")}
            if(report.products.isNotEmpty())item{Text(stringResource(R.string.report_products),style=MaterialTheme.typography.titleMedium)}
            items(report.products,key={"product.${it.id}.${it.name}"}){Text("${it.name} · ${it.quantity} · ${formatIdr(it.gross)}")}
            item{OutlinedButton(onClick=export,enabled=!state.busy){Text(stringResource(R.string.report_export))}}
        }
        item {if(state.exportDone)Text(stringResource(R.string.export_done));OutlinedButton(onClick=retry,enabled=!state.busy){Text(stringResource(R.string.retry))}}
    }
}
@Composable private fun ReportMetric(label:String,value:String,modifier:Modifier=Modifier){Column(modifier){Text(label,style=MaterialTheme.typography.labelLarge);Text(value,style=MaterialTheme.typography.titleLarge)}}
