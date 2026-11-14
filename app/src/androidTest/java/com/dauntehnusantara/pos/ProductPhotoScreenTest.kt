package com.dauntehnusantara.pos

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.presentation.*
import com.dauntehnusantara.pos.feature.catalog.photos.domain.*
import com.dauntehnusantara.pos.feature.catalog.photos.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class ProductPhotoScreenTest {
    @get:Rule val compose=createComposeRule()
    private val category=Category(RecordInfo(brandId=DEMO_BRAND_ID,name="Tea"))
    private val product=Product(RecordInfo(brandId=DEMO_BRAND_ID,name="Original"),category.info.id,6000,imagePath="missing",imageVersion=1)
    private val menu=Catalog(categories=listOf(category),products=listOf(product))
    @Test fun offlinePhotoActionsAreDisabledButCatalogRemainsReadable() {
        compose.setContent{DaunTehTheme{CatalogEditorScreen(CatalogEditorState(catalog=menu,loading=false,offline=true),{}, {}, {})}}
        compose.onNodeWithText("Product").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Ganti foto"))
        compose.onNodeWithText("Ganti foto").assertIsNotEnabled();compose.onNodeWithText("Hapus foto").assertIsNotEnabled()
    }
    @Test fun onlineRemoveUsesProductPhotoRevisionAndMissingImageOffersRetry() {
        var removed:Product?=null
        val photos=object:ProductPhotoRepository {
            override suspend fun load(path:String):ByteArray?=null
            override suspend fun currentScope():PullScope?=null
            override suspend fun upload(scope:PullScope,path:String,bytes:ByteArray){}
        }
        compose.setContent{DaunTehTheme{CompositionLocalProvider(LocalProductPhotos provides PhotoAccess(photos,"owner")){
            CatalogEditorScreen(CatalogEditorState(catalog=menu,loading=false,pull=PullStatus(initialized=true,epoch="epoch")),{}, {}, {},removePhoto={removed=it})
        }}}
        compose.onNodeWithText("Product").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Muat foto"));compose.onNodeWithText("Muat foto").assertIsEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Hapus foto"));compose.onNodeWithText("Hapus foto").performClick()
        compose.runOnIdle{assertEquals(1L,removed?.imageVersion);assertEquals(product.info.id,removed?.info?.id)}
    }
}
