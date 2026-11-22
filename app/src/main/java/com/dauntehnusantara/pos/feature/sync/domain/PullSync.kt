package com.dauntehnusantara.pos.feature.sync.domain

import kotlinx.coroutines.flow.Flow

data class PullScope(val sale: SaleScope, val role: String, val project: String) {
    val key: String get() = listOf(project, sale.brandId, sale.outletId, sale.userId, sale.deviceId, role).joinToString("|")
}
data class Change(val seq: Long, val type: String, val id: String, val action: String, val payload: String)
data class PullPage(val scope: PullScope, val after: Long, val next: Long, val more: Boolean, val changes: List<Change>, val epoch: String, val stockEnabled:Boolean=false)
data class PullStatus(val seq: Long = 0, val initialized: Boolean = false, val lastSuccessMs: Long? = null, val error: String? = null, val epoch: String? = null)
class PullFailure(val code: String) : Exception(code)
interface PullGateway {
    suspend fun pullScope(): PullScope?
    suspend fun fetch(scope: PullScope, after: Long, epoch: String? = null): PullPage
}
interface PullRepository {
    fun observe(scope: PullScope): Flow<PullStatus>
    suspend fun sync(): Boolean
}
