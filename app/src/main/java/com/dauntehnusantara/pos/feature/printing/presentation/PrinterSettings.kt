package com.dauntehnusantara.pos.feature.printing.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.components.*
import com.dauntehnusantara.pos.feature.printing.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope

@Composable
fun PrinterSettingsRoute(model:PrinterViewModel,scope:PullScope?,enabled:Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var denied by remember{mutableStateOf(false)}
    var settingsFailed by remember{mutableStateOf(false)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        denied=!it;if(it)model.refresh()
    }
    LaunchedEffect(scope){model.bind(scope)}
    DisposableEffect(model){onDispose{model.leave()}}
    PrinterSettings(state,enabled,onRefresh={
        if(Build.VERSION.SDK_INT>=31&&ContextCompat.checkSelfPermission(context,Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED)
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        else{denied=false;model.refresh()}
    },onSelect=model::select,onTest=model::test,onSettings={
        settingsFailed=runCatching{context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))}.isFailure
    },onAppSettings={
        settingsFailed=runCatching{context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri()))}.isFailure
    },denied=denied,settingsFailed=settingsFailed)
}

@Composable
internal fun PrinterSettings(state:PrinterState,enabled:Boolean,onRefresh:()->Unit,onSelect:(PrinterDevice?)->Unit,
    onTest:()->Unit,onSettings:()->Unit,onAppSettings:()->Unit,denied:Boolean=false,settingsFailed:Boolean=false) {
    var confirm by remember(state.scope,state.selected){mutableStateOf(false)}
    val active=enabled&&!state.busy&&state.scope!=null
    AppSection(stringResource(R.string.printer_title)) {
        Text(stringResource(R.string.printer_profile))
        Text(stringResource(R.string.printer_provisional),style=MaterialTheme.typography.bodySmall)
        state.selected?.let{Text(it.name);Text(it.address,style=MaterialTheme.typography.bodySmall)}
            ?:Text(stringResource(R.string.printer_none))
        if(denied||state.problem==PrinterProblem.PERMISSION) {
            AppErrorText(stringResource(R.string.printer_permission))
            TextButton(onClick=onAppSettings,enabled=active){Text(stringResource(R.string.printer_app_settings))}
        }
        if(settingsFailed)AppErrorText(stringResource(R.string.printer_settings_failed))
        state.problem?.takeIf{it!=PrinterProblem.PERMISSION}?.let{problem->
            AppErrorText(stringResource(when(problem){
                PrinterProblem.OFF->R.string.printer_off
                PrinterProblem.UNAVAILABLE->R.string.printer_unavailable
                else->R.string.printer_error
            }))
        }
        state.result?.let { result -> Text(stringResource(when(result){
            PrinterResult.SENT->R.string.printer_sent
            PrinterResult.UNKNOWN->R.string.printer_unknown
            PrinterResult.FAILED->R.string.printer_failed
        })) }
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick=onRefresh,enabled=active){Text(stringResource(R.string.printer_list))}
        TextButton(onClick=onSettings,enabled=active){Text(stringResource(R.string.printer_bluetooth_settings))}
        if(state.loaded&&state.devices.isEmpty())Text(stringResource(R.string.printer_empty))
        state.devices.forEach { device ->
            OutlinedButton(onClick={onSelect(device)},enabled=active,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Column { Text(device.name);Text(device.address,style=MaterialTheme.typography.bodySmall) }
            }
        }
        Button(onClick={confirm=true},enabled=active&&state.selected!=null){Text(stringResource(R.string.printer_test))}
        TextButton(onClick={onSelect(null)},enabled=active&&state.selected!=null){Text(stringResource(R.string.printer_forget))}
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text(stringResource(R.string.printer_test))},
        text={Text(stringResource(R.string.printer_confirm))},
        confirmButton={TextButton(onClick={confirm=false;onTest()},enabled=active){Text(stringResource(R.string.printer_send_test))}},
        dismissButton={TextButton(onClick={confirm=false}){Text(stringResource(R.string.access_cancel))}})
}
