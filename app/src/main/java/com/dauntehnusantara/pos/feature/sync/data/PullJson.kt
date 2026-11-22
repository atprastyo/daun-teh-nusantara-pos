package com.dauntehnusantara.pos.feature.sync.data

import com.dauntehnusantara.pos.feature.sync.domain.*
import com.dauntehnusantara.pos.feature.checkout.data.CheckoutJson
import com.dauntehnusantara.pos.feature.checkout.domain.*
import org.json.JSONObject

internal object PullJson {
    fun page(raw: JSONObject, scope: PullScope, after: Long): PullPage {
        val s = scope.sale
        require(raw.getInt("schema_version") == 1 && raw.getString("brand_id") == s.brandId &&
            raw.getString("outlet_id") == s.outletId && raw.getString("user_id") == s.userId &&
            raw.getString("device_id") == s.deviceId && raw.getString("role") == scope.role && raw.getLong("after_seq") == after)
        val a = raw.getJSONArray("changes")
        require(a.length() <= 100)
        return PullPage(scope, after, raw.getLong("next_seq"), raw.getBoolean("has_more"), (0 until a.length()).map {
            val c = a.getJSONObject(it)
            Change(c.getLong("seq"), c.getString("entity_type"), c.getString("entity_id"), c.getString("action"), c.getJSONObject("payload").toString())
        }, raw.getString("feed_epoch").also { require(java.util.UUID.fromString(it).toString() == it) },raw.optBoolean("stock_enabled",false))
    }
    // Canonical comparison ignores JSON object order/whitespace, preserves all receipt fields.
    fun canonical(value: Any?): String = when (value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
        is org.json.JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is Number -> value.toString().toBigDecimal().stripTrailingZeros().toPlainString()
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
    }
    fun receipt(payload: String): Receipt {
        val j = com.dauntehnusantara.pos.core.network.JsonSafety.obj(payload)
        require(j.getInt("schema_version") == 1 && !j.getBoolean("demo") && j.getLong("discount_idr") == 0L)
        val lines = CheckoutJson.readLines(j.getJSONArray("items").toString())
        val id = j.getString("sale_id"); val op = j.getString("operation_id")
        CheckoutRules.validateDraft(Draft(id, op, 0, lines))
        val total = lines.fold(0L) { sum, line -> Math.addExact(sum, line.totalIdr) }
        require(total == j.getLong("total_idr") && total == j.getLong("subtotal_idr"))
        lines.forEachIndexed { i, line -> require(line.totalIdr == j.getJSONArray("items").getJSONObject(i).getLong("line_total_idr")) }
        val p = j.getJSONObject("payment"); val method = PaymentMethod.valueOf(p.getString("method"))
        val payment = CheckoutRules.payment(total, PaymentInput(method, if (p.isNull("tendered_idr")) null else p.getLong("tendered_idr"),
            method == PaymentMethod.QRIS_MANUAL && p.getString("verification") == "CASHIER_CONFIRMED_MANUALLY"))
        require(payment.amountIdr == p.getLong("amount_idr") && payment.verification == p.getString("verification") &&
            payment.changeIdr == if (p.isNull("change_idr")) null else p.getLong("change_idr"))
        return Receipt(id, op, j.getString("brand_id"), j.getString("outlet_id"), j.getString("device_id"), j.getString("cashier_user_id"),
            j.getLong("created_at_utc_ms"), lines, total, payment)
    }
}
