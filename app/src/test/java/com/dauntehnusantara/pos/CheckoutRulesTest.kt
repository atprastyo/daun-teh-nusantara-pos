package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.catalog.data.DemoCatalog
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.core.designsystem.formatJakarta
import org.junit.Assert.*
import org.junit.Test

class CheckoutRulesTest {
    private val product = DemoCatalog.products[0]
    private val catalog = Catalog(listOf(DemoCatalog.category), listOf(product), DemoCatalog.groups, DemoCatalog.options,
        DemoCatalog.groups.mapIndexed { i, group -> ProductGroup(product.info.id, group.info.id, i) })
    private val choices = listOf("Reguler", "Gula normal", "Es normal", "Boba").map { name -> catalog.options.first { it.info.name == name }.info.id }
    private fun line(quantity: Int = 1) = CheckoutRules.line(catalog, product.info.id, choices, quantity)
    @Test fun modifierAndQuantityTotalsUseIntegerIdr() {
        val line = line(2)
        assertEquals(9000L, line.unitPriceIdr); assertEquals(18000L, line.totalIdr)
        assertEquals(18000L, Draft(newId(), newId(), 0, listOf(line)).totalIdr)
    }
    @Test fun emptyCartAndInvalidQuantityAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { line(0) }
        assertThrows(IllegalArgumentException::class.java) { line(1000) }
        assertThrows(IllegalArgumentException::class.java) { CheckoutRules.validateDraft(Draft(newId(), newId(), 0, emptyList())) }
    }
    @Test fun totalsRejectOverflowRatherThanWrapping() {
        val huge = catalog.copy(products = listOf(product.copy(basePriceIdr = Long.MAX_VALUE)))
        assertThrows(ArithmeticException::class.java) { CheckoutRules.line(huge, product.info.id, choices, 1) }
        val noOptions = catalog.copy(groups = emptyList(), assignments = emptyList(), products = listOf(product.copy(basePriceIdr = Long.MAX_VALUE)))
        assertThrows(ArithmeticException::class.java) { CheckoutRules.line(noOptions, product.info.id, emptyList(), 2) }
        val maximum = CheckoutRules.line(noOptions, product.info.id, emptyList(), 1)
        assertThrows(ArithmeticException::class.java) { Draft(newId(), newId(), 0, listOf(maximum, maximum.copy(id = newId()))).totalIdr }
    }
    @Test fun cashTenderMustCoverTotalAndChangeIsExact() {
        assertEquals(2000L, CheckoutRules.payment(18000, PaymentInput(PaymentMethod.CASH, 20000)).changeIdr)
        assertEquals(0L, CheckoutRules.payment(18000, PaymentInput(PaymentMethod.CASH, 18000)).changeIdr)
        assertThrows(IllegalArgumentException::class.java) { CheckoutRules.payment(18000, PaymentInput(PaymentMethod.CASH, 17999)) }
        assertThrows(IllegalArgumentException::class.java) { CheckoutRules.payment(0, PaymentInput(PaymentMethod.CASH, -1)) }
        assertThrows(IllegalArgumentException::class.java) { CheckoutRules.payment(1, PaymentInput(PaymentMethod.CASH)) }
    }
    @Test fun maximumCashAmountIsNotRoundedThroughDouble() {
        assertEquals(Long.MAX_VALUE - 1, CheckoutRules.payment(1, PaymentInput(PaymentMethod.CASH, Long.MAX_VALUE)).changeIdr)
    }
    @Test fun manualQrisRequiresExplicitConfirmationAndHasNoCashChange() {
        assertThrows(IllegalArgumentException::class.java) { CheckoutRules.payment(9000, PaymentInput(PaymentMethod.QRIS_MANUAL)) }
        assertThrows(IllegalArgumentException::class.java) { CheckoutRules.payment(9000, PaymentInput(PaymentMethod.QRIS_MANUAL, 10000, true)) }
        val result = CheckoutRules.payment(9000, PaymentInput(PaymentMethod.QRIS_MANUAL, manuallyVerified = true))
        assertEquals("CASHIER_CONFIRMED_MANUALLY", result.verification)
        assertNull(result.changeIdr); assertNull(result.tenderedIdr)
    }
    @Test fun changedPriceOrModifierRulesCauseConflictWithoutRepricingSnapshot() {
        val draft = Draft(newId(), newId(), 0, listOf(line()))
        val changed = catalog.copy(products = listOf(product.copy(basePriceIdr = 7000)))
        assertThrows(CheckoutConflict::class.java) { CheckoutRules.checkCatalog(draft, changed) }
        val changedGroup = catalog.copy(groups = catalog.groups.map { it.copy(info = it.info.copy(version = 9)) })
        assertThrows(CheckoutConflict::class.java) { CheckoutRules.checkCatalog(draft, changedGroup) }
        assertEquals(9000L, draft.totalIdr)
        CheckoutRules.checkCatalog(draft, catalog)
    }
    @Test fun archivedProductAndMissingRequiredOptionRejectCheckout() {
        val draft = Draft(newId(), newId(), 0, listOf(line()))
        val archived = catalog.copy(products = listOf(product.copy(info = product.info.copy(active = false))))
        assertThrows(CheckoutConflict::class.java) { CheckoutRules.checkCatalog(draft, archived) }
        assertThrows(IllegalArgumentException::class.java) { CheckoutRules.line(catalog, product.info.id, emptyList(), 1) }
    }
    @Test fun receiptTimeUsesJakarta() { assertTrue(formatJakarta(0).endsWith("07:00:00")) }
}
