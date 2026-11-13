package com.dauntehnusantara.pos.feature.catalog.domain

import java.util.UUID
import kotlinx.coroutines.flow.Flow

// UTC epoch milliseconds; money is integer IDR. Version 0 means not yet persisted.
data class RecordInfo(
    val id: String = UUID.randomUUID().toString(), val brandId: String,
    val name: String, val active: Boolean = true, val version: Long = 0,
    val createdAtUtcMs: Long = 0, val updatedAtUtcMs: Long = 0,
)
data class Category(val info: RecordInfo, val sortOrder: Int = 0)
data class Product(val info: RecordInfo, val categoryId: String, val basePriceIdr: Long)
data class ModifierGroup(val info: RecordInfo, val minSelect: Int, val maxSelect: Int)
data class ModifierOption(val info: RecordInfo, val groupId: String, val priceDeltaIdr: Long)
data class ProductGroup(val productId: String, val groupId: String, val sortOrder: Int)
data class Catalog(
    val categories: List<Category> = emptyList(), val products: List<Product> = emptyList(),
    val groups: List<ModifierGroup> = emptyList(), val options: List<ModifierOption> = emptyList(),
    val assignments: List<ProductGroup> = emptyList(),
) {
    fun visibleProducts() = products.filter { product ->
        product.info.active && categories.any { it.info.id == product.categoryId && it.info.active }
    }
    fun groupsFor(productId: String) = assignments.filter { it.productId == productId }
        .sortedBy { it.sortOrder }.mapNotNull { link -> groups.find { it.info.id == link.groupId && it.info.active } }
}

enum class CatalogRecord { Category, Product, Group, Option }
class CatalogConflict : IllegalStateException("Catalog version changed")

interface CatalogRepository {
    fun observe(brandId: String): Flow<Catalog>
    suspend fun save(category: Category): Category
    suspend fun save(product: Product): Product
    suspend fun save(group: ModifierGroup): ModifierGroup
    suspend fun save(option: ModifierOption): ModifierOption
    suspend fun assignGroups(brandId: String, productId: String, groupIds: List<String>, expectedVersion: Long)
    suspend fun archive(brandId: String, kind: CatalogRecord, id: String, expectedVersion: Long)
    suspend fun seedDemo()
}

object CatalogRules {
    fun validate(info: RecordInfo) {
        require(UUID.fromString(info.id).toString() == info.id) { "Invalid UUID" }
        require(UUID.fromString(info.brandId).toString() == info.brandId) { "Invalid brand UUID" }
        require(info.name.isNotBlank() && info.name.length <= 120) { "Name must contain 1–120 characters" }
        require(info.version >= 0 && info.version < Long.MAX_VALUE) { "Invalid version" }
    }
    fun validate(category: Category) { validate(category.info); require(category.sortOrder >= 0) }
    fun validate(product: Product) { validate(product.info); require(product.basePriceIdr >= 0) }
    fun validate(group: ModifierGroup) {
        validate(group.info)
        require(group.minSelect >= 0 && group.maxSelect >= group.minSelect && group.maxSelect > 0)
    }
    fun validate(option: ModifierOption) { validate(option.info); require(option.priceDeltaIdr >= 0) }

    // A catalog-level validator for a modifier choice; cart/checkout calculations belong to M2.
    fun validateSelection(catalog: Catalog, productId: String, selectedIds: List<String>) {
        require(catalog.visibleProducts().any { it.info.id == productId }) { "Unavailable product" }
        require(selectedIds.distinct().size == selectedIds.size) { "Duplicate option" }
        val groups = catalog.groupsFor(productId)
        val options = selectedIds.map { id ->
            catalog.options.find { it.info.id == id && it.info.active && groups.any { g -> g.info.id == it.groupId } }
                ?: throw IllegalArgumentException("Unavailable option")
        }
        groups.forEach { group ->
            val count = options.count { it.groupId == group.info.id }
            require(count in group.minSelect..group.maxSelect) { "Invalid modifier count" }
        }
    }
}

// Local demonstration scope only; never a provisioned server brand or outlet.
const val DEMO_BRAND_ID = "9f03cb6e-091b-4c0f-9e54-18e8b87b39cf"
