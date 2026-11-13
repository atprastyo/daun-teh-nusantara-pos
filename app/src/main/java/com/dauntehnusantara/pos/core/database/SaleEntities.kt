package com.dauntehnusantara.pos.core.database

import androidx.room.*

@Entity(tableName = "checkout_session")
data class CheckoutSessionEntity(@PrimaryKey val scope: String, val deviceId: String,
    val draftId: String, val operationId: String, val revision: Long, val cartJson: String, val lastSaleId: String?)

@Entity(tableName = "sales", indices = [Index(value = ["operationId"], unique = true), Index(value = ["outletId", "createdAtUtcMs"])])
data class SaleEntity(@PrimaryKey val id: String, val operationId: String, val requestHash: String,
    val brandId: String, val outletId: String, val deviceId: String, val cashierId: String,
    val createdAtUtcMs: Long, val totalIdr: Long, val status: String = "COMPLETED",
    val subtotalIdr: Long = totalIdr, val discountIdr: Long = 0)

@Entity(tableName = "sale_items", indices = [Index("saleId")], foreignKeys = [ForeignKey(
    entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.RESTRICT)])
data class SaleItemEntity(@PrimaryKey val id: String, val saleId: String, val productId: String,
    val productNameSnapshot: String, val unitBasePriceIdr: Long, val quantity: Int,
    val lineTotalIdr: Long, val snapshotJson: String)

@Entity(tableName = "sale_item_modifiers", indices = [Index("lineId")], foreignKeys = [ForeignKey(
    entity = SaleItemEntity::class, parentColumns = ["id"], childColumns = ["lineId"], onDelete = ForeignKey.RESTRICT)])
data class SaleModifierEntity(@PrimaryKey val id: String, val lineId: String, val optionId: String,
    val groupNameSnapshot: String, val optionNameSnapshot: String, val priceDeltaIdr: Long)

@Entity(tableName = "payments", indices = [Index(value = ["saleId"], unique = true)], foreignKeys = [ForeignKey(
    entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.RESTRICT)])
data class PaymentEntity(@PrimaryKey val id: String, val saleId: String, val method: String,
    val amountIdr: Long, val tenderedIdr: Long?, val changeIdr: Long?, val verificationStatus: String)

@Entity(tableName = "sync_outbox", indices = [Index(value = ["aggregateId"], unique = true)], foreignKeys = [ForeignKey(
    entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["aggregateId"], onDelete = ForeignKey.RESTRICT)])
data class OutboxEntity(@PrimaryKey val id: String, val aggregateId: String,
    val operationType: String, val payloadJson: String, val state: String,
    val attempts: Int, val createdAtUtcMs: Long)
