package com.dauntehnusantara.pos

import android.app.Application
import androidx.room.Room
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.catalog.data.RoomCatalogRepository
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.sync.data.RoomSyncRepository
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class PushSyncTest {
    private lateinit var db: PosDatabase
    private lateinit var repository: RoomSyncRepository
    private val scope = SaleScope(newId(),newId(),newId(),newId())
    private val clock = object : Clock() {
        var time = 1800000000000L
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = Instant.ofEpochMilli(time)
    }
    private val gateway = object : SaleGateway {
        var current: SaleScope? = scope
        var result: PushResult = PushResult.Accepted
        var cancel = false
        val sent = mutableListOf<PendingSale>()
        val serverOperations = mutableSetOf<String>()
        override suspend fun backgroundScope() = current
        override suspend fun checkoutScope() = current
        override suspend fun submit(sale: PendingSale): PushResult {
            sent += sale
            serverOperations += sale.operationId // Server committed before simulated lost ack/cancellation.
            if (cancel) throw CancellationException("process interrupted")
            return result
        }
    }
    private val scheduler = object : SyncScheduler {
        var enqueued = 0
        var forced = false
        override fun enqueue(force: Boolean) { enqueued++; forced = force }
        override fun ensurePeriodic() { }
    }
    private val context get() = RuntimeEnvironment.getApplication()
    private val name = "push-test.db"
    @Before fun before() { context.deleteDatabase(name); open() }
    private fun open() {
        db = Room.databaseBuilder(context,PosDatabase::class.java,name).addCallback(CatalogConstraints).build()
        repository = RoomSyncRepository(db,gateway,scheduler,clock)
    }
    @After fun after() { db.close();context.deleteDatabase(name) }
    private suspend fun receipt(): Receipt {
        val catalog = RoomCatalogRepository(db,db.catalogDao(),clock)
        val category = catalog.save(Category(RecordInfo(brandId=scope.brandId,name="Tea")))
        val product = catalog.save(Product(RecordInfo(brandId=scope.brandId,name="Original"),category.info.id,6000))
        val line = CheckoutRules.line(catalog.observe(scope.brandId).first(),product.info.id,emptyList(),2)
        return Receipt(newId(),newId(),scope.brandId,scope.outletId,scope.deviceId,scope.userId,clock.millis(),listOf(line),12000,
            CheckoutRules.payment(12000,PaymentInput(PaymentMethod.CASH,20000)))
    }
    private suspend fun row(r: Receipt) = requireNotNull(db.checkoutDao().outbox(r.operationId))
    private fun count(table: String): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst();it.getInt(0) }
    @Test fun frozenRealSaleAndOutboxCommitTogetherAndSurviveRestart(): Unit = runBlocking {
        val r=receipt();repository.record(r);repository.record(r)
        assertEquals(1,count("sales"));assertEquals(1,count("payments"));assertEquals(1,count("sync_outbox"))
        val original=row(r).payloadJson;assertTrue(original.contains("\"demo\":false"))
        db.close();open();assertEquals(original,row(r).payloadJson)
        assertFalse(repository.drain());assertEquals("SYNCED",row(r).state);assertNotNull(row(r).acknowledgedAtMs)
        repository.drain();assertEquals(1,gateway.sent.size)
    }
    @Test fun ackLossRetriesImmutablePayloadWithSameOperationAfterReopen(): Unit = runBlocking {
        val r=receipt();repository.record(r);gateway.result=PushResult.Retry
        assertTrue(repository.drain());assertEquals("RETRY",row(r).state);assertEquals(1,row(r).attempts)
        repository.drain();assertEquals(1,gateway.sent.size) // Due time is honored.
        clock.time=row(r).nextAttemptAtMs+1;db.close();open();gateway.result=PushResult.Accepted
        repository.drain();assertEquals(gateway.sent[0].payload,gateway.sent[1].payload);assertEquals(1,gateway.serverOperations.size)
        assertEquals(2,row(r).attempts);assertEquals("SYNCED",row(r).state)
    }
    @Test fun cancellationCannotStrandInflightOperation(): Unit = runBlocking {
        val r=receipt();repository.record(r);gateway.cancel=true
        try { repository.drain();fail("Expected cancellation") } catch (_: CancellationException) { }
        assertEquals("RETRY",row(r).state);gateway.cancel=false;clock.time=row(r).nextAttemptAtMs+1
        db.close();open();repository.drain();assertEquals("SYNCED",row(r).state);assertEquals(1,gateway.serverOperations.size)
    }
    @Test fun changedPriceReviewPreservesReceiptAndCannotBeBlindlyRetried(): Unit = runBlocking {
        val r=receipt();repository.record(r);val original=row(r).payloadJson
        gateway.result=PushResult.Review("CATALOG_CHANGED");repository.drain();repository.retry(scope);repository.drain()
        assertEquals("REVIEW",row(r).state);assertEquals(original,row(r).payloadJson);assertEquals(1,gateway.sent.size)
        val status=repository.observe(scope).first();assertEquals(1,status.review);assertEquals("CATALOG_CHANGED",status.lastCode)
    }
    @Test fun logoutAndAnotherUserCannotDrainOrReadOriginalQueue(): Unit = runBlocking {
        val r=receipt();repository.record(r);gateway.current=null;repository.drain();assertTrue(gateway.sent.isEmpty())
        gateway.current=scope.copy(userId=newId());repository.drain();assertTrue(gateway.sent.isEmpty())
        assertEquals(SyncStatus(),repository.observe(requireNotNull(gateway.current)).first())
        gateway.current=scope;gateway.result=PushResult.AuthRequired;repository.drain();assertEquals("AUTH_REQUIRED",row(r).state)
        gateway.result=PushResult.Accepted;repository.retry(scope);assertTrue(scheduler.forced);repository.drain();assertEquals("SYNCED",row(r).state)
    }
    @Test fun invalidPaymentOrFailedOutboxInsertLeavesNoPartialSale(): Unit = runBlocking {
        val r=receipt()
        try { repository.record(r.copy(payment=r.payment.copy(changeIdr=0)));fail("Expected invalid payment") } catch (_: IllegalArgumentException) { }
        assertEquals(0,count("sales"))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_outbox BEFORE INSERT ON sync_outbox BEGIN SELECT RAISE(ABORT,'Simulated disk failure'); END")
        try { repository.record(r);fail("Expected rollback") } catch (_: android.database.SQLException) { }
        assertEquals(0,count("sales"));assertEquals(0,count("payments"));assertEquals(0,count("sale_items"));assertEquals(0,scheduler.enqueued)
    }
    @Test fun scopeMismatchCannotCreateRealSaleAndTerminalPayloadCannotBeMutated(): Unit = runBlocking {
        val r=receipt()
        try { repository.record(r.copy(deviceId=newId()));fail("Scope must match") } catch (_: IllegalArgumentException) { }
        repository.record(r)
        try { db.openHelper.writableDatabase.execSQL("UPDATE sync_outbox SET payloadJson='{}'");fail("Payload must be immutable") } catch (_: android.database.SQLException) { }
        assertEquals(1,count("sales"))
    }
    @Test fun retryDelayGrowsAndStaysBounded() {
        assertTrue(RetryPolicy.delayMs("a",2)>RetryPolicy.delayMs("a",1))
        assertEquals(RetryPolicy.delayMs("a",100),RetryPolicy.delayMs("a",101))
        assertTrue(RetryPolicy.delayMs("a",100)<=6*60*60_000L+15_000)
    }
}
