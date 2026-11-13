package com.dauntehnusantara.pos.core.database

import androidx.room.*

@Dao
interface CheckoutDao {
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
    @Query("SELECT * FROM sales WHERE brandId = :brand AND outletId = :outlet ORDER BY createdAtUtcMs DESC, id DESC LIMIT 100")
    suspend fun sales(brand: String, outlet: String): List<SaleEntity>
    @Query("SELECT * FROM sale_items WHERE saleId = :saleId ORDER BY rowid") suspend fun items(saleId: String): List<SaleItemEntity>
    @Query("SELECT * FROM payments WHERE saleId = :saleId") suspend fun payment(saleId: String): PaymentEntity
    @Query("SELECT COUNT(*) FROM sync_outbox o JOIN sales s ON s.id = o.aggregateId WHERE s.brandId = :brand AND s.outletId = :outlet AND o.state = 'PENDING_DEMO'")
    suspend fun pending(brand: String, outlet: String): Int
}
