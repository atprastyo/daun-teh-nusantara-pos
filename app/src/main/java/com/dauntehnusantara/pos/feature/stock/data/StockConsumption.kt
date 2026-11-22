package com.dauntehnusantara.pos.feature.stock.data

import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import org.json.JSONObject
import org.json.JSONArray

/** Local estimates are not global availability. Immutable intent shares the checkout transaction. */
internal object StockConsumption {
    fun identity(s:PullScope)=java.security.MessageDigest.getInstance("SHA-256").digest(listOf(s.project,s.sale.brandId,s.sale.outletId,s.sale.deviceId,s.sale.userId).joinToString("|").toByteArray()).joinToString(""){"%02x".format(it)}
    fun balanceKey(s:PullScope,p:String)="stock.balance.${s.key}.$p"
    fun pendingPrefix(s:PullScope)="stock.sale.pending.${identity(s)}."
    fun deliveryKey(s:PullScope,op:String,p:String)="stock.delivery.${identity(s)}.$op.$p"
    private fun quantities(lines:List<CartLine>)=lines.groupBy{it.productId}.mapValues{(_,items)->items.fold(0L){n,l->Math.addExact(n,l.quantity.toLong())}}
    fun validateIntent(raw:String,r:Receipt) {
        val j=JSONObject(raw);require(java.util.UUID.fromString(j.getString("epoch")).toString()==j.getString("epoch"))
        val a=j.getJSONArray("products");val expected=quantities(r.lines);require(a.length()==expected.size)
        val seen=mutableSetOf<String>()
        for(i in 0 until a.length()) {
            val item=a.getJSONObject(i);val id=item.getString("product_id")
            require(seen.add(id)&&expected[id]==item.getLong("quantity"))
            if(!item.isNull("generation"))require(java.util.UUID.fromString(item.getString("generation")).toString()==item.getString("generation"))
        }
    }
    suspend fun delivery(db:PosDatabase,s:PullScope,op:String):String? {
        if(db.pullDao().metadata("stock.intent.$op")==null)return null
        val raw=db.pullDao().metadata(pendingPrefix(s)+op)?:return "APPLIED"
        val a=JSONObject(raw).getJSONArray("products")
        return if((0 until a.length()).any{db.pullDao().metadata(deliveryKey(s,op,a.getJSONObject(it).getString("product_id")))=="REVIEW"})"REVIEW" else "PENDING"
    }
    suspend fun freeze(db:PosDatabase,s:PullScope,r:Receipt) {
        if(db.pullDao().metadata("stock.enabled.${s.key}")!="true")return
        val epoch=requireNotNull(db.pullDao().cursor(s.key)?.serverEpoch)
        val products=quantities(r.lines).map{(id,q)->
            val balance=db.pullDao().metadata(balanceKey(s,id))?.let(::JSONObject)
            JSONObject().put("product_id",id).put("quantity",q).put("generation",balance?.getString("generation")?:JSONObject.NULL)
        }
        val raw=JSONObject().put("epoch",epoch).put("products",JSONArray(products)).toString()
        validateIntent(raw,r)
        db.metadataDao().put(AppMetadata("stock.intent.${r.operationId}",raw))
        db.metadataDao().put(AppMetadata(pendingPrefix(s)+r.operationId,raw))
    }
    suspend fun apply(db:PosDatabase,s:PullScope,c:Change) {
        val j=JSONObject(c.payload);require(j.getInt("schema_version")==1 && j.getString("outlet_id")==s.sale.outletId && c.action=="UPSERT")
        val product=j.getString("product_id");require(java.util.UUID.fromString(product).toString()==product)
        val op=if(j.isNull("operation_id"))null else j.getString("operation_id")
        if(c.type=="STOCK") {
            require(c.id==product && j.getLong("quantity") in -1_000_000_000_000L..1_000_000_000L && j.getLong("revision")>0)
            require(java.util.UUID.fromString(j.getString("generation")).toString()==j.getString("generation"))
            val old=db.pullDao().metadata(balanceKey(s,product))?.let(::JSONObject)
            if(old==null||old.getLong("revision")<j.getLong("revision"))db.metadataDao().put(AppMetadata(balanceKey(s,product),j.toString()))
            else if(old.getLong("revision")==j.getLong("revision"))require(old.getLong("quantity")==j.getLong("quantity")&&old.getString("generation")==j.getString("generation"))
        }else require(c.id==op && j.getString("status") in listOf("REVIEW","SKIPPED"))
        if(op!=null) {
            require(java.util.UUID.fromString(op).toString()==op)
            val raw=db.pullDao().metadata(pendingPrefix(s)+op)?:return
            db.metadataDao().put(AppMetadata(deliveryKey(s,op,product),if(c.type=="STOCK")"APPLIED" else j.getString("status")))
            val items=JSONObject(raw).getJSONArray("products")
            val done=(0 until items.length()).all { db.pullDao().metadata(deliveryKey(s,op,items.getJSONObject(it).getString("product_id"))) in listOf("APPLIED","SKIPPED") }
            if(done)db.metadataDao().delete(pendingPrefix(s)+op)
        }
    }
    suspend fun warnings(db:PosDatabase,s:PullScope,draft:Draft):List<String> {
        if(db.pullDao().metadata("stock.enabled.${s.key}")!="true")return emptyList()
        val pending=db.metadataDao().entries(pendingPrefix(s)).map { it.key.removePrefix(pendingPrefix(s)) to JSONObject(it.value).getJSONArray("products") }
        return quantities(draft.lines).map { (product,cart)->
            val name=draft.lines.first{it.productId==product}.productName
            val balance=db.pullDao().metadata(balanceKey(s,product))?.let(::JSONObject)
            if(balance==null)"$name: stok belum dicatat. Penjualan tetap diizinkan; stok perlu ditinjau."
            else {
                var unconfirmed=0L;var review=false
                for((op,items) in pending)for(i in 0 until items.length()) {
                    val item=items.getJSONObject(i)
                    if(item.getString("product_id")!=product)continue
                    val status=db.pullDao().metadata(deliveryKey(s,op,product))
                    if(status in listOf("APPLIED","SKIPPED"))continue
                    if(status=="REVIEW"||item.optString("generation")!=balance.getString("generation")){review=true;continue}
                    unconfirmed=Math.addExact(unconfirmed,item.getLong("quantity"))
                }
                val estimate=Math.subtractExact(balance.getLong("quantity"),unconfirmed)
                "$name: server ${balance.getLong("quantity")}, belum terkonfirmasi $unconfirmed, perkiraan $estimate unit."+
                    (if(estimate<cart)" Stok tidak cukup; penjualan tetap diizinkan." else "")+
                    (if(review)" Ada pergerakan stok yang perlu ditinjau." else "")
            }
        }+"Perkiraan dari sinkronisasi terakhir; belum mencakup penjualan offline perangkat lain."
    }
}
