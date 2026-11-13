package com.dauntehnusantara.pos.feature.sync.domain

import com.dauntehnusantara.pos.feature.checkout.domain.Receipt
import kotlinx.coroutines.flow.Flow

/** Public identifiers only. Credentials stay in the encrypted auth data layer. */
data class SaleScope(val brandId: String, val outletId: String, val deviceId: String, val userId: String)
data class PendingSale(val operationId: String, val saleId: String, val scope: SaleScope, val payload: String, val attempts: Int)
sealed interface PushResult {
    data object Accepted : PushResult
    data class Review(val code: String) : PushResult
    data object AuthRequired : PushResult
    data object Retry : PushResult
}
interface SaleGateway {
    suspend fun backgroundScope(): SaleScope?
    suspend fun checkoutScope(): SaleScope?
    suspend fun submit(sale: PendingSale): PushResult
}
data class SyncStatus(val pending: Int = 0, val retrying: Int = 0, val authRequired: Int = 0,
    val review: Int = 0, val synced: Int = 0, val lastCode: String? = null)
interface SyncRepository {
    fun observe(scope: SaleScope): Flow<SyncStatus>
    /** Frozen receipt comes from checkout; validates scope, catalog, payment and atomic persistence. */
    suspend fun record(receipt: Receipt)
    suspend fun drain(): Boolean
    suspend fun retry(scope: SaleScope)
}
interface SyncScheduler { fun enqueue(force: Boolean = false); fun ensurePeriodic() }
object RetryPolicy {
    // Stable operation-specific jitter; capped at six hours. WorkManager adds its own backoff.
    fun delayMs(operation: String, attempts: Int): Long =
        (30_000L * (1L shl (attempts - 1).coerceIn(0, 10))).coerceAtMost(6 * 60 * 60_000L) +
            (operation.hashCode().toLong() and 0x7fffffff) % 15_000
}
