package com.dauntehnusantara.pos

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.history.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.PullStatus
import java.time.*
import org.junit.*
import org.junit.Assert.*

class HistoryScreenTest {
    @get:Rule val compose=createComposeRule()
    private val session=HistorySession()
    private fun receipt(name:String,at:String,method:PaymentMethod=PaymentMethod.CASH):Receipt {
        val category=Category(RecordInfo(brandId=DEMO_BRAND_ID,name="Tea"));val product=Product(RecordInfo(brandId=DEMO_BRAND_ID,name=name),category.info.id,6000)
        val line=CheckoutRules.line(Catalog(categories=listOf(category),products=listOf(product)),product.info.id,emptyList(),1)
        return Receipt(newId(),newId(),DEMO_BRAND_ID,"outlet","device","cashier",Instant.parse(at).toEpochMilli(),listOf(line),6000,
            CheckoutRules.payment(6000,if(method==PaymentMethod.CASH)PaymentInput(method,10000)else PaymentInput(method,manuallyVerified=true)))
    }
    private val a=receipt("Teh Melati","2026-11-14T17:10:00Z")
    private val b=receipt("Teh Lemon","2026-11-14T16:55:00Z",PaymentMethod.QRIS_MANUAL)
    private fun ready()=HistoryState(scopeKey="a",receipts=listOf(a,b),deliveries=mapOf(a.id to ReceiptDelivery(ReceiptDeliveryState.PENDING),b.id to ReceiptDelivery(ReceiptDeliveryState.SYNCED)),loading=false,
        pull=PullStatus(1,true,100),allowed=true,today=LocalDate.of(2026,11,15))
    private fun row(receipt:Receipt)=compose.onNodeWithTag("history.receipt.${receipt.id}")
    private fun showRow(receipt:Receipt):SemanticsNodeInteraction {
        compose.onNodeWithTag("history.list").performScrollToNode(hasTestTag("history.receipt.${receipt.id}"))
        return row(receipt)
    }
    private fun search(value:String){compose.onNodeWithTag("history.search").performScrollTo().performTextClearance();compose.onNodeWithTag("history.search").performTextInput(value)}
    @Test fun originalNameAndFullIdSearchAreCacheLimitedWithDistinctNoMatch() {
        compose.setContent{DaunTehTheme{HistoryScreen(ready(),session,{})}}
        compose.onNodeWithText("Hingga 100 struk",substring=true).assertExists()
        search("LEMON");showRow(b).assertExists();row(a).assertDoesNotExist()
        search(a.id);showRow(a).assertExists();row(b).assertDoesNotExist()
        search("absent");compose.onNodeWithText("Tidak ada struk yang cocok dalam cache ini.").assertExists()
        compose.onNodeWithText("Hapus pencarian/filter").performScrollTo().performClick();showRow(a).assertExists();showRow(b).assertExists()
    }
    @Test fun dateAndProofFiltersIntersectAndClearingDoesNotDeleteRows() {
        compose.setContent{DaunTehTheme{HistoryScreen(ready(),session,{})}}
        compose.onNodeWithTag("history.date").performScrollTo().performClick();compose.onNodeWithText("Hari ini",substring=false).performClick()
        showRow(a).assertExists();row(b).assertDoesNotExist()
        compose.onNodeWithTag("history.status").performScrollTo().performClick();compose.onNodeWithText("Diterima server",substring=false).performClick()
        row(a).assertDoesNotExist();row(b).assertDoesNotExist()
        compose.onNodeWithText("Hapus pencarian/filter").performScrollTo().performClick();showRow(a).assertExists();showRow(b).assertExists()
    }
    @Test fun focusedDetailIsReadOnlyAndBackRestoresQueryAndSelectedRowFocus() {
        val snapshot=ready()
        compose.setContent{DaunTehTheme{HistoryScreen(snapshot,session,{})}}
        search("melati");showRow(a).performClick()
        compose.onNodeWithText("Struk tersedia di perangkat").assertExists();compose.onNodeWithText(a.id).assertExists()
        compose.onNodeWithText("Penjualan berikutnya").assertDoesNotExist();compose.onNodeWithText("Coba pengiriman outlet lagi").assertDoesNotExist()
        compose.onNodeWithText("Kembali ke riwayat").performClick();showRow(a).assertExists()
        compose.runOnIdle{assertEquals("melati",session.state.value.query);assertNull(session.state.value.selectedId);assertEquals(listOf(a,b),snapshot.receipts)}
    }
    @Test fun recoveryReviewKeepsOriginalReceiptAndHasNoRetryOrChargeAgain() {
        val snapshot=ready().copy(deliveries=mapOf(a.id to ReceiptDelivery(ReceiptDeliveryState.REVIEW,true)),pull=PullStatus(initialized=false,error="REBOOTSTRAP_REQUIRED"))
        compose.setContent{DaunTehTheme{HistoryScreen(snapshot,session,{})}}
        showRow(a).performClick()
        compose.onNodeWithText("Jangan hapus data aplikasi",substring=true).assertExists()
        compose.onNodeWithText("Perlu ditinjau · katalog berubah").assertExists();compose.onNodeWithText("Coba pengiriman outlet lagi").assertDoesNotExist()
        compose.onNodeWithText("Simpan penjualan lokal").assertDoesNotExist();compose.onNodeWithText("Penjualan berikutnya").assertDoesNotExist()
    }
    @Test fun missingProofUsesUnknownAndManualQrisDoesNotShowCashTender() {
        compose.setContent{DaunTehTheme{HistoryScreen(ready().copy(deliveries=emptyMap()),session,{})}}
        showRow(b).performClick()
        compose.onNodeWithText("Status pengiriman belum tersedia").assertExists()
        compose.onNodeWithText("QRIS dikonfirmasi manual oleh kasir",substring=true).assertExists()
        compose.onNodeWithText("Uang diterima",substring=true).assertDoesNotExist()
    }
    @Test fun refreshRequestDisablesRepeatAndRetainsFiltersAndKnownRows() {
        var state by mutableStateOf(ready());var calls=0
        compose.setContent{DaunTehTheme{HistoryScreen(state,session,{calls++;state=state.copy(refreshing=true)})}}
        search("melati");compose.onNodeWithTag("history.refresh").performScrollTo().performClick()
        compose.onNodeWithTag("history.refresh").assertIsNotEnabled();showRow(a).assertExists()
        compose.runOnIdle{assertEquals(1,calls);assertEquals("melati",session.state.value.query);state=state.copy(refreshing=false,refresh=HistoryRefresh.LOCAL)}
        compose.onNodeWithTag("history.list").performScrollToNode(hasText("Pembacaan lokal berhasil.",substring=true))
        compose.onNodeWithText("Pembacaan lokal berhasil.",substring=true).assertIsDisplayed()
    }
    @Test fun boundedSnapshotEvictionLeavesListAndReportsSelectionCleared() {
        var state by mutableStateOf(ready())
        compose.setContent{DaunTehTheme{HistoryScreen(state,session,{})}}
        showRow(a).performClick();compose.runOnIdle{state=state.copy(receipts=listOf(b))}
        showRow(b).assertExists();compose.onNodeWithText("Struk tidak lagi ada",substring=true).assertExists();compose.onNodeWithText("Struk penjualan").assertDoesNotExist()
    }
    @Test fun retainedReadFailureAndInitialEmptyNeverClaimNoSales() {
        var state by mutableStateOf(ready().copy(loadFailed=true))
        compose.setContent{DaunTehTheme{HistoryScreen(state,session,{})}}
        showRow(a).assertExists();compose.onNodeWithText("Belum ada struk di cache ini.",substring=true).assertDoesNotExist()
        compose.runOnIdle{state=state.copy(receipts=emptyList(),loadFailed=false,pull=PullStatus())}
        compose.onNodeWithText("Tunggu sinkronisasi awal",substring=true).assertExists()
        compose.onNodeWithText("Belum ada struk di cache ini.",substring=true).assertDoesNotExist()
    }
}
