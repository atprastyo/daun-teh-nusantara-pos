package com.dauntehnusantara.pos.feature.checkout.data

import androidx.room.withTransaction
import com.dauntehnusantara.pos.BuildConfig
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.catalog.data.toDomain
import com.dauntehnusantara.pos.feature.catalog.domain.DEMO_BRAND_ID
import com.dauntehnusantara.pos.feature.checkout.domain.*
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class RoomCheckoutRepository @Inject constructor(private val db: PosDatabase, private val clock: Clock) : CheckoutRepository {
    private val dao get() = db.checkoutDao()
    private fun guard() { check(BuildConfig.DEBUG) { "Demo checkout is disabled in release until authenticated enrollment exists" } }
    private suspend fun session(): CheckoutSessionEntity {
        guard()
        return dao.session(DEMO_OUTLET_ID) ?: CheckoutSessionEntity(DEMO_OUTLET_ID, newId(), newId(), newId(), 0, "[]", null).also { dao.insert(it) }
    }
    private fun CheckoutSessionEntity.draft() = Draft(draftId, operationId, revision, CheckoutJson.readLines(cartJson)).also { if (it.lines.isNotEmpty()) CheckoutRules.validateDraft(it) }
    override fun observe() = db.invalidationTracker.createFlow("checkout_session", "sales", "sync_outbox")
        .map { db.withTransaction {
            val session = session()
            CheckoutState(session.draft(), session.lastSaleId?.let { receipt(requireNotNull(dao.sale(it, DEMO_BRAND_ID, DEMO_OUTLET_ID))) }, dao.pending(DEMO_BRAND_ID, DEMO_OUTLET_ID))
        } }
    private fun requireDraft(session: CheckoutSessionEntity, id: String, operation: String, revision: Long) {
        if (session.draftId != id || session.operationId != operation || session.revision != revision || session.lastSaleId != null) throw CheckoutConflict()
    }
    private suspend fun edit(expected: Draft, change: suspend (List<CartLine>) -> List<CartLine>): Unit = db.withTransaction {
        val session = session()
        requireDraft(session, expected.id, expected.operationId, expected.revision)
        val lines = change(session.draft().lines)
        require(lines.size <= CheckoutRules.MAX_LINES)
        if (lines.isNotEmpty()) CheckoutRules.validateDraft(Draft(session.draftId, session.operationId, session.revision, lines))
        dao.update(session.copy(cartJson = CheckoutJson.lines(lines), revision = Math.addExact(session.revision, 1)))
    }
    override suspend fun add(draft: Draft, line: CartLine) = edit(draft) { lines ->
        val catalog = db.catalogDao().snapshot(DEMO_BRAND_ID).toDomain()
        val fresh = try { CheckoutRules.line(catalog, line.productId, line.modifiers.map { it.optionId }, line.quantity, line.id) }
        catch (_: IllegalArgumentException) { throw CheckoutConflict() }
        if (fresh != line) throw CheckoutConflict()
        lines + fresh
    }
    override suspend fun quantity(draft: Draft, lineId: String, quantity: Int) = edit(draft) { lines ->
        require(quantity in 1..CheckoutRules.MAX_QUANTITY)
        require(lines.any { it.id == lineId })
        lines.map { if (it.id == lineId) it.copy(quantity = quantity) else it }
    }
    override suspend fun remove(draft: Draft, lineId: String) = edit(draft) { lines ->
        require(lines.any { it.id == lineId }); lines.filterNot { it.id == lineId }
    }
    private fun hash(command: CheckoutCommand): String {
        val p = command.payment
        val canonical = listOf(command.draftId, command.operationId, command.revision.toString(), p.method.name,
            p.tenderedIdr?.toString() ?: "null", p.manuallyVerified.toString()).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
    override suspend fun complete(command: CheckoutCommand): Receipt = db.withTransaction {
        guard()
        val requestHash = hash(command)
        dao.byOperation(command.operationId)?.let { existing ->
            if (existing.requestHash != requestHash || existing.brandId != DEMO_BRAND_ID || existing.outletId != DEMO_OUTLET_ID) throw CheckoutConflict()
            return@withTransaction receipt(existing)
        }
        val session = session()
        requireDraft(session, command.draftId, command.operationId, command.revision)
        val draft = session.draft()
        CheckoutRules.validateDraft(draft)
        CheckoutRules.checkCatalog(draft, db.catalogDao().snapshot(DEMO_BRAND_ID).toDomain())
        val payment = CheckoutRules.payment(draft.totalIdr, command.payment)
        val receipt = Receipt(draft.id, draft.operationId, DEMO_BRAND_ID, DEMO_OUTLET_ID, session.deviceId,
            DEMO_CASHIER_ID, clock.millis(), draft.lines, draft.totalIdr, payment)
        dao.insert(SaleEntity(receipt.id, receipt.operationId, requestHash, receipt.brandId, receipt.outletId,
            receipt.deviceId, receipt.cashierId, receipt.createdAtUtcMs, receipt.totalIdr))
        dao.insert(receipt.lines.map { line -> SaleItemEntity(line.id, receipt.id, line.productId, line.productName,
            line.unitBasePriceIdr, line.quantity, line.totalIdr, CheckoutJson.line(line).toString()) })
        dao.insertModifiers(receipt.lines.flatMap { line -> line.modifiers.map { option ->
            SaleModifierEntity(newId(), line.id, option.optionId, option.groupName, option.optionName, option.priceDeltaIdr)
        } })
        dao.insert(PaymentEntity(newId(), receipt.id, payment.method.name, payment.amountIdr, payment.tenderedIdr, payment.changeIdr, payment.verification))
        dao.insert(OutboxEntity(receipt.operationId, receipt.id, "DEMO_SALE", CheckoutJson.payload(receipt), "PENDING_DEMO", 0, receipt.createdAtUtcMs))
        // Clearing the consumed draft and retaining its receipt is part of the same commit.
        dao.update(session.copy(draftId = newId(), operationId = newId(), revision = 0, cartJson = "[]", lastSaleId = receipt.id))
        receipt
    }
    private suspend fun receipt(sale: SaleEntity): Receipt {
        val payment = dao.payment(sale.id)
        val lines = dao.items(sale.id).map { CheckoutJson.readLine(JSONObject(it.snapshotJson)) }
        check(lines.fold(0L) { total, line -> Math.addExact(total, line.totalIdr) } == sale.totalIdr && payment.amountIdr == sale.totalIdr)
        return Receipt(sale.id, sale.operationId, sale.brandId, sale.outletId, sale.deviceId, sale.cashierId, sale.createdAtUtcMs,
            lines, sale.totalIdr, PaymentResult(PaymentMethod.valueOf(payment.method), payment.amountIdr, payment.tenderedIdr, payment.changeIdr, payment.verificationStatus))
    }
    override suspend fun dismissReceipt(saleId: String): Unit = db.withTransaction {
        val session = session()
        if (session.lastSaleId == null) return@withTransaction
        if (session.lastSaleId != saleId) throw CheckoutConflict()
        dao.update(session.copy(lastSaleId = null))
    }
    override suspend fun receipts(): List<Receipt> = db.withTransaction {
        guard(); dao.sales(DEMO_BRAND_ID, DEMO_OUTLET_ID).map { receipt(it) }
    }
}
