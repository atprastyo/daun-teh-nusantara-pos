package com.dauntehnusantara.pos.feature.pos.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.core.designsystem.*
import com.dauntehnusantara.pos.core.designsystem.components.*
import com.dauntehnusantara.pos.feature.checkout.domain.*

internal fun Modifier.semanticsLabel(label:String)=semantics{contentDescription=label}

@Composable
internal fun PosNotice(message:String) {
    AppNotice(message)
}

@Composable
internal fun PosErrorText(error:PosError?) {
    if(error!=null)AppErrorText(stringResource(when(error){PosError.CONFLICT->R.string.pos_conflict;PosError.INVALID->R.string.pos_invalid;PosError.OVERFLOW->R.string.pos_overflow;PosError.STORAGE->R.string.pos_storage_error}))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CheckoutForm(draft:Draft,busy:Boolean,error:PosError?,onPay:(PaymentInput)->Unit,onBack:()->Unit,demo:Boolean=true,stale:Boolean=false,stockWarnings:List<String> = emptyList()) {
    var methodName by rememberSaveable(draft.id,draft.revision){mutableStateOf(PaymentMethod.CASH.name)}
    var cashText by rememberSaveable(draft.id,draft.revision){mutableStateOf("")}
    // Manual confirmation is intentionally memory-only; recreation requires rechecking.
    var verified by remember(draft.id,draft.revision){mutableStateOf(false)}
    val method=PaymentMethod.valueOf(methodName)
    val tender=moneyInputLongOrNull(cashText)
    val input=PaymentInput(method,if(method==PaymentMethod.CASH)tender else null,verified)
    val result=runCatching{CheckoutRules.payment(draft.totalIdr,input)}.getOrNull()
    Box(Modifier.fillMaxSize(),contentAlignment=androidx.compose.ui.Alignment.TopCenter) {
        Column(Modifier.widthIn(max=560.dp).fillMaxHeight().imePadding().padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                TextButton(onClick=onBack,enabled=!busy){Text(stringResource(R.string.pos_back_cart))}
                Text(stringResource(R.string.pos_checkout),style=MaterialTheme.typography.headlineMedium)
                Text(stringResource(R.string.pos_total,formatIdr(draft.totalIdr)),style=MaterialTheme.typography.headlineSmall)
                stockWarnings.forEach{PosNotice(it)}
                draft.lines.forEach{Text("${it.quantity} × ${it.productName} · ${formatIdr(it.totalIdr)}")}
                if(stale)PosNotice("Katalog berubah. Kembali ke keranjang, hapus item yang berubah lalu pilih ulang.")
                Column(Modifier.selectableGroup()) {
                    PaymentMethod.entries.forEach { choice ->
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(method==choice,enabled=!busy,role=Role.RadioButton){methodName=choice.name;verified=false}.padding(vertical=8.dp)) {
                            RadioButton(method==choice,onClick=null);Text(stringResource(if(choice==PaymentMethod.CASH)R.string.pos_cash else R.string.pos_qris),Modifier.padding(start=12.dp))
                        }
                    }
                }
                if(method==PaymentMethod.CASH) {
                    MoneyInputField(cashText,{cashText=it},stringResource(R.string.pos_tendered),enabled=!busy,
                        isError=cashText.isNotEmpty()&&result==null,modifier=Modifier.fillMaxWidth().testTag("pos.cash"))
                    Text("Pilih nominal uang diterima",style=MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        listOf(5000L,10000L,20000L,50000L,100000L).forEach { amount ->
                            OutlinedButton(onClick={cashText=amount.toString()},enabled=!busy,
                                modifier=Modifier.heightIn(min=48.dp).testTag("pos.cash.preset.$amount")) {
                                Text(formatIdr(amount))
                            }
                        }
                        TextButton(onClick={cashText=draft.totalIdr.toString()},enabled=!busy,
                            modifier=Modifier.heightIn(min=48.dp).testTag("pos.cash.exact")){Text("Uang pas")}
                    }
                    if(result==null)Text(stringResource(R.string.pos_cash_invalid),color=MaterialTheme.colorScheme.error)
                    else Text(stringResource(R.string.pos_change,formatIdr(requireNotNull(result.changeIdr))),style=MaterialTheme.typography.titleLarge)
                } else {
                    PosNotice(stringResource(R.string.pos_qris_notice))
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(verified,enabled=!busy,role=Role.Checkbox,onValueChange={verified=it}).padding(vertical=8.dp)) {
                        Checkbox(verified,onCheckedChange=null);Text(stringResource(R.string.pos_qris_confirm),Modifier.weight(1f).padding(start=8.dp))
                    }
                }
                PosErrorText(error)
                if(demo)Text(stringResource(R.string.pos_demo))
                Text("Struk lokal berbeda dari penerimaan server.",style=MaterialTheme.typography.bodySmall)
            }
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            AppPrimaryButton(stringResource(if(busy)R.string.pos_saving else R.string.pos_save),onClick={onPay(input)},enabled=result!=null&&!stale,busy=busy,modifier=Modifier.fillMaxWidth().testTag("pos.save"))
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun UncertainCheckout(state:PosUiState,actions:PosActions) {
    BackHandler { /* A frozen command must be reconciled, not replaced. */ }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(if(state.busy)"Menyimpan / memeriksa hasil lokal…" else "Periksa hasil penyimpanan",style=MaterialTheme.typography.headlineMedium)
        PosNotice("Jangan meminta pembayaran lagi atau membuat penjualan pengganti. Periksa struk untuk perintah asli sebelum mencoba menyimpan.")
        state.pendingDraft?.let { draft ->
            Text("Total asli ${formatIdr(draft.totalIdr)}",style=MaterialTheme.typography.headlineSmall)
            draft.lines.forEach { Text("${it.quantity} × ${it.productName} · ${formatIdr(it.totalIdr)}") }
        }
        state.pending?.payment?.let { payment ->
            Text(if(payment.method==PaymentMethod.CASH)"Tunai · uang diterima ${formatIdr(payment.tenderedIdr?:0)}" else "QRIS · dikonfirmasi manual oleh kasir")
        }
        if(state.recoveryRequired)PosNotice(stringResource(R.string.recovery_required))
        PosErrorText(state.error)
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick=actions.reconcile,enabled=!state.busy,modifier=Modifier.fillMaxWidth()){Text("Periksa struk tersimpan")}
        if(state.retryLocalAllowed)Button(onClick=actions.retryLocal,enabled=!state.busy&&!state.syncRequired&&!state.recoveryRequired,modifier=Modifier.fillMaxWidth()){Text("Coba simpan perintah yang sama")}
    }
}

