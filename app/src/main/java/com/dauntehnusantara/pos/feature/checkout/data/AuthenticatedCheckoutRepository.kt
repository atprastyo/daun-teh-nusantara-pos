package com.dauntehnusantara.pos.feature.checkout.data

import androidx.room.withTransaction
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.catalog.data.toDomain
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.sync.data.PullJson
import com.dauntehnusantara.pos.feature.sync.data.RoomSyncRepository
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class RoomAuthenticatedCheckoutFactory @Inject constructor(private val db: PosDatabase, private val clock: Clock,
    private val gateway: SaleGateway, private val pullGateway: PullGateway, private val sync: RoomSyncRepository, private val scheduler: SyncScheduler) : AuthenticatedCheckoutFactory {
    override fun open(scope: PullScope): CheckoutRepository = AuthenticatedCheckoutRepository(db, clock, gateway, pullGateway, sync, scheduler, scope)
}
internal class AuthenticatedCheckoutRepository(private val db: PosDatabase, private val clock: Clock,
    private val gateway: SaleGateway, private val pullGateway: PullGateway, private val sync: RoomSyncRepository, private val scheduler: SyncScheduler,
    private val scope: PullScope) : CheckoutRepository {
    private val s get() = scope.sale
    private val dao get() = db.checkoutDao()
    private suspend fun guard() { check(pullGateway.pullScope() == scope); check(gateway.checkoutScope() == s) { "Checkout locked" }; check(db.pullDao().cursor(scope.key)?.initialized == true) { "Initial sync required" } }
    private suspend fun session(): CheckoutSessionEntity = dao.session(scope.key) ?: CheckoutSessionEntity(scope.key, s.deviceId, newId(), newId(), 0, "[]", null).also { dao.insert(it) }
    private fun CheckoutSessionEntity.draft() = Draft(draftId, operationId, revision, CheckoutJson.readLines(cartJson))
    private suspend fun local(sale: SaleEntity): Receipt {
        val p = dao.payment(sale.id)
        return Receipt(sale.id, sale.operationId, sale.brandId, sale.outletId, sale.deviceId, sale.cashierId, sale.createdAtUtcMs,
            dao.items(sale.id).map { CheckoutJson.readLine(JSONObject(it.snapshotJson)) }, sale.totalIdr,
            PaymentResult(PaymentMethod.valueOf(p.method), p.amountIdr, p.tenderedIdr, p.changeIdr, p.verificationStatus))
    }
    override fun observe() = db.invalidationTracker.createFlow("checkout_session", "sales", "sync_outbox", "sync_cursor", "remote_sales", "app_metadata").map {
        check(pullGateway.pullScope() == scope && gateway.backgroundScope() == s)
        db.withTransaction {
            check(db.pullDao().metadata("sync.project").let { it == null || it == scope.project })
            val session = session()
            val deliveries = dao.syncRows(s.brandId, s.outletId, s.deviceId, s.userId).associate { row ->
                row.aggregateId to ReceiptDelivery(
                    runCatching { ReceiptDeliveryState.valueOf(row.state) }.getOrDefault(ReceiptDeliveryState.UNKNOWN),
                    row.state == "REVIEW" && row.lastErrorCode == "CATALOG_CHANGED",com.dauntehnusantara.pos.feature.stock.data.StockConsumption.delivery(db,scope,row.id))
            }.toMutableMap()
            // Only the validated, current-scope feed proves delivery of downloaded receipts.
            db.pullDao().sales(scope.key).forEach { row ->
                val remote = PullJson.receipt(row.payload)
                val localSale = dao.byOperation(remote.operationId)
                if (remote.id == row.saleId && remote.operationId == row.operationId &&
                    remote.brandId == s.brandId && remote.outletId == s.outletId &&
                    (scope.role in listOf("OWNER", "MANAGER") || remote.cashierId == s.userId) &&
                    (localSale == null || local(localSale) == remote)) {
                    deliveries[remote.id] = (deliveries[remote.id]?:ReceiptDelivery(ReceiptDeliveryState.SYNCED)).copy(state=ReceiptDeliveryState.SYNCED)
                }
            }
            CheckoutState(session.draft(), session.lastSaleId?.let { local(requireNotNull(dao.sale(it, s.brandId, s.outletId))) },
                dao.syncRows(s.brandId, s.outletId, s.deviceId, s.userId).count { it.state != "SYNCED" }, deliveries,com.dauntehnusantara.pos.feature.stock.data.StockConsumption.warnings(db,scope,session.draft()))
        }
    }
    private fun expected(session: CheckoutSessionEntity, draft: Draft) {
        if (session.draftId != draft.id || session.operationId != draft.operationId || session.revision != draft.revision || session.lastSaleId != null) throw CheckoutConflict()
    }
    private suspend fun edit(draft: Draft, change: suspend (List<CartLine>) -> List<CartLine>) {
        guard()
        db.withTransaction {
        val session = session(); expected(session, draft)
        val lines = change(session.draft().lines)
        if (lines.isNotEmpty()) CheckoutRules.validateDraft(draft.copy(lines = lines))
        dao.update(session.copy(cartJson = CheckoutJson.lines(lines), revision = Math.addExact(session.revision, 1)))
        }
    }
    override suspend fun add(draft: Draft, line: CartLine) = edit(draft) { lines ->
        val catalog = db.catalogDao().snapshot(s.brandId).toDomain()
        val fresh = CheckoutRules.line(catalog, line.productId, line.modifiers.map { it.optionId }, line.quantity, line.id)
        if (fresh != line) throw CheckoutConflict()
        lines + fresh
    }
    override suspend fun quantity(draft: Draft, lineId: String, quantity: Int) = edit(draft) { lines ->
        require(quantity in 1..CheckoutRules.MAX_QUANTITY && lines.any { it.id == lineId })
        lines.map { if (it.id == lineId) it.copy(quantity = quantity) else it }
    }
    override suspend fun remove(draft: Draft, lineId: String) = edit(draft) { lines ->
        require(lines.any { it.id == lineId }); lines.filterNot { it.id == lineId }
    }
    override suspend fun complete(command: CheckoutCommand): Receipt {
        guard()
        val receipt = db.withTransaction {
            val p = command.payment
            val commandKey = commandKey(command)
            val key = "checkout.command.${command.operationId}"
            dao.byOperation(command.operationId)?.let { old ->
                if (old.brandId != s.brandId || old.outletId != s.outletId || old.deviceId != s.deviceId || old.cashierId != s.userId || db.pullDao().metadata(key) != commandKey) throw CheckoutConflict()
                return@withTransaction local(old)
            }
            val session = session(); val draft = session.draft()
            expected(session, Draft(command.draftId, command.operationId, command.revision, emptyList()))
            CheckoutRules.validateDraft(draft)
            CheckoutRules.checkCatalog(draft, db.catalogDao().snapshot(s.brandId).toDomain())
            val receipt = Receipt(draft.id, draft.operationId, s.brandId, s.outletId, s.deviceId, s.userId, clock.millis(), draft.lines,
                draft.totalIdr, CheckoutRules.payment(draft.totalIdr, p))
            sync.recordAuthorized(receipt, s, scope) // Nested Room transaction: graph + outbox + consumed draft commit together.
            db.metadataDao().put(AppMetadata(key, commandKey))
            dao.update(session.copy(draftId = newId(), operationId = newId(), revision = 0, cartJson = "[]", lastSaleId = receipt.id))
            receipt
        }
        scheduler.enqueue()
        return receipt
    }
    private fun commandKey(command: CheckoutCommand): String = command.payment.let { p ->
        listOf(command.draftId, command.operationId, command.revision, p.method, p.tenderedIdr, p.manuallyVerified).joinToString("|")
    }
    override suspend fun reconcile(command: CheckoutCommand): CheckoutResolution {
        check(pullGateway.pullScope() == scope && gateway.backgroundScope() == s)
        return db.withTransaction {
            check(db.pullDao().metadata("sync.project").let { it == null || it == scope.project })
            val old = dao.byOperation(command.operationId)
            if (old != null) {
                if (old.id != command.draftId || old.brandId != s.brandId || old.outletId != s.outletId ||
                    old.deviceId != s.deviceId || old.cashierId != s.userId ||
                    db.pullDao().metadata("checkout.command.${command.operationId}") != commandKey(command)) CheckoutResolution.Unknown
                else CheckoutResolution.Saved(local(old))
            } else {
                val current = dao.session(scope.key)
                if (current != null && current.lastSaleId == null && current.draftId == command.draftId &&
                    current.operationId == command.operationId && current.revision == command.revision)
                    CheckoutResolution.Unsaved else CheckoutResolution.Unknown
            }
        }
    }
    override suspend fun dismissReceipt(saleId: String) {
        guard()
        db.withTransaction {
        val session = session()
        if (session.lastSaleId != null && session.lastSaleId != saleId) throw CheckoutConflict()
        dao.update(session.copy(lastSaleId = null))
        }
    }
    override suspend fun receipts(): List<Receipt> {
        check(pullGateway.pullScope() == scope && gateway.backgroundScope() == s)
        return db.withTransaction {
        check(db.pullDao().metadata("sync.project").let { it == null || it == scope.project })
        val local = dao.authorizedSales(s.brandId, s.outletId, s.userId, scope.role in listOf("OWNER", "MANAGER")).map { local(it) }
        val remote = db.pullDao().sales(scope.key).map { PullJson.receipt(it.payload) }
        (local + remote).distinctBy { it.id }.sortedWith(compareByDescending<Receipt> { it.createdAtUtcMs }.thenBy { it.id }).take(100)
        }
    }
}
