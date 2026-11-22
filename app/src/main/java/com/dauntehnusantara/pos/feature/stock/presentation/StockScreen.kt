package com.dauntehnusantara.pos.feature.stock.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.core.designsystem.components.*
import com.dauntehnusantara.pos.core.designsystem.formatJakarta
import com.dauntehnusantara.pos.feature.stock.domain.*
import java.util.UUID

@Composable
fun StockRoute(model:StockViewModel,expanded:Boolean) {
    val state by model.state.collectAsStateWithLifecycle()
    StockScreen(state,expanded,model::refresh,model::save,model::retryPending)
}
@Composable
fun StockScreen(state:StockState,expanded:Boolean,onRefresh:(StockQuery)->Unit,onSave:(StockCommand)->Unit,onRetry:()->Unit) {
    val productList=rememberLazyListState()
    LaunchedEffect(state.scope,state.error,state.pending,state.saved) {
        if(state.error!=null||state.pending!=null||state.saved)productList.scrollToItem(0)
    }
    var search by rememberSaveable(state.scope?.key){mutableStateOf(state.query.search)}
    var resolving by remember(state.scope){mutableStateOf<StockReview?>(null)}
    var editing by remember(state.scope){mutableStateOf<StockProduct?>(null)}
    var editEpoch by remember(state.scope){mutableStateOf("")}
    LaunchedEffect(state.pending,state.saved){if(state.pending!=null||state.saved){editing=null;resolving=null}}
    val editable=state.scope!=null&&!state.offline&&!state.busy&&!state.stale&&state.pending==null
    Column(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        // Keep notices in the scrollable content below so short/large-text windows retain all actions.
        val products:@Composable (Modifier)->Unit={ modifier->
            LazyColumn(modifier,state=productList,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)) {
                item {AppScreenHeader("Stok outlet",description=state.outlet)}
                item {Text(if(state.snapshot?.automatic==true)"Penjualan mengurangi stok outlet. Saldo negatif diperbolehkan dan perlu ditinjau." else "Catatan manual. Pengurangan otomatis belum diaktifkan di server.")}
                if(state.offline)item{AppNotice("Offline — hanya melihat catatan tersimpan. Perubahan stok memerlukan internet.")}
                state.snapshot?.let { s->item{Text("Data diambil ${formatJakarta(s.capturedAtMs)} WIB${if(state.stale) " · data tersimpan, perlu diperbarui" else ""}",style=MaterialTheme.typography.bodySmall)} }
                if(state.saved)item{AppNotice("Perubahan stok telah dikonfirmasi server.",tone=NoticeTone.SUCCESS)}
                state.error?.let { error->item {AppNotice(stockError(error),tone=NoticeTone.ERROR)} }
                if(state.pending!=null)item {
                    AppNotice("Ada perubahan yang belum selesai diperiksa. Ulangi pemeriksaan dengan operasi yang sama sebelum membuat perubahan lain.")
                    Text("Jumlah: ${state.pending.quantity} unit · ${state.pending.reason}")
                    OutlinedButton(onClick=onRetry,enabled=!state.busy&&!state.offline){Text("Periksa / ulangi perubahan")}
                }
                if(state.snapshot?.reviews?.isNotEmpty()==true)item {
                    AppNotice("Ada pergerakan yang perlu ditinjau. Maksimum 50 ditampilkan; selesaikan lalu perbarui untuk melihat berikutnya.")
                    state.snapshot.reviews.forEach { r->
                        Text("${r.name}: ${r.quantity} unit · ${when(r.issue){"COUNT_CHANGED"->"hitungan outlet telah diperbarui";"OPENING_REQUIRED"->"stok awal belum diketahui";"FINANCIAL_REVIEW"->"transaksi keuangan perlu ditinjau";"BALANCE_LIMIT"->"saldo perlu diperiksa";else->"penjualan dari versi lama"}}")
                        OutlinedButton(onClick={resolving=r},enabled=editable){Text("Tinjau ${r.name}")}
                    }
                }
                item {
                    AppTextField(search,{search=it.take(120)},"Cari produk",Modifier.fillMaxWidth(),enabled=!state.busy)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick={onRefresh(StockQuery(search=search))},enabled=!state.busy&&!state.offline){Text("Cari / perbarui")}
                        if(state.busy)CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
                if(state.snapshot==null&&!state.busy)item{AppEmptyState("Data stok belum tersedia. Perbarui saat online.")}
                if(state.snapshot?.products?.isEmpty()==true)item{AppEmptyState("Tidak ada produk yang sesuai.")}
                items(state.snapshot?.products.orEmpty(),key={it.id}) { p->
                    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Text(p.name,style=MaterialTheme.typography.titleMedium)
                            Text(p.quantity?.let{"$it unit"}?:"Belum dicatat",style=MaterialTheme.typography.headlineSmall,color=if((p.quantity?:0)<0)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            if(!p.active)Text("Produk diarsipkan")
                            if(editable&&p.active)OutlinedButton(onClick={editing=p;editEpoch=state.snapshot!!.epoch}){Text("Ubah jumlah")}
                            OutlinedButton(onClick={onRefresh(state.query.copy(productId=p.id,before=null))},enabled=!state.busy&&!state.offline){Text("Riwayat produk")}
                        }
                    }
                }
                if(state.snapshot!=null)item{
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        if(state.query.offset>0)OutlinedButton(onClick={onRefresh(state.query.copy(offset=(state.query.offset-100).coerceAtLeast(0)))},enabled=!state.busy&&!state.offline){Text("Produk sebelumnya")}
                        if(state.snapshot.hasMore)OutlinedButton(onClick={onRefresh(state.query.copy(offset=state.query.offset+100))},enabled=!state.busy&&!state.offline){Text("Produk berikutnya")}
                    }
                }
                if(!expanded){item{Text("Riwayat perubahan",style=MaterialTheme.typography.titleLarge)}
                    stockHistory(state,onRefresh)
                }
            }
        }
        if(expanded)Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            products(Modifier.weight(1f))
            LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=24.dp)){
                item{Text("Riwayat perubahan",style=MaterialTheme.typography.titleLarge)}
                stockHistory(state,onRefresh)
            }
        } else products(Modifier.weight(1f))
    }
    editing?.let { product->
        var count by rememberSaveable(product.id,product.revision){mutableStateOf(product.quantity?.toString().orEmpty())}
        var reason by rememberSaveable(product.id,product.revision){mutableStateOf("")}
        val number=StockRules.parse(count)
        val valid=number!=null && reason.trim().length in 3..240 && (product.revision==0L||number!=product.quantity||state.snapshot?.automatic==true)
        AppConfirmation("Ubah jumlah stok",{editing=null},state.busy,actions={
            AppPrimaryButton("Simpan jumlah",{onSave(StockCommand(UUID.randomUUID().toString(),editEpoch,product.id,number!!,product.revision,reason.trim()))},Modifier.fillMaxWidth(),enabled=valid&&editable,busy=state.busy)
            OutlinedButton(onClick={editing=null},enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Batal")}
        }) {
            Text(product.name,style=MaterialTheme.typography.titleLarge)
            Text("Outlet: ${state.outlet}")
            Text("Sebelumnya: ${product.quantity?.let{"$it unit"}?:"belum dicatat"}")
            AppTextField(count,{count=it.take(32)},"Jumlah saat ini (unit)",Modifier.fillMaxWidth(),enabled=!state.busy,isError=count.isNotEmpty()&&number==null,
                supportingText="Bilangan bulat 0–1.000.000.000",keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            AppTextField(reason,{reason=it.take(240)},"Alasan perubahan",Modifier.fillMaxWidth(),enabled=!state.busy,supportingText="Wajib 3–240 karakter")
            if(number!=null)Text("Selisih: ${signed(number-(product.quantity?:0))} unit${if(product.revision==0L) " · pencatatan awal" else ""}")
            if(state.snapshot?.automatic==true)AppNotice("Hitungan fisik baru membuat penjualan offline dengan hitungan lama perlu ditinjau. Pastikan semua perangkat sudah sinkron bila memungkinkan.")
            Text("Catatan lama tetap tersimpan. Jumlah ini hanya berlaku di outlet ini.")
        }
    }
    resolving?.let { r->
        var reason by rememberSaveable(r.operationId,r.productId){mutableStateOf("")}
        var deduct by rememberSaveable(r.operationId,r.productId){mutableStateOf(false)}
        AppConfirmation("Rekonsiliasi stok",{resolving=null},state.busy,actions={
            AppPrimaryButton("Konfirmasi rekonsiliasi",{onSave(StockCommand(UUID.randomUUID().toString(),state.snapshot!!.epoch,r.productId,r.quantity,r.revision,reason.trim(),r.operationId,deduct))},
                enabled=editable&&reason.trim().length in 3..240&&(!deduct||r.balance!=null),busy=state.busy)
            OutlinedButton(onClick={resolving=null},enabled=!state.busy){Text("Batal")}
        }) {
            Text("${r.name}: ${r.quantity} unit")
            Text("Saldo server: ${r.balance?.toString()?:"belum dicatat"}")
            Text("Pilih berdasarkan hitungan fisik. Tindakan ini tidak menerima atau mengubah transaksi keuangan yang ditolak.")
            Column(Modifier.selectableGroup()) {
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(!deduct,enabled=!state.busy,role=androidx.compose.ui.semantics.Role.RadioButton){deduct=false}) {
                    RadioButton(!deduct,null);Text("Sudah termasuk dalam hitungan; jangan kurangi lagi")
                }
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(deduct,enabled=!state.busy&&r.balance!=null,role=androidx.compose.ui.semantics.Role.RadioButton){deduct=true}) {
                    RadioButton(deduct,null,enabled=r.balance!=null);Text("Belum termasuk; kurangi stok sekarang")
                }
            }
            if(r.balance==null)AppNotice("Catat stok awal sebelum memilih pengurangan.")
            AppTextField(reason,{reason=it.take(240)},"Alasan rekonsiliasi",enabled=!state.busy)
        }
    }

}
private fun androidx.compose.foundation.lazy.LazyListScope.stockHistory(state:StockState,onRefresh:(StockQuery)->Unit) {
    item {
        Text(if(state.query.productId==null) "Semua produk · maksimum 50 catatan per halaman" else "Produk terpilih · maksimum 50 catatan per halaman")
        if(state.query.productId!=null||state.query.before!=null)OutlinedButton(onClick={onRefresh(state.query.copy(productId=null,before=null))},enabled=!state.busy&&!state.offline){Text("Semua riwayat terbaru")}
    }
    if(state.snapshot?.history?.isEmpty()==true)item{AppEmptyState("Belum ada perubahan stok pada halaman ini.")}
    items(state.snapshot?.history.orEmpty(),key={it.seq}) { h->
        AppListRow {
            Text(h.productName,style=MaterialTheme.typography.titleMedium)
            Text("${h.previous} → ${h.quantity} unit (${signed(h.delta)})${if(h.revision==1L) " · awal" else ""}")
            Text(h.reason)
            Text("${h.actor.ifBlank{"Staf"}} · ${formatJakarta(h.timeMs)} WIB",style=MaterialTheme.typography.bodySmall)
        }
    }
    if(state.snapshot?.history?.size==50)item{
        OutlinedButton(onClick={onRefresh(state.query.copy(before=state.snapshot.history.last().seq))},enabled=!state.busy&&!state.offline){Text("Riwayat lebih lama")}
    }
}
private fun signed(value:Long)=if(value>0)"+$value" else value.toString()
private fun stockError(code:String)=when(code){
    "CONFLICT"->"Jumlah stok berubah di perangkat lain. Perbarui data dan hitung ulang sebelum menyimpan."
    "INVALID"->"Jumlah tidak berubah atau produk tidak tersedia. Perbarui lalu periksa isian."
    "NOT_CONFIGURED"->"Fitur stok belum dikonfigurasi di server. Hubungi pengelola."
    "AUTH_REQUIRED"->"Akses stok tidak tersedia. Periksa akun dan outlet Anda."
    "REBOOTSTRAP_REQUIRED"->"Data server telah dipulihkan. Simpan ekspor pemulihan dan hubungi pengelola untuk memeriksa operasi stok."
    else->"Belum dapat memastikan data stok. Periksa koneksi lalu ulangi. Perubahan yang tertunda tetap disimpan."
}
