package com.dauntehnusantara.pos

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.pos.presentation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PosScreenTest {
    @get:Rule val compose = createComposeRule()
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
        compose.onNodeWithText("Simpan penjualan lokal").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        compose.onNodeWithText("Menyimpan…").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, submissions); assertEquals(10000L, paid?.tenderedIdr) }
    }
    @Test fun qrisNeedsOperatorConfirmation() {
        var paid: PaymentInput? = null
        compose.setContent { DaunTehTheme { PosScreen(ready(), PosActions(pay = { paid = it }), demoEnabled = true) } }
        compose.onNodeWithText("Keranjang (1)", substring = true).performClick()
        compose.onNodeWithText("Pembayaran").performClick()
        compose.onNodeWithText("QRIS · konfirmasi manual").performClick()
        compose.onNodeWithText("Simpan penjualan lokal").assertIsNotEnabled()
        compose.onNodeWithText("Saya telah memeriksa dan memastikan pembayaran diterima.").performClick()
        compose.onNodeWithText("Simpan penjualan lokal").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
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

}
