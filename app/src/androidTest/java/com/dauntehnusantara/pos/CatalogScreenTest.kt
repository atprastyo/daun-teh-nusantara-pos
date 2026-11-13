package com.dauntehnusantara.pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.presentation.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CatalogScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emptyAndRetryStatesAreActionable() {
        var retries = 0
        compose.setContent {
            DaunTehTheme { CatalogScreen(CatalogUiState(loading = false, failed = true), { retries++ }, {}, {}) }
        }
        compose.onNodeWithText("Coba lagi").performClick()
        compose.runOnIdle { assertEquals(1, retries) }
    }
    @Test fun productOpensModifierDetails() {
        val category = Category(RecordInfo(brandId = DEMO_BRAND_ID, name = "Teh"))
        val product = Product(RecordInfo(brandId = DEMO_BRAND_ID, name = "Teh contoh"), category.info.id, 6000)
        val group = ModifierGroup(RecordInfo(brandId = DEMO_BRAND_ID, name = "Ukuran"), 1, 1)
        val option = ModifierOption(RecordInfo(brandId = DEMO_BRAND_ID, name = "Besar"), group.info.id, 3000)
        val catalog = Catalog(listOf(category), listOf(product), listOf(group), listOf(option), listOf(ProductGroup(product.info.id, group.info.id, 0)))
        compose.setContent {
            DaunTehTheme { CatalogScreen(CatalogUiState(catalog = catalog, loading = false), {}, {}, {}, demoEnabled = false) }
        }
        compose.onNodeWithText("Teh contoh").performClick()
        compose.onNodeWithText("Ukuran").assertIsDisplayed()
        compose.onNodeWithText("Pilih 1–1 opsi").assertIsDisplayed()
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithText("Ukuran").assertDoesNotExist()
        compose.onNodeWithText("Muat menu demo (debug)").assertDoesNotExist()
    }
}
