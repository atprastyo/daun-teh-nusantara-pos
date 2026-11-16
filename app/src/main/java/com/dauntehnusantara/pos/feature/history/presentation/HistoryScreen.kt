package com.dauntehnusantara.pos.feature.history.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.*
import com.dauntehnusantara.pos.core.designsystem.components.AppTextField
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.pos.presentation.ReceiptContent
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun HistoryRoute(model:HistoryViewModel,expanded:Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    HistoryScreen(state,model.session,model::refresh,expanded)
}

@Composable
fun HistoryScreen(state:HistoryState,session:HistorySession,onRefresh:()->Unit,expanded:Boolean=false) {
    key(state.scopeKey) { HistoryBody(state,session,onRefresh,expanded) }
}

@Composable
private fun HistoryBody(state:HistoryState,session:HistorySession,onRefresh:()->Unit,expanded:Boolean) {
    val filters by session.state.collectAsStateWithLifecycle()
    val token=remember(state.scopeKey){session.generation}
    val rows=remember(state.receipts,state.deliveries,state.today,filters.query,filters.day,filters.status) { historyRows(state,filters) }
    val selected=rows.find{it.id==filters.selectedId}
    val list=rememberLazyListState(filters.scrollIndex,filters.scrollOffset)
    var priorFilters by remember{mutableStateOf(Triple(filters.query,filters.day,filters.status))}
    var returnFocus by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(state,filters.query,filters.day,filters.status,filters.selectedId){session.guard(token){session.reconcile(state)}}
    LaunchedEffect(filters.query,filters.day,filters.status){
        val current=Triple(filters.query,filters.day,filters.status)
        if(current!=priorFilters){list.scrollToItem(0);priorFilters=current}
    }
    LaunchedEffect(list){snapshotFlow{list.firstVisibleItemIndex to list.firstVisibleItemScrollOffset}.distinctUntilChanged().collect{(i,o)->session.guard(token){session.scroll(i,o)}}}
    val close={session.guard(token){returnFocus=selected?.id;session.close()}}
    if(selected!=null)BackHandler(onBack=close)
    if(!state.allowed&&!state.loading) {
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.history_ui_unavailable))
            if(state.loadFailed)Button(onClick=onRefresh){Text(stringResource(R.string.history_ui_read_retry))}
        };return
    }
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val split=expanded&&maxWidth>=1000.dp&&maxHeight>=480.dp&&LocalDensity.current.fontScale<1.5f
        if(split)Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            HistoryList(state,filters,rows,session,onRefresh,list,Modifier.width(420.dp).fillMaxHeight(),returnFocus,token){returnFocus=null}
            Surface(Modifier.weight(1f).fillMaxHeight().padding(end=16.dp,top=16.dp,bottom=16.dp),shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceContainerHighest) {
                if(selected!=null)HistoryDetail(state,selected,close,true)
                else Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
                    Text(stringResource(R.string.history_ui_select),style=MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.history_ui_snapshot))
                }
            }
        }else if(selected!=null)HistoryDetail(state,selected,close,false)
        else HistoryList(state,filters,rows,session,onRefresh,list,Modifier.fillMaxSize(),returnFocus,token){returnFocus=null}
    }
}

