package com.dauntehnusantara.pos.core.database

import androidx.room.*

@Dao
interface CheckoutDao {
    @Query("SELECT s.deviceId FROM sales s JOIN sync_outbox o ON o.aggregateId=s.id WHERE o.operationType='SUBMIT_SALE' AND s.cashierId=:user AND s.outletId=:outlet ORDER BY s.createdAtUtcMs,s.id LIMIT 1")
    suspend fun retainedDevice(user: String, outlet: String): String?

    @Query("SELECT * FROM sync_outbox WHERE id = :id") suspend fun outbox(id: String): OutboxEntity?
    @Query("SELECT o.* FROM sync_outbox o JOIN sales s ON s.id=o.aggregateId WHERE o.operationType='SUBMIT_SALE' AND o.state IN ('PENDING','RETRY','AUTH_REQUIRED') AND o.nextAttemptAtMs<=:now AND s.brandId=:brand AND s.outletId=:outlet AND s.deviceId=:device AND s.cashierId=:user ORDER BY o.createdAtUtcMs,o.id LIMIT 20")
    suspend fun due(brand: String, outlet: String, device: String, user: String, now: Long): List<OutboxEntity>
    @Query("SELECT o.* FROM sync_outbox o JOIN sales s ON s.id=o.aggregateId WHERE o.operationType='SUBMIT_SALE' AND s.brandId=:brand AND s.outletId=:outlet AND s.deviceId=:device AND s.cashierId=:user")
    suspend fun syncRows(brand: String, outlet: String, device: String, user: String): List<OutboxEntity>
    @Query("UPDATE sync_outbox SET state=:state,attempts=:attempts,nextAttemptAtMs=:next,lastErrorCode=:error,acknowledgedAtMs=:ack WHERE id=:id AND state!='SYNCED' AND state!='REVIEW'")
    suspend fun syncResult(id: String, state: String, attempts: Int, next: Long, error: String?, ack: Long?)
    @Query("UPDATE sync_outbox SET nextAttemptAtMs=0 WHERE id=:id AND state IN ('PENDING','RETRY','AUTH_REQUIRED')")
    suspend fun retry(id: String)

    @Query("SELECT * FROM checkout_session WHERE scope = :scope") suspend fun session(scope: String): CheckoutSessionEntity?
    @Insert suspend fun insert(session: CheckoutSessionEntity)
    @Update suspend fun update(session: CheckoutSessionEntity)
    @Insert suspend fun insert(sale: SaleEntity)
    @Insert suspend fun insert(items: List<SaleItemEntity>)
    @Insert suspend fun insertModifiers(items: List<SaleModifierEntity>)
    @Insert suspend fun insert(payment: PaymentEntity)
    @Insert suspend fun insert(outbox: OutboxEntity)
    @Query("SELECT * FROM sales WHERE operationId = :id") suspend fun byOperation(id: String): SaleEntity?
    @Query("SELECT * FROM sales WHERE id = :id AND brandId = :brand AND outletId = :outlet")
    suspend fun sale(id: String, brand: String, outlet: String): SaleEntity?
    @Query("SELECT * FROM sales WHERE brandId=:brand AND outletId=:outlet AND (:allCashiers OR cashierId=:user) ORDER BY createdAtUtcMs DESC,id DESC LIMIT 100")
    suspend fun authorizedSales(brand: String, outlet: String, user: String, allCashiers: Boolean): List<SaleEntity>
    @Query("SELECT * FROM sales WHERE brandId = :brand AND outletId = :outlet ORDER BY createdAtUtcMs DESC, id DESC LIMIT 100")
    suspend fun sales(brand: String, outlet: String): List<SaleEntity>
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY rowid") suspend fun items(saleId: String): List<SaleItemEntity>
    @Query("SELECT * FROM payments WHERE saleId = :saleId") suspend fun payment(saleId: String): PaymentEntity
    @Query("SELECT COUNT(*) FROM sync_outbox o JOIN sales s ON s.id = o.aggregateId WHERE s.brandId = :brand AND s.outletId = :outlet AND o.state = 'PENDING_DEMO'")
    suspend fun pending(brand: String, outlet: String): Int
}
