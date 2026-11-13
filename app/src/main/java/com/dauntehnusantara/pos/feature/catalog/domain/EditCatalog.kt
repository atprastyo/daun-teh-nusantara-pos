package com.dauntehnusantara.pos.feature.catalog.domain

import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import java.util.UUID
import javax.inject.Inject

data class CatalogMutation(
    val operationId: String = UUID.randomUUID().toString(), val kind: CatalogRecord,
    val id: String = UUID.randomUUID().toString(), val expectedVersion: Long = 0,
    val name: String = "", val active: Boolean = true, val priceIdr: Long? = null,
    val parentId: String? = null, val minSelect: Int? = null, val maxSelect: Int? = null,
    val sortOrder: Int = 0, val groupIds: List<String> = emptyList(),
) {
    fun validate() {
        require(UUID.fromString(operationId).toString() == operationId && UUID.fromString(id).toString() == id)
        require(expectedVersion in 0 until Long.MAX_VALUE && name.trim().length in 1..120)
        when (kind) {
            CatalogRecord.Category -> require(sortOrder >= 0)
            CatalogRecord.Product, CatalogRecord.Option -> {
                require(requireNotNull(priceIdr) >= 0)
                require(UUID.fromString(requireNotNull(parentId)).toString() == parentId)
                require(groupIds.size <= 100 && groupIds.distinct().size == groupIds.size)
                groupIds.forEach { require(UUID.fromString(it).toString() == it) }
            }
            CatalogRecord.Group -> require(requireNotNull(minSelect) >= 0 && requireNotNull(maxSelect) > 0 && maxSelect >= minSelect)
        }
    }
}
data class CatalogAck(val operationId: String, val id: String, val version: Long, val throughSeq: Long, val epoch: String)
interface CatalogGateway { suspend fun saveCatalog(scope: PullScope, epoch: String, change: CatalogMutation): CatalogAck }
class EditCatalog @Inject constructor(private val gateway: CatalogGateway) {
    suspend operator fun invoke(scope: PullScope, epoch: String, change: CatalogMutation): CatalogAck {
        require(scope.role in listOf("OWNER", "MANAGER")); change.validate()
        return gateway.saveCatalog(scope, epoch, change.copy(name = change.name.trim()))
    }
}