@Composable
private fun HistoryList(state:HistoryState,filters:HistoryFilters,rows:List<Receipt>,session:HistorySession,onRefresh:()->Unit,
    list:LazyListState,modifier:Modifier,returnFocus:String?,token:Long,onFocused:()->Unit) {
    LazyColumn(modifier.testTag("history.list"),state=list,contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item(key="title") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.history),style=MaterialTheme.typography.headlineLarge)
                Text(stringResource(if(state.outletWide)R.string.history_ui_outlet_subtitle else R.string.history_ui_subtitle),color=MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick=onRefresh,enabled=!state.refreshing&&!state.loading,modifier=Modifier.testTag("history.refresh")) {
                    Text(stringResource(if(state.refreshing)R.string.history_ui_refreshing else if(state.loadFailed)R.string.history_ui_read_retry else R.string.history_ui_refresh))
                }
            }
        }
        item(key="notices") { HistoryNotices(state) }
        item(key="cache") { HistoryNotice(stringResource(R.string.history_ui_cache)) }
        item(key="search") {
            AppTextField(filters.query,{value->session.guard(token){session.search(value)}},label=stringResource(R.string.history_ui_search),enabled=!state.loading,
                leadingIcon={Icon(Icons.Default.Search,null)},modifier=Modifier.fillMaxWidth().testTag("history.search"))
        }
        item(key="filters") {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                HistoryMenu(stringResource(R.string.history_ui_date,dayLabel(filters.day)),"history.date",HistoryDay.entries.map{it to dayLabel(it)},filters.day,{value->session.guard(token){session.day(value)}})
                HistoryMenu(stringResource(R.string.history_ui_status,statusLabel(filters.status)),"history.status",
                    listOf(null to stringResource(R.string.history_ui_all_status))+listOf(ReceiptDeliveryState.PENDING,ReceiptDeliveryState.RETRY,ReceiptDeliveryState.SYNCED,ReceiptDeliveryState.REVIEW,ReceiptDeliveryState.AUTH_REQUIRED,ReceiptDeliveryState.UNKNOWN).map{it to statusLabel(it)},filters.status,{value->session.guard(token){session.status(value)}})
            }
        }
        if(filters.query.isNotBlank()||filters.day!=HistoryDay.ALL||filters.status!=null)item(key="clear"){
            TextButton(onClick={session.guard(token){session.clearFilters()}}){Text(stringResource(R.string.history_ui_clear))}
        }
        if(filters.selectionEvicted)item(key="evicted"){HistoryNotice(stringResource(R.string.history_ui_evicted))}
        if(!state.loading)item(key="count"){Text(stringResource(R.string.history_ui_count,rows.size),style=MaterialTheme.typography.bodySmall)}
        if(rows.isEmpty()&&!state.loading)item(key="empty") {
            Text(stringResource(when{
                state.loadFailed->R.string.history_ui_read_error
                !state.demo&&!state.pull.initialized->R.string.history_ui_initial_empty
                state.receipts.isEmpty()->R.string.history_ui_empty
                else->R.string.history_ui_no_match
            }))
        }
        var previous:LocalDate?=null
        rows.forEach { receipt ->
            val date=Instant.ofEpochMilli(receipt.createdAtUtcMs).atZone(HISTORY_ZONE).toLocalDate()
            if(date!=previous) {
                item(key="day-$date"){Text(date.format(DateTimeFormatter.ofPattern("dd MMMM yyyy",Locale.forLanguageTag("id"))),style=MaterialTheme.typography.titleSmall)}
                previous=date
            }
            item(key=receipt.id) {
                val focus=remember(receipt.id){FocusRequester()}
                LaunchedEffect(returnFocus){if(returnFocus==receipt.id){focus.requestFocus();onFocused()}}
                ReceiptRow(receipt,state.deliveries[receipt.id],receipt.id==filters.selectedId,{session.guard(token){session.select(receipt.id)}},Modifier.focusRequester(focus))
            }
        }
    }
}

@Composable
private fun <T> HistoryMenu(label:String,tag:String,choices:List<Pair<T,String>>,current:T,onChoose:(T)->Unit) {
    var open by remember{mutableStateOf(false)}
    Box {
        OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth().testTag(tag)){Text(label)}
        DropdownMenu(expanded=open,onDismissRequest={open=false}) {
            choices.forEach{(value,text)->DropdownMenuItem(text={Text(text)},onClick={open=false;onChoose(value)},modifier=Modifier.semantics{this.selected=value==current})}
        }
    }
}

@Composable
private fun HistoryDetail(state:HistoryState,receipt:Receipt,onClose:()->Unit,split:Boolean) {
    val headingFocus=remember(receipt.id){FocusRequester()}
    LaunchedEffect(receipt.id){headingFocus.requestFocus()}
    Column(Modifier.fillMaxSize().semantics{paneTitle="Detail struk"}) {
        TextButton(onClick=onClose,modifier=Modifier.padding(horizontal=12.dp).focusRequester(headingFocus)) {
            Text(stringResource(if(split)R.string.history_ui_close else R.string.history_ui_back))
        }
        ReceiptContent(receipt,Modifier.weight(1f).fillMaxWidth(),demo=state.demo,history=true,
            delivery=state.deliveries[receipt.id],notice=when{
                state.pull.error=="REBOOTSTRAP_REQUIRED"->stringResource(R.string.recovery_required)
                state.loadFailed->stringResource(R.string.history_ui_read_error)
                else->null
            })
    }
}

