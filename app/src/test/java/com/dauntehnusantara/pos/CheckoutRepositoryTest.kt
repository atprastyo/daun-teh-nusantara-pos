package com.dauntehnusantara.pos

import android.app.Application
import androidx.room.Room
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.catalog.data.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.data.RoomCheckoutRepository
import com.dauntehnusantara.pos.feature.checkout.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
@Config(sdk = [28], application = Application::class)
class CheckoutRepositoryTest {
    private lateinit var db: PosDatabase
    private lateinit var repository: RoomCheckoutRepository
    private lateinit var catalogRepository: RoomCatalogRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC)
    private val context get() = RuntimeEnvironment.getApplication()
    private val name = "checkout-test.db"
    @Before fun before() { context.deleteDatabase(name); open() }
    private fun open() {
        db = Room.databaseBuilder(context, PosDatabase::class.java, name).addCallback(CatalogConstraints).build()
        repository = RoomCheckoutRepository(db, clock)
        catalogRepository = RoomCatalogRepository(db, db.catalogDao(), clock)
    }
    @After fun after() { db.close(); context.deleteDatabase(name) }
    private suspend fun prepare(quantity: Int = 1): Draft {
        Assume.assumeTrue(BuildConfig.DEBUG)
        catalogRepository.save(DemoCatalog.category)
        DemoCatalog.groups.forEach { catalogRepository.save(it) }
        DemoCatalog.options.forEach { catalogRepository.save(it) }
        val product = catalogRepository.save(DemoCatalog.products[0])
        catalogRepository.assignGroups(DEMO_BRAND_ID, product.info.id, DemoCatalog.groups.map { it.info.id }, product.info.version)
        val catalog = catalogRepository.observe(DEMO_BRAND_ID).first()
        val choices = listOf("Reguler", "Gula normal", "Es normal", "Boba").map { name -> catalog.options.first { it.info.name == name }.info.id }
        val draft = repository.observe().first().draft
        repository.add(draft, CheckoutRules.line(catalog, product.info.id, choices, quantity))
        return repository.observe().first().draft
    }
    private fun command(draft: Draft, input: PaymentInput = PaymentInput(PaymentMethod.CASH, 20000)) = CheckoutCommand(draft.id, draft.operationId, draft.revision, input)
    private fun count(table: String): Int = db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getInt(0) }
    private fun payload(): String = db.openHelper.writableDatabase.query("SELECT payloadJson FROM sync_outbox").use { it.moveToFirst(); it.getString(0) }
    @Test fun debugSessionIsStableAndReleaseHasNoDemoCheckout(): Unit = runBlocking {
        if (BuildConfig.DEBUG) {
            val first = repository.observe().first()
            db.close(); open()
            assertEquals(first, repository.observe().first())
        } else {
            try { repository.observe().first(); fail("Release must require enrollment") } catch (_: IllegalStateException) { }
            try { repository.complete(CheckoutCommand(newId(), newId(), 0, PaymentInput(PaymentMethod.CASH, 0))); fail("Release must reject checkout") }
            catch (_: IllegalStateException) { }
            assertEquals(0, count("sales")); assertEquals(0, count("checkout_session"))
        }
    }
    @Test fun cartAndSaleSurviveReopenWithCompleteGraphAndPendingOperation(): Unit = runBlocking {
        val draft = prepare(2)
        db.close(); open()
        assertEquals(draft, repository.observe().first().draft)
        val receipt = repository.complete(command(draft))
        assertEquals(18000L, receipt.totalIdr); assertEquals(2000L, receipt.payment.changeIdr)
        db.close(); open()
        val state = repository.observe().first()
        assertEquals(receipt, state.receipt); assertEquals(1, state.pendingCount); assertTrue(state.draft.lines.isEmpty())
        assertEquals(1, count("sales")); assertEquals(1, count("payments")); assertEquals(1, count("sale_items"))
        assertEquals(4, count("sale_item_modifiers")); assertEquals(1, count("sync_outbox"))
        val payload = JSONObject(payload())
        assertEquals(receipt.operationId, payload.getString("operation_id"))
        assertEquals(receipt.id, payload.getString("sale_id")); assertTrue(payload.getBoolean("demo"))
        assertEquals(18000L, payload.getLong("total_idr"))
    }
    @Test fun lostAcknowledgmentRetryReturnsSameSaleAndRejectsDifferentPayload(): Unit = runBlocking {
        val draft = prepare(); val command = command(draft)
        val committed = repository.complete(command)
        db.close(); open()
        assertEquals(committed, repository.complete(command))
        try { repository.complete(command.copy(payment = PaymentInput(PaymentMethod.CASH, 30000))); fail("Expected payload conflict") }
        catch (_: CheckoutConflict) { }
        assertEquals(1, count("sales")); assertEquals(1, count("sync_outbox"))
    }
    @Test fun concurrentDuplicateSubmitCreatesOnlyOneSale(): Unit = runBlocking {
        val command = command(prepare())
        val receipts = coroutineScope { List(4) { async(Dispatchers.Default) { repository.complete(command) } }.awaitAll() }
        assertEquals(1, receipts.distinct().size); assertEquals(1, count("sales")); assertEquals(1, count("payments")); assertEquals(1, count("sync_outbox"))
    }
    @Test fun outboxFailureRollsBackSaleGraphAndRetainsRetryableDraft(): Unit = runBlocking {
        val draft = prepare(); val command = command(draft)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_outbox BEFORE INSERT ON sync_outbox BEGIN SELECT RAISE(ABORT, 'Simulated disk failure'); END")
        try { repository.complete(command); fail("Expected write failure") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        listOf("sales", "payments", "sale_items", "sale_item_modifiers", "sync_outbox").forEach { assertEquals(0, count(it)) }
        assertEquals(draft, repository.observe().first().draft); assertNull(repository.observe().first().receipt)
        sql.execSQL("DROP TRIGGER fail_outbox")
        assertEquals(draft.id, repository.complete(command).id); assertEquals(1, count("sync_outbox"))
    }
    @Test fun staleCatalogRejectsCheckoutAndLeavesPricesUntouched(): Unit = runBlocking {
        val draft = prepare()
        val product = catalogRepository.observe(DEMO_BRAND_ID).first().products.first()
        catalogRepository.save(product.copy(basePriceIdr = 18000))
        try { repository.complete(command(draft)); fail("Expected catalog conflict") } catch (_: CheckoutConflict) { }
        assertEquals(draft, repository.observe().first().draft); assertEquals(0, count("sales"))
    }
    @Test fun receiptsAndOutboxKeepOriginalNamesAndCannotBeRewritten(): Unit = runBlocking {
        val draft = prepare(); val receipt = repository.complete(command(draft)); val before = payload()
        val product = catalogRepository.observe(DEMO_BRAND_ID).first().products.first()
        catalogRepository.save(product.copy(info = product.info.copy(name = "Renamed"), basePriceIdr = 45000))
        assertEquals(receipt, repository.receipts().first()); assertEquals(before, payload())
        val sql = db.openHelper.writableDatabase
        for (statement in listOf("UPDATE sales SET totalIdr = 1", "DELETE FROM sale_items", "UPDATE payments SET amountIdr = 1", "UPDATE sync_outbox SET payloadJson = '{}'")) {
            try { sql.execSQL(statement); fail("Expected append-only guard") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        }
    }
    @Test fun manualQrisCannotCompleteWithoutConfirmation(): Unit = runBlocking {
        val draft = prepare()
        try { repository.complete(command(draft, PaymentInput(PaymentMethod.QRIS_MANUAL))); fail("Expected manual confirmation") }
        catch (_: IllegalArgumentException) { }
        assertEquals(0, count("sales")); assertEquals(draft, repository.observe().first().draft)
        val receipt = repository.complete(command(draft, PaymentInput(PaymentMethod.QRIS_MANUAL, manuallyVerified = true)))
        assertEquals("CASHIER_CONFIRMED_MANUALLY", receipt.payment.verification)
        assertNull(receipt.payment.tenderedIdr); assertNull(receipt.payment.changeIdr)
    }
    @Test fun staleCartEditsCannotOverwriteAndNewSaleGetsNewOperation(): Unit = runBlocking {
        val draft = prepare(); val line = draft.lines.first()
        repository.quantity(draft, line.id, 2)
        try { repository.remove(draft, line.id); fail("Expected cart conflict") } catch (_: CheckoutConflict) { }
        val updated = repository.observe().first().draft
        assertEquals(2, updated.lines.first().quantity)
        val receipt = repository.complete(command(updated))
        repository.dismissReceipt(receipt.id)
        val fresh = repository.observe().first()
        assertNull(fresh.receipt); assertNotEquals(draft.operationId, fresh.draft.operationId)
        assertEquals(1, repository.receipts().size)
    }
}
