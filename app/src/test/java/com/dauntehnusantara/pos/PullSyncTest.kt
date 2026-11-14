package com.dauntehnusantara.pos

import android.app.Application
import androidx.room.Room
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.catalog.data.toDomain
import com.dauntehnusantara.pos.feature.checkout.data.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.sync.data.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class PullSyncTest {
    private val brand = newId(); private val outlet = newId(); private val user = newId()
    private val category = newId(); private val product = newId(); private val group = newId(); private val option = newId()
    private val clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"),ZoneOffset.UTC)
    private val events = mutableListOf<Change>()
    private var epoch = newId()
    private val serverSales = mutableMapOf<String,String>()
    private val scheduler = object : SyncScheduler { override fun enqueue(force: Boolean) {};override fun ensurePeriodic() {} }
    private val clients = mutableListOf<Client>()
    private val context get() = RuntimeEnvironment.getApplication()
    private inner class Gateway(var context: PullScope) : SaleGateway,PullGateway {
        var failAfter: Long? = null
        var loseAck = false
        var rewrite: ((PullPage) -> PullPage)? = null
        val requested = mutableListOf<Long>()
        override suspend fun backgroundScope() = context.sale
        override suspend fun checkoutScope() = context.sale
        override suspend fun pullScope() = context
        override suspend fun submit(sale: PendingSale): PushResult {
            require(sale.scope==context.sale)
            if(serverSales.putIfAbsent(sale.operationId,sale.payload)==null) events += Change((events.lastOrNull()?.seq?:0)+1,"SALE",sale.saleId,"UPSERT",sale.payload)
            return if(loseAck) PushResult.Retry else PushResult.Accepted
        }
        override suspend fun fetch(scope: PullScope,after:Long,epoch:String?):PullPage {
            requested+=after
            if(failAfter!=null && after>=requireNotNull(failAfter))throw PullFailure("UNAVAILABLE")
            val visible=events.filter { it.seq>after && (it.type!="SALE" || scope.role!="CASHIER" || JSONObject(it.payload).getString("cashier_user_id")==scope.sale.userId) }
            val batch=visible.take(2);val more=visible.size>batch.size
            val next=if(more)batch.last().seq else events.lastOrNull()?.seq?:after
            val page=PullPage(scope,after,next,more,batch,this@PullSyncTest.epoch)
            return rewrite?.invoke(page)?:page
        }
    }
    private inner class Client(val name:String, val gateway:Gateway) {
        lateinit var db:PosDatabase
        lateinit var push:RoomSyncRepository
        lateinit var pull:RoomPullRepository
        lateinit var checkout:CheckoutRepository
        init { open() }
        fun open() {
            db=Room.databaseBuilder(context,PosDatabase::class.java,name).addCallback(CatalogConstraints).build()
            push=RoomSyncRepository(db,gateway,scheduler,clock)
            pull=RoomPullRepository(db,gateway,clock)
            checkout=RoomAuthenticatedCheckoutFactory(db,clock,gateway,gateway,push,scheduler).open(gateway.context)
        }
        suspend fun menu()=db.catalogDao().snapshot(brand).toDomain()
        suspend fun status()=pull.observe(gateway.context).first()
        suspend fun sell():Receipt {
            val d=checkout.observe().first().draft
            checkout.add(d,CheckoutRules.line(menu(),product,listOf(option),1))
            val draft=checkout.observe().first().draft
            return checkout.complete(CheckoutCommand(draft.id,draft.operationId,draft.revision,PaymentInput(PaymentMethod.CASH,10000)))
        }
        fun reopen() { db.close();open() }
    }
    private fun client(role:String="CASHIER",device:String=newId()):Client {
        val name="pull-${newId()}.db"
        return Client(name,Gateway(PullScope(SaleScope(brand,outlet,device,user),role,"https://example.invalid"))).also { clients+=it }
    }
    private fun record(id:String,name:String,version:Long=1)=JSONObject().put("id",id).put("brand_id",brand).put("name",name).put("version",version)
        .put("active",true).put("created_at","2026-11-12T00:00:00Z").put("updated_at","2026-11-13T00:00:00Z")
    private fun event(table:String,row:JSONObject,id:String=row.optString("id")):Change = Change((events.lastOrNull()?.seq?:0)+1,"CATALOG",id,
        if(row.getBoolean("active"))"UPSERT" else "DELETE",JSONObject().put("schema_version",1).put("table",table).put("record",row).toString())
    @Before fun before() {
        events+=event("categories",record(category,"Tea").put("sort_order",0))
        events+=event("products",record(product,"Original").put("category_id",category).put("base_price_idr",6000))
        events+=event("modifier_groups",record(group,"Size").put("min_select",1).put("max_select",1))
        events+=event("modifier_options",record(option,"Large").put("group_id",group).put("price_delta_idr",1000))
        events+=event("product_modifier_groups",JSONObject().put("brand_id",brand).put("product_id",product).put("group_id",group).put("sort_order",0).put("version",1).put("active",true),product)
    }
    @After fun after() { clients.forEach { it.db.close();context.deleteDatabase(it.name) } }
    private suspend fun fails(block:suspend()->Unit) { try { block();fail("Expected rejected page") } catch(_:PullFailure) {} catch(_:IllegalArgumentException) {} }
    @Test fun twoOfflineDevicesConvergeAfterAckLossAndRestartWithoutDuplicateSales():Unit=runBlocking {
        val a=client();val b=client();a.pull.sync();b.pull.sync()
        assertEquals(a.menu(),b.menu());assertTrue(a.status().initialized)
        val first=a.sell();val second=b.sell();a.reopen();b.reopen()
        assertEquals(first,a.checkout.observe().first().receipt);assertEquals(second,b.checkout.observe().first().receipt)
        a.gateway.loseAck=true;a.push.drain();b.push.drain()
        a.pull.sync();b.pull.sync();a.push.drain()
        assertEquals(2,serverSales.size)
        assertEquals(setOf(first.id,second.id),a.checkout.receipts().map { it.id }.toSet())
        assertEquals(a.checkout.receipts(),b.checkout.receipts())
        assertEquals("SYNCED",a.db.checkoutDao().outbox(first.operationId)?.state)
        assertEquals(events.last().seq,a.status().seq)
    }
    @Test fun interruptedPaginationResumesLastCommittedPageAfterReopen():Unit=runBlocking {
        val a=client();a.gateway.failAfter=2
        fails { a.pull.sync() };assertEquals(2L,a.status().seq);assertFalse(a.status().initialized)
        assertEquals("UNAVAILABLE",a.status().error);assertNull(a.status().lastSuccessMs);a.reopen();a.gateway.failAfter=null;a.gateway.requested.clear()
        a.pull.sync();assertEquals(2L,a.gateway.requested.first());assertTrue(a.status().initialized);assertEquals(5L,a.status().seq)
    }
    @Test fun pageAndCursorRollbackTogetherAndRetryKeepsOriginalCatalog():Unit=runBlocking {
        val a=client();a.pull.sync()
        events+=event("products",record(product,"Updated",2).put("category_id",category).put("base_price_idr",8000))
        events+=Change(7,"UNKNOWN",newId(),"UPSERT","{}")
        fails { a.pull.sync() };assertEquals(5L,a.status().seq);assertEquals("Original",a.menu().products.single().info.name)
        events.removeAt(events.lastIndex);a.pull.sync();assertEquals(6L,a.status().seq);assertEquals("Updated",a.menu().products.single().info.name)
    }
    @Test fun updatedPricesAndAssignmentTombstoneDoNotRewriteCompletedReceipt():Unit=runBlocking {
        val a=client();a.pull.sync();val sale=a.sell()
        events+=event("products",record(product,"Updated",2).put("category_id",category).put("base_price_idr",8000))
        events+=event("product_modifier_groups",JSONObject().put("brand_id",brand).put("product_id",product).put("group_id",group).put("sort_order",0).put("version",2).put("active",false),product)
        a.pull.sync();assertTrue(a.menu().assignments.isEmpty());assertEquals(8000L,a.menu().products.single().basePriceIdr)
        assertEquals(sale,a.checkout.receipts().single());assertEquals(7000L,sale.totalIdr)
    }
    @Test fun changedRoleBackfillsOlderSalesWithoutLeakingThemAfterDemotion():Unit=runBlocking {
        val a=client();a.pull.sync();val own=a.sell()
        val peer=own.copy(id=newId(),operationId=newId(),cashierId=newId(),deviceId=newId())
        events+=Change(6,"SALE",peer.id,"UPSERT",CheckoutJson.payload(peer,false));a.pull.sync()
        assertEquals(6L,a.status().seq);assertEquals(listOf(own),a.checkout.receipts())
        val cashierScope=a.gateway.context
        a.gateway.context=cashierScope.copy(role="MANAGER")
        a.checkout=RoomAuthenticatedCheckoutFactory(a.db,clock,a.gateway,a.gateway,a.push,scheduler).open(a.gateway.context)
        a.pull.sync();assertEquals(2,a.checkout.receipts().size)
        val stale=a.checkout;a.gateway.context=cashierScope
        try { stale.receipts();fail("Stale manager scope must be locked") } catch(_:IllegalStateException) {}
        a.checkout=RoomAuthenticatedCheckoutFactory(a.db,clock,a.gateway,a.gateway,a.push,scheduler).open(cashierScope)
        assertEquals(listOf(own),a.checkout.receipts())
    }
    @Test fun malformedOrCrossScopePageCannotAdvanceCursor():Unit=runBlocking {
        val a=client();a.gateway.rewrite={it.copy(scope=it.scope.copy(role="OWNER"))};fails { a.pull.sync() };assertEquals(0L,a.status().seq)
        a.gateway.rewrite={it.copy(changes=it.changes.reversed())};fails { a.pull.sync() };assertEquals(0L,a.status().seq)
        a.gateway.rewrite=null;a.pull.sync();assertEquals(5L,a.status().seq)
    }
    @Test fun firstPullRequiredAndConsumedDraftIsAtomicWithSale():Unit=runBlocking {
        val a=client()
        try { a.checkout.complete(CheckoutCommand(newId(),newId(),0,PaymentInput(PaymentMethod.CASH,10000)));fail("Initial pull required") } catch(_:IllegalStateException) {}
        a.pull.sync();val d=a.checkout.observe().first().draft;a.checkout.add(d,CheckoutRules.line(a.menu(),product,listOf(option),1))
        val draft=a.checkout.observe().first().draft
        a.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_cart BEFORE UPDATE ON checkout_session BEGIN SELECT RAISE(ABORT,'Disk error'); END")
        try { a.checkout.complete(CheckoutCommand(draft.id,draft.operationId,draft.revision,PaymentInput(PaymentMethod.CASH,10000)));fail("Rollback expected") } catch(_:android.database.SQLException) {}
        assertNull(a.db.checkoutDao().byOperation(draft.operationId));assertEquals(draft,a.checkout.observe().first().draft)
        a.db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_cart")
        val command=CheckoutCommand(draft.id,draft.operationId,draft.revision,PaymentInput(PaymentMethod.CASH,10000))
        val sale=a.checkout.complete(command);assertEquals(sale,a.checkout.complete(command));a.reopen();assertEquals(sale,a.checkout.observe().first().receipt)
    }
    @Test fun staleCatalogReplayFromNewRoleCannotRollBackNewerPrices():Unit=runBlocking {
        val a=client();a.pull.sync()
        events+=event("products",record(product,"New",2).put("category_id",category).put("base_price_idr",9000));a.pull.sync()
        a.gateway.context=a.gateway.context.copy(role="MANAGER");a.gateway.failAfter=2
        fails { a.pull.sync() };assertEquals("New",a.menu().products.single().info.name)
        a.gateway.failAfter=null;a.pull.sync();assertEquals(9000L,a.menu().products.single().basePriceIdr)
    }
    @Test fun backendChangeCannotMixCachedReceiptsOrDeleteOriginalPendingWork():Unit=runBlocking {
        val a=client();a.pull.sync();val original=a.sell()
        a.gateway.context=a.gateway.context.copy(project="https://other.invalid")
        a.checkout=RoomAuthenticatedCheckoutFactory(a.db,clock,a.gateway,a.gateway,a.push,scheduler).open(a.gateway.context)
        try { a.pull.sync();fail("Backend binding must hold") } catch(e:PullFailure) { assertEquals("PROJECT_CHANGED",e.code) }
        assertEquals(0L,a.status().seq)
        try { a.checkout.receipts();fail("Old backend receipts must not be shown") } catch(_:IllegalStateException) {}
        assertNotNull(a.db.checkoutDao().byOperation(original.operationId))
        assertEquals("PENDING",a.db.checkoutDao().outbox(original.operationId)?.state)
    }

    @Test fun reusedSaleIdentityWithDifferentOperationStopsPageAndPreservesPendingReceipt():Unit=runBlocking {
        val a=client();a.pull.sync();val original=a.sell()
        val conflict=original.copy(operationId=newId())
        events+=Change(6,"SALE",original.id,"UPSERT",CheckoutJson.payload(conflict,false))
        fails { a.pull.sync() }
        assertEquals(5L,a.status().seq);assertEquals(listOf(original),a.checkout.receipts())
        assertEquals("PENDING",a.db.checkoutDao().outbox(original.operationId)?.state)
        assertTrue(a.db.pullDao().sales(a.gateway.context.key).isEmpty())
    }

    @Test fun recoveryArchiveRetainsOriginalOperationsAndDetectsTamperingWithoutCredentials():Unit=runBlocking {
        val a=client();a.pull.sync();val sale=a.sell()
        val archive=com.dauntehnusantara.pos.feature.recovery.data.RoomRecoveryRepository(a.db,a.gateway,a.gateway,clock)
        val raw=archive.capture(a.gateway.context)
        val operations=com.dauntehnusantara.pos.feature.recovery.data.RecoveryArchive.inspect(raw,a.gateway.context)
        assertEquals(listOf(requireNotNull(a.db.checkoutDao().outbox(sale.operationId)).payloadJson),operations)
        assertFalse(raw.contains("access_token"));assertFalse(raw.contains("refresh_token"));assertFalse(raw.contains("device_secret"))
        val tampered=JSONObject(raw);tampered.getJSONObject("payload").getJSONArray("operations").getJSONObject(0).put("state","SYNCED")
        try{com.dauntehnusantara.pos.feature.recovery.data.RecoveryArchive.inspect(tampered.toString(),a.gateway.context);fail("Checksum mismatch must fail")}catch(_:IllegalArgumentException){}
        val original=a.gateway.context;a.gateway.context=original.copy(sale=original.sale.copy(userId=newId()))
        try{archive.capture(original);fail("Another user must not export original data")}catch(_:com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure){}
        assertEquals("PENDING",a.db.checkoutDao().outbox(sale.operationId)?.state)
    }
    @Test fun restoredFeedGenerationStopsCheckoutButPreservesCursorCatalogAndExportablePendingSales():Unit=runBlocking {
        val a=client();a.pull.sync();val sale=a.sell();val originalCursor=a.status().seq
        epoch=newId();fails{a.pull.sync()}
        assertEquals("REBOOTSTRAP_REQUIRED",a.status().error);assertFalse(a.status().initialized);assertEquals(originalCursor,a.status().seq)
        assertEquals("PENDING",a.db.checkoutDao().outbox(sale.operationId)?.state)
        val raw=com.dauntehnusantara.pos.feature.recovery.data.RoomRecoveryRepository(a.db,a.gateway,a.gateway,clock).capture(a.gateway.context)
        assertEquals(1,com.dauntehnusantara.pos.feature.recovery.data.RecoveryArchive.inspect(raw,a.gateway.context).size)
        try{a.checkout.dismissReceipt(sale.id);fail("Checkout must remain gated until reviewed recovery")}catch(_:IllegalStateException){}
    }

    @Test fun photoOnlyFeedPersistsAcrossReopenWithoutRepricingPaidSaleOrChangingProductVersion():Unit=runBlocking {
        val a=client();a.pull.sync();val paid=a.sell()
        val path=com.dauntehnusantara.pos.feature.catalog.photos.domain.PhotoRules.newPath(brand,product)
        events+=event("product_photos",record(product,"Photo").put("image_path",path))
        a.pull.sync()
        suspend fun photoMenu()=com.dauntehnusantara.pos.feature.catalog.data.RoomCatalogRepository(a.db,a.db.catalogDao(),clock).observe(brand).first()
        assertEquals(path,photoMenu().products.single().imagePath);assertEquals(1L,photoMenu().products.single().info.version)
        a.reopen();assertEquals(path,photoMenu().products.single().imagePath)
        assertEquals(paid,a.checkout.receipts().single())
        events+=event("product_photos",record(product,"Photo",2).put("active",false).put("image_path",JSONObject.NULL))
        a.pull.sync();assertNull(photoMenu().products.single().imagePath);assertEquals(2L,photoMenu().products.single().imageVersion)
        assertEquals(1L,photoMenu().products.single().info.version)
    }

}
