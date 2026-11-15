package com.dauntehnusantara.pos.feature.checkout.domain

import com.dauntehnusantara.pos.feature.catalog.domain.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
data class GroupVersion(val id: String, val version: Long)
data class ModifierSnapshot(val optionId: String, val groupId: String, val groupName: String,
    val optionName: String, val priceDeltaIdr: Long, val optionVersion: Long)
data class CartLine(val id: String, val productId: String, val productName: String,
    val productVersion: Long, val categoryVersion: Long, val unitBasePriceIdr: Long,
    val quantity: Int, val modifiers: List<ModifierSnapshot>, val groups: List<GroupVersion>) {
    val unitPriceIdr: Long get() = modifiers.fold(unitBasePriceIdr) { sum, option -> Math.addExact(sum, option.priceDeltaIdr) }
    val totalIdr: Long get() = Math.multiplyExact(unitPriceIdr, quantity.toLong())
}
data class Draft(val id: String, val operationId: String, val revision: Long, val lines: List<CartLine>) {
    val totalIdr: Long get() = lines.fold(0L) { total, line -> Math.addExact(total, line.totalIdr) }
}
enum class PaymentMethod { CASH, QRIS_MANUAL }
data class PaymentInput(val method: PaymentMethod, val tenderedIdr: Long? = null, val manuallyVerified: Boolean = false)
data class PaymentResult(val method: PaymentMethod, val amountIdr: Long, val tenderedIdr: Long?,
    val changeIdr: Long?, val verification: String)
data class CheckoutCommand(val draftId: String, val operationId: String, val revision: Long, val payment: PaymentInput)
data class Receipt(val id: String, val operationId: String, val brandId: String, val outletId: String,
    val deviceId: String, val cashierId: String, val createdAtUtcMs: Long, val lines: List<CartLine>,
    val totalIdr: Long, val payment: PaymentResult)
enum class ReceiptDeliveryState { PENDING, RETRY, AUTH_REQUIRED, REVIEW, SYNCED, UNKNOWN }
data class ReceiptDelivery(val state: ReceiptDeliveryState, val catalogChanged: Boolean = false)
data class CheckoutState(val draft: Draft, val receipt: Receipt?, val pendingCount: Int,
    val deliveries: Map<String, ReceiptDelivery> = emptyMap())
sealed interface CheckoutResolution {
    data class Saved(val receipt: Receipt) : CheckoutResolution
    data object Unsaved : CheckoutResolution
    data object Unknown : CheckoutResolution
}
class CheckoutConflict : IllegalStateException("Draft or catalog changed")

object CheckoutRules {
    const val MAX_QUANTITY = 999
    const val MAX_LINES = 100
    fun line(catalog: Catalog, productId: String, optionIds: List<String>, quantity: Int, id: String = newId()): CartLine {
        require(quantity in 1..MAX_QUANTITY) { "Invalid quantity" }
        CatalogRules.validateSelection(catalog, productId, optionIds)
        val product = catalog.visibleProducts().first { it.info.id == productId }
        val category = catalog.categories.first { it.info.id == product.categoryId }
        require(product.basePriceIdr >= 0)
        val groups = catalog.groupsFor(productId)
        val options = optionIds.sorted().map { optionId ->
            val option = catalog.options.first { it.info.id == optionId }
            require(option.priceDeltaIdr >= 0)
            val group = groups.first { it.info.id == option.groupId }
            ModifierSnapshot(option.info.id, group.info.id, group.info.name, option.info.name, option.priceDeltaIdr, option.info.version)
        }
        return CartLine(id, productId, product.info.name, product.info.version, category.info.version,
            product.basePriceIdr, quantity, options, groups.sortedBy { it.info.id }.map { GroupVersion(it.info.id, it.info.version) })
            .also { it.totalIdr } // Check integer overflow before accepting a line.
    }
    fun validateDraft(draft: Draft) {
        require(draft.lines.isNotEmpty() && draft.lines.size <= MAX_LINES) { "Empty or oversized cart" }
        require(draft.lines.map { it.id }.distinct().size == draft.lines.size)
        draft.lines.forEach { line ->
            require(line.quantity in 1..MAX_QUANTITY && line.unitBasePriceIdr >= 0)
            require(line.modifiers.all { it.priceDeltaIdr >= 0 })
            line.totalIdr
        }
        draft.totalIdr
    }
    fun checkCatalog(draft: Draft, catalog: Catalog) {
        draft.lines.forEach { saved ->
            val fresh = try { line(catalog, saved.productId, saved.modifiers.map { it.optionId }, saved.quantity, saved.id) }
            catch (_: IllegalArgumentException) { throw CheckoutConflict() }
            if (fresh != saved) throw CheckoutConflict()
        }
    }
    fun payment(totalIdr: Long, input: PaymentInput): PaymentResult {
        require(totalIdr >= 0)
        return when (input.method) {
            PaymentMethod.CASH -> {
                val tendered = requireNotNull(input.tenderedIdr) { "Cash amount is required" }
                require(tendered >= totalIdr) { "Insufficient cash" }
                PaymentResult(input.method, totalIdr, tendered, Math.subtractExact(tendered, totalIdr), "CASH_RECEIVED")
            }
            PaymentMethod.QRIS_MANUAL -> {
                require(input.manuallyVerified && input.tenderedIdr == null) { "Manual QRIS confirmation is required" }
                PaymentResult(input.method, totalIdr, null, null, "CASHIER_CONFIRMED_MANUALLY")
            }
        }
    }
}

interface CheckoutRepository {
    fun observe(): Flow<CheckoutState>
    suspend fun add(draft: Draft, line: CartLine)
    suspend fun quantity(draft: Draft, lineId: String, quantity: Int)
    suspend fun remove(draft: Draft, lineId: String)
    suspend fun complete(command: CheckoutCommand): Receipt
    suspend fun dismissReceipt(saleId: String)
    suspend fun receipts(): List<Receipt>
    /** Read-only reconciliation of the original command, never a new sale. */
    suspend fun reconcile(command: CheckoutCommand): CheckoutResolution = CheckoutResolution.Unknown
}

// Development identities only, never an enrollment or offline authorization lease.
const val DEMO_OUTLET_ID = "54757036-e8f1-49c4-b080-18d268dce46f"
const val DEMO_CASHIER_ID = "bf96ba04-dee2-4d1a-ab58-c9d79c28fe63"
