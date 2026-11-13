package com.dauntehnusantara.pos.feature.catalog.data

import com.dauntehnusantara.pos.feature.catalog.domain.*
import java.util.UUID

// Illustrative menu/prices only, explicitly loaded by a developer. Stable IDs make retries safe.
object DemoCatalog {
    private fun info(key: String, name: String) = RecordInfo(
        UUID.nameUUIDFromBytes("daunteh.demo.$key".toByteArray(Charsets.UTF_8)).toString(), DEMO_BRAND_ID, name,
    )
    val category = Category(info("tea", "Teh Nusantara"))
    val groups = listOf(
        ModifierGroup(info("size", "Ukuran"), 1, 1),
        ModifierGroup(info("sugar", "Gula"), 1, 1),
        ModifierGroup(info("ice", "Es"), 1, 1),
        ModifierGroup(info("topping", "Topping"), 0, 2),
    )
    val options = listOf(
        ModifierOption(info("regular", "Reguler"), groups[0].info.id, 0),
        ModifierOption(info("large", "Besar"), groups[0].info.id, 3000),
        ModifierOption(info("normal-sugar", "Gula normal"), groups[1].info.id, 0),
        ModifierOption(info("no-sugar", "Tanpa gula"), groups[1].info.id, 0),
        ModifierOption(info("normal-ice", "Es normal"), groups[2].info.id, 0),
        ModifierOption(info("no-ice", "Tanpa es"), groups[2].info.id, 0),
        ModifierOption(info("jelly", "Jeli"), groups[3].info.id, 2000),
        ModifierOption(info("boba", "Boba"), groups[3].info.id, 3000),
    )
    val products = listOf(
        Product(info("original", "Teh Original"), category.info.id, 6000),
        Product(info("milk", "Teh Susu"), category.info.id, 10000),
        Product(info("lemon", "Teh Lemon"), category.info.id, 9000),
    )
}
