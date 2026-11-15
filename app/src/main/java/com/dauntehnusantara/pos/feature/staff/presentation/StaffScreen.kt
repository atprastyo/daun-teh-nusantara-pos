package com.dauntehnusantara.pos.feature.staff.presentation

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import com.dauntehnusantara.pos.core.designsystem.components.*
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
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.reports.presentation.ManagementError
import com.dauntehnusantara.pos.core.designsystem.formatJakarta

@Composable
fun StaffRoute(model:StaffViewModel,reviewerBusy:Boolean=false,approval:@Composable (Boolean)->Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val draft by model.session.state.collectAsStateWithLifecycle()
    StaffScreen(state,model::reload,model::member,model::device,
        approval={approval(!state.busy&&!state.blocked&&draft.target==null)},session=model.session,reviewerBusy=reviewerBusy,onConfirm=model::confirm)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffScreen(state:StaffState,reload:()->Unit,member:(StaffMember,String)->Unit,device:(StaffDevice,String)->Unit,approval:@Composable ()->Unit={},
    session:StaffSession?=null,reviewerBusy:Boolean=false,onConfirm:(()->Unit)?=null) {
    val editor=session?:remember(state.scope?.key,state.generation){StaffSession()}
    val draft by editor.state.collectAsState()
    val token=editor.generation
    val locked=state.busy||state.offline||state.blocked||reviewerBusy
    val roster=state.roster?.takeIf{it.scope==state.scope&&!state.offline&&it.scope.role in listOf("OWNER","MANAGER")}
    val tab=if(draft.tab==StaffTab.AUDIT&&state.scope?.role!="OWNER")StaffTab.MEMBERS else draft.tab
    val focus=remember(state.scope?.key,state.generation){mutableMapOf<String,FocusRequester>()}
    var restoreFocus by remember(state.scope?.key,state.generation){mutableStateOf<String?>(null)}
    LaunchedEffect(draft.target,restoreFocus){if(draft.target==null&&restoreFocus!=null){withFrameNanos{};runCatching{focus[restoreFocus]?.requestFocus()};restoreFocus=null}}
    val currentTarget=draft.target?.takeIf{target->target.scope==state.scope&&roster!=null&&when(target){
        is StaffTarget.Member->roster.members.any{it==target.value}
        is StaffTarget.Device->roster.devices.any{it==target.value}
    }}
    LaunchedEffect(currentTarget,state.offline){if(currentTarget==null||state.offline)editor.cancel()}
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide=maxWidth>=1000.dp&&LocalDensity.current.fontScale<1.5f
    Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
    LazyColumn(Modifier.weight(1f).fillMaxHeight().testTag("staff.list"),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        item {AppScreenHeader(stringResource(R.string.auth_staff),description="Kelola akses untuk outlet yang dipilih.")}
        item {Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(state.offline)Text("Pengelolaan staf memerlukan koneksi. Data dan draf sebelumnya tidak ditampilkan.")
            ManagementError(state.error)
            val outcome=when(state.outcome){StaffOutcome.NONE->null;StaffOutcome.SAVED->"Perubahan diterima dan daftar diperbarui.";StaffOutcome.ACK_REFRESH_FAILED->"Perubahan diterima; daftar belum diperbarui. Perbarui daftar saja.";StaffOutcome.CONFLICT->"Data berubah. Perbarui dan tinjau target kembali.";StaffOutcome.UNCERTAIN->"Hasil belum dapat dipastikan. Perbarui dan periksa akses sebelum mencoba lagi."}
            if(outcome!=null)Text(outcome,Modifier.semantics{liveRegion=LiveRegionMode.Polite})
            if(state.busy){Text(if(state.writing)"Memproses perubahan akses…" else if(roster!=null)"Memperbarui · data sebelumnya" else "Memuat daftar staf…",Modifier.semantics{liveRegion=LiveRegionMode.Polite});LinearProgressIndicator(Modifier.fillMaxWidth())}
            OutlinedButton(onClick=reload,enabled=!state.busy&&!state.offline&&!reviewerBusy){Text(stringResource(R.string.staff_reload))}
        }}
        if(roster!=null) {
            item {Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                StaffTab.entries.filter{it!=StaffTab.AUDIT||roster.scope.role=="OWNER"}.forEach { option->
                    FilterChip(selected=tab==option,onClick={editor.guard(token){editor.tab(option)}},label={Text(when(option){StaffTab.MEMBERS->"Anggota";StaffTab.DEVICES->"Perangkat";StaffTab.AUDIT->"Aktivitas akses"})})
                }
            }}
            when(tab) {
                StaffTab.MEMBERS->{
                    if(roster.members.isEmpty())item{AppEmptyState(stringResource(R.string.staff_empty))}
                    items(roster.members,key={"member.${it.id}"}){m->StaffRow {
                        Text(m.name,style=MaterialTheme.typography.titleMedium);Text(m.email);Text(staffRole(m.role),style=MaterialTheme.typography.labelLarge)
                        Text(if(!m.profileActive)"Profil nonaktif · tinjau administrator" else if(m.active)"Akses outlet aktif" else "Akses outlet nonaktif")
                        if(!m.profileActive)Text("Keanggotaan outlet ${if(m.active)"aktif" else "nonaktif"}; profil terpisah.",style=MaterialTheme.typography.bodySmall)
                        OutlinedButton(modifier=Modifier.focusRequester(focus.getOrPut("m.${m.id}"){FocusRequester()}),onClick={editor.guard(token){editor.open(StaffTarget.Member(roster.scope,m))}},enabled=!locked&&staffCanChange(roster.scope,m)) {
                            Text(stringResource(if(m.active)R.string.staff_disable else R.string.staff_enable))
                        }
                        if(!staffCanChange(roster.scope,m))Text("Tidak dapat diubah melalui akun ini.",style=MaterialTheme.typography.bodySmall)
                    }}
                }
                StaffTab.DEVICES->{
                    if(roster.devices.isEmpty())item{AppEmptyState("Belum ada perangkat yang dikembalikan.")}
                    items(roster.devices,key={"device.${it.id}"}){d->StaffRow {
                        Text(roster.members.find{it.id==d.userId}?.name?:d.userId,style=MaterialTheme.typography.titleMedium)
                        AppIdentity(d.id)
                        Text(staffRole(d.role),style=MaterialTheme.typography.labelLarge)
                        if(d.id==roster.scope.sale.deviceId)Text("Perangkat ini")
                        Text(stringResource(if(d.revoked)R.string.staff_revoked else if(d.approved)R.string.staff_approved else R.string.staff_pending))
                        OutlinedButton(modifier=Modifier.focusRequester(focus.getOrPut("d.${d.id}"){FocusRequester()}),onClick={editor.guard(token){editor.open(StaffTarget.Device(roster.scope,d))}},enabled=!locked&&staffCanChange(roster.scope,d)) {
                            Text(stringResource(if(d.revoked)R.string.staff_restore_device else R.string.staff_revoke_device))
                        }
                        if(!staffCanChange(roster.scope,d))Text(when{d.id==roster.scope.sale.deviceId->"Perangkat ini tidak dapat dicabut di sini.";d.role==null->"Tinjau akses anggota terlebih dahulu.";else->"Pemulihan hanya oleh Pemilik."},style=MaterialTheme.typography.bodySmall)
                    }}
                    item {Column(verticalArrangement=Arrangement.spacedBy(12.dp)){approval()}}
                }
                StaffTab.AUDIT->{
                    item {Text("Hingga 50 aktivitas akses outlet yang dikembalikan",style=MaterialTheme.typography.titleMedium)}
                    if(roster.audit.isEmpty())item{Text("Tidak ada aktivitas yang dikembalikan.")}
                    items(roster.audit,key={"audit.${it.id}"}){entry->StaffRow {
                        Text(stringResource(auditLabel(entry.action)),style=MaterialTheme.typography.titleMedium);Text(formatJakarta(entry.createdAtMs))
                        entry.reason?.let{Text(it)}
                        SelectionContainer{Text("${entry.actorId} → ${entry.targetId.orEmpty()}",style=MaterialTheme.typography.bodySmall)}
                    }}
                }
            }
        } else if(!state.busy&&!state.offline)item{Text("Daftar staf belum tersedia. Perbarui untuk memvalidasi akses.")}
        if(!wide)item {StaffHelp()}
    }
    if(wide)Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top=20.dp,end=20.dp)){StaffHelp()}
    }
    }
    if(currentTarget!=null&&!state.offline) {
        val cancel={if(!state.busy)editor.guard(token){restoreFocus=when(currentTarget){is StaffTarget.Member->"m.${currentTarget.value.id}";is StaffTarget.Device->"d.${currentTarget.value.id}"};editor.cancel()}}
        AppConfirmation(staffAction(currentTarget)+"?",cancel,busy=state.busy,actions={
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick=cancel,enabled=!state.busy){Text("Batal")}
                    AppPrimaryButton(text=if(state.busy)"Memproses…" else staffAction(currentTarget),busy=state.busy,modifier=Modifier.weight(1f).testTag("staff.confirm"),onClick={editor.guard(token){
                        if(onConfirm!=null)onConfirm() else {when(currentTarget){is StaffTarget.Member->member(currentTarget.value,draft.reason);is StaffTarget.Device->device(currentTarget.value,draft.reason)};editor.cancel()}
                    }},enabled=!locked&&draft.reason.trim().length in 3..240)
                }
        }) {
                    when(currentTarget){is StaffTarget.Member->{Text(currentTarget.value.name);Text(currentTarget.value.email);SelectionContainer{Text(currentTarget.value.id)};Text(staffRole(currentTarget.value.role))}
                        is StaffTarget.Device->{Text(roster?.members?.find{it.id==currentTarget.value.userId}?.name?:currentTarget.value.userId);SelectionContainer{Text(currentTarget.value.id)};Text(staffRole(currentTarget.value.role))}}
                    Text(state.outletName.ifBlank{state.scope?.sale?.outletId.orEmpty()})
                    Text(when(currentTarget){is StaffTarget.Member->if(currentTarget.value.active)"Akses outlet aktif → nonaktif" else "Akses outlet nonaktif → aktif"
                        is StaffTarget.Device->if(currentTarget.value.revoked)"Dicabut → menunggu persetujuan" else "Akses perangkat → dicabut"})
                    Text(staffEffect(currentTarget))
                    OutlinedTextField(draft.reason,{value->editor.guard(token){editor.reason(value)}},Modifier.fillMaxWidth().testTag("staff.reason"),enabled=!state.busy,label={Text(stringResource(R.string.staff_reason))})
                    Text("3–240 karakter setelah spasi tepi dihapus. Alasan dicatat; hindari kata sandi atau rahasia.",style=MaterialTheme.typography.bodySmall)
                    if(draft.reason.trim().length !in 3..240)Text("Periksa panjang alasan.",color=MaterialTheme.colorScheme.error)
        }
    }
}
@Composable private fun StaffRow(content:@Composable ColumnScope.()->Unit){AppListRow(content=content)}
private fun staffEffect(target:StaffTarget)=when(target){
    is StaffTarget.Member->if(target.value.active)"Akses anggota di outlet ini dinonaktifkan dan perangkat outlet milik anggota dicabut. Perangkat terputus dapat tetap memakai izin offline hingga validasi online atau masa izin berakhir."
        else "Akses outlet diaktifkan. Perangkat yang dicabut tetap dicabut. Pemilik perlu memulihkan perangkat, lalu persetujuan baru tetap diperlukan. Profil global tidak diaktifkan di sini."
    is StaffTarget.Device->if(target.value.revoked)"Pemulihan kembali ke menunggu persetujuan, bukan aktif. Persetujuan baru sesuai peran tetap diperlukan."
        else "Perangkat dicabut pada pemeriksaan online. Ini tidak menghapus struk atau menjamin logout seketika ketika offline. Izin offline tetap dibatasi masa izin yang sudah ada."
}
private fun auditLabel(action:String):Int=when(action){"STAFF_PROVISION"->R.string.audit_provision;"STAFF_ENABLE"->R.string.staff_enable;"STAFF_DISABLE"->R.string.staff_disable;"DEVICE_REVOKE"->R.string.staff_revoke_device;"DEVICE_RESTORE_PENDING"->R.string.staff_restore_device;"DEVICE_APPROVE"->R.string.staff_approved;else->R.string.staff_ui_audit_unknown}

@Composable private fun StaffHelp(){Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=MaterialTheme.shapes.large){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Text("Akun ditambahkan oleh administrator",style=MaterialTheme.typography.titleMedium)
    Text("Hubungi administrator untuk akun, peran atau kata sandi. Tidak ada undangan atau pendaftaran di aplikasi.")
    Text("Izin offline perangkat terputus tetap dibatasi masa izin yang sudah ada.",style=MaterialTheme.typography.bodySmall)
}}}
