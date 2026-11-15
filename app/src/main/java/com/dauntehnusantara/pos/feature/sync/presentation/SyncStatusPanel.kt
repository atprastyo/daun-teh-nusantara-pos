package com.dauntehnusantara.pos.feature.sync.presentation

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.feature.sync.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

@HiltViewModel
class SyncStatusViewModel @Inject constructor(private val gateway:SaleGateway,private val repository:SyncRepository,
    private val pullGateway:PullGateway,private val pull:PullRepository):ViewModel() {
    private val mutable=MutableStateFlow<SyncStatus?>(null);val state=mutable.asStateFlow()
    private val pullMutable=MutableStateFlow<PullStatus?>(null);val pullState=pullMutable.asStateFlow()
    private var job:Job?=null;private var retryJob:Job?=null;private var epoch=0L
    var boundScope by mutableStateOf<PullScope?>(null);private set
    var failed by mutableStateOf(false);private set
    var retrying by mutableStateOf(false);private set
    var scheduled by mutableStateOf(false);private set
    fun clear(){epoch++;val old=job;val retry=retryJob;boundScope=null;mutable.value=null;pullMutable.value=null;failed=false;retrying=false;scheduled=false;old?.cancel();retry?.cancel()}
    private suspend fun matches(s:PullScope)=gateway.backgroundScope()==s.sale&&pullGateway.pullScope()==s
    fun bind(expected:PullScope?) {clear();if(expected==null)return;val token=epoch
        job=viewModelScope.launch {try {
            if(!matches(expected)){if(epoch==token)failed=true;return@launch}
            if(epoch!=token)return@launch
            boundScope=expected
            kotlinx.coroutines.flow.combine(repository.observe(expected.sale),pull.observe(expected)){push,download->push to download}.collect{(push,download)->
                require(listOf(push.pending,push.retrying,push.authRequired,push.review,push.synced).all{it>=0})
                if(epoch==token&&matches(expected)){mutable.value=push;pullMutable.value=download}
                else if(epoch==token)clear()
            }
        }catch(e:CancellationException){throw e}catch(_:Exception){if(epoch==token){failed=true;mutable.value=null;pullMutable.value=null}}}
    }
    fun load(outlet:String){clear();val token=epoch;job=viewModelScope.launch {
        try{val s=pullGateway.pullScope()?.takeIf{it.sale.outletId==outlet};if(epoch==token)bind(s)}
        catch(e:CancellationException){throw e}catch(_:Exception){if(epoch==token)failed=true}
    }}
    fun retry(online:Boolean=true) {
        val s=boundScope?:return
        if(!online||retrying||failed||pullMutable.value?.error=="REBOOTSTRAP_REQUIRED")return
        val token=epoch;retrying=true;scheduled=false
        retryJob=viewModelScope.launch {try {
            if(!matches(s)){if(epoch==token)clear();return@launch}
            repository.retry(s.sale) // Room implementation requests force-enqueue; never claim delivered.
            if(epoch==token&&matches(s))scheduled=true
        }catch(e:CancellationException){throw e}catch(_:Exception){if(epoch==token)failed=true}
        finally{if(epoch==token)retrying=false}}
    }
}
@Composable
fun SyncStatusPanel(outlet: String, model: SyncStatusViewModel = viewModel()) {
    LaunchedEffect(outlet) { model.load(outlet) }
    DisposableEffect(model, outlet) { onDispose { model.clear() } }
    val status by model.state.collectAsStateWithLifecycle()
    val pull by model.pullState.collectAsStateWithLifecycle()
    pull?.let {
        if (it.error == "REBOOTSTRAP_REQUIRED") Text(stringResource(R.string.recovery_required), color = MaterialTheme.colorScheme.error)
        else if (!it.initialized) Text(stringResource(R.string.pull_initial_required))
        if (it.error != null) Text(stringResource(R.string.pull_failed), color = MaterialTheme.colorScheme.error)
        it.lastSuccessMs?.let { time -> Text(stringResource(R.string.pull_current, com.dauntehnusantara.pos.core.designsystem.formatJakarta(time))) }
    }
    SyncStatusContent(status, model.failed) { if (model.failed) model.load(outlet) else model.retry() }
}
@Composable
fun SyncStatusContent(status: SyncStatus?, failed: Boolean, retry: () -> Unit) {
    Text(stringResource(R.string.sync_title), style = MaterialTheme.typography.titleMedium)
    if (failed) Text(stringResource(R.string.sync_failed), color = MaterialTheme.colorScheme.error)
    else if (status == null) CircularProgressIndicator()
    status?.let { s ->
        if (s.pending.toLong() + s.retrying + s.authRequired + s.review + s.synced == 0L) Text(stringResource(R.string.sync_empty))
        else {
            Text(stringResource(R.string.sync_counts, s.pending.toLong() + s.retrying, s.authRequired, s.review, s.synced))
            if (s.review > 0) Text(stringResource(R.string.sync_review), color = MaterialTheme.colorScheme.error)
            if (s.authRequired > 0) Text(stringResource(R.string.sync_auth))
        }
    }
    OutlinedButton(onClick = retry) { Text(stringResource(R.string.sync_retry)) }
    Text(stringResource(R.string.sync_boundary))
}
