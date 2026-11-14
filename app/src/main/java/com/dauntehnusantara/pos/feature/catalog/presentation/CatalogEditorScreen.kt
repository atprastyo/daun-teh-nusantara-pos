package com.dauntehnusantara.pos.feature.catalog.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.dauntehnusantara.pos.feature.catalog.photos.presentation.ProductPhoto
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.feature.catalog.domain.*

@Composable
fun CatalogEditorRoute(vm: CatalogEditorViewModel, launchPhotoPicker:(Product)->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    CatalogEditorScreen(state, vm::save, vm::retrySync, vm::retryCommand,launchPhotoPicker,vm::removePhoto,vm::retryPhoto)
}

@Composable
fun CatalogEditorScreen(state: CatalogEditorState, save: (CatalogMutation)->Unit, refresh: ()->Unit, retry: ()->Unit, pickPhoto:(Product)->Unit={}, removePhoto:(Product)->Unit={}, retryPhoto:()->Unit={}) {
    var kind by remember { mutableStateOf(CatalogRecord.Category) }
    var form by remember { mutableStateOf<CatalogMutation?>(null) }
    LaunchedEffect(state.scope, state.offline) { form = null }
    val c=state.catalog
    val rows=when(kind){
        CatalogRecord.Category->c.categories.map{CatalogMutation(kind=kind,id=it.info.id,expectedVersion=it.info.version,name=it.info.name,active=it.info.active,sortOrder=it.sortOrder)}
        CatalogRecord.Product->c.products.map{p->CatalogMutation(kind=kind,id=p.info.id,expectedVersion=p.info.version,name=p.info.name,active=p.info.active,priceIdr=p.basePriceIdr,parentId=p.categoryId,groupIds=c.assignments.filter{it.productId==p.info.id}.sortedBy{it.sortOrder}.map{it.groupId})}
        CatalogRecord.Group->c.groups.map{CatalogMutation(kind=kind,id=it.info.id,expectedVersion=it.info.version,name=it.info.name,active=it.info.active,minSelect=it.minSelect,maxSelect=it.maxSelect)}
        CatalogRecord.Option->c.options.map{CatalogMutation(kind=kind,id=it.info.id,expectedVersion=it.info.version,name=it.info.name,active=it.info.active,priceIdr=it.priceDeltaIdr,parentId=it.groupId)}
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Katalog", style=MaterialTheme.typography.headlineMedium)
        Text("Perubahan berlaku untuk seluruh outlet Daun Teh Nusantara. Pengeditan memerlukan koneksi online.")
        if(state.loading || state.busy || state.photoBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if(state.offline) Text("Offline — katalog hanya dapat dilihat.")
        if(state.waitingThrough!=null) Text("Tersimpan di server. Menunggu katalog lokal diperbarui…")
        if(state.saved && state.waitingThrough==null) Text("Katalog lokal sudah diperbarui.")
        (state.error ?: state.pull.error)?.let { code ->
            Text(when(code){"CONFLICT"->"Katalog berubah. Muat ulang, lalu periksa perubahan sebelum menyimpan kembali."
                "INVALID"->"Data tidak valid. Periksa harga, kategori, dan pilihan wajib."
                "REBOOTSTRAP_REQUIRED"->"Data server dipulihkan. Tinjau pemulihan melalui Pengaturan."
                "AUTH_REQUIRED"->"Otorisasi diperlukan. Masuk kembali."
                else->"Belum dapat memastikan perubahan. Coba kembali dengan permintaan yang sama."}, color=MaterialTheme.colorScheme.error)
        }
        if(state.photoBusy) Text("Menyiapkan / mengunggah foto…")
        state.photoError?.let { Text(when(it){
            "INVALID_IMAGE"->"Foto tidak didukung atau terlalu besar. Pilih gambar lain (maksimal 16 MB)."
            "CONFLICT"->"Foto berubah di perangkat lain. Muat ulang sebelum memilih lagi."
            "INVALID"->"Foto belum tersedia di server. Periksa penyiapan Storage lalu pilih ulang."
            else->"Foto belum dapat disimpan. Coba kembali; katalog dan checkout tetap tersedia."},color=MaterialTheme.colorScheme.error) }
        if(state.photoCommand!=null) OutlinedButton(onClick=retryPhoto,enabled=!state.photoBusy&&!state.offline){Text("Coba simpan foto lagi")}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=refresh,enabled=!state.busy){Text("Muat ulang")}
            if(state.pending!=null) Button(onClick=retry,enabled=!state.busy&&!state.offline){Text("Coba simpan lagi")}
        }
        CatalogRecord.entries.forEach { tab -> FilterChip(selected=kind==tab,onClick={kind=tab},label={Text(tab.name)}) }
        Button(onClick={form=CatalogMutation(kind=kind)},enabled=state.editable){Text("Tambah ${kind.name}")}
        } }
        if(!state.loading&&rows.isEmpty()) item { Text("Belum ada data ${kind.name}.") }
        items(rows,key={it.id}) { row -> OutlinedCard(Modifier.fillMaxWidth()) {
            if(kind==CatalogRecord.Product) c.products.find{it.info.id==row.id}?.let { product ->
                Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    ProductPhoto(product.imagePath,product.info.name,Modifier.size(80.dp))
                    Column {
                        TextButton(onClick={pickPhoto(product)},enabled=state.editable){Text(if(product.imagePath==null) "Tambah foto" else "Ganti foto")}
                        if(product.imagePath!=null) TextButton(onClick={removePhoto(product)},enabled=state.editable){Text("Hapus foto")}
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp),horizontalArrangement=Arrangement.SpaceBetween){
                Column(Modifier.weight(1f)){Text(row.name);Text(if(row.active) "Aktif · v${row.expectedVersion}" else "Diarsipkan · v${row.expectedVersion}");row.priceIdr?.let{Text("Rp $it")}}
                TextButton(onClick={form=row},enabled=state.editable){Text("Edit")}
            }
        }}
    }
    form?.let { original -> CatalogForm(original,state.catalog,onDismiss={form=null},onSave={if(state.editable){save(it);form=null}}) }
}