@Composable
private fun ReceiptRow(receipt:Receipt,delivery:ReceiptDelivery?,isSelected:Boolean,onSelect:()->Unit,modifier:Modifier) {
    OutlinedCard(onClick=onSelect,modifier=modifier.fillMaxWidth().heightIn(min=48.dp).testTag("history.receipt.${receipt.id}").semantics{selected=isSelected},
        colors=CardDefaults.outlinedCardColors(containerColor=if(isSelected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(receipt.lines.firstOrNull()?.productName.orEmpty(),style=MaterialTheme.typography.titleMedium)
            if(receipt.lines.size>1)Text(stringResource(R.string.history_ui_other_lines,receipt.lines.size-1),style=MaterialTheme.typography.bodySmall)
            Text(formatIdr(receipt.totalIdr),style=MaterialTheme.typography.titleLarge)
            Text(formatJakarta(receipt.createdAtUtcMs),style=MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.history_ui_items,receipt.lines.sumOf{it.quantity},stringResource(if(receipt.payment.method==PaymentMethod.CASH)R.string.pos_cash else R.string.pos_qris)),style=MaterialTheme.typography.bodySmall)
            Surface(shape=MaterialTheme.shapes.small,color=when(delivery?.state){
                ReceiptDeliveryState.SYNCED->MaterialTheme.colorScheme.primaryContainer
                ReceiptDeliveryState.REVIEW,ReceiptDeliveryState.AUTH_REQUIRED->MaterialTheme.colorScheme.secondaryContainer
                else->MaterialTheme.colorScheme.surfaceContainerHighest
            }){Text(statusLabel(delivery?.state?:ReceiptDeliveryState.UNKNOWN),Modifier.padding(8.dp),style=MaterialTheme.typography.labelMedium)}
            Text(stringResource(R.string.history_ui_id_hint,receipt.id.takeLast(8)),style=MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HistoryNotices(state:HistoryState) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        if(state.demo)HistoryNotice(stringResource(R.string.pos_demo))
        if(state.loading){LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(R.string.history_ui_loading))}
        if(state.offline)HistoryNotice(stringResource(R.string.history_ui_offline))
        if(state.loadFailed)HistoryNotice(stringResource(R.string.history_ui_read_error))
        if(!state.demo) {
            if(state.pull.error=="REBOOTSTRAP_REQUIRED")HistoryNotice(stringResource(R.string.recovery_required))
            else if(!state.pull.initialized)HistoryNotice(stringResource(R.string.history_ui_initial))
            else if(state.pull.error!=null)HistoryNotice(stringResource(R.string.pull_failed))
        }
        if(state.refreshing){LinearProgressIndicator(Modifier.fillMaxWidth());Text(stringResource(R.string.history_ui_refreshing),Modifier.semantics{liveRegion=LiveRegionMode.Polite})}
        else when(state.refresh){
            HistoryRefresh.NONE->Unit
            else->HistoryNotice(stringResource(when(state.refresh){
                HistoryRefresh.LOCAL->R.string.history_ui_local;HistoryRefresh.PULL->R.string.history_ui_pull
                HistoryRefresh.MORE->R.string.history_ui_more;else->R.string.history_ui_failed
            }))
        }
    }
}
@Composable private fun HistoryNotice(text:String) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerHighest,shape=MaterialTheme.shapes.medium,modifier=Modifier.fillMaxWidth()) {
        Text(text,Modifier.padding(16.dp).semantics{liveRegion=LiveRegionMode.Polite},style=MaterialTheme.typography.bodySmall)
    }
}
@Composable private fun dayLabel(day:HistoryDay)=stringResource(when(day){HistoryDay.ALL->R.string.history_ui_all_dates;HistoryDay.TODAY->R.string.history_ui_today;HistoryDay.YESTERDAY->R.string.history_ui_yesterday})
@Composable private fun statusLabel(status:ReceiptDeliveryState?)=stringResource(when(status){
    null->R.string.history_ui_all_status;ReceiptDeliveryState.PENDING->R.string.history_ui_pending
    ReceiptDeliveryState.RETRY->R.string.history_ui_retry;ReceiptDeliveryState.SYNCED->R.string.history_ui_synced
    ReceiptDeliveryState.REVIEW->R.string.history_ui_review;ReceiptDeliveryState.AUTH_REQUIRED->R.string.history_ui_auth
    ReceiptDeliveryState.UNKNOWN->R.string.history_ui_unknown
})
