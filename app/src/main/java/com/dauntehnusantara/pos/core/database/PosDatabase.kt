package com.dauntehnusantara.pos.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// Initial development schema; no upgrade paths are maintained before release.
@Entity(tableName = "app_metadata")
data class AppMetadata(@PrimaryKey val key: String, val value: String)

@Dao
interface MetadataDao {
    @Query("SELECT COUNT(*) FROM app_metadata")
    fun observeEntryCount(): Flow<Int>

    @Query("SELECT * FROM app_metadata WHERE `key` LIKE :prefix || '%'")
    suspend fun entries(prefix:String):List<AppMetadata>
    @Query("DELETE FROM app_metadata WHERE `key`=:key") suspend fun delete(key:String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(metadata: AppMetadata)
}

@Database(
    entities = [SyncCursorEntity::class, RemoteSaleEntity::class, AppMetadata::class, CategoryEntity::class, ProductEntity::class, GroupEntity::class, OptionEntity::class, ProductGroupEntity::class, CheckoutSessionEntity::class, SaleEntity::class, SaleItemEntity::class, SaleModifierEntity::class, PaymentEntity::class, OutboxEntity::class],
    version = 1, exportSchema = true,
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun pullDao(): PullDao
    abstract fun checkoutDao(): CheckoutDao
    abstract fun catalogDao(): CatalogDao
    abstract fun metadataDao(): MetadataDao
}
