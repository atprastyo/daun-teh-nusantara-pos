package com.dauntehnusantara.pos.feature.catalog.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dauntehnusantara.pos.feature.catalog.domain.*

@Composable
fun CatalogEditorRoute(vm: CatalogEditorViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    CatalogEditorScreen(state, vm::save, vm::retrySync, vm::retryCommand)
}

@Composable
fun CatalogEditorScreen(state: CatalogEditorState, save: (CatalogMutation)->Unit, refresh: ()->Unit, retry: ()->Unit) {
    var kind by remember { mutableStateOf(CatalogRecord.Category) }
    var form by remember { mutableStateOf<CatalogMutation?>(null) }
    LaunchedEffect(state.scope, state.offline) { form = null }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Katalog", style=MaterialTheme.typography.headlineMedium)
        Text("Perubahan berlaku untuk seluruh outlet Daun Teh Nusantara. Pengeditan memerlukan koneksi online.")
        if(state.loading || state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
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
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=refresh,enabled=!state.busy){Text("Muat ulang")}
            if(state.pending!=null) Button(onClick=retry,enabled=!state.busy&&!state.offline){Text("Coba simpan lagi")}
        }
        CatalogRecord.entries.forEach { tab -> FilterChip(selected=kind==tab,onClick={kind=tab},label={Text(tab.name)}) }
        Button(onClick={form=CatalogMutation(kind=kind)},enabled=state.editable){Text("Tambah ${kind.name}")}
        val c=state.catalog
        val rows=when(kind){
            CatalogRecord.Category->c.categories.map{CatalogMutation(kind=kind,id=it.info.id,expectedVersion=it.info.version,name=it.info.name,active=it.info.active,sortOrder=it.sortOrder)}
            CatalogRecord.Product->c.products.map{p->CatalogMutation(kind=kind,id=p.info.id,expectedVersion=p.info.version,name=p.info.name,active=p.info.active,priceIdr=p.basePriceIdr,parentId=p.categoryId,groupIds=c.assignments.filter{it.productId==p.info.id}.sortedBy{it.sortOrder}.map{it.groupId})}
            CatalogRecord.Group->c.groups.map{CatalogMutation(kind=kind,id=it.info.id,expectedVersion=it.info.version,name=it.info.name,active=it.info.active,minSelect=it.minSelect,maxSelect=it.maxSelect)}
            CatalogRecord.Option->c.options.map{CatalogMutation(kind=kind,id=it.info.id,expectedVersion=it.info.version,name=it.info.name,active=it.info.active,priceIdr=it.priceDeltaIdr,parentId=it.groupId)}
        }
        if(!state.loading&&rows.isEmpty()) Text("Belum ada data ${kind.name}.")
        rows.forEach { row -> OutlinedCard(Modifier.fillMaxWidth()) {
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
