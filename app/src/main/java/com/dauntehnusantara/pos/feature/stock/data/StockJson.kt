package com.dauntehnusantara.pos.feature.stock.data

import com.dauntehnusantara.pos.feature.stock.domain.*
import org.json.JSONObject
import org.json.JSONArray
import java.time.Instant

object StockJson {
    fun command(c:StockCommand)=JSONObject().put("operation_id",c.operationId).put("epoch",c.epoch).put("product_id",c.productId)
        .put("quantity",c.quantity).put("expected_revision",c.expectedRevision).put("reason",c.reason).put("sale_operation_id",c.saleOperationId?:JSONObject.NULL).put("deduct",c.deduct).toString()
    fun command(raw:String)=JSONObject(raw).let { StockCommand(it.getString("operation_id"),it.getString("epoch"),it.getString("product_id"),it.getLong("quantity"),it.getLong("expected_revision"),it.getString("reason"),if(it.isNull("sale_operation_id"))null else it.getString("sale_operation_id"),it.optBoolean("deduct",false)).also(StockCommand::validate) }
    fun read(raw:String):StockSnapshot {
        val j=JSONObject(raw)
        val products=j.getJSONArray("products");val history=j.getJSONArray("history")
        require(products.length()<=100 && history.length()<=50)
        return StockSnapshot(j.getString("epoch"),Instant.parse(j.getString("captured_at")).toEpochMilli(),
            (0 until products.length()).map { products.getJSONObject(it).let { p->
                StockProduct(p.getString("product_id"),p.getString("name"),p.getBoolean("active"),if(p.isNull("quantity"))null else p.getLong("quantity"),p.getLong("revision")).also {
                    require(it.revision>=0 && (it.quantity==null && it.revision==0L || it.quantity!=null && it.quantity in StockRules.MIN_BALANCE..StockRules.MAX_COUNT && it.revision>0))
                }
            } },j.getBoolean("has_more"),(0 until history.length()).map { history.getJSONObject(it).let { h->
                StockMovement(h.getLong("seq"),h.getString("operation_id"),h.getString("product_id"),h.getString("product_name"),h.getLong("previous_quantity"),h.getLong("quantity"),h.getLong("delta"),h.getLong("revision"),h.getString("actor_name"),h.getString("reason"),Instant.parse(h.getString("created_at")).toEpochMilli()).also {
                    require(it.seq>0 && it.quantity in StockRules.MIN_BALANCE..StockRules.MAX_COUNT && it.previous in StockRules.MIN_BALANCE..StockRules.MAX_COUNT && it.delta==it.quantity-it.previous && it.revision>0)
                }
            } },j.optBoolean("automatic",false),j.optJSONArray("reviews")?.let{a->
                require(a.length()<=50)
                (0 until a.length()).map{a.getJSONObject(it).let{r->StockReview(r.getString("operation_id"),r.getString("product_id"),r.getString("product_name"),r.getLong("quantity"),r.getString("issue"),if(r.isNull("balance"))null else r.getLong("balance"),r.getLong("revision"))}}
            }?:emptyList()).also { require(java.util.UUID.fromString(it.epoch).toString()==it.epoch) }
    }
    fun write(s:StockSnapshot)=JSONObject().put("epoch",s.epoch).put("captured_at",Instant.ofEpochMilli(s.capturedAtMs).toString())
        .put("automatic",s.automatic).put("reviews",JSONArray(s.reviews.map{r->JSONObject().put("operation_id",r.operationId).put("product_id",r.productId).put("product_name",r.name).put("quantity",r.quantity).put("issue",r.issue).put("balance",r.balance?:JSONObject.NULL).put("revision",r.revision)}))
        .put("has_more",s.hasMore).put("products",JSONArray(s.products.map { p->JSONObject().put("product_id",p.id).put("name",p.name).put("active",p.active).put("quantity",p.quantity?:JSONObject.NULL).put("revision",p.revision) }))
        .put("history",JSONArray(s.history.map { h->JSONObject().put("seq",h.seq).put("operation_id",h.operationId).put("product_id",h.productId).put("product_name",h.productName).put("previous_quantity",h.previous).put("quantity",h.quantity).put("delta",h.delta).put("revision",h.revision).put("actor_name",h.actor).put("reason",h.reason).put("created_at",Instant.ofEpochMilli(h.timeMs).toString()) })).toString()
}
