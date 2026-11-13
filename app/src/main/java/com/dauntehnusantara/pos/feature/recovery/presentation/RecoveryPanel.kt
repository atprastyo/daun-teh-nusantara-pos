package com.dauntehnusantara.pos.feature.recovery.presentation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.recovery.domain.ExportRecovery
import com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure
import com.dauntehnusantara.pos.feature.reports.presentation.ManagementError
import com.dauntehnusantara.pos.feature.sync.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

@HiltViewModel
class RecoveryViewModel @Inject constructor(auth:AuthRepository,private val gateway:PullGateway,private val export:ExportRecovery):ViewModel() {
    var busy by mutableStateOf(false);private set
    var done by mutableStateOf(false);private set
    var error by mutableStateOf<String?>(null);private set
    private var action:Job?=null
    private var scope:PullScope?=null
    private var prepared:PullScope?=null
    init{viewModelScope.launch{auth.state.map{Triple(it.stage,it.membership,it.email to it.offline)}.distinctUntilChanged().collectLatest{(stage,_,_)->
        action?.cancel();busy=false;done=false;error=null;scope=if(stage==AuthStage.READY)gateway.pullScope()else null
    }}}
    fun prepare():String? { if(busy)return null;prepared=scope?:return null;return "daun-teh-recovery-${System.currentTimeMillis()}.json" }
    fun save(write:suspend(String)->Unit){val s=prepared;prepared=null;if(s==null){error="EXPORT_FAILED";return};if(busy)return;action=viewModelScope.launch{
        busy=true;done=false;error=null
        try{val archive=export(s);withContext(Dispatchers.IO){write(archive)};done=true}
        catch(e:CancellationException){throw e}catch(e:Exception){error=(e as? ManagementFailure)?.code?:"EXPORT_FAILED"}
        finally{busy=false}
    }}
}
@Composable
fun RecoveryPanel(model:RecoveryViewModel) {
    val resolver=LocalContext.current.contentResolver
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        if(uri!=null)model.save { raw->requireNotNull(resolver.openOutputStream(uri,"wt")).bufferedWriter(Charsets.UTF_8).use{it.write(raw)} }
    }
    Text(stringResource(R.string.recovery_title),style=MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.recovery_notice))
    OutlinedButton(onClick={model.prepare()?.let { name->picker.launch(name) }},enabled=!model.busy){Text(stringResource(R.string.recovery_export))}
    if(model.busy)LinearProgressIndicator()
    if(model.done)Text(stringResource(R.string.export_done))
    ManagementError(model.error)
}
