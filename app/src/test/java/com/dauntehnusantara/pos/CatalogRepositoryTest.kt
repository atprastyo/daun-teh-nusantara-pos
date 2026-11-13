package com.dauntehnusantara.pos

import android.app.Application
import androidx.room.Room
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.catalog.data.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class CatalogRepositoryTest {
    private lateinit var db: PosDatabase
    private lateinit var repository: RoomCatalogRepository
    private val clock = Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC)
    private val context get() = RuntimeEnvironment.getApplication()
    private val dbName = "catalog-test.db"
    @Before fun open() {
        context.deleteDatabase(dbName)
        reopen()
    }
    private fun reopen() {
        db = Room.databaseBuilder(context, PosDatabase::class.java, dbName).addCallback(CatalogConstraints).build()
        repository = RoomCatalogRepository(db, db.catalogDao(), clock)
    }
    @After fun close() { db.close(); context.deleteDatabase(dbName) }
    private suspend fun fixtures() {
        repository.save(DemoCatalog.category)
        DemoCatalog.groups.forEach { repository.save(it) }
        DemoCatalog.options.forEach { repository.save(it) }
        DemoCatalog.products.forEach { p ->
            val stored = repository.save(p)
            repository.assignGroups(DEMO_BRAND_ID, p.info.id, DemoCatalog.groups.map { it.info.id }, stored.info.version)
        }
    }
    @Test fun offlineCrudPersistsThroughReopenAndKeepsTombstones(): Unit = runBlocking {
        fixtures()
        val before = repository.observe(DEMO_BRAND_ID).first()
        val product = before.products.first()
        val saved = repository.save(product.copy(info = product.info.copy(name = "Nama baru"), basePriceIdr = 12000))
        assertEquals(product.info.version + 1, saved.info.version)
        assertEquals(product.info.createdAtUtcMs, saved.info.createdAtUtcMs)
        assertEquals(clock.millis(), saved.info.updatedAtUtcMs)
        db.close(); reopen()
        val reopened = repository.observe(DEMO_BRAND_ID).first()
        assertEquals(12000L, reopened.products.first { it.info.id == saved.info.id }.basePriceIdr)
        repository.archive(DEMO_BRAND_ID, CatalogRecord.Product, saved.info.id, saved.info.version)
        val after = repository.observe(DEMO_BRAND_ID).first()
        assertFalse(after.products.first { it.info.id == saved.info.id }.info.active)
        assertEquals(2, after.visibleProducts().size)
        assertEquals(12, after.assignments.size)
    }
    @Test fun staleUpdateAndAssignmentFailWithoutOverwriting(): Unit = runBlocking {
        fixtures()
        val product = repository.observe(DEMO_BRAND_ID).first().products.first()
        val current = repository.save(product.copy(basePriceIdr = 17000))
        try { repository.save(product.copy(basePriceIdr = 1)); fail("Expected conflict") } catch (_: CatalogConflict) { }
        try { repository.assignGroups(DEMO_BRAND_ID, product.info.id, emptyList(), product.info.version); fail("Expected conflict") } catch (_: CatalogConflict) { }
        val after = repository.observe(DEMO_BRAND_ID).first()
        assertEquals(current, after.products.first { it.info.id == product.info.id })
        assertEquals(4, after.groupsFor(product.info.id).size)
    }
    @Test fun badAssignmentRollsBackExistingLinks(): Unit = runBlocking {
        fixtures()
        val before = repository.observe(DEMO_BRAND_ID).first()
        val product = before.products.first()
        try { repository.assignGroups(DEMO_BRAND_ID, product.info.id, listOf(UUID.randomUUID().toString()), product.info.version); fail("Expected invalid group") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, repository.observe(DEMO_BRAND_ID).first())
    }
    @Test fun crossBrandReferencesAndWritesAreRejected(): Unit = runBlocking {
        fixtures()
        val otherBrand = UUID.randomUUID().toString()
        val category = repository.save(Category(RecordInfo(brandId = otherBrand, name = "Other")))
        try { repository.save(Product(RecordInfo(brandId = DEMO_BRAND_ID, name = "Invalid"), category.info.id, 0)); fail("Expected brand rejection") }
        catch (_: IllegalArgumentException) { }
        val original = repository.observe(DEMO_BRAND_ID).first().categories.first()
        try { repository.save(original.copy(info = original.info.copy(brandId = otherBrand))); fail("Expected scoped conflict") }
        catch (_: CatalogConflict) { }
        assertEquals(listOf(category), repository.observe(otherBrand).first().categories)
        assertTrue(repository.observe(otherBrand).first().products.isEmpty())
    }
    @Test fun daoEnforcesPricesForeignKeysAndModifierBounds(): Unit = runBlocking {
        fixtures()
        val dao = db.catalogDao()
        val product = dao.products(DEMO_BRAND_ID).first()
        try { dao.put(product.copy(basePriceIdr = -1)); fail("Expected DB constraint") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        try { dao.put(product.copy(categoryId = UUID.randomUUID().toString())); fail("Expected FK constraint") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        val group = dao.groups(DEMO_BRAND_ID).first()
        try { dao.put(group.copy(minSelect = 2, maxSelect = 1)); fail("Expected DB constraint") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(product, dao.product(product.record.id))
    }
    @Test fun categoriesGroupsAndOptionsCanBeUpdatedAndArchived(): Unit = runBlocking {
        fixtures()
        val before = repository.observe(DEMO_BRAND_ID).first()
        val c = repository.save(before.categories.first().copy(sortOrder = 2))
        val g = repository.save(before.groups.first().copy(maxSelect = 3))
        val o = repository.save(before.options.first().copy(priceDeltaIdr = 4000))
        repository.archive(DEMO_BRAND_ID, CatalogRecord.Category, c.info.id, c.info.version)
        repository.archive(DEMO_BRAND_ID, CatalogRecord.Group, g.info.id, g.info.version)
        repository.archive(DEMO_BRAND_ID, CatalogRecord.Option, o.info.id, o.info.version)
        val after = repository.observe(DEMO_BRAND_ID).first()
        assertTrue(after.visibleProducts().isEmpty())
        assertFalse(after.groups.first { it.info.id == g.info.id }.info.active)
        assertFalse(after.options.first { it.info.id == o.info.id }.info.active)
    }
    @Test fun demoSeedIsIdempotentOrDisabledInRelease(): Unit = runBlocking {
        if (!BuildConfig.DEBUG) {
            try { repository.seedDemo(); fail("Release must reject demo seed") } catch (_: IllegalStateException) { }
            return@runBlocking
        }
        repository.seedDemo()
        val product = repository.observe(DEMO_BRAND_ID).first().products.first()
        repository.save(product.copy(basePriceIdr = 23000))
        repository.seedDemo()
        val after = repository.observe(DEMO_BRAND_ID).first()
        assertEquals(3, after.products.size)
        assertEquals(23000L, after.products.first { it.info.id == product.info.id }.basePriceIdr)
        assertEquals("complete", db.catalogDao().metadata("demo_catalog_v1"))
    }
    @Test fun failedSeedRollsBackAndCanRetry(): Unit = runBlocking {
        org.junit.Assume.assumeTrue(BuildConfig.DEBUG)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("CREATE TRIGGER fail_demo BEFORE INSERT ON products BEGIN SELECT RAISE(ABORT, 'Simulated write failure'); END")
        try { repository.seedDemo(); fail("Expected simulated write failure") }
        catch (_: android.database.sqlite.SQLiteConstraintException) { }
        val failed = repository.observe(DEMO_BRAND_ID).first()
        assertTrue(failed.categories.isEmpty() && failed.groups.isEmpty() && failed.options.isEmpty())
        assertNull(db.catalogDao().metadata("demo_catalog_v1"))
        sql.execSQL("DROP TRIGGER fail_demo")
        repository.seedDemo()
        assertEquals(3, repository.observe(DEMO_BRAND_ID).first().products.size)
        assertEquals("complete", db.catalogDao().metadata("demo_catalog_v1"))
    }
}
