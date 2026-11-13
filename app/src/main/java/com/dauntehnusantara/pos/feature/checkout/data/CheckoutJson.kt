package com.dauntehnusantara.pos.feature.checkout.data

import com.dauntehnusantara.pos.feature.checkout.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object CheckoutJson {
    fun line(value: CartLine) = JSONObject().put("id", value.id).put("product_id", value.productId)
        .put("product_name", value.productName).put("product_version", value.productVersion)
        .put("category_version", value.categoryVersion).put("unit_base_price_idr", value.unitBasePriceIdr)
        .put("quantity", value.quantity).put("line_total_idr", value.totalIdr)
        .put("groups", JSONArray(value.groups.map { JSONObject().put("id", it.id).put("version", it.version) }))
        .put("modifiers", JSONArray(value.modifiers.map {
            JSONObject().put("option_id", it.optionId).put("group_id", it.groupId).put("group_name", it.groupName)
                .put("option_name", it.optionName).put("price_delta_idr", it.priceDeltaIdr).put("option_version", it.optionVersion)
        }))
    fun readLine(json: JSONObject): CartLine {
        val modifiers = json.getJSONArray("modifiers")
        val groups = json.getJSONArray("groups")
        return CartLine(json.getString("id"), json.getString("product_id"), json.getString("product_name"),
            json.getLong("product_version"), json.getLong("category_version"), json.getLong("unit_base_price_idr"), json.getInt("quantity"),
            (0 until modifiers.length()).map { modifiers.getJSONObject(it).let { o ->
                ModifierSnapshot(o.getString("option_id"), o.getString("group_id"), o.getString("group_name"),
                    o.getString("option_name"), o.getLong("price_delta_idr"), o.getLong("option_version"))
            } }, (0 until groups.length()).map { groups.getJSONObject(it).let { g -> GroupVersion(g.getString("id"), g.getLong("version")) } })
    }
    fun lines(values: List<CartLine>) = JSONArray(values.map(::line)).toString()
    fun readLines(value: String): List<CartLine> = JSONArray(value).let { array -> (0 until array.length()).map { readLine(array.getJSONObject(it)) } }
    fun payload(receipt: Receipt): String = JSONObject().put("schema_version", 1).put("demo", true)
        .put("operation_id", receipt.operationId).put("sale_id", receipt.id).put("brand_id", receipt.brandId)
        .put("outlet_id", receipt.outletId).put("device_id", receipt.deviceId).put("cashier_user_id", receipt.cashierId)
        .put("created_at_utc_ms", receipt.createdAtUtcMs).put("subtotal_idr", receipt.totalIdr).put("discount_idr", 0).put("total_idr", receipt.totalIdr)
        .put("items", JSONArray(receipt.lines.map(::line)))
        .put("payment", JSONObject().put("method", receipt.payment.method.name).put("amount_idr", receipt.payment.amountIdr)
            .put("tendered_idr", receipt.payment.tenderedIdr ?: JSONObject.NULL).put("change_idr", receipt.payment.changeIdr ?: JSONObject.NULL)
            .put("verification", receipt.payment.verification)).toString()
}
