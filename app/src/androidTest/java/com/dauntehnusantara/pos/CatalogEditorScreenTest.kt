package com.dauntehnusantara.pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.catalog.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.PullStatus
import org.junit.Rule
import org.junit.Test

class CatalogEditorScreenTest {
    @get:Rule val compose=createComposeRule()
    @Test fun offlineCatalogExplainsSharedBrandScopeAndDisablesCreation() {
        compose.setContent { DaunTehTheme { CatalogEditorScreen(CatalogEditorState(loading=false,offline=true),{}, {}, {}) } }
        compose.onNodeWithText("Perubahan berlaku untuk seluruh outlet",substring=true).assertExists()
        compose.onNodeWithText("Offline — katalog hanya dapat dilihat.").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tambah Category"))
        compose.onNodeWithText("Tambah Category").assertIsNotEnabled()
    }
    @Test fun newFormRequiresNameBeforeSubmit() {
        compose.setContent { DaunTehTheme { CatalogEditorScreen(CatalogEditorState(loading=false,pull=PullStatus(initialized=true,epoch="test")),{}, {}, {}) } }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Tambah Category"))
        compose.onNodeWithText("Tambah Category").performClick()
        compose.onNodeWithText("Simpan").assertIsNotEnabled()
        compose.onNodeWithText("Nama").performTextInput("Tea")
        compose.onNodeWithText("Simpan").assertIsEnabled()
    }
}
