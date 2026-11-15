package com.dauntehnusantara.pos

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.PullStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class, qualifiers="w390dp-h844dp")
class CatalogEditorPresentationTest {
    val compose=createComposeRule()
    @get:Rule val rules:TestRule=RuleChain.outerRule(object:ExternalResource(){
        override fun before(){val app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app,ComponentActivity::class.java))}
    }).around(compose)
    private val category=Category(RecordInfo(brandId=DEMO_BRAND_ID,name="Teh original",version=1))
    private val product=Product(RecordInfo(brandId=DEMO_BRAND_ID,name="Teh Melati",version=3),category.info.id,6000)
    private val second=Product(RecordInfo(brandId=DEMO_BRAND_ID,name="Teh Lemon",version=2),category.info.id,8000)
    private val group=ModifierGroup(RecordInfo(brandId=DEMO_BRAND_ID,name="Topping",version=1),0,2)
    private val option=ModifierOption(RecordInfo(brandId=DEMO_BRAND_ID,name="Jelly",version=2),group.info.id,2000)
    private val menu=Catalog(listOf(category),listOf(product,second),listOf(group),listOf(option))
    private fun ready()=CatalogEditorState(catalog=menu,loading=false,pull=PullStatus(1,true,100,null,"epoch"))
    private fun edit()=compose.onNodeWithContentDescription("Edit Teh Melati").performScrollTo().performClick()
    private fun field(key:String)=compose.onNodeWithTag("catalog.field.$key")
    private fun replace(key:String,value:String){field(key).performScrollTo().performTextClearance();field(key).performTextInput(value)}

    @Test fun productFirstLocalizedTabsSearchAndNoMatchAreDistinctFromEmpty() {
        compose.setContent{DaunTehTheme{CatalogEditorScreen(ready(),{}, {}, {})}}
        compose.onNodeWithText("Produk").assertIsSelected()
        compose.onNodeWithText("Cari nama").performScrollTo().performTextInput("LEMON")
        compose.onNodeWithContentDescription("Edit Teh Lemon").assertExists()
        compose.onNodeWithContentDescription("Edit Teh Melati").assertDoesNotExist()
        compose.onNodeWithText("Cari nama").performTextClearance();compose.onNodeWithText("Cari nama").performTextInput("absent")
        compose.onNodeWithText("Tidak ada hasil yang cocok.").assertExists()
        compose.onNodeWithText("Hapus pencarian/filter").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit Teh Melati").assertExists()
    }
    @Test fun emptySetupOpensCategoryFirstAndInvalidNameCannotSubmit() {
        compose.setContent{DaunTehTheme{CatalogEditorScreen(ready().copy(catalog=Catalog()),{}, {}, {})}}
        compose.onNodeWithText("Tambah produk").performScrollTo().performClick()
        compose.onNodeWithText("Tambah kategori").assertIsDisplayed()
        compose.onNodeWithText("Simpan perubahan").assertIsNotEnabled()
        replace("name","Teh baru");compose.onNodeWithText("Simpan perubahan").assertIsEnabled()
        replace("order","2147483648");compose.onNodeWithText("Simpan perubahan").assertIsNotEnabled()
    }
    @Test fun productPriceZeroAllowedButDecimalAndOverflowRejected() {
        compose.setContent{DaunTehTheme{CatalogEditorScreen(ready(),{}, {}, {})}}
        edit();replace("price","0");compose.onNodeWithText("Simpan perubahan").assertIsEnabled()
        replace("price","1.5");compose.onNodeWithText("Simpan perubahan").assertIsNotEnabled()
        replace("price","9223372036854775808");compose.onNodeWithText("Simpan perubahan").assertIsNotEnabled()
        compose.onNodeWithText("Simpan produk sebelum menambahkan foto.").assertDoesNotExist()
    }
    @Test fun dedicatedArchiveConfirmsAndDoesNotSaveUnrelatedDraftFields() {
        var saved:CatalogMutation?=null
        compose.setContent{DaunTehTheme{CatalogEditorScreen(ready(),{saved=it}, {}, {})}}
        edit();replace("price","9000")
        compose.onNodeWithText("Arsipkan produk").performScrollTo().performClick()
        compose.runOnIdle{assertNull(saved)}
        compose.onNode(hasText("Batal") and hasAnyAncestor(isDialog())).performClick()
        field("price").assertTextContains("9000")
        compose.onNodeWithText("Arsipkan produk").performScrollTo().performClick()
        compose.onNodeWithText("Arsipkan",substring=false).performClick()
        compose.runOnIdle{assertEquals(6000L,saved?.priceIdr);assertEquals(false,saved?.active);assertEquals(product.info.id,saved?.id);assertEquals(3L,saved?.expectedVersion)}
    }
    @Test fun savingInactiveDraftExplicitlyIncludesEditedFields() {
        var saved:CatalogMutation?=null
        compose.setContent{DaunTehTheme{CatalogEditorScreen(ready(),{saved=it}, {}, {})}}
        edit();replace("name","Nama baru")
        compose.onNodeWithText("Aktif untuk penggunaan baru").performScrollTo().performClick()
        compose.onNodeWithText("Simpan perubahan").performClick()
        compose.onNodeWithText("Arsipkan dan simpan").performClick()
        compose.runOnIdle{assertEquals("Nama baru",saved?.name);assertEquals(false,saved?.active)}
    }
    @Test fun existingOptionParentIsReadOnly() {
        compose.setContent{DaunTehTheme{CatalogEditorScreen(ready(),{}, {}, {})}}
        compose.onNodeWithText("Pilihan").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit Jelly").performScrollTo().performClick()
        compose.onNodeWithText("Grup tidak dapat diubah setelah dibuat.").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(isSelectable()).assertCountEquals(0)
    }
    @Test fun submittedFormWaitsForMatchingAckAndPullAndBlocksRepeats() {
        var current by mutableStateOf(ready())
        var sent:CatalogMutation?=null
        compose.setContent{DaunTehTheme{CatalogEditorScreen(current,{sent=it;current=current.copy(pending=it,busy=true)}, {}, {})}}
        edit();replace("name","Nama tersimpan");compose.onNodeWithText("Simpan perubahan").performClick()
        compose.onNodeWithText("Simpan perubahan").assertIsNotEnabled();compose.onNodeWithText("Batal").assertIsNotEnabled()
        compose.runOnIdle{current=current.copy(busy=false,error="UNAVAILABLE")}
        field("name").assertTextContains("Nama tersimpan");compose.onNodeWithText("Simpan perubahan").assertIsNotEnabled()
        compose.runOnIdle{current=current.copy(pending=null,error=null,saved=true,completedOperationId=sent!!.operationId,waitingThrough=3)}
        compose.onNodeWithTag("catalog.editor").assertExists()
        compose.runOnIdle{current=current.copy(waitingThrough=null,pull=current.pull.copy(seq=3,lastSuccessMs=101))}
        compose.onNodeWithTag("catalog.editor").assertExists()
        compose.onNodeWithText("Simpan perubahan").assertIsNotEnabled()
        compose.runOnIdle{current=current.copy(catalog=menu.copy(products=listOf(product.copy(info=product.info.copy(version=4,name="Nama tersimpan")),second)))}
        compose.onNodeWithTag("catalog.editor").assertDoesNotExist()
    }
    @Test fun photoCompletionPreservesUnsubmittedProductText() {
        var current by mutableStateOf(ready())
        compose.setContent{DaunTehTheme{CatalogEditorScreen(current,{}, {}, {})}}
        edit();replace("name","Unsaved product")
        compose.runOnIdle{current=current.copy(saved=true,waitingThrough=3)}
        compose.runOnIdle{current=current.copy(waitingThrough=null,pull=current.pull.copy(seq=3,lastSuccessMs=101))}
        field("name").assertTextContains("Unsaved product")
    }
    @Test fun staleVersionRequiresExplicitReplacementRatherThanOverwritingDraft() {
        var current by mutableStateOf(ready())
        compose.setContent{DaunTehTheme{CatalogEditorScreen(current,{}, {}, {})}}
        edit();replace("name","Unsaved product")
        compose.runOnIdle{current=current.copy(catalog=menu.copy(products=listOf(product.copy(info=product.info.copy(version=4,name="Latest server")),second)))}
        field("name").assertTextContains("Unsaved product");compose.onNodeWithText("Simpan perubahan").assertIsNotEnabled()
        compose.onNodeWithText("Gunakan data terbaru").performScrollTo().performClick()
        field("name").assertTextContains("Latest server");compose.onNodeWithText("Simpan perubahan").assertIsEnabled()
    }
    @Test fun photoRetryAndRemoveConfirmationUseIndependentRevision() {
        val pictured=product.copy(imagePath="missing",imageVersion=9)
        var removed:Product?=null
        var loads=0
        val repository=object:com.dauntehnusantara.pos.feature.catalog.photos.domain.ProductPhotoRepository {
            override suspend fun load(path:String):ByteArray? { loads++;return null }
            override suspend fun currentScope():com.dauntehnusantara.pos.feature.sync.domain.PullScope?=null
            override suspend fun upload(scope:com.dauntehnusantara.pos.feature.sync.domain.PullScope,path:String,bytes:ByteArray){}
        }
        compose.setContent{DaunTehTheme{CompositionLocalProvider(
            com.dauntehnusantara.pos.feature.catalog.photos.presentation.LocalProductPhotos provides
                com.dauntehnusantara.pos.feature.catalog.photos.presentation.PhotoAccess(repository,"scope")){
            CatalogEditorScreen(ready().copy(catalog=menu.copy(products=listOf(pictured))),{}, {}, {},removePhoto={removed=it})
        }}}
        compose.onNodeWithText("Muat foto").performScrollTo().assertIsEnabled().assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle{assertTrue(loads>=2)}
        edit();compose.onNodeWithText("Hapus foto").performScrollTo().performClick()
        compose.runOnIdle{assertNull(removed)}
        compose.onNode(hasText("Batal") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle{assertNull(removed)}
        compose.onNodeWithText("Hapus foto").performScrollTo().performClick()
        compose.onNode(hasText("Hapus foto") and hasAnyAncestor(isDialog())).performClick()
        compose.runOnIdle{assertEquals(9L,removed?.imageVersion);assertEquals(3L,removed?.info?.version)}
    }

    @Test fun offlineAndUnresolvedCommandsDisableEveryNewMutation() {
        var current by mutableStateOf(ready().copy(offline=true))
        compose.setContent{DaunTehTheme{CatalogEditorScreen(current,{}, {}, {})}}
        compose.onNodeWithText("Tambah produk").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Edit Teh Melati").assertIsNotEnabled()
        compose.runOnIdle{current=current.copy(offline=false,pending=CatalogMutation(kind=CatalogRecord.Category,name="Frozen"))}
        compose.onNodeWithText("Tambah produk").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Edit Teh Melati").assertIsNotEnabled()
    }
    @Test
    @Config(qualifiers="w1280dp-h900dp")
    fun wideEditorKeepsListAndInputSurvivesLargeTextFallback() {
        var scale by mutableStateOf(1f)
        compose.setContent{
            val density=LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density,scale)){
                DaunTehTheme{CatalogEditorScreen(ready(),{}, {}, {})}
            }
        }
        edit();compose.onNodeWithTag("catalog.list").assertIsDisplayed();compose.onNodeWithTag("catalog.editor").assertIsDisplayed()
        replace("name","Survives resizing")
        compose.onNodeWithContentDescription("Edit Teh Lemon").assertIsNotEnabled()
        compose.onNodeWithText("Tambah produk").assertIsNotEnabled()
        compose.runOnIdle{scale=2f}
        compose.onNodeWithTag("catalog.list").assertDoesNotExist()
        field("name").assertTextContains("Survives resizing")
        compose.onNodeWithText("Simpan perubahan").assertIsDisplayed()
    }
    @Test
    @Config(qualifiers="w800dp-h900dp")
    fun mediumEditorUsesDialogWithReachableActions() {
        compose.setContent{DaunTehTheme{CatalogEditorScreen(ready(),{}, {}, {})}}
        edit();compose.onNode(isDialog()).assertExists();compose.onNodeWithText("Simpan perubahan").assertIsDisplayed()
    }
}
