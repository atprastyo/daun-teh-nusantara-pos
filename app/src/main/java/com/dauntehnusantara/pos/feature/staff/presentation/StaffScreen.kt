package com.dauntehnusantara.pos.feature.staff.presentation

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.reports.presentation.ManagementError

@Composable
fun StaffRoute(model:StaffViewModel,approval:@Composable () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    StaffScreen(state,model::reload,model::member,model::device,approval)
}
@Composable
fun StaffScreen(state:StaffState,reload:()->Unit,member:(StaffMember,String)->Unit,device:(StaffDevice,String)->Unit,approval:@Composable () -> Unit={}) {
    var selectedMember by remember {mutableStateOf<StaffMember?>(null)}
    var selectedDevice by remember {mutableStateOf<StaffDevice?>(null)}
    var reason by remember {mutableStateOf("")}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.auth_staff),style=MaterialTheme.typography.headlineMedium);Text(stringResource(R.string.auth_staff_manual)) }
        item { if(state.offline)Text(stringResource(R.string.manage_offline));ManagementError(state.error)
            if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(state.saved)Text(stringResource(R.string.staff_saved))
            OutlinedButton(onClick=reload,enabled=!state.busy&&!state.offline){Text(stringResource(R.string.staff_reload))}
        }
        val roster=state.roster
        if(roster!=null) {
            if(roster.members.isEmpty())item {Text(stringResource(R.string.staff_empty))}
            items(roster.members,key={"member.${it.id}"}) { m->
                OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("${m.name} · ${m.role}",style=MaterialTheme.typography.titleMedium);Text(m.email)
                    Text(stringResource(if(m.active&&m.profileActive)R.string.staff_active else R.string.staff_inactive))
                    OutlinedButton(onClick={selectedMember=m;selectedDevice=null;reason=""},enabled=!state.busy&&!state.offline&&StaffRules.mayChange(roster.scope,m)&&(m.active||m.profileActive)) {
                        Text(stringResource(if(m.active)R.string.staff_disable else R.string.staff_enable))
                    }
                }}
            }
            item{Text(stringResource(R.string.staff_devices),style=MaterialTheme.typography.titleMedium)}
            items(roster.devices,key={"device.${it.id}"}) { d->
                OutlinedCard(Modifier.fillMaxWidth()) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(d.id);Text(roster.members.find{it.id==d.userId}?.name?:d.userId)
                    Text(stringResource(if(d.revoked)R.string.staff_revoked else if(d.approved)R.string.staff_approved else R.string.staff_pending))
                    OutlinedButton(onClick={selectedDevice=d;selectedMember=null;reason=""},enabled=!state.busy&&!state.offline&&
                        (if(d.revoked)StaffRules.mayRestore(roster.scope,d)&&d.role!=null else StaffRules.mayRevoke(roster.scope,d))) {
                        Text(stringResource(if(d.revoked)R.string.staff_restore_device else R.string.staff_revoke_device))
                    }
                }}
            }
            if(roster.audit.isNotEmpty())item{Text(stringResource(R.string.staff_audit),style=MaterialTheme.typography.titleMedium)}
            items(roster.audit,key={"audit.${it.id}"}){entry->Column {
                Text("${com.dauntehnusantara.pos.core.designsystem.formatJakarta(entry.createdAtMs)} · ${stringResource(auditLabel(entry.action))}")
                entry.reason?.let{Text(it)}
                Text("${entry.actorId} → ${entry.targetId?:""}",style=MaterialTheme.typography.bodySmall)
            }}
        }
        item{Column(verticalArrangement=Arrangement.spacedBy(12.dp)){approval()}}
    }
    if(selectedMember!=null||selectedDevice!=null)AlertDialog(onDismissRequest={selectedMember=null;selectedDevice=null},
        title={Text(stringResource(R.string.staff_confirm))},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.staff_effect))
            OutlinedTextField(reason,{if(it.length<=240)reason=it},label={Text(stringResource(R.string.staff_reason))})
        }},confirmButton={TextButton(onClick={selectedMember?.let{member(it,reason)};selectedDevice?.let{device(it,reason)};selectedMember=null;selectedDevice=null},enabled=reason.trim().length in 3..240&&!state.busy&&!state.offline){Text(stringResource(R.string.staff_apply))}},
        dismissButton={TextButton(onClick={selectedMember=null;selectedDevice=null}){Text(stringResource(R.string.catalog_close))}})
}

private fun auditLabel(action:String):Int=when(action){
    "STAFF_PROVISION"->R.string.audit_provision
    "STAFF_ENABLE"->R.string.staff_enable
    "STAFF_DISABLE"->R.string.staff_disable
    "DEVICE_REVOKE"->R.string.staff_revoke_device
    "DEVICE_RESTORE_PENDING"->R.string.staff_restore_device
    "DEVICE_APPROVE"->R.string.staff_approved
    else->R.string.staff_saved
}