@Composable
fun ReceiptContent(receipt:Receipt,modifier:Modifier=Modifier,onNext:(()->Unit)?=null,busy:Boolean=false,error:PosError?=null,demo:Boolean=true,
    delivery:ReceiptDelivery?=null,onRetryDelivery:(()->Unit)?=null,notice:String?=null,history:Boolean=false) {
    Box(modifier,contentAlignment=androidx.compose.ui.Alignment.TopCenter) {
        Column(Modifier.widthIn(max=620.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            PosErrorText(error)
            Text(stringResource(R.string.pos_receipt),style=MaterialTheme.typography.headlineMedium)
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large) {
                Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(if(demo)R.string.pos_local_saved else if(history)R.string.history_ui_available else R.string.pos_real_saved),style=MaterialTheme.typography.titleMedium)
                    Text("Pembayaran sudah dicatat. Jangan menagih ulang.")
                }
            }
            if(notice!=null)PosNotice(notice)
            Text(stringResource(R.string.app_name),style=MaterialTheme.typography.titleMedium)
            Text(formatJakarta(receipt.createdAtUtcMs),style=MaterialTheme.typography.bodySmall)
            Text("ID struk",style=MaterialTheme.typography.labelMedium);SelectionContainer { Text(receipt.id,style=MaterialTheme.typography.bodySmall) }
            receipt.lines.forEach { line ->
                HorizontalDivider();Text(line.productName,style=MaterialTheme.typography.titleMedium)
                line.modifiers.forEach{Text(stringResource(R.string.pos_receipt_modifier,it.groupName,it.optionName,formatIdr(it.priceDeltaIdr)),style=MaterialTheme.typography.bodySmall)}
                Text("${line.quantity} × ${formatIdr(line.unitPriceIdr)}");Text(formatIdr(line.totalIdr),style=MaterialTheme.typography.titleMedium)
            }
            HorizontalDivider();Text(stringResource(R.string.pos_total,formatIdr(receipt.totalIdr)),style=MaterialTheme.typography.headlineSmall)
            Text(stringResource(if(receipt.payment.method==PaymentMethod.CASH)R.string.pos_cash else R.string.pos_qris))
            receipt.payment.tenderedIdr?.let{Text(stringResource(R.string.pos_paid,formatIdr(it)))}
            receipt.payment.changeIdr?.let{Text(stringResource(R.string.pos_change,formatIdr(it)))}
            if(receipt.payment.method==PaymentMethod.QRIS_MANUAL)Text(stringResource(R.string.pos_qris_manual_receipt))
            if(demo)Text(stringResource(R.string.pos_demo))else ReceiptDeliveryContent(delivery,busy,if(history)null else onRetryDelivery)
            if(!history&&onNext!=null)Button(onClick=onNext,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.pos_next))}
        }
    }
}

