package com.dauntehnusantara.pos.feature.settings.presentation

import android.app.Activity
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.dauntehnusantara.pos.core.designsystem.components.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.core.designsystem.formatJakarta
import com.dauntehnusantara.pos.feature.sync.domain.*
import com.dauntehnusantara.pos.feature.sync.presentation.SyncStatusViewModel
import com.dauntehnusantara.pos.feature.reports.presentation.ManagementError

@Composable
fun SettingsRoute(model:SettingsViewModel,sync:SyncStatusViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val push by sync.state.collectAsStateWithLifecycle();val pull by sync.pullState.collectAsStateWithLifecycle()
    val context=LocalContext.current
    var launchedId by rememberSaveable{mutableStateOf<String?>(null)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->
        val id=launchedId;launchedId=null
        if(uri==null)model.cancelPicker(id)
        else if(id!=null)model.write(id){raw->requireNotNull(context.contentResolver.openOutputStream(uri,"wt")).bufferedWriter(Charsets.UTF_8).use{it.write(raw)}}
    }
    LaunchedEffect(state.scope){sync.bind(state.scope)}
    LaunchedEffect(state.ticket){state.ticket?.let{ticket->if(launchedId==null){launchedId=ticket.id;try{picker.launch(ticket.filename)}catch(_:Exception){model.launchFailed(ticket.id);launchedId=null}}}}
    DisposableEffect(model,sync,context){onDispose {
        var owner:android.content.Context=context
        while(owner is ContextWrapper&&owner !is Activity)owner=owner.baseContext
        if((owner as? Activity)?.isChangingConfigurations!=true)model.leave()
        sync.clear()
    }}
    val matching=state.scope!=null&&sync.boundScope==state.scope
    SettingsScreen(state,push.takeIf{matching},pull.takeIf{matching},sync.failed,sync.retrying,sync.scheduled,
        outstandingPicker=launchedId!=null,onReload=model::reload,
        onRetry={sync.retry(!state.offline&&!state.busy)},onValidate=model::validateAccess,onExport=model::openExport,
        onSignOut=model::openSignOut,onCancel=model::cancelDialog,onPrepare=model::prepare,onConfirmSignOut=model::signOut)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state:SettingsState,push:SyncStatus?=null,pull:PullStatus?=null,readFailed:Boolean=false,retrying:Boolean=false,
    scheduled:Boolean=false,outstandingPicker:Boolean=false,onReload:()->Unit={},onRetry:()->Unit={},onValidate:()->Unit={},
    onExport:()->Unit={},onSignOut:()->Unit={},onCancel:()->Unit={},onPrepare:()->Unit={},onConfirmSignOut:()->Unit={}) {
    val exportFocus=remember(state.scope?.key,state.generation){FocusRequester()}
    val sessionFocus=remember(state.scope?.key,state.generation){FocusRequester()}
    var pendingFocus by remember(state.scope?.key,state.generation){mutableStateOf<Boolean?>(null)}
    LaunchedEffect(state.exportConfirm,state.signOutConfirm,pendingFocus){if(!state.exportConfirm&&!state.signOutConfirm&&pendingFocus!=null){withFrameNanos{};runCatching{if(pendingFocus==true)exportFocus.requestFocus()else sessionFocus.requestFocus()};pendingFocus=null}}
    val cancelAction={pendingFocus=state.exportConfirm;onCancel()}
    val locked=state.busy||outstandingPicker||retrying
    val recovery=pull?.error=="REBOOTSTRAP_REQUIRED"
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide=maxWidth>=840.dp&&LocalDensity.current.fontScale<1.5f
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("settings.page"),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            AppScreenHeader("Setelan")
            ManagementError(state.error)
            if(state.loading){Text("Memuat akses perangkat…");LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(state.offline)AppNotice("Akses offline. Tindakan jaringan memerlukan koneksi; ekspor lokal tetap memeriksa izin.")
            if(recovery)AppNotice("Data server perlu direkonsiliasi. Struk dan antrean lokal tetap disimpan. Jangan hapus data aplikasi. Ekspor data perangkat, lalu hubungi pemilik/operator. Login atau ekspor tidak menghapus status ini.")
            val access:@Composable ()->Unit={AppSection("Akun & akses") {
                if(state.email.isNotBlank())SelectionContainer{Text(state.email)}
                Text(state.leaseExpiresMs?.let{"Izin offline berlaku hingga ${formatJakarta(it)}"}?:"Masa izin offline belum tersedia.")
                state.scope?.let{AppIdentity(it.sale.deviceId,label="Identitas perangkat")}
                Text("Maksimal satu bulan kalender sesuai izin server. Boot, waktu dan kunci perangkat tetap diperiksa.",style=MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick=onValidate,enabled=!locked&&!state.offline){Text("Validasi akses online")}
            }}
            val sync:@Composable ()->Unit={AppSection("Status sinkronisasi perangkat") {
                if(readFailed||push==null) {
                    Text(if(readFailed)"Status antrean belum dapat dibaca. Jumlah belum tersedia." else if(state.scope==null&&!state.loading)"Status belum tersedia untuk akses ini." else "Memuat status antrean…")
                    OutlinedButton(onClick=onReload,enabled=!locked){Text("Muat ulang status")}
                }else {
                    SettingsCount("Menunggu pengiriman",push.pending.toLong()+push.retrying)
                    SettingsCount("Perlu validasi akses",push.authRequired.toLong())
                    SettingsCount("Perlu ditinjau",push.review.toLong())
                    SettingsCount("Diterima server · status lokal",push.synced.toLong())
                    Text(pull?.lastSuccessMs?.let{"Unduhan berhasil terakhir: ${formatJakarta(it)}"}?:"Unduhan berhasil terakhir belum tersedia.")
                    Text("Bukan waktu unggah atau cadangan.",style=MaterialTheme.typography.bodySmall)
                    if(pull!=null&&!pull.initialized&&!recovery)Text("Pengambilan awal belum lengkap; penjualan tetap mengikuti batas kesiapan data.")
                    if(pull?.error!=null&&!recovery)Text("Unduhan belum berhasil. Data tersimpan tidak dianggap baru.")
                    if(push.review>0)Text("Perlu ditinjau: struk asli tetap disimpan. Tidak ada penggantian harga atau pengiriman ulang otomatis.")
                    if(push.authRequired>0)Text("Validasi akses atau persetujuan perangkat diperlukan.")
                    OutlinedButton(onClick=onRetry,enabled=!locked&&!state.offline&&!recovery&&state.scope!=null){Text(if(retrying)"Menjadwalkan…" else "Coba pengiriman ulang")}
                }
                if(scheduled)Text("Pengiriman dijadwalkan; tunggu status berubah. Ini bukan konfirmasi diterima server.",Modifier.semantics{liveRegion=LiveRegionMode.Polite})
            }}
            val export:@Composable ()->Unit={AppSection("Data pemulihan perangkat") {
                Text("Arsip JSON untuk akun, outlet dan perangkat ini. Termasuk operasi lokal yang dipertahankan dan draf, bukan seluruh data server.")
                Text("Bukan CSV laporan atau cadangan server. Tidak berisi kredensial. Pilih tujuan pribadi; checksum bukan enkripsi atau tanda tangan.",style=MaterialTheme.typography.bodySmall)
                Button(modifier=Modifier.focusRequester(exportFocus),onClick=onExport,enabled=!locked&&state.scope!=null){Text("Ekspor data perangkat")}
                if(state.phase!=RecoveryPhase.NONE){Text(when(state.phase){RecoveryPhase.CAPTURE->"Menyiapkan arsip…";RecoveryPhase.PICKER->"Pilih tujuan pada Android…";RecoveryPhase.WRITING->"Menulis arsip…";else->""},Modifier.semantics{liveRegion=LiveRegionMode.Polite});LinearProgressIndicator(Modifier.fillMaxWidth())}
                state.ticket?.let{Text("Snapshot ditangkap ${formatJakarta(it.capturedAtMs)}. Perubahan sesudahnya tidak masuk arsip ini.")}
                if(state.exportDone)Text("Arsip selesai di lokasi yang dipilih. Lindungi salinannya.",Modifier.semantics{liveRegion=LiveRegionMode.Polite})
            }}
            val session:@Composable ()->Unit={AppSection("Sesi akun") {
                Text("Keluar bukan cara memperbaiki sinkronisasi. Data lokal tetap disimpan.")
                OutlinedButton(modifier=Modifier.focusRequester(sessionFocus),onClick=onSignOut,enabled=!locked&&state.scope!=null,colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text(if(state.signingOut)"Keluar…" else "Keluar dari akun")}
            }}
            if(wide)Row(horizontalArrangement=Arrangement.spacedBy(24.dp)){
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp)){access();sync()}
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp)){export();session()}
            }else {access();sync();export();session()}
        }
        if(state.exportConfirm||state.signOutConfirm) {
            AppConfirmation(if(state.exportConfirm)"Ekspor data perangkat?" else "Keluar dari akun?",cancelAction,busy=locked,actions={
                    AppPrimaryButton(if(state.exportConfirm)"Siapkan arsip" else "Keluar dari akun",onClick=if(state.exportConfirm)onPrepare else onConfirmSignOut,enabled=!locked,modifier=Modifier.fillMaxWidth().testTag("settings.confirm"))
                    if(state.signOutConfirm)TextButton(onClick={pendingFocus=true;onCancel()},enabled=!locked){Text("Kembali untuk ekspor")}
                    TextButton(onClick=cancelAction,enabled=!locked){Text("Batal")}
            }) {
                    Text(state.email);Text(state.outletName)
                    state.scope?.let{SelectionContainer{Text(it.sale.deviceId)}}
                    if(state.exportConfirm) {
                        Text("Arsip berisi payload asli, ID operasi/struk, status pengiriman dan draf. Termasuk data diterima server yang dipertahankan untuk rekonsiliasi.")
                        Text("Pilih tujuan pribadi. SHA-256 memeriksa integritas, bukan enkripsi, keaslian atau izin impor. Maksimal 16 MiB UTF-8. Tidak ada pemulihan otomatis.")
                    }else {
                        Text("Keluar menghapus izin masuk lokal. Struk dan antrean lokal tetap disimpan, tetapi pengiriman memerlukan masuk kembali dengan akun dan perangkat yang berwenang.")
                        Text(if(push==null||readFailed)"Keadaan antrean belum dapat dipastikan." else "Menunggu ${push.pending.toLong()+push.retrying} · perlu akses ${push.authRequired} · perlu ditinjau ${push.review}.")
                        Text("Keluar bukan cara memperbaiki sinkronisasi atau data server yang dipulihkan.")
                    }
            }
        }
    }
}
@Composable private fun SettingsCount(label:String,count:Long){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){Text(label,Modifier.weight(1f));Text(count.toString(),style=MaterialTheme.typography.titleMedium)}}
