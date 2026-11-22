package com.dauntehnusantara.pos.feature.recovery.data

import androidx.room.withTransaction
import com.dauntehnusantara.pos.core.database.PosDatabase
import com.dauntehnusantara.pos.feature.recovery.domain.RecoveryRepository
import com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure
import com.dauntehnusantara.pos.feature.checkout.data.CheckoutJson
import com.dauntehnusantara.pos.feature.sync.data.PullJson
import com.dauntehnusantara.pos.feature.sync.domain.*
import org.json.JSONObject
import org.json.JSONArray
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject

internal object RecoveryArchive {
    private fun hash(raw:String)=MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun encode(payload:JSONObject):String = JSONObject().put("schema_version",1).put("kind","LOCAL_RECOVERY")
        .put("sha256",hash(PullJson.canonical(payload))).put("payload",payload).toString()
    /** Integrity check only; this never imports data, grants access, or proves file authenticity. */
    fun inspect(raw:String,scope:PullScope):List<String> {
        require(raw.toByteArray(Charsets.UTF_8).size<=16*1024*1024)
        val envelope=com.dauntehnusantara.pos.core.network.JsonSafety.obj(raw,16*1024*1024);require(envelope.getInt("schema_version")==1 && envelope.getString("kind")=="LOCAL_RECOVERY")
        val p=envelope.getJSONObject("payload");require(envelope.getString("sha256")==hash(PullJson.canonical(p)))
        require(p.getString("scope")==scope.key && p.getString("project")==scope.project)
        if(p.has("stock_pending")&&!p.isNull("stock_pending")) {
            require(scope.role in listOf("OWNER","MANAGER"))
            com.dauntehnusantara.pos.feature.stock.data.StockJson.command(p.getString("stock_pending"))
        }
        val records=p.getJSONArray("operations");val operations=mutableSetOf<String>();val sales=mutableSetOf<String>()
        return (0 until records.length()).map { i->
            val row=records.getJSONObject(i);val payload=row.getString("payload");val receipt=PullJson.receipt(payload)
            require(receipt.brandId==scope.sale.brandId && receipt.outletId==scope.sale.outletId && receipt.deviceId==scope.sale.deviceId && receipt.cashierId==scope.sale.userId)
            if(row.has("stock_intent")&&!row.isNull("stock_intent"))com.dauntehnusantara.pos.feature.stock.data.StockConsumption.validateIntent(row.getString("stock_intent"),receipt)
            require(receipt.operationId==row.getString("operation_id") && receipt.id==row.getString("sale_id") && operations.add(receipt.operationId) && sales.add(receipt.id))
            require(row.getString("state") in listOf("PENDING","RETRY","AUTH_REQUIRED","REVIEW","SYNCED") && row.getInt("attempts")>=0)
            require(hash(payload)==row.getString("request_hash"));payload
        }
    }
}
class RoomRecoveryRepository @Inject constructor(private val db:PosDatabase,private val gateway:SaleGateway,private val pull:PullGateway,private val clock:Clock):RecoveryRepository {
    override suspend fun snapshot(scope:PullScope):com.dauntehnusantara.pos.feature.recovery.domain.RecoverySnapshot {
        val raw=capture(scope)
        val time=com.dauntehnusantara.pos.core.network.JsonSafety.obj(raw,16*1024*1024).getJSONObject("payload").getLong("captured_at_ms")
        require(time>=0)
        return com.dauntehnusantara.pos.feature.recovery.domain.RecoverySnapshot(raw,time)
    }
    override suspend fun capture(scope:PullScope):String {
        if(gateway.checkoutScope()!=scope.sale || pull.pullScope()!=scope)throw ManagementFailure("AUTH_REQUIRED")
        val raw=db.withTransaction {
            val project=db.pullDao().metadata("sync.project")
            if(project!=null && project!=scope.project)throw ManagementFailure("AUTH_REQUIRED")
            val dao=db.checkoutDao();val s=scope.sale
            val rows=dao.syncRows(s.brandId,s.outletId,s.deviceId,s.userId)
            val operations=JSONArray(rows.map { row->
                val sale=requireNotNull(dao.byOperation(row.id))
                JSONObject().put("operation_id",row.id).put("sale_id",row.aggregateId).put("payload",row.payloadJson)
                    .put("request_hash",sale.requestHash).put("state",row.state).put("attempts",row.attempts)
                    .put("next_attempt_at_ms",row.nextAttemptAtMs).put("last_error",row.lastErrorCode?:JSONObject.NULL)
                    .put("stock_intent",db.pullDao().metadata("stock.intent.${row.id}")?:JSONObject.NULL)
                    .put("command",db.pullDao().metadata("checkout.command.${row.id}")?:JSONObject.NULL)
            })
            val session=dao.session(scope.key)
            val draft=session?.let {
                CheckoutJson.readLines(it.cartJson)
                JSONObject().put("draft_id",it.draftId).put("operation_id",it.operationId).put("revision",it.revision)
                    .put("cart",JSONArray(it.cartJson)).put("last_sale_id",it.lastSaleId?:JSONObject.NULL)
            }
            RecoveryArchive.encode(JSONObject().put("scope",scope.key).put("project",scope.project).put("captured_at_ms",clock.millis())
                .put("operations",operations).put("draft",draft?:JSONObject.NULL)
                .put("stock_pending",db.pullDao().metadata("stock.pending.${scope.key}")?.takeUnless{it=="null"}?:JSONObject.NULL))
        }
        if(gateway.checkoutScope()!=scope.sale || pull.pullScope()!=scope)throw ManagementFailure("AUTH_REQUIRED")
        if(raw.toByteArray(Charsets.UTF_8).size>16*1024*1024)throw ManagementFailure("TOO_LARGE")
        RecoveryArchive.inspect(raw,scope)
        return raw
    }
}
