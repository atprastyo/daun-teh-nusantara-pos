package com.dauntehnusantara.pos.feature.stock.data

import androidx.room.withTransaction
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.stock.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RoomStockRepository @Inject constructor(private val db:PosDatabase,private val auth:ReportingGateway,private val remote:StockGateway,
    private val scheduler:com.dauntehnusantara.pos.feature.sync.domain.SyncScheduler=object:com.dauntehnusantara.pos.feature.sync.domain.SyncScheduler{override fun enqueue(force:Boolean){};override fun ensurePeriodic(){}}):StockRepository {
    private val mutex=Mutex()
    private suspend fun check(s:PullScope,online:Boolean=false) { if(auth.reportingScope(online)!=s)throw ManagementFailure("AUTH_REQUIRED") }
    private fun pendingKey(s:PullScope)="stock.pending.${s.key}"
    private fun cacheKey(s:PullScope,q:StockQuery)="stock.cache.${s.key}."+java.security.MessageDigest.getInstance("SHA-256")
        .digest(org.json.JSONObject().put("search",q.search).put("offset",q.offset).put("product",q.productId).put("before",q.before).toString().toByteArray()).joinToString(""){"%02x".format(it)}
    override suspend fun cached(scope:PullScope,query:StockQuery):StockSnapshot? {
        check(scope);return db.pullDao().metadata(cacheKey(scope,query))?.let(StockJson::read)
    }
    override suspend fun refresh(scope:PullScope,query:StockQuery):StockSnapshot {
        check(scope,true);val snapshot=remote.stock(scope,query);check(scope)
        db.metadataDao().put(AppMetadata(cacheKey(scope,query),StockJson.write(snapshot)));return snapshot
    }
    override suspend fun pending(scope:PullScope):StockCommand? {
        check(scope);return db.pullDao().metadata(pendingKey(scope))?.takeUnless{it=="null"}?.let(StockJson::command)
    }
    override suspend fun prepare(scope:PullScope,command:StockCommand):Unit = mutex.withLock {
        check(scope,true);command.validate()
        db.withTransaction {
            val old=db.pullDao().metadata(pendingKey(scope))?.takeUnless{it=="null"}
            if(old!=null && StockJson.command(old)!=command)throw ManagementFailure("PENDING")
            db.metadataDao().put(AppMetadata(pendingKey(scope),StockJson.command(command)))
        }
    }
    override suspend fun submit(scope:PullScope):Unit = mutex.withLock {
        check(scope,true);val command=pending(scope)?:return@withLock
        val ack=try { remote.writeStock(scope,command) }
        catch(e:CancellationException){throw e}
        catch(e:ManagementFailure) {
            // These are definitive transactional rejections; uncertain transport outcomes retain identity.
            if(e.code in setOf("CONFLICT","INVALID"))db.metadataDao().put(AppMetadata(pendingKey(scope),"null"))
            throw e
        }
        // Clear the journal only after a validated acknowledgement, even if the UI scope has since changed.
        db.withTransaction {
            // A physical recount starts a new generation. Publishing its own acknowledged
            // baseline locally avoids an unnecessary review when this operator next sells.
            if(command.saleOperationId==null&&ack?.generation!=null&&db.pullDao().cursor(scope.key)?.serverEpoch==command.epoch) {
                val row=org.json.JSONObject().put("schema_version",1).put("outlet_id",scope.sale.outletId).put("product_id",command.productId)
                    .put("quantity",ack.quantity).put("revision",ack.revision).put("generation",ack.generation).put("operation_id",org.json.JSONObject.NULL)
                val old=db.pullDao().metadata(StockConsumption.balanceKey(scope,command.productId))?.let{org.json.JSONObject(it)}
                if(old==null||old.getLong("revision")<ack.revision)db.metadataDao().put(AppMetadata(StockConsumption.balanceKey(scope,command.productId),row.toString()))
            }
            db.metadataDao().put(AppMetadata(pendingKey(scope),"null"))
        }
        runCatching{scheduler.enqueue(force=true)}
    }
}
