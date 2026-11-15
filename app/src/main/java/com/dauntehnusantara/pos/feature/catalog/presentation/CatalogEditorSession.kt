package com.dauntehnusantara.pos.feature.catalog.presentation

import com.dauntehnusantara.pos.R
import com.dauntehnusantara.pos.feature.catalog.domain.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class CatalogFilter { ALL, ACTIVE, ARCHIVED }

/** Raw form text stays in memory, never SavedState/Room or a photo/sale outbox. */
data class CatalogDraft(val original: CatalogMutation, val name: String = original.name,
    val active: Boolean = original.active, val price: String = (original.priceIdr ?: 0).toString(),
    val order: String = original.sortOrder.toString(), val minimum: String = (original.minSelect ?: 0).toString(),
    val maximum: String = (original.maxSelect ?: 1).toString(), val parent: String? = original.parentId,
    val groups: List<String> = original.groupIds.toList()) {
    private fun whole(value: String) = value.matches(Regex("[0-9]+"))
    fun errors(catalog: Catalog): Map<String, Int> = buildMap {
        if (name.trim().length !in 1..120) put("name", R.string.catalog_ui_name_error)
        when (original.kind) {
            CatalogRecord.Category -> if (!whole(order) || order.toIntOrNull() == null) put("order", R.string.catalog_ui_order_error)
            CatalogRecord.Group -> {
                val min = minimum.takeIf(::whole)?.toIntOrNull()
                val max = maximum.takeIf(::whole)?.toIntOrNull()
                if (min == null) put("minimum", R.string.catalog_ui_min_error)
                if (max == null || max < 1 || min != null && max < min) put("maximum", R.string.catalog_ui_max_error)
                if (active && min != null && catalog.assignedToActiveProduct(original.id) &&
                    catalog.options.count { it.groupId == original.id && it.info.active } < min) put("minimum", R.string.catalog_ui_required_options)
            }
            CatalogRecord.Product, CatalogRecord.Option -> {
                if (!whole(price) || price.toLongOrNull() == null) put("price", R.string.catalog_ui_price_error)
                if (original.kind == CatalogRecord.Product) {
                    val category = catalog.categories.find { it.info.id == parent }
                    if (category == null || active && !category.info.active) put("parent", R.string.catalog_ui_category_error)
                    if (groups.size > 100 || groups.distinct().size != groups.size || groups.any { id ->
                        val g = catalog.groups.find { it.info.id == id }
                        g == null || !g.info.active && id !in original.groupIds || active && g.info.active &&
                            catalog.options.count { it.groupId == id && it.info.active } < g.minSelect
                    }) put("groups", R.string.catalog_ui_group_error)
                } else {
                    val group = catalog.groups.find { it.info.id == parent }
                    if (group == null || active && !group.info.active || original.expectedVersion > 0 && parent != original.parentId)
                        put("parent", R.string.catalog_ui_parent_error)
                    if (!active && group != null && group.info.active && catalog.assignedToActiveProduct(group.info.id) &&
                        catalog.options.count { it.groupId == group.info.id && it.info.active && it.info.id != original.id } < group.minSelect)
                        put("active", R.string.catalog_ui_required_options)
                }
            }
        }
    }
    fun command(catalog: Catalog): CatalogMutation? {
        if (errors(catalog).isNotEmpty()) return null
        return original.copy(operationId = UUID.randomUUID().toString(), name = name,
            active = active, priceIdr = price.toLongOrNull(), sortOrder = order.toIntOrNull() ?: 0,
            minSelect = minimum.toIntOrNull(), maxSelect = maximum.toIntOrNull(), parentId = parent, groupIds = groups.toList())
            .takeIf { runCatching { it.validate() }.isSuccess }
    }
}

private fun Catalog.assignedToActiveProduct(groupId: String) = assignments.any { a ->
    a.groupId == groupId && products.any { it.info.id == a.productId && it.info.active }
}

data class CatalogEditorUi(val kind: CatalogRecord = CatalogRecord.Product, val query: String = "",
    val filter: CatalogFilter = CatalogFilter.ALL, val draft: CatalogDraft? = null,
    val submittedOperation: String? = null, val needsReview: Boolean = false)

/** Activity ViewModel owns the production instance, so resizing/rotation doesn't drop input. */
class CatalogEditorSession {
    private val mutable = MutableStateFlow(CatalogEditorUi())
    val state = mutable.asStateFlow()
    private var scopeKey: String? = null
    var formScroll = androidx.compose.foundation.ScrollState(0); private set
    fun bindScope(key: String) {
        if (scopeKey != null && scopeKey != key) clear()
        scopeKey = key
    }
    fun clear() { mutable.value = CatalogEditorUi(); scopeKey = null }
    fun tab(kind: CatalogRecord) { if (state.value.draft == null) mutable.update { it.copy(kind = kind, query = "", filter = CatalogFilter.ALL) } }
    fun search(query: String) { mutable.update { it.copy(query = query) } }
    fun filter(filter: CatalogFilter) { mutable.update { it.copy(filter = filter) } }
    fun clearFilters() { mutable.update { it.copy(query = "", filter = CatalogFilter.ALL) } }
    fun open(row: CatalogMutation) { formScroll = androidx.compose.foundation.ScrollState(0); mutable.update { it.copy(kind = row.kind, draft = CatalogDraft(row), submittedOperation = null, needsReview = false) } }
    fun update(change: (CatalogDraft) -> CatalogDraft) { mutable.update { it.copy(draft = it.draft?.let(change)) } }
    fun close() { mutable.update { it.copy(draft = null, submittedOperation = null, needsReview = false) } }
    fun submitted(operation: String) { mutable.update { it.copy(submittedOperation = operation) } }
    fun conflict() { mutable.update { it.copy(needsReview = it.draft != null) } }
    fun applied(operation: String) { if (state.value.submittedOperation == operation) close() }
}

internal fun Catalog.editorRows(kind: CatalogRecord): List<CatalogMutation> = when (kind) {
    CatalogRecord.Category -> categories.map { CatalogMutation(kind = kind, id = it.info.id, expectedVersion = it.info.version, name = it.info.name, active = it.info.active, sortOrder = it.sortOrder) }
    CatalogRecord.Group -> groups.map { CatalogMutation(kind = kind, id = it.info.id, expectedVersion = it.info.version, name = it.info.name, active = it.info.active, minSelect = it.minSelect, maxSelect = it.maxSelect) }
    CatalogRecord.Option -> options.map { CatalogMutation(kind = kind, id = it.info.id, expectedVersion = it.info.version, name = it.info.name, active = it.info.active, priceIdr = it.priceDeltaIdr, parentId = it.groupId) }
    CatalogRecord.Product -> products.map { p -> CatalogMutation(kind = kind, id = p.info.id, expectedVersion = p.info.version, name = p.info.name, active = p.info.active, priceIdr = p.basePriceIdr, parentId = p.categoryId,
        groupIds = assignments.filter { it.productId == p.info.id }.sortedBy { it.sortOrder }.map { it.groupId }) }
}
