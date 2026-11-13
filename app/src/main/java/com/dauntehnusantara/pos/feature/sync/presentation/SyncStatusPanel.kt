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
class SyncStatusViewModel @Inject constructor(private val gateway: SaleGateway, private val repository: SyncRepository,
    private val scheduler: SyncScheduler, private val pullGateway: PullGateway, private val pull: PullRepository) : ViewModel() {
    private val pullMutable = MutableStateFlow<PullStatus?>(null)
    val pullState = pullMutable.asStateFlow()
    private val mutable = MutableStateFlow<SyncStatus?>(null)
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var scope: SaleScope? = null
    var failed by mutableStateOf(false); private set
    fun load(outlet: String) {
        job?.cancel(); mutable.value = null; pullMutable.value = null; scope = null; failed = false
        job = viewModelScope.launch {
            try {
                val selected = gateway.backgroundScope()?.takeIf { it.outletId == outlet } ?: return@launch
                scope = selected
                val pullScope = requireNotNull(pullGateway.pullScope())
                require(pullScope.sale == selected)
                kotlinx.coroutines.flow.combine(repository.observe(selected), pull.observe(pullScope)) { pushStatus, pullStatus -> pushStatus to pullStatus }
                    .collect { (pushStatus, pullStatus) -> mutable.value = pushStatus; pullMutable.value = pullStatus }
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
        }
    }
    fun clear() { job?.cancel(); scope = null; mutable.value = null; pullMutable.value = null; failed = false }
    fun retry() { viewModelScope.launch {
        try { scope?.let { repository.retry(it) }; scheduler.enqueue() }
        catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
    } }
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
        if (s.pending + s.retrying + s.authRequired + s.review + s.synced == 0) Text(stringResource(R.string.sync_empty))
        else {
            Text(stringResource(R.string.sync_counts, s.pending + s.retrying, s.authRequired, s.review, s.synced))
            if (s.review > 0) Text(stringResource(R.string.sync_review), color = MaterialTheme.colorScheme.error)
            if (s.authRequired > 0) Text(stringResource(R.string.sync_auth))
        }
    }
    OutlinedButton(onClick = retry) { Text(stringResource(R.string.sync_retry)) }
    Text(stringResource(R.string.sync_boundary))
}
