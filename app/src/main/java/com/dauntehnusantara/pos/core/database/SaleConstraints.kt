package com.dauntehnusantara.pos.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

object SaleConstraints {
    fun install(db: SupportSQLiteDatabase) {
        val checks = mapOf(
            "sales" to "NEW.subtotalIdr < 0 OR NEW.discountIdr < 0 OR NEW.discountIdr > NEW.subtotalIdr OR NEW.totalIdr != NEW.subtotalIdr - NEW.discountIdr OR NEW.totalIdr < 0 OR NEW.createdAtUtcMs < 0 OR NEW.status != 'COMPLETED'",
            "sale_items" to "NEW.quantity < 1 OR NEW.quantity > 999 OR NEW.unitBasePriceIdr < 0 OR NEW.lineTotalIdr < 0",
            "sale_item_modifiers" to "NEW.priceDeltaIdr < 0",
            "payments" to "NEW.amountIdr != (SELECT totalIdr FROM sales WHERE id = NEW.saleId) OR NEW.amountIdr < 0 OR NEW.method NOT IN ('CASH','QRIS_MANUAL') OR (NEW.method = 'CASH' AND (NEW.tenderedIdr IS NULL OR NEW.changeIdr IS NULL OR NEW.tenderedIdr < NEW.amountIdr OR NEW.changeIdr != NEW.tenderedIdr - NEW.amountIdr OR NEW.verificationStatus != 'CASH_RECEIVED')) OR (NEW.method = 'QRIS_MANUAL' AND (NEW.tenderedIdr IS NOT NULL OR NEW.changeIdr IS NOT NULL OR NEW.verificationStatus != 'CASHIER_CONFIRMED_MANUALLY'))",
            "sync_outbox" to "NEW.id != (SELECT operationId FROM sales WHERE id = NEW.aggregateId) OR NOT ((NEW.operationType = 'DEMO_SALE' AND NEW.state = 'PENDING_DEMO') OR (NEW.operationType = 'SUBMIT_SALE' AND NEW.state = 'PENDING')) OR NEW.attempts != 0",
            "checkout_session" to "NEW.revision < 0",
        )
        checks.forEach { (table, condition) ->
            db.execSQL("CREATE TRIGGER IF NOT EXISTS validate_${table}_insert BEFORE INSERT ON $table WHEN $condition BEGIN SELECT RAISE(ABORT, 'Invalid checkout record'); END")
        }
        listOf("sales", "sale_items", "sale_item_modifiers", "payments").forEach { table ->
            listOf("UPDATE", "DELETE").forEach { event ->
                db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_${table}_${event.lowercase()} BEFORE $event ON $table BEGIN SELECT RAISE(ABORT, 'Completed sales are immutable'); END")
            }
        }
        db.execSQL("CREATE TRIGGER IF NOT EXISTS validate_outbox_update BEFORE UPDATE ON sync_outbox WHEN NEW.attempts < OLD.attempts OR NEW.nextAttemptAtMs < 0 OR (OLD.state IN ('SYNCED','REVIEW') AND NEW.state != OLD.state) OR (NEW.operationType = 'DEMO_SALE' AND NEW.state != 'PENDING_DEMO') OR (NEW.operationType = 'SUBMIT_SALE' AND NEW.state NOT IN ('PENDING','RETRY','AUTH_REQUIRED','REVIEW','SYNCED')) OR (NEW.state = 'SYNCED' AND NEW.acknowledgedAtMs IS NULL) BEGIN SELECT RAISE(ABORT, 'Invalid sync transition'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_outbox_delete BEFORE DELETE ON sync_outbox BEGIN SELECT RAISE(ABORT, 'Outbox deletion requires an explicit retention policy'); END")
        db.execSQL("CREATE TRIGGER IF NOT EXISTS immutable_outbox_identity BEFORE UPDATE OF id, aggregateId, operationType, payloadJson, createdAtUtcMs ON sync_outbox BEGIN SELECT RAISE(ABORT, 'Outbox identity is immutable'); END")
    }
}
