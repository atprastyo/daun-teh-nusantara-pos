package com.dauntehnusantara.pos.core.database

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object CatalogConstraints : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) { install(db); SaleConstraints.install(db) }
    fun install(db: SupportSQLiteDatabase) {
        val common = "NEW.name = '' OR length(trim(NEW.name)) = 0 OR length(NEW.name) > 120 OR NEW.version < 1 OR NEW.created_at_utc_ms < 0 OR NEW.updated_at_utc_ms < NEW.created_at_utc_ms OR NEW.active NOT IN (0, 1)"
        val checks = mapOf(
            "categories" to "$common OR NEW.sort_order < 0",
            "products" to "$common OR NEW.base_price_idr < 0",
            "modifier_groups" to "$common OR NEW.min_select < 0 OR NEW.max_select < NEW.min_select OR NEW.max_select < 1",
            "modifier_options" to "$common OR NEW.price_delta_idr < 0",
            "product_modifier_groups" to "NEW.sort_order < 0",
        )
        checks.forEach { (table, condition) ->
            listOf("INSERT", "UPDATE").forEach { event ->
                db.execSQL("CREATE TRIGGER IF NOT EXISTS validate_${table}_${event.lowercase()} BEFORE $event ON $table WHEN $condition BEGIN SELECT RAISE(ABORT, 'Invalid catalog record'); END")
            }
        }
    }
}
