package com.dauntehnusantara.pos

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.stock.domain.*
import com.dauntehnusantara.pos.feature.stock.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w390dp-h844dp")
class StockPresentationTest {
    val compose=createComposeRule()
    @get:Rule val rules:TestRule=RuleChain.outerRule(object:ExternalResource(){
        override fun before(){val app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app,ComponentActivity::class.java))}
    }).around(compose)
    private val id="00000000-0000-4000-8000-000000000001"
    private val scope=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private val snapshot=StockSnapshot(id,1800000000000,listOf(StockProduct(id,"Teh Melati",true,null,0)),false,emptyList())
    private fun ready()=StockState(scope=scope,outlet="Outlet A",snapshot=snapshot,stale=false)
    private fun scroll(text:String){compose.onAllNodes(hasScrollToNodeAction())[0].performScrollToNode(hasText(text))}
    @Test fun countEntryRequiresWholeUnitsAndReasonAndCapturesOriginalRevision() {
        var saved:StockCommand?=null
        compose.setContent{DaunTehTheme{StockScreen(ready(),false,{}, {saved=it},{})}}
        scroll("Ubah jumlah");compose.onNodeWithText("Belum dicatat").assertIsDisplayed();compose.onNodeWithText("Ubah jumlah").performClick()
        compose.onNodeWithText("Simpan jumlah").assertIsNotEnabled()
        compose.onNodeWithText("Jumlah saat ini (unit)").performTextInput("1.5")
        compose.onNodeWithText("Alasan perubahan").performTextInput("Hitung awal")
        compose.onNodeWithText("Simpan jumlah").assertIsNotEnabled()
        compose.onNodeWithText("Jumlah saat ini (unit)").performTextReplacement("12")
        compose.onNodeWithText("Simpan jumlah").performClick()
        compose.runOnIdle{assertEquals(12L,saved!!.quantity);assertEquals(0L,saved!!.expectedRevision);assertEquals(id,saved!!.epoch)}
    }
    @Test fun offlineSnapshotShowsZeroWithoutAllowingWrites() {
        compose.setContent{DaunTehTheme{StockScreen(ready().copy(offline=true,stale=true,snapshot=snapshot.copy(products=listOf(snapshot.products[0].copy(quantity=0,revision=1)))),false,{}, {},{})}}
        scroll("0 unit");compose.onNodeWithText("0 unit").assertIsDisplayed();compose.onNodeWithText("Ubah jumlah").assertDoesNotExist()
        compose.onNodeWithText("Cari / perbarui").assertIsNotEnabled()
    }
    @Test fun uncertainCommandBlocksNewCountAndProvidesSameOperationRetry() {
        var retries=0
        compose.setContent{DaunTehTheme{StockScreen(ready().copy(pending=StockCommand(id,id,id,12,0,"Hitung awal")),false,{}, {},{retries++})}}
        scroll("Periksa / ulangi perubahan");compose.onNodeWithText("Periksa / ulangi perubahan").performClick()
        compose.onNodeWithText("Ubah jumlah").assertDoesNotExist();compose.runOnIdle{assertEquals(1,retries)}
    }
    @Test @Config(qualifiers="w1000dp-h800dp") fun expandedHistoryShowsActorReasonAndSignedMovement() {
        val history=StockMovement(1,id,id,"Teh Melati",10,7,-3,2,"Rani","Hitung ulang",1800000000000)
        compose.setContent{DaunTehTheme{StockScreen(ready().copy(snapshot=snapshot.copy(history=listOf(history))),true,{}, {},{})}}
        compose.onNodeWithText("10 → 7 unit (-3)").assertIsDisplayed();compose.onNodeWithText("Hitung ulang").assertIsDisplayed()
        compose.onNodeWithText("Rani",substring=true).assertIsDisplayed()
    }
    @Test fun stockNavigationOnlyForManagementRoles() {
        assertFalse(AccessDestination.STOCK in RoleNavigation.destinations(Role.CASHIER))
        assertTrue(AccessDestination.STOCK in RoleNavigation.destinations(Role.OWNER))
        assertTrue(AccessDestination.STOCK in RoleNavigation.destinations(Role.MANAGER))
    }
    @Test fun conflictNoticeBecomesVisibleAfterReturningFromCountForm() {
        var value by mutableStateOf(ready().copy(snapshot=snapshot.copy(products=List(20){snapshot.products[0].copy(id="p$it",name="Tea $it")})))
        compose.setContent{DaunTehTheme{StockScreen(value,false,{}, {},{})}}
        scroll("Tea 19")
        compose.runOnIdle{value=value.copy(error="CONFLICT",stale=true)}
        compose.onNodeWithText("Jumlah stok berubah di perangkat lain.",substring=true).assertIsDisplayed()
    }

    @Test fun reconciliationCapturesChoiceReasonAndExpectedRevision() {
        var saved:StockCommand?=null
        val review=StockReview(id,id,"Teh Melati",3,"COUNT_CHANGED",8,2)
        compose.setContent{DaunTehTheme{StockScreen(ready().copy(snapshot=snapshot.copy(automatic=true,reviews=listOf(review))),false,{}, {saved=it},{})}}
        scroll("Tinjau Teh Melati");compose.onNodeWithText("Tinjau Teh Melati").performClick()
        compose.onNodeWithText("Konfirmasi rekonsiliasi").assertIsNotEnabled()
        compose.onNodeWithText("Belum termasuk; kurangi stok sekarang").performClick()
        compose.onNodeWithText("Alasan rekonsiliasi").performTextInput("Belum termasuk stok fisik")
        compose.onNodeWithText("Konfirmasi rekonsiliasi").performClick()
        compose.runOnIdle{assertTrue(saved!!.deduct);assertEquals(id,saved!!.saleOperationId);assertEquals(2L,saved!!.expectedRevision);assertEquals(3L,saved!!.quantity)}
    }
    @Test fun insufficientStockWarningDoesNotDisablePayment() {
        var payments=0
        val line=com.dauntehnusantara.pos.feature.checkout.domain.CartLine(id,id,"Teh Melati",1,1,5000,1,emptyList(),emptyList())
        val draft=com.dauntehnusantara.pos.feature.checkout.domain.Draft(id,id,0,listOf(line))
        compose.setContent{DaunTehTheme{com.dauntehnusantara.pos.feature.pos.presentation.CheckoutForm(draft,false,null,{payments++},{},demo=false,
            stockWarnings=listOf("Stok tidak cukup; penjualan tetap diizinkan."))}}
        compose.onNodeWithText("Stok tidak cukup; penjualan tetap diizinkan.").assertExists()
        compose.onNodeWithTag("pos.cash").performTextInput("5000");compose.onNodeWithTag("pos.save").assertIsEnabled();compose.onNodeWithTag("pos.save").performClick()
        compose.runOnIdle{assertEquals(1,payments)}
    }

}
