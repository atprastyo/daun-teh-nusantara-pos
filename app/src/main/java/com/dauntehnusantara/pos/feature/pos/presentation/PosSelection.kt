package com.dauntehnusantara.pos.feature.pos.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.dauntehnusantara.pos.core.designsystem.components.AppTextField
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.formatIdr
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*

@Composable
internal fun ModifierPicker(catalog:Catalog,product:Product,busy:Boolean,error:PosError?,draft:Draft?,onAdd:(List<String>,Int)->Unit) {
    var selected by rememberSaveable(product.info.id){mutableStateOf(emptyList<String>())}
    var quantityText by rememberSaveable(product.info.id){mutableStateOf("1")}
    val quantity=quantityText.takeIf{it.matches(Regex("[0-9]+"))}?.toIntOrNull()
    val line=runCatching {
        require(draft!=null && draft.lines.size<CheckoutRules.MAX_LINES)
        CheckoutRules.line(catalog,product.info.id,selected,quantity?:0).also { Math.addExact(draft.totalIdr,it.totalIdr) }
    }.getOrNull()
    Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(product.info.name,style=MaterialTheme.typography.headlineSmall)
            Text("Harga dasar ${formatIdr(product.basePriceIdr)}",style=MaterialTheme.typography.titleMedium)
            catalog.groupsFor(product.info.id).forEach { group ->
                val options=catalog.options.filter{it.groupId==group.info.id&&it.info.active}
                val selectedInGroup=options.count{it.info.id in selected}
                Column(if(group.maxSelect==1)Modifier.selectableGroup() else Modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(group.info.name,style=MaterialTheme.typography.titleMedium)
                    Text("${if(group.minSelect==0)"Opsional" else "Wajib"} · pilih ${group.minSelect}–${group.maxSelect}",style=MaterialTheme.typography.bodySmall)
                    if(options.size<group.minSelect)Text("Pilihan wajib belum tersedia. Hubungi pengelola.",color=MaterialTheme.colorScheme.error)
                    fun clearGroup() { selected=selected.filterNot{id->catalog.options.any{it.info.id==id&&it.groupId==group.info.id}} }
                    if(group.minSelect==0&&group.maxSelect==1) Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selectedInGroup==0,enabled=!busy,role=Role.RadioButton){clearGroup()}.padding(8.dp)) {
                        RadioButton(selectedInGroup==0,onClick=null);Text("Tanpa pilihan",Modifier.padding(start=8.dp))
                    }
                    options.forEach { option ->
                        val checked=option.info.id in selected
                        val enabled=!busy&&(checked||group.maxSelect==1||selectedInGroup<group.maxSelect)
                        val choose={ if(group.maxSelect==1){clearGroup();selected=selected+option.info.id}
                            else selected=if(checked)selected-option.info.id else selected+option.info.id }
                        val selector=if(group.maxSelect==1)Modifier.selectable(checked,enabled=enabled,role=Role.RadioButton,onClick=choose)
                            else Modifier.toggleable(checked,enabled=enabled,role=Role.Checkbox,onValueChange={choose()})
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).then(selector).padding(8.dp).testTag("pos.option.${option.info.id}")) {
                            if(group.maxSelect==1)RadioButton(checked,onClick=null,enabled=enabled)else Checkbox(checked,onCheckedChange=null,enabled=enabled)
                            Text("${option.info.name} · +${formatIdr(option.priceDeltaIdr)}",Modifier.weight(1f).padding(start=8.dp))
                        }
                    }
                }
            }
            if(selected.isNotEmpty())TextButton(onClick={selected=emptyList()},enabled=!busy){Text("Hapus pilihan")}
            AppTextField(quantityText,{quantityText=it},stringResource(R.string.pos_quantity),enabled=!busy,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("pos.quantity"))
            if(draft?.lines?.size==CheckoutRules.MAX_LINES)Text("Keranjang maksimal 100 baris.",color=MaterialTheme.colorScheme.error)
            else if(line==null)Text("Periksa pilihan, jumlah 1–999, dan batas total.",color=MaterialTheme.colorScheme.error)
            else Text("Total pilihan ${formatIdr(line.totalIdr)}",style=MaterialTheme.typography.titleLarge)
            PosErrorText(error)
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        Button(onClick={onAdd(selected,requireNotNull(quantity))},enabled=line!=null&&!busy,modifier=Modifier.fillMaxWidth().testTag("pos.add")){Text(if(busy)"Menambah…" else stringResource(R.string.pos_add))}
        Spacer(Modifier.height(16.dp))
    }
}
