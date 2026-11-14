package com.dauntehnusantara.pos.feature.catalog.data

import androidx.room.withTransaction
import com.dauntehnusantara.pos.BuildConfig
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.map

class RoomCatalogRepository @Inject constructor(
    private val db: PosDatabase, private val dao: CatalogDao, private val clock: Clock,
) : CatalogRepository {
    override fun observe(brandId: String) = db.invalidationTracker.createFlow(
        "app_metadata", "categories", "products", "modifier_groups", "modifier_options", "product_modifier_groups",
    ).map { db.withTransaction { dao.snapshot(brandId).toDomain().let { menu -> menu.copy(products=menu.products.map { product ->
            val raw=dao.metadata("sync.catalog.product_photos.${product.info.id}")
            if(raw==null)product else runCatching {
                val saved=org.json.JSONObject(raw);val photo=org.json.JSONObject(saved.getString("payload"))
                product.copy(imageVersion=saved.getLong("version"),imagePath=if(photo.getBoolean("active"))photo.getString("image_path")else null)
            }.getOrDefault(product)
        }) } } }

    private fun next(info: RecordInfo, previous: CatalogRecordColumns?): CatalogRecordColumns {
        CatalogRules.validate(info)
        if (previous == null) {
            if (info.version != 0L) throw CatalogConflict()
        } else if (previous.brandId != info.brandId || previous.version != info.version) throw CatalogConflict()
        val now = maxOf(clock.millis(), previous?.updatedAtUtcMs ?: 0L)
        return CatalogRecordColumns(info.id, info.brandId, info.name.trim(), info.active,
            info.version + 1, previous?.createdAtUtcMs ?: now, now)
    }
    override suspend fun save(category: Category): Category = db.withTransaction {
        CatalogRules.validate(category)
        val row = CategoryEntity(next(category.info, dao.category(category.info.id)?.record), category.sortOrder)
        dao.put(row); row.toDomain()
    }
    override suspend fun save(product: Product): Product = db.withTransaction {
        CatalogRules.validate(product)
        val category = requireNotNull(dao.category(product.categoryId)) { "Unknown category" }
        require(category.record.brandId == product.info.brandId && (!product.info.active || category.record.active)) { "Unavailable category" }
        val row = ProductEntity(next(product.info, dao.product(product.info.id)?.record), product.categoryId, product.basePriceIdr)
        dao.put(row); row.toDomain()
    }
    override suspend fun save(group: ModifierGroup): ModifierGroup = db.withTransaction {
        CatalogRules.validate(group)
        val row = GroupEntity(next(group.info, dao.group(group.info.id)?.record), group.minSelect, group.maxSelect)
        dao.put(row); row.toDomain()
    }
    override suspend fun save(option: ModifierOption): ModifierOption = db.withTransaction {
        CatalogRules.validate(option)
        val previous = dao.option(option.info.id)
        require(previous == null || previous.groupId == option.groupId) { "Options cannot change groups" }
        val group = requireNotNull(dao.group(option.groupId)) { "Unknown modifier group" }
        require(group.record.brandId == option.info.brandId && (!option.info.active || group.record.active)) { "Unavailable group" }
        val row = OptionEntity(next(option.info, previous?.record), option.groupId, option.priceDeltaIdr)
        dao.put(row); row.toDomain()
    }
    override suspend fun assignGroups(brandId: String, productId: String, groupIds: List<String>, expectedVersion: Long) = db.withTransaction {
        require(groupIds.distinct().size == groupIds.size) { "Duplicate group" }
        val product = requireNotNull(dao.product(productId)) { "Unknown product" }
        val record = next(product.record.toDomain().copy(brandId = brandId, version = expectedVersion), product.record)
        require(product.record.active) { "Unavailable product" }
        groupIds.forEach { id ->
            val group = requireNotNull(dao.group(id)) { "Unknown group" }
            require(group.record.brandId == brandId && group.record.active) { "Unavailable group" }
            require(dao.options(brandId).count { it.groupId == id && it.record.active } >= group.minSelect) { "Group lacks required options" }
        }
        dao.clearAssignments(productId)
        dao.insertAssignments(groupIds.mapIndexed { index, id -> ProductGroupEntity(productId, id, brandId, index) })
        dao.put(product.copy(record = record))
    }
    override suspend fun archive(brandId: String, kind: CatalogRecord, id: String, expectedVersion: Long): Unit = db.withTransaction {
        // Tombstones keep identities and references intact for future sync.
        fun archived(previous: CatalogRecordColumns): CatalogRecordColumns = next(
            previous.toDomain().copy(brandId = brandId, active = false, version = expectedVersion), previous,
        )
        when (kind) {
            CatalogRecord.Category -> dao.put(requireNotNull(dao.category(id)).let { it.copy(record = archived(it.record)) })
            CatalogRecord.Product -> dao.put(requireNotNull(dao.product(id)).let { it.copy(record = archived(it.record)) })
            CatalogRecord.Group -> dao.put(requireNotNull(dao.group(id)).let { it.copy(record = archived(it.record)) })
            CatalogRecord.Option -> dao.put(requireNotNull(dao.option(id)).let { it.copy(record = archived(it.record)) })
        }
    }
    override suspend fun seedDemo(): Unit = db.withTransaction {
        check(BuildConfig.DEBUG) { "Demo seeding is debug-only" }
        if (dao.metadata("demo_catalog_v1") != null) return@withTransaction
        val current = dao.snapshot(DEMO_BRAND_ID)
        check(current.categories.isEmpty() && current.products.isEmpty() && current.groups.isEmpty() && current.options.isEmpty()) { "Demo scope is not empty" }
        val category = save(DemoCatalog.category)
        DemoCatalog.groups.forEach { save(it) }
        DemoCatalog.options.forEach { save(it) }
        DemoCatalog.products.forEach { product ->
            val stored = save(product.copy(categoryId = category.info.id))
            assignGroups(DEMO_BRAND_ID, stored.info.id, DemoCatalog.groups.map { it.info.id }, stored.info.version)
        }
        dao.insertMetadata(AppMetadata("demo_catalog_v1", "complete"))
    }
}

private fun CatalogRecordColumns.toDomain() = RecordInfo(id, brandId, name, active, version, createdAtUtcMs, updatedAtUtcMs)
private fun CategoryEntity.toDomain() = Category(record.toDomain(), sortOrder)
private fun ProductEntity.toDomain() = Product(record.toDomain(), categoryId, basePriceIdr)
private fun GroupEntity.toDomain() = ModifierGroup(record.toDomain(), minSelect, maxSelect)
private fun OptionEntity.toDomain() = ModifierOption(record.toDomain(), groupId, priceDeltaIdr)
internal fun CatalogRows.toDomain() = Catalog(categories.map { it.toDomain() }, products.map { it.toDomain() },
    groups.map { it.toDomain() }, options.map { it.toDomain() }, assignments.map { ProductGroup(it.productId, it.groupId, it.sortOrder) })
