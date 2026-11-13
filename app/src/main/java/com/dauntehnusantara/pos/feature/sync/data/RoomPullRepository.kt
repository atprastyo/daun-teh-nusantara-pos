package com.dauntehnusantara.pos.feature.sync.data

import androidx.room.withTransaction
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

@Singleton
class RoomPullRepository @Inject constructor(private val db: PosDatabase, private val gateway: PullGateway, private val clock: Clock) : PullRepository {
    private val mutex = Mutex()
    private val dao get() = db.pullDao()
    override fun observe(scope: PullScope) = db.invalidationTracker.createFlow("sync_cursor").map {
        dao.cursor(scope.key)?.let { PullStatus(it.lastSeq, it.initialized, it.lastSuccessMs, it.errorCode, it.serverEpoch) } ?: PullStatus()
    }
    override suspend fun sync(): Boolean = mutex.withLock {
        val scope = gateway.pullScope() ?: return@withLock false
        try {
            repeat(5) {
                val cursor = dao.cursor(scope.key) ?: SyncCursorEntity(scope.key)
                val page = gateway.fetch(scope, cursor.lastSeq, cursor.serverEpoch)
                // Session/context change during IO must not apply under the new account's cursor.
                if (gateway.pullScope() != scope) throw PullFailure("SCOPE_CHANGED")
                apply(page, cursor)
                if (!page.more) return@withLock false
            }
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            db.withTransaction {
                val cursor = dao.cursor(scope.key) ?: SyncCursorEntity(scope.key)
                val code = (e as? PullFailure)?.code ?: "INVALID_PAGE"
                dao.put(cursor.copy(errorCode = code, initialized = cursor.initialized && code != "REBOOTSTRAP_REQUIRED"))
            }
            throw e
        }
    }
    private suspend fun apply(page: PullPage, expected: SyncCursorEntity) = db.withTransaction {
        if(expected.serverEpoch != null && expected.serverEpoch != page.epoch) throw PullFailure("REBOOTSTRAP_REQUIRED")
        require(page.scope.key == expected.scope && page.after == expected.lastSeq && page.next >= page.after)
        require(page.changes.size <= 100 && (!page.more || page.changes.isNotEmpty()))
        require(page.changes.all { it.seq > page.after && it.seq <= page.next } && page.changes.zipWithNext().all { (a, b) -> a.seq < b.seq })
        require((dao.cursor(expected.scope) ?: SyncCursorEntity(expected.scope)) == expected)
        val project = dao.metadata("sync.project")
        if (project != null && project != page.scope.project) throw PullFailure("PROJECT_CHANGED")
        var previous = page.after
        for (change in page.changes) {
            require(change.seq > previous && change.seq <= page.next)
            previous = change.seq
            require(change.action in listOf("UPSERT", "DELETE"))
            when (change.type) {
                "CATALOG" -> catalog(page.scope, change)
                "SALE" -> sale(page.scope, change)
                else -> throw PullFailure("UNSUPPORTED_SCHEMA")
            }
        }
        db.metadataDao().put(AppMetadata("sync.project", page.scope.project))
        dao.put(expected.copy(lastSeq = page.next, serverEpoch = page.epoch, initialized = expected.initialized || !page.more, lastSuccessMs = if (!page.more) clock.millis() else expected.lastSuccessMs, errorCode = null))
    }
    private suspend fun catalog(scope: PullScope, change: Change) {
        val j = JSONObject(change.payload)
        require(j.getInt("schema_version") == 1)
        val table = j.getString("table")
        require(table in listOf("categories", "products", "modifier_groups", "modifier_options", "product_modifier_groups"))
        val r = j.getJSONObject("record")
        require(r.getString("brand_id") == scope.sale.brandId)
        val assignment = table == "product_modifier_groups"
        val id = r.getString(if (assignment) "product_id" else "id")
        require(id == change.id)
        require(java.util.UUID.fromString(id).toString() == id)
        val version = r.getLong("version"); require(version > 0)
        val active = r.getBoolean("active")
        require((change.action == "UPSERT") == active)
        val key = "sync.catalog.$table.$id" + if (assignment) ".${r.getString("group_id")}" else ""
        val canonical = PullJson.canonical(r)
        dao.metadata(key)?.let { old ->
            val saved = JSONObject(old)
            if (saved.getLong("version") >= version) {
                if (saved.getLong("version") == version) require(saved.getString("payload") == canonical)
                return
            }
        }
        val catalog = db.catalogDao()
        if (assignment) {
            val group = r.getString("group_id")
            if (active) catalog.put(ProductGroupEntity(id, group, scope.sale.brandId, r.getInt("sort_order")))
            else catalog.removeAssignment(id, group)
        } else {
            val record = CatalogRecordColumns(id, scope.sale.brandId, r.getString("name"), active, version,
                Instant.parse(r.getString("created_at")).toEpochMilli(), Instant.parse(r.getString("updated_at")).toEpochMilli())
            when (table) {
                "categories" -> catalog.put(CategoryEntity(record, r.getInt("sort_order")))
                "products" -> catalog.put(ProductEntity(record, r.getString("category_id"), r.getLong("base_price_idr")))
                "modifier_groups" -> catalog.put(GroupEntity(record, r.getInt("min_select"), r.getInt("max_select")))
                "modifier_options" -> catalog.put(OptionEntity(record, r.getString("group_id"), r.getLong("price_delta_idr")))
            }
        }
        db.metadataDao().put(AppMetadata(key, JSONObject().put("version", version).put("payload", canonical).toString()))
    }
    private suspend fun sale(scope: PullScope, change: Change) {
        require(change.action == "UPSERT") // Sales cannot be deleted or overwritten by feed delivery.
        val receipt = PullJson.receipt(change.payload)
        val s = scope.sale
        require(receipt.id == change.id && receipt.brandId == s.brandId && receipt.outletId == s.outletId)
        require(scope.role in listOf("OWNER", "MANAGER") || scope.role == "CASHIER" && receipt.cashierId == s.userId)
        val canonical = PullJson.canonical(JSONObject(change.payload))
        dao.sale(scope.key, receipt.id)?.let {
            require(it.operationId == receipt.operationId && PullJson.canonical(JSONObject(it.payload)) == canonical)
            return
        }
        val localOperation = db.checkoutDao().byOperation(receipt.operationId)
        val localIdentity = db.checkoutDao().sale(receipt.id, s.brandId, s.outletId)
        (localOperation ?: localIdentity)?.let { local ->
            require(localOperation == local && localIdentity == local)
            val outbox = requireNotNull(db.checkoutDao().outbox(local.operationId))
            require(local.id == receipt.id && outbox.operationType == "SUBMIT_SALE" && PullJson.canonical(JSONObject(outbox.payloadJson)) == canonical)
            db.checkoutDao().syncResult(local.operationId, "SYNCED", outbox.attempts, 0, null, clock.millis())
        }
        dao.insert(RemoteSaleEntity(scope.key, receipt.id, receipt.operationId, change.seq, change.payload))
    }
}