@Composable
private fun ReceiptDeliveryContent(delivery:ReceiptDelivery?,busy:Boolean,retry:(()->Unit)?) {
    delivery?.stockStatus?.let { stock->
        PosNotice(when(stock){"APPLIED"->"Stok telah diproses oleh server, terpisah dari status transaksi keuangan.";"REVIEW"->"Pergerakan stok perlu ditinjau Owner/Manager. Status pembayaran asli tetap dipertahankan.";else->"Pengurangan stok tersimpan di perangkat dan menunggu konfirmasi server."})
    }
    val status=delivery?.state?:ReceiptDeliveryState.UNKNOWN
    val label=when(status){
        ReceiptDeliveryState.PENDING->"Menunggu kirim"
        ReceiptDeliveryState.RETRY->"Pengiriman belum pasti"
        ReceiptDeliveryState.AUTH_REQUIRED->"Pengiriman menunggu validasi akses"
        ReceiptDeliveryState.REVIEW->if(delivery?.catalogChanged==true)"Perlu ditinjau · katalog berubah" else "Perlu ditinjau"
        ReceiptDeliveryState.SYNCED->"Diterima server"
        ReceiptDeliveryState.UNKNOWN->"Status pengiriman belum tersedia"
    }
    val help=when(status){
        ReceiptDeliveryState.PENDING->"Struk tersimpan di perangkat; penerimaan server belum dikonfirmasi."
        ReceiptDeliveryState.RETRY->"Permintaan asli akan dicoba kembali tanpa mengubah struk."
        ReceiptDeliveryState.AUTH_REQUIRED->"Hubungkan kembali dan validasi akses melalui Setelan. Struk tetap tersimpan."
        ReceiptDeliveryState.REVIEW->"Hubungi pemilik/pengelola. Harga dan pembayaran asli tetap dipertahankan. Jangan membuat penjualan pengganti atau menagih ulang."
        ReceiptDeliveryState.SYNCED->"Penerimaan server terkonfirmasi. Ini bukan bukti cadangan terpisah atau verifikasi QRIS oleh bank."
        ReceiptDeliveryState.UNKNOWN->"Metadata struk ini belum cukup untuk memastikan pengiriman. Periksa status melalui Setelan."
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(label,style=MaterialTheme.typography.titleMedium,modifier=Modifier.semantics{liveRegion=LiveRegionMode.Polite});Text(help)
            if(retry!=null&&status in listOf(ReceiptDeliveryState.PENDING,ReceiptDeliveryState.RETRY)) {
                TextButton(onClick=retry,enabled=!busy){Text("Coba pengiriman outlet lagi")}
                Text("Antrean akun/perangkat ini pada outlet aktif. Hasil server tetap ditunggu.",style=MaterialTheme.typography.bodySmall)
            }
        }
    }
}
