package com.dauntehnusantara.pos

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.pos.presentation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w390dp-h844dp")
class PosPresentationTest {
    val compose=createComposeRule()
    @get:Rule val rules:TestRule=RuleChain.outerRule(object:ExternalResource(){
        override fun before(){val app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app,ComponentActivity::class.java))}
    }).around(compose)
    private fun ready(): PosUiState {
        val category = Category(RecordInfo(brandId = DEMO_BRAND_ID, name = "Teh"))
        val product = Product(RecordInfo(brandId = DEMO_BRAND_ID, name = "Teh contoh"), category.info.id, 6000)
        val catalog = Catalog(categories = listOf(category), products = listOf(product))
        val line = CheckoutRules.line(catalog, product.info.id, emptyList(), 1)
        return PosUiState(catalog, CheckoutState(Draft(newId(), newId(), 1, listOf(line)), null, 0), loading = false)
    }
    @Test fun compactCheckoutRejectsMissingCashAndDisablesRepeatSubmit() {
        var state by mutableStateOf(ready())
        var submissions = 0
        var paid: PaymentInput? = null
        compose.setContent { DaunTehTheme { PosScreen(state, PosActions(pay = { paid = it; submissions++; state = state.copy(busy = true) }), demoEnabled = true) } }
        compose.onNodeWithText("Keranjang (1)", substring = true).performClick()
        compose.onNodeWithText("Pembayaran").performClick()
        compose.onNodeWithText("Simpan penjualan lokal").assertIsNotEnabled()
        compose.onNodeWithText("Uang diterima (IDR)").performTextInput("10000")
        compose.onNodeWithText("Simpan penjualan lokal").assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("Menyimpan…").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, submissions); assertEquals(10000L, paid?.tenderedIdr) }
    }
    @Test fun cashShortcutsReplaceAmountValidateChangeAndSubmitExactTender() {
        var paid:PaymentInput?=null
        compose.setContent{DaunTehTheme{CheckoutForm(ready().checkout!!.draft,false,null,{paid=it},{})}}
        compose.onNodeWithTag("pos.cash").performTextInput("12345")
        for(amount in listOf(5000L,10000L,20000L,50000L,100000L)) {
            compose.onNodeWithTag("pos.cash.preset.$amount").performScrollTo().performClick()
            compose.onNodeWithTag("pos.cash").assertTextContains(amount.toString())
            if(amount<6000L)compose.onNodeWithTag("pos.save").assertIsNotEnabled()
            else compose.onNodeWithTag("pos.save").assertIsEnabled()
        }
        compose.onNodeWithText("Kembalian: ${com.dauntehnusantara.pos.core.designsystem.formatIdr(94000L)}").assertExists()
        compose.onNodeWithTag("pos.save").performClick()
        compose.runOnIdle{assertEquals(100000L,paid?.tenderedIdr)}
        compose.onNodeWithTag("pos.cash.exact").performScrollTo().performClick()
        compose.onNodeWithTag("pos.cash").assertTextContains("6000")
        compose.onNodeWithTag("pos.save").performClick()
        compose.runOnIdle{assertEquals(6000L,paid?.tenderedIdr)}
    }
    @Test fun cashShortcutsDisableWhileSavingAndHideForQris() {
        var busy by mutableStateOf(false)
        compose.setContent{DaunTehTheme{CheckoutForm(ready().checkout!!.draft,busy,null,{},{})}}
        compose.runOnIdle{busy=true}
        for(amount in listOf(5000L,10000L,20000L,50000L,100000L))
            compose.onNodeWithTag("pos.cash.preset.$amount").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("pos.cash.exact").performScrollTo().assertIsNotEnabled()
        compose.runOnIdle{busy=false}
        compose.onNodeWithText("QRIS · konfirmasi manual").performScrollTo().performClick()
        compose.onNodeWithTag("pos.cash.preset.5000").assertDoesNotExist()
    }
    @Test @Config(qualifiers="w1200dp-h900dp") fun cashShortcutsRemainReachableOnTabletWithLargeText() {
        compose.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                DaunTehTheme{CheckoutForm(ready().checkout!!.draft,false,null,{},{})}
            }
        }
        for(amount in listOf(5000L,10000L,20000L,50000L,100000L))
            compose.onNodeWithTag("pos.cash.preset.$amount").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("pos.cash.exact").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("pos.save").assertIsEnabled()
    }
    @Test fun qrisNeedsOperatorConfirmation() {
        var paid: PaymentInput? = null
        compose.setContent { DaunTehTheme { PosScreen(ready(), PosActions(pay = { paid = it }), demoEnabled = true) } }
        compose.onNodeWithText("Keranjang (1)", substring = true).performClick()
        compose.onNodeWithText("Pembayaran").performClick()
        compose.onNodeWithText("QRIS · konfirmasi manual").performClick()
        compose.onNodeWithText("Simpan penjualan lokal").assertIsNotEnabled()
        compose.onNodeWithText("Saya telah memeriksa dan memastikan pembayaran diterima.").performClick()
        compose.onNodeWithText("Simpan penjualan lokal").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(PaymentMethod.QRIS_MANUAL, paid?.method); assertEquals(true, paid?.manuallyVerified); assertNull(paid?.tenderedIdr) }
    }
    @Test fun releaseDoesNotExposeDemoCheckout() {
        compose.setContent { DaunTehTheme { PosScreen(ready(), PosActions(), demoEnabled = false) } }
        compose.onNodeWithText("Kasir belum tersedia", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Simpan penjualan lokal").assertDoesNotExist()
    }
    @Test fun realCheckoutRequiresInitialCatalogPull() {
        compose.setContent { DaunTehTheme { PosScreen(ready().copy(syncRequired = true), PosActions(), demoEnabled = true, demo = false) } }
        compose.onNodeWithText("Sinkronkan katalog secara online", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Simpan penjualan lokal").assertDoesNotExist()
    }
    @Test fun realReceiptNeverUsesDemoOrUnsentDemoLabels() {
        val state = ready()
        val draft = requireNotNull(state.checkout).draft
        val receipt = Receipt(draft.id, draft.operationId, "brand", "outlet", "device", "cashier", 0, draft.lines, draft.totalIdr,
            CheckoutRules.payment(draft.totalIdr, PaymentInput(PaymentMethod.CASH, 100000)))
        compose.setContent { DaunTehTheme { ReceiptContent(receipt, demo = false) } }
        compose.onNodeWithText("Struk tersimpan di perangkat.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("belum dikirim (demo)", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Mode demo lokal", substring = true).assertDoesNotExist()
    }

    @Test fun restoredFeedPreservesRecoveryInstructionsAndBlocksNewSales() {
        compose.setContent { DaunTehTheme { PosScreen(ready().copy(syncRequired=true,recoveryRequired=true),PosActions(),demoEnabled=true,demo=false) } }
        compose.onNodeWithText("Jangan hapus data aplikasi",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Simpan penjualan lokal").assertDoesNotExist()
    }


    @Test fun searchAndCategoryFiltersDoNotAlterPersistedCart() {
        val state=ready()
        compose.setContent{DaunTehTheme{PosScreen(state,PosActions(),demoEnabled=true)}}
        compose.onNodeWithTag("pos.search").performTextInput("absent")
        compose.onNodeWithText("Tidak ada hasil yang cocok.").assertExists()
        compose.onNodeWithText("Hapus pencarian/filter").performScrollTo().performClick()
        compose.onNodeWithText("Teh contoh").assertExists()
        compose.onNodeWithText("Keranjang (1)",substring=true).assertExists()
    }
    @Test fun failedAddKeepsSelectionUntilObservedSuccess() {
        var state by mutableStateOf(ready())
        var count=0
        val product=state.catalog.products.single()
        compose.setContent{DaunTehTheme{PosScreen(state,PosActions(add={_,_,_->count++;state=if(count==1)state.copy(error=PosError.STORAGE)else state.copy(added=state.added+1,error=null)}),demoEnabled=true)}}
        compose.onNodeWithTag("pos.product.${product.info.id}").performScrollTo().performClick()
        compose.onNodeWithTag("pos.add").performClick()
        compose.onNodeWithTag("pos.quantity").assertExists()
        compose.runOnIdle{assertEquals(1,count)}
        compose.onNodeWithTag("pos.add").performClick()
        compose.waitForIdle();compose.onNodeWithTag("pos.quantity").assertDoesNotExist()
    }
    @Test fun qrisConfirmationResetsOnMethodAndDraftRevisionChanges() {
        var state by mutableStateOf(ready())
        compose.setContent{DaunTehTheme{PosScreen(state,PosActions(),demoEnabled=true)}}
        compose.onNodeWithText("Keranjang (1)",substring=true).performClick();compose.onNodeWithText("Pembayaran").performClick()
        compose.onNodeWithText("QRIS · konfirmasi manual").performScrollTo().performClick()
        compose.onNodeWithText("Saya telah memeriksa dan memastikan pembayaran diterima.").performScrollTo().performClick()
        compose.onNodeWithTag("pos.save").assertIsEnabled()
        compose.onNodeWithText("Tunai").performScrollTo().performClick();compose.onNodeWithText("QRIS · konfirmasi manual").performScrollTo().performClick()
        compose.onNodeWithTag("pos.save").assertIsNotEnabled()
        compose.onNodeWithText("Saya telah memeriksa dan memastikan pembayaran diterima.").performScrollTo().performClick()
        compose.runOnIdle{val cart=state.checkout!!;state=state.copy(checkout=cart.copy(draft=cart.draft.copy(revision=cart.draft.revision+1)))}
        compose.onNodeWithTag("pos.save").assertIsNotEnabled();compose.onNodeWithTag("pos.cash").assertExists()
    }
    @Test fun receiptRemainsReadableDuringRecoveryAndReviewNeverOffersResubmit() {
        val state=ready();val d=state.checkout!!.draft
        val receipt=Receipt(d.id,d.operationId,"brand","outlet","device","cashier",0,d.lines,d.totalIdr,CheckoutRules.payment(d.totalIdr,PaymentInput(PaymentMethod.CASH,10000)))
        val cart=state.checkout!!.copy(receipt=receipt,deliveries=mapOf(receipt.id to ReceiptDelivery(ReceiptDeliveryState.REVIEW,true)))
        compose.setContent{DaunTehTheme{PosScreen(state.copy(checkout=cart,syncRequired=true,recoveryRequired=true),PosActions(),demoEnabled=true,demo=false)}}
        compose.onNodeWithText("Struk tersimpan di perangkat.",substring=true).assertExists()
        compose.onNodeWithText("Perlu ditinjau · katalog berubah").assertExists()
        compose.onNodeWithText("Coba pengiriman outlet lagi").assertDoesNotExist()
        compose.onNodeWithText("Penjualan berikutnya").performScrollTo().assertIsNotEnabled()
    }
    @Test fun aggregatePendingNeverClaimsReceiptDeliveryAndUnknownCommandCannotPay() {
        val state=ready();val d=state.checkout!!.draft
        compose.setContent{DaunTehTheme{PosScreen(state.copy(pending=CheckoutCommand(d.id,d.operationId,d.revision,PaymentInput(PaymentMethod.CASH,10000))),PosActions(),demoEnabled=true,demo=false)}}
        compose.onNodeWithText("Periksa struk tersimpan").assertIsEnabled()
        compose.onNodeWithText("Coba simpan perintah yang sama").assertDoesNotExist()
        compose.onNodeWithTag("pos.save").assertDoesNotExist();compose.onNodeWithText("Jangan meminta pembayaran lagi",substring=true).assertExists()
    }
    @Test @Config(qualifiers="w1200dp-h900dp") fun tabletHasPersistentCartAndLargeTextCanUseFocusedCart() {
        var large by mutableStateOf(false)
        compose.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,if(large)2f else 1f)){DaunTehTheme{PosScreen(ready(),PosActions(),expanded=true,demoEnabled=true)}}}
        compose.onNodeWithText("Keranjang",substring=false).assertExists()
        compose.runOnIdle{large=true};compose.waitForIdle()
        compose.onNodeWithText("Keranjang (1)",substring=true).assertExists().performClick()
        compose.onNodeWithText("Kembali ke menu").assertExists()
    }

    @Test fun optionalRadioHasExplicitNoneAndMultiChoiceStopsAtMaximum() {
        val state=ready();val product=state.catalog.products.single()
        val optional=ModifierGroup(RecordInfo(brandId=DEMO_BRAND_ID,name="Gula"),0,1)
        val topping=ModifierGroup(RecordInfo(brandId=DEMO_BRAND_ID,name="Topping"),1,2)
        val sugar=ModifierOption(RecordInfo(brandId=DEMO_BRAND_ID,name="Normal"),optional.info.id,0)
        val toppings=(1..3).map{ModifierOption(RecordInfo(brandId=DEMO_BRAND_ID,name="Pilihan $it"),topping.info.id,1000)}
        val menu=state.catalog.copy(groups=listOf(optional,topping),options=listOf(sugar)+toppings,assignments=listOf(ProductGroup(product.info.id,optional.info.id,0),ProductGroup(product.info.id,topping.info.id,1)))
        var chosen:List<String>?=null
        compose.setContent{DaunTehTheme{ModifierPicker(menu,product,false,null,state.checkout?.draft){options,_->chosen=options}}}
        compose.onNodeWithText("Tanpa pilihan").assertIsSelected();compose.onNodeWithTag("pos.add").assertIsNotEnabled()
        compose.onNodeWithTag("pos.option.${sugar.info.id}").performScrollTo().performClick();compose.onNodeWithText("Tanpa pilihan").performScrollTo().performClick()
        toppings.take(2).forEach{compose.onNodeWithTag("pos.option.${it.info.id}").performScrollTo().performClick()}
        compose.onNodeWithTag("pos.option.${toppings.last().info.id}").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("pos.add").performClick();compose.runOnIdle{assertEquals(toppings.take(2).map{it.info.id}.toSet(),chosen?.toSet());assertFalse(sugar.info.id in chosen.orEmpty())}
    }
    @Test fun selectionRejectsInvalidQuantityAndHundredLineLimit() {
        val initial=ready();val product=initial.catalog.products.single()
        var draft by mutableStateOf(initial.checkout!!.draft)
        compose.setContent{DaunTehTheme{ModifierPicker(initial.catalog,product,false,null,draft){_,_->}}}
        for(value in listOf("0","1000","1.5","2147483648")) {
            compose.onNodeWithTag("pos.quantity").performScrollTo().performTextClearance();compose.onNodeWithTag("pos.quantity").performTextInput(value)
            compose.onNodeWithTag("pos.add").assertIsNotEnabled()
        }
        compose.onNodeWithTag("pos.quantity").performTextClearance();compose.onNodeWithTag("pos.quantity").performTextInput("999");compose.onNodeWithTag("pos.add").assertIsEnabled()
        compose.runOnIdle{draft=draft.copy(lines=List(100){draft.lines.first().copy(id=newId())})};compose.onNodeWithTag("pos.add").assertIsNotEnabled()
    }
    @Test fun outletScopeClearsSearchAndOpenSelection() {
        var state by mutableStateOf(ready().copy(scopeKey="a"))
        compose.setContent{DaunTehTheme{PosScreen(state,PosActions(),demoEnabled=true)}}
        val product=state.catalog.products.single()
        compose.onNodeWithTag("pos.product.${product.info.id}").performScrollTo().performClick();compose.onNodeWithTag("pos.quantity").assertExists()
        compose.runOnIdle{state=state.copy(scopeKey="b")};compose.onNodeWithTag("pos.quantity").assertDoesNotExist()
        compose.onNodeWithTag("pos.search").performTextInput("absent");compose.runOnIdle{state=state.copy(scopeKey="c")}
        compose.onNodeWithText("Tidak ada hasil yang cocok.").assertDoesNotExist();compose.onNodeWithText("Teh contoh").assertExists()
    }
}
