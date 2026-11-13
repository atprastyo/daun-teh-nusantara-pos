package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.catalog.data.DemoCatalog
import com.dauntehnusantara.pos.feature.catalog.domain.*
import org.junit.Assert.*
import org.junit.Test

class CatalogRulesTest {
    private val demo = Catalog(listOf(DemoCatalog.category), DemoCatalog.products, DemoCatalog.groups,
        DemoCatalog.options, DemoCatalog.groups.mapIndexed { i, g -> ProductGroup(DemoCatalog.products[0].info.id, g.info.id, i) })
    private val product = DemoCatalog.products[0].info.id
    private val required = listOf("Reguler", "Gula normal", "Es normal").map { name -> DemoCatalog.options.first { it.info.name == name }.info.id }

    @Test fun requiredChoicesAndOptionalToppingsAreValid() {
        CatalogRules.validateSelection(demo, product, required)
        CatalogRules.validateSelection(demo, product, required + DemoCatalog.options.last().info.id)
    }
    @Test fun missingRequiredChoiceIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validateSelection(demo, product, required.drop(1)) }
    }
    @Test fun tooManySingleSelectChoicesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validateSelection(demo, product, required + DemoCatalog.options[1].info.id) }
    }
    @Test fun duplicateAndUnknownChoicesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validateSelection(demo, product, required + required.first()) }
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validateSelection(demo, product, required + "unknown") }
    }
    @Test fun inactiveOrUnassignedOptionsAreRejected() {
        val inactive = demo.copy(options = demo.options.map { if (it.info.id == required[0]) it.copy(info = it.info.copy(active = false)) else it })
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validateSelection(inactive, product, required) }
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validateSelection(demo.copy(assignments = emptyList()), product, required) }
    }
    @Test fun archivedCategoryHidesItsProducts() {
        val archived = demo.copy(categories = listOf(DemoCatalog.category.copy(info = DemoCatalog.category.info.copy(active = false))))
        assertTrue(archived.visibleProducts().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validateSelection(archived, product, required) }
    }
    @Test fun invalidNamesPricesAndLimitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(DemoCatalog.category.copy(info = DemoCatalog.category.info.copy(name = "  "))) }
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(DemoCatalog.products[0].copy(basePriceIdr = -1)) }
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(DemoCatalog.groups[0].copy(minSelect = 2, maxSelect = 1)) }
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(DemoCatalog.options[0].copy(priceDeltaIdr = -1)) }
    }
    @Test fun uuidAndVersionAreValidatedAndLongPricesRemainIntegral() {
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(DemoCatalog.category.info.copy(id = "invalid")) }
        assertThrows(IllegalArgumentException::class.java) { CatalogRules.validate(DemoCatalog.category.info.copy(version = -1)) }
        CatalogRules.validate(DemoCatalog.products[0].copy(basePriceIdr = Long.MAX_VALUE))
    }
}
