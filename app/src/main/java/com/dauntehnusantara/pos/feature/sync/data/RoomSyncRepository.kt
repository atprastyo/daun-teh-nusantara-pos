package com.dauntehnusantara.pos.feature.sync.data

import androidx.room.withTransaction
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.catalog.data.toDomain
import com.dauntehnusantara.pos.feature.catalog.domain.DEMO_BRAND_ID
import com.dauntehnusantara.pos.feature.checkout.data.CheckoutJson
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class RoomSyncRepository @Inject constructor(private val db: PosDatabase, private val gateway: SaleGateway,
    private val scheduler: SyncScheduler, private val clock: Clock) : SyncRepository {
    private val dao get() = db.checkoutDao()
    private val drainMutex = Mutex()
    private suspend fun rows(s: SaleScope) = dao.syncRows(s.brandId, s.outletId, s.deviceId, s.userId)
    override fun observe(scope: SaleScope) = db.invalidationTracker.createFlow("sales", "sync_outbox").map {
        val rows = rows(scope)
        SyncStatus(rows.count { it.state == "PENDING" }, rows.count { it.state == "RETRY" },
            rows.count { it.state == "AUTH_REQUIRED" }, rows.count { it.state == "REVIEW" }, rows.count { it.state == "SYNCED" },
            rows.lastOrNull { it.lastErrorCode != null }?.lastErrorCode)
    }
    override suspend fun record(receipt: Receipt) {
        val s = requireNotNull(gateway.checkoutScope()) { "Authenticated checkout is locked" }
        recordAuthorized(receipt, s)
    }
    internal suspend fun recordAuthorized(receipt: Receipt, s: SaleScope, stockScope:PullScope?=null) {
        require(receipt.brandId != DEMO_BRAND_ID && receipt.brandId == s.brandId && receipt.outletId == s.outletId &&
            receipt.cashierId == s.userId && receipt.deviceId == s.deviceId)
        val ids = listOf(receipt.id, receipt.operationId, s.brandId, s.outletId, s.deviceId, s.userId) + receipt.lines.flatMap { line ->
            listOf(line.id, line.productId) + line.groups.map { it.id } + line.modifiers.flatMap { listOf(it.optionId, it.groupId) }
        }
        require(ids.all { java.util.UUID.fromString(it).toString() == it })
        require(receipt.createdAtUtcMs in 0..clock.millis())
        val payload = CheckoutJson.payload(receipt, demo = false)
        require(payload.toByteArray(Charsets.UTF_8).size <= 262144)
        val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        db.withTransaction {
            dao.byOperation(receipt.operationId)?.let { existing ->
                if (existing.requestHash != hash || dao.outbox(receipt.operationId)?.payloadJson != payload) throw CheckoutConflict()
                return@withTransaction
            }
            val draft = Draft(receipt.id, receipt.operationId, 0, receipt.lines)
            CheckoutRules.validateDraft(draft)
            CheckoutRules.checkCatalog(draft, db.catalogDao().snapshot(s.brandId).toDomain())
            require(receipt.totalIdr == draft.totalIdr)
            val payment = receipt.payment
            require(payment == CheckoutRules.payment(receipt.totalIdr, PaymentInput(payment.method, payment.tenderedIdr,
                payment.method == PaymentMethod.QRIS_MANUAL && payment.verification == "CASHIER_CONFIRMED_MANUALLY")))
            dao.insert(SaleEntity(receipt.id, receipt.operationId, hash, s.brandId, s.outletId, s.deviceId, s.userId, receipt.createdAtUtcMs, receipt.totalIdr))
            dao.insert(receipt.lines.map { SaleItemEntity(it.id, receipt.id, it.productId, it.productName, it.unitBasePriceIdr, it.quantity, it.totalIdr, CheckoutJson.line(it).toString()) })
            dao.insertModifiers(receipt.lines.flatMap { line -> line.modifiers.map { SaleModifierEntity(newId(), line.id, it.optionId, it.groupName, it.optionName, it.priceDeltaIdr) } })
            dao.insert(PaymentEntity(newId(), receipt.id, payment.method.name, payment.amountIdr, payment.tenderedIdr, payment.changeIdr, payment.verification))
            if(stockScope!=null)com.dauntehnusantara.pos.feature.stock.data.StockConsumption.freeze(db,stockScope,receipt)
            dao.insert(OutboxEntity(receipt.operationId, receipt.id, "SUBMIT_SALE", payload, "PENDING", 0, receipt.createdAtUtcMs))
        }
        // Crash between commit and enqueue is recovered by the periodic/startup sweep.
        scheduler.enqueue()
    }
    override suspend fun retry(scope: SaleScope) {
        if (gateway.backgroundScope() != scope) return
        db.withTransaction { rows(scope).forEach { dao.retry(it.id) } }
        scheduler.enqueue(force = true)
    }
    override suspend fun drain(): Boolean = drainMutex.withLock {
        val scope = gateway.backgroundScope() ?: return@withLock false
        val due = dao.due(scope.brandId, scope.outletId, scope.deviceId, scope.userId, clock.millis())
        for (row in due) {
            val attempts = (row.attempts.toLong() + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            // Persist the attempt before IO. No IN_FLIGHT state can strand work after process death.
            dao.syncResult(row.id, "RETRY", attempts, clock.millis() + RetryPolicy.delayMs(row.id, attempts), "DELIVERY_UNCERTAIN", null)
            val result = gateway.submit(PendingSale(row.id, row.aggregateId, scope, row.payloadJson, attempts,db.pullDao().metadata("stock.intent.${row.id}")))
            val now = clock.millis()
            when (result) {
                PushResult.Accepted -> dao.syncResult(row.id, "SYNCED", attempts, 0, null, now)
                is PushResult.Review -> dao.syncResult(row.id, "REVIEW", attempts, 0, result.code, null)
                PushResult.AuthRequired -> { dao.syncResult(row.id, "AUTH_REQUIRED", attempts, now + 60_000, "AUTH_REQUIRED", null); break }
                PushResult.Retry -> Unit
            }
        }
        rows(scope).any { it.state in listOf("PENDING", "RETRY", "AUTH_REQUIRED") }
    }
}
