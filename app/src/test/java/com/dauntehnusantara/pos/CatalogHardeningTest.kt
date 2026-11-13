package com.dauntehnusantara.pos

import android.app.Application
import com.dauntehnusantara.pos.core.network.JsonSafety
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.data.CatalogCommandJson
import com.dauntehnusantara.pos.feature.catalog.presentation.CatalogEditorState
import com.dauntehnusantara.pos.feature.sync.domain.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class CatalogHardeningTest {
    private val epoch="11111111-1111-4111-8111-111111111111"
    private val parent="22222222-2222-4222-8222-222222222222"
    private fun invalid(block:()->Unit){assertTrue(runCatching(block).isFailure)}
    @Test fun deepJsonIsRejectedBeforeRecursiveParsingButQuotedBracesAreAllowed() {
        invalid{JsonSafety.obj("{\"x\":"+"[".repeat(33)+"0"+"]".repeat(33)+"}")}
        val raw=JSONObject().put("value","[{}] \\\" escaped").toString()
        assertEquals("[{}] \\\" escaped",JsonSafety.obj(raw).getString("value"))
        JsonSafety.obj("{\"x\":"+"[".repeat(31)+"0"+"]".repeat(31)+"}")
        invalid{JsonSafety.obj("{\"unfinished\": [")}
        invalid{JsonSafety.obj("{\"x\":\"éé\"}",11)}
    }
    @Test fun catalogFieldsUseWholeNonnegativeMoneyUniqueGroupsAndValidBounds() {
        val product=CatalogMutation(kind=CatalogRecord.Product,name="Tea",priceIdr=6000,parentId=parent)
        product.validate();invalid{product.copy(priceIdr=-1).validate()};invalid{product.copy(parentId=null).validate()}
        invalid{product.copy(groupIds=listOf(parent,parent)).validate()};invalid{product.copy(name=" ").validate()}
        invalid{product.copy(expectedVersion=Long.MAX_VALUE).validate()}
        invalid{CatalogMutation(kind=CatalogRecord.Group,name="Ice",minSelect=2,maxSelect=1).validate()}
    }
    @Test fun acknowledgmentsAreBoundToCommandVersionAndGeneration() {
        val c=CatalogMutation(kind=CatalogRecord.Category,name="Tea",expectedVersion=7)
        val j=JSONObject().put("operation_id",c.operationId).put("id",c.id).put("kind",c.kind.name).put("version",8).put("through_seq",42).put("feed_epoch",epoch)
        assertEquals(42,CatalogCommandJson.ack(j,c,epoch).throughSeq)
        invalid{CatalogCommandJson.ack(j,c.copy(id=parent),epoch)}
        invalid{CatalogCommandJson.ack(j,c,parent)}
        invalid{CatalogCommandJson.ack(j.put("version",9),c,epoch)}
    }
    @Test fun editingWaitsForOnlineInitializedErrorFreePulledCatalogAndResolvedCommand() {
        val ready=CatalogEditorState(loading=false,pull=PullStatus(initialized=true,epoch=epoch))
        assertTrue(ready.editable);assertFalse(ready.copy(offline=true).editable)
        assertFalse(ready.copy(waitingThrough=3).editable)
        assertFalse(ready.copy(pull=ready.pull.copy(error="REBOOTSTRAP_REQUIRED")).editable)
        assertFalse(ready.copy(pending=CatalogMutation(kind=CatalogRecord.Category,name="Tea")).editable)
        assertFalse(ready.copy(pull=ready.pull.copy(epoch=null)).editable)
    }
    @Test fun cashierCannotInvokeEditorAndOwnerCommandRetainsStableOperation():Unit=runBlocking {
        var sent:CatalogMutation?=null
        val edit=EditCatalog(object:CatalogGateway{override suspend fun saveCatalog(scope:PullScope,epoch:String,change:CatalogMutation):CatalogAck {
            sent=change;return CatalogAck(change.operationId,change.id,1,1,epoch)
        }})
        val s=PullScope(SaleScope("brand","outlet","device","user"),"CASHIER","https://example.invalid")
        val c=CatalogMutation(kind=CatalogRecord.Category,name=" Tea ")
        assertTrue(runCatching{edit(s,epoch,c)}.isFailure);assertNull(sent)
        edit(s.copy(role="OWNER"),epoch,c);assertEquals(c.operationId,sent!!.operationId);assertEquals("Tea",sent!!.name)
    }
}
