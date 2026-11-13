package com.dauntehnusantara.pos.feature.catalog.data

import com.dauntehnusantara.pos.feature.catalog.domain.*
import org.json.JSONArray
import org.json.JSONObject

internal object CatalogCommandJson {
    fun write(c: CatalogMutation) = JSONObject().put("operation_id", c.operationId).put("kind", c.kind.name)
        .put("id", c.id).put("expected_version", c.expectedVersion).put("name", c.name).put("active", c.active)
        .put("price_idr", c.priceIdr ?: JSONObject.NULL).put("parent_id", c.parentId ?: JSONObject.NULL)
        .put("min_select", c.minSelect ?: JSONObject.NULL).put("max_select", c.maxSelect ?: JSONObject.NULL)
        .put("sort_order", c.sortOrder).put("group_ids", JSONArray(c.groupIds))
    fun ack(j: JSONObject, c: CatalogMutation, epoch: String): CatalogAck {
        require(j.getString("operation_id") == c.operationId && j.getString("id") == c.id && j.getString("kind") == c.kind.name && j.getString("feed_epoch") == epoch)
        return CatalogAck(c.operationId, c.id, j.getLong("version"), j.getLong("through_seq"), epoch).also {
            require(it.version == Math.addExact(c.expectedVersion, 1) && it.throughSeq > 0)
        }
    }
}
