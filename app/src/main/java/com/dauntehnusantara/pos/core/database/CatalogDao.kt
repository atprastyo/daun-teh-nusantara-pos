package com.dauntehnusantara.pos.core.database

import androidx.room.*

@Dao
interface CatalogDao {
    @Query("SELECT * FROM categories WHERE brand_id = :brand ORDER BY sort_order, name, id")
    suspend fun categories(brand: String): List<CategoryEntity>
    @Query("SELECT * FROM products WHERE brand_id = :brand ORDER BY name, id")
    suspend fun products(brand: String): List<ProductEntity>
    @Query("SELECT * FROM modifier_groups WHERE brand_id = :brand ORDER BY name, id")
    suspend fun groups(brand: String): List<GroupEntity>
    @Query("SELECT * FROM modifier_options WHERE brand_id = :brand ORDER BY name, id")
    suspend fun options(brand: String): List<OptionEntity>
    @Query("SELECT * FROM product_modifier_groups WHERE brand_id = :brand ORDER BY sort_order, group_id")
    suspend fun assignments(brand: String): List<ProductGroupEntity>
    @Query("SELECT * FROM categories WHERE id = :id") suspend fun category(id: String): CategoryEntity?
    @Query("SELECT * FROM products WHERE id = :id") suspend fun product(id: String): ProductEntity?
    @Query("SELECT * FROM modifier_groups WHERE id = :id") suspend fun group(id: String): GroupEntity?
    @Query("SELECT * FROM modifier_options WHERE id = :id") suspend fun option(id: String): OptionEntity?
    @Upsert suspend fun put(value: CategoryEntity)
    @Upsert suspend fun put(value: ProductEntity)
    @Upsert suspend fun put(value: GroupEntity)
    @Upsert suspend fun put(value: OptionEntity)
    @Insert suspend fun insertAssignments(values: List<ProductGroupEntity>)
    @Query("DELETE FROM product_modifier_groups WHERE product_id = :id") suspend fun clearAssignments(id: String)
    @Query("SELECT value FROM app_metadata WHERE `key` = :key") suspend fun metadata(key: String): String?
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertMetadata(value: AppMetadata)

    @Transaction
    suspend fun snapshot(brand: String) = CatalogRows(categories(brand), products(brand), groups(brand), options(brand), assignments(brand))
}
