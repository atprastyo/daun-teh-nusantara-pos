package com.dauntehnusantara.pos.feature.reports.data

import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import org.json.JSONObject
import java.time.LocalDate

internal fun JSONObject.boundScope(scope:PullScope, sourceOutletField:String="outlet_id") {
    require(getInt("schema_version")==1 && getString("brand_id")==scope.sale.brandId && getString("user_id")==scope.sale.userId &&
        getString("device_id")==scope.sale.deviceId && getString("role")==scope.role && getString(sourceOutletField)==scope.sale.outletId)
}
internal object ReportJson {
    fun read(j:JSONObject,q:ReportQuery):SalesReport {
        j.boundScope(q.source,"source_outlet_id")
        require((if(j.isNull("outlet_id"))null else j.getString("outlet_id"))==q.outletId)
        require(j.getString("from_date")==q.from.toString() && j.getString("to_date")==q.to.toString())
        val s=j.getJSONObject("summary")
        fun n(v:JSONObject,key:String)=v.getString(key).toLong()
        fun <T> rows(key:String,read:(JSONObject)->T):List<T> { val a=j.getJSONArray(key);return (0 until a.length()).map { read(a.getJSONObject(it)) } }
        return SalesReport(q,j.getLong("as_of_ms"),ReportSummary(n(s,"sale_count"),n(s,"subtotal_idr"),n(s,"discount_idr"),n(s,"total_idr"),n(s,"cash_idr"),n(s,"qris_idr")),
            rows("days") { ReportDay(LocalDate.parse(it.getString("day")),n(it,"sale_count"),n(it,"total_idr")) },
            rows("outlets") { ReportOutlet(it.getString("outlet_id"),it.getString("name"),n(it,"sale_count"),n(it,"total_idr")) },
            rows("products") { ReportProduct(it.getString("product_id"),it.getString("name"),n(it,"quantity"),n(it,"gross_idr")) },
            rows("choices") { OutletChoice(it.getString("id"),it.getString("name"),it.getBoolean("active")) }).also(ReportRules::validate)
    }
    fun write(r:SalesReport):String {
        val q=r.query;val scope=q.source;val s=r.summary
        fun a(values:List<JSONObject>)=org.json.JSONArray(values)
        return JSONObject().put("schema_version",1).put("brand_id",scope.sale.brandId).put("user_id",scope.sale.userId).put("device_id",scope.sale.deviceId)
            .put("role",scope.role).put("source_outlet_id",scope.sale.outletId).put("outlet_id",q.outletId?:JSONObject.NULL)
            .put("from_date",q.from).put("to_date",q.to).put("as_of_ms",r.asOfMs)
            .put("summary",JSONObject().put("sale_count",s.count.toString()).put("subtotal_idr",s.subtotal.toString()).put("discount_idr",s.discount.toString()).put("total_idr",s.total.toString()).put("cash_idr",s.cash.toString()).put("qris_idr",s.qris.toString()))
            .put("days",a(r.days.map { JSONObject().put("day",it.date).put("sale_count",it.count.toString()).put("total_idr",it.total.toString()) }))
            .put("outlets",a(r.outlets.map { JSONObject().put("outlet_id",it.id).put("name",it.name).put("sale_count",it.count.toString()).put("total_idr",it.total.toString()) }))
            .put("products",a(r.products.map { JSONObject().put("product_id",it.id).put("name",it.name).put("quantity",it.quantity.toString()).put("gross_idr",it.gross.toString()) }))
            .put("choices",a(r.choices.map { JSONObject().put("id",it.id).put("name",it.name).put("active",it.active) })).toString()
    }
}