@Composable
private fun CatalogForm(original:CatalogMutation,catalog:Catalog,onDismiss:()->Unit,onSave:(CatalogMutation)->Unit) {
    var name by remember(original){mutableStateOf(original.name)}
    var active by remember(original){mutableStateOf(original.active)}
    var price by remember(original){mutableStateOf(original.priceIdr?.toString()?:"0")}
    var min by remember(original){mutableStateOf(original.minSelect?.toString()?:"0")}
    var max by remember(original){mutableStateOf(original.maxSelect?.toString()?:"1")}
    var order by remember(original){mutableStateOf(original.sortOrder.toString())}
    var parent by remember(original){mutableStateOf(original.parentId)}
    var groups by remember(original){mutableStateOf(original.groupIds)}
    val change=original.copy(name=name,active=active,priceIdr=price.toLongOrNull(),minSelect=min.toIntOrNull(),maxSelect=max.toIntOrNull(),sortOrder=order.toIntOrNull()?:-1,parentId=parent,groupIds=groups)
    val valid=runCatching{change.validate()}.isSuccess
    AlertDialog(onDismissRequest=onDismiss,title={Text("${original.kind.name} · ${if(original.expectedVersion==0L) "Tambah" else "Edit"}")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name,{name=it},label={Text("Nama")},singleLine=true)
            Row { Checkbox(active,{active=it});Text("Aktif") }
            when(original.kind){
                CatalogRecord.Category->OutlinedTextField(order,{order=it},label={Text("Urutan (0 atau lebih)")},singleLine=true)
                CatalogRecord.Group->{OutlinedTextField(min,{min=it},label={Text("Minimal pilihan")},singleLine=true);OutlinedTextField(max,{max=it},label={Text("Maksimal pilihan")},singleLine=true)}
                CatalogRecord.Product,CatalogRecord.Option->{
                    OutlinedTextField(price,{price=it},label={Text("Harga IDR (bilangan bulat)")},singleLine=true)
                    Text(if(original.kind==CatalogRecord.Product) "Kategori" else "Grup modifier (tetap setelah dibuat)")
                    val parents=if(original.kind==CatalogRecord.Product)catalog.categories.map{it.info}else catalog.groups.map{it.info}
                    parents.filter{it.active||it.id==parent}.forEach { item ->
                        Row { RadioButton(parent==item.id,{parent=item.id},enabled=original.kind==CatalogRecord.Product||original.expectedVersion==0L);Text(item.name) }
                    }
                    if(original.kind==CatalogRecord.Product){
                        Text("Grup modifier")
                        catalog.groups.filter{it.info.active||it.info.id in original.groupIds}.forEach { g ->
                            Row {Checkbox(g.info.id in groups,{checked->groups=if(checked)groups+g.info.id else groups-g.info.id},enabled=g.info.active||g.info.id in groups);Text(g.info.name)}
                        }
                    }
                }
            }
            if(!valid) Text("Lengkapi nama, nilai bilangan bulat, dan pilihan induk yang sesuai.")
        }
    },confirmButton={TextButton(onClick={onSave(change)},enabled=valid){Text("Simpan")}},dismissButton={TextButton(onClick=onDismiss){Text("Batal")}})
}
