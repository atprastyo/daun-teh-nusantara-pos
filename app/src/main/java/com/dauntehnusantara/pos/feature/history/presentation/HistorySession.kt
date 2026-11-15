package com.dauntehnusantara.pos.feature.history.presentation

import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullStatus
import java.time.*
import java.util.Locale
import kotlinx.coroutines.flow.*

val HISTORY_ZONE: ZoneId = ZoneId.of("Asia/Jakarta")
enum class HistoryDay { ALL, TODAY, YESTERDAY }
enum class HistoryRefresh { NONE, LOCAL, PULL, MORE, FAILED }
data class HistoryState(
    val scopeKey: String = "locked", val receipts: List<Receipt> = emptyList(),
    val deliveries: Map<String, ReceiptDelivery> = emptyMap(), val loading: Boolean = true,
    val loadFailed: Boolean = false, val refreshing: Boolean = false,
    val refresh: HistoryRefresh = HistoryRefresh.NONE, val pull: PullStatus = PullStatus(),
    val offline: Boolean = false, val allowed: Boolean = false,
    val today: LocalDate = LocalDate.now(HISTORY_ZONE), val demo: Boolean = false,
)
data class HistoryFilters(val query: String = "", val day: HistoryDay = HistoryDay.ALL,
    val status: ReceiptDeliveryState? = null, val selectedId: String? = null,
    val selectionEvicted: Boolean = false, val scrollIndex: Int = 0, val scrollOffset: Int = 0)

/** Select only the already authorized bounded cache; keep repository order and snapshots. */
fun historyRows(state: HistoryState, filters: HistoryFilters): List<Receipt> {
    val query = filters.query.trim().lowercase(Locale.forLanguageTag("id"))
    val target = when(filters.day) { HistoryDay.ALL -> null; HistoryDay.TODAY -> state.today; HistoryDay.YESTERDAY -> state.today.minusDays(1) }
    return state.receipts.filter { receipt ->
        (query.isEmpty() || receipt.id.lowercase(Locale.forLanguageTag("id")).contains(query) ||
            receipt.lines.any { it.productName.lowercase(Locale.forLanguageTag("id")).contains(query) }) &&
            (target == null || Instant.ofEpochMilli(receipt.createdAtUtcMs).atZone(HISTORY_ZONE).toLocalDate() == target) &&
            (filters.status == null || (state.deliveries[receipt.id]?.state ?: ReceiptDeliveryState.UNKNOWN) == filters.status)
    }
}
fun historyToday(clock: Clock): LocalDate = clock.instant().atZone(HISTORY_ZONE).toLocalDate()
fun historyTickDelay(clock: Clock): Long {
    val now = clock.instant()
    val midnight = now.atZone(HISTORY_ZONE).toLocalDate().plusDays(1).atStartOfDay(HISTORY_ZONE).toInstant()
    return Duration.between(now, midnight).toMillis().coerceIn(1, 60_000)
}

/** Same-scope in-memory presentation only. No sale/cart/authorization mutation callbacks. */
class HistorySession {
    private val mutable = MutableStateFlow(HistoryFilters())
    val state = mutable.asStateFlow()
    var generation: Long = 0
        private set
    fun clear() { generation++; mutable.value = HistoryFilters() }
    fun guard(token:Long,block:()->Unit) { if(token==generation)block() }
    fun search(value: String) { mutable.update { it.copy(query=value,scrollIndex=0,scrollOffset=0) } }
    fun day(value: HistoryDay) { mutable.update { it.copy(day=value,scrollIndex=0,scrollOffset=0) } }
    fun status(value: ReceiptDeliveryState?) { mutable.update { it.copy(status=value,scrollIndex=0,scrollOffset=0) } }
    fun clearFilters() { mutable.update { it.copy(query="",day=HistoryDay.ALL,status=null,scrollIndex=0,scrollOffset=0) } }
    fun select(id: String) { mutable.update { it.copy(selectedId=id,selectionEvicted=false) } }
    fun close() { mutable.update { it.copy(selectedId=null,selectionEvicted=false) } }
    fun scroll(index: Int, offset: Int) { mutable.update { it.copy(scrollIndex=index,scrollOffset=offset) } }
    fun reconcile(snapshot: HistoryState) {
        val selected = mutable.value.selectedId ?: return
        if(historyRows(snapshot,mutable.value).none { it.id == selected })
            mutable.update { it.copy(selectedId=null,selectionEvicted=true) }
    }
}
