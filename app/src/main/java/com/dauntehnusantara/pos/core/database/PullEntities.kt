package com.dauntehnusantara.pos.core.database

import androidx.room.*

@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(@PrimaryKey val scope: String, val lastSeq: Long = 0, val initialized: Boolean = false,
    val lastSuccessMs: Long? = null, val errorCode: String? = null, val serverEpoch: String? = null)
@Entity(tableName = "remote_sales", primaryKeys = ["scope", "saleId"])
data class RemoteSaleEntity(val scope: String, val saleId: String, val operationId: String, val seq: Long, val payload: String)
@Dao
interface PullDao {
    @Query("SELECT * FROM sync_cursor WHERE scope=:scope") suspend fun cursor(scope: String): SyncCursorEntity?
    @Upsert suspend fun put(cursor: SyncCursorEntity)
    @Query("SELECT * FROM remote_sales WHERE scope=:scope ORDER BY seq DESC LIMIT 100") suspend fun sales(scope: String): List<RemoteSaleEntity>
    @Query("SELECT * FROM remote_sales WHERE scope=:scope AND saleId=:sale") suspend fun sale(scope: String, sale: String): RemoteSaleEntity?
    @Insert suspend fun insert(sale: RemoteSaleEntity)
    @Query("SELECT value FROM app_metadata WHERE `key`=:key") suspend fun metadata(key: String): String?
}
