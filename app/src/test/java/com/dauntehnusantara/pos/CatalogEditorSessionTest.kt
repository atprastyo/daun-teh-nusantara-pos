package com.dauntehnusantara.pos

import android.app.Application
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.presentation.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28], application=Application::class)
class CatalogEditorSessionTest {
    private val category = Category(RecordInfo(brandId=DEMO_BRAND_ID,name="Tea",version=3))
    private val group = ModifierGroup(RecordInfo(brandId=DEMO_BRAND_ID,name="Topping",version=1),1,2)
    private val product = Product(RecordInfo(brandId=DEMO_BRAND_ID,name="Tea",version=4),category.info.id,6000)
    private val option = ModifierOption(RecordInfo(brandId=DEMO_BRAND_ID,name="Jelly",version=2),group.info.id,0)
    private val catalog = Catalog(listOf(category),listOf(product),listOf(group),listOf(option),listOf(ProductGroup(product.info.id,group.info.id,0)))
    private val original get() = catalog.editorRows(CatalogRecord.Product).single()

    @Test fun moneyAcceptsZeroAndLongMaximumWithoutRounding() {
        val draft=CatalogDraft(original)
        assertNotNull(draft.copy(price="0").command(catalog))
        assertEquals(Long.MAX_VALUE,draft.copy(price=Long.MAX_VALUE.toString()).command(catalog)?.priceIdr)
        listOf("-1","1.5","9223372036854775808","","+1").forEach { assertTrue(draft.copy(price=it).errors(catalog).containsKey("price")) }
    }
    @Test fun namesOrderAndBoundsAreValidatedWithoutFallbackNumbers() {
        val categoryDraft=CatalogDraft(catalog.editorRows(CatalogRecord.Category).single())
        assertTrue(categoryDraft.copy(name=" ").errors(catalog).containsKey("name"))
        assertTrue(categoryDraft.copy(name="x".repeat(121)).errors(catalog).containsKey("name"))
        listOf("-1","2147483648","1.2","").forEach { assertTrue(categoryDraft.copy(order=it).errors(catalog).containsKey("order")) }
        val draft=CatalogDraft(catalog.editorRows(CatalogRecord.Group).single())
        assertTrue(draft.copy(minimum="2",maximum="1").errors(catalog).containsKey("maximum"))
        assertTrue(draft.copy(maximum="0").errors(catalog).containsKey("maximum"))
        assertTrue(draft.copy(minimum="2",maximum="3").errors(catalog).containsKey("minimum"))
    }
    @Test fun archivedParentAndRequiredOptionsDoNotSilentlyChangeAssignments() {
        assertTrue(CatalogDraft(original).errors(catalog.copy(categories=listOf(category.copy(info=category.info.copy(active=false))))).containsKey("parent"))
        assertTrue(CatalogDraft(original).errors(catalog.copy(options=emptyList())).containsKey("groups"))
        val archived=group.copy(info=group.info.copy(active=false))
        assertFalse(CatalogDraft(original).errors(catalog.copy(groups=listOf(archived))).containsKey("groups"))
        assertTrue(CatalogDraft(original.copy(groupIds=emptyList())).copy(groups=listOf(group.info.id)).errors(catalog.copy(groups=listOf(archived))).containsKey("groups"))
    }
    @Test fun optionParentIsImmutableAndRequiredOptionCannotBeArchived() {
        val draft=CatalogDraft(catalog.editorRows(CatalogRecord.Option).single())
        assertTrue(draft.copy(parent=category.info.id).errors(catalog).containsKey("parent"))
        assertTrue(draft.copy(active=false).errors(catalog).containsKey("active"))
    }
    @Test fun newEditedCommandsHaveNewOperationButKeepRecordVersionAndOrder() {
        val draft=CatalogDraft(original)
        val a=requireNotNull(draft.command(catalog));val b=requireNotNull(draft.copy(name="Changed").command(catalog))
        assertNotEquals(a.operationId,b.operationId);assertEquals(a.id,b.id);assertEquals(4,b.expectedVersion)
        assertEquals(original.groupIds,b.groupIds)
    }
    @Test fun sessionKeepsDraftForSameScopeAndClearsAcrossScopeOrLogout() {
        val session=CatalogEditorSession();session.bindScope("scope-a");session.open(original);session.update{it.copy(name="Unsaved")}
        session.bindScope("scope-a");assertEquals("Unsaved",session.state.value.draft?.name)
        session.bindScope("scope-b");assertNull(session.state.value.draft)
        session.open(original);session.clear();assertNull(session.state.value.draft)
    }
    @Test fun unrelatedPhotoCompletionCannotCloseSubmittedCatalogDraft() {
        val session=CatalogEditorSession();session.open(original);session.submitted("catalog-op")
        session.applied("photo-op");assertNotNull(session.state.value.draft)
        session.applied("catalog-op");assertNull(session.state.value.draft)
    }
}
