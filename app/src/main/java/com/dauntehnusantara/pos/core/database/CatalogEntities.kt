package com.dauntehnusantara.pos.core.database

import androidx.room.*

data class CatalogRecordColumns(
    val id: String, @ColumnInfo(name = "brand_id") val brandId: String,
    val name: String, val active: Boolean, val version: Long,
    @ColumnInfo(name = "created_at_utc_ms") val createdAtUtcMs: Long,
    @ColumnInfo(name = "updated_at_utc_ms") val updatedAtUtcMs: Long,
)

@Entity(tableName = "categories", primaryKeys = ["id"], indices = [Index(value = ["id", "brand_id"], unique = true), Index("brand_id")])
data class CategoryEntity(@Embedded val record: CatalogRecordColumns, @ColumnInfo(name = "sort_order") val sortOrder: Int)

@Entity(tableName = "products", primaryKeys = ["id"],
    indices = [Index(value = ["id", "brand_id"], unique = true), Index(value = ["category_id", "brand_id"]), Index("brand_id")],
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id", "brand_id"], childColumns = ["category_id", "brand_id"], onDelete = ForeignKey.RESTRICT)])
data class ProductEntity(@Embedded val record: CatalogRecordColumns,
    @ColumnInfo(name = "category_id") val categoryId: String, @ColumnInfo(name = "base_price_idr") val basePriceIdr: Long)

@Entity(tableName = "modifier_groups", primaryKeys = ["id"], indices = [Index(value = ["id", "brand_id"], unique = true), Index("brand_id")])
data class GroupEntity(@Embedded val record: CatalogRecordColumns,
    @ColumnInfo(name = "min_select") val minSelect: Int, @ColumnInfo(name = "max_select") val maxSelect: Int)

@Entity(tableName = "modifier_options", primaryKeys = ["id"],
    indices = [Index(value = ["group_id", "brand_id"]), Index("brand_id")],
    foreignKeys = [ForeignKey(entity = GroupEntity::class, parentColumns = ["id", "brand_id"], childColumns = ["group_id", "brand_id"], onDelete = ForeignKey.RESTRICT)])
data class OptionEntity(@Embedded val record: CatalogRecordColumns,
    @ColumnInfo(name = "group_id") val groupId: String, @ColumnInfo(name = "price_delta_idr") val priceDeltaIdr: Long)

@Entity(tableName = "product_modifier_groups", primaryKeys = ["product_id", "group_id"],
    indices = [Index(value = ["product_id", "brand_id"]), Index(value = ["group_id", "brand_id"]), Index("brand_id")],
    foreignKeys = [
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id", "brand_id"], childColumns = ["product_id", "brand_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = GroupEntity::class, parentColumns = ["id", "brand_id"], childColumns = ["group_id", "brand_id"], onDelete = ForeignKey.RESTRICT),
    ])
data class ProductGroupEntity(@ColumnInfo(name = "product_id") val productId: String,
    @ColumnInfo(name = "group_id") val groupId: String, @ColumnInfo(name = "brand_id") val brandId: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int)

data class CatalogRows(val categories: List<CategoryEntity>, val products: List<ProductEntity>,
    val groups: List<GroupEntity>, val options: List<OptionEntity>, val assignments: List<ProductGroupEntity>)
