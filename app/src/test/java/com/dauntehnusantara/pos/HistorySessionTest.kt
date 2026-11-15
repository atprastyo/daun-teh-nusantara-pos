package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.history.presentation.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class HistorySessionTest {
    private val category=Category(RecordInfo(brandId=DEMO_BRAND_ID,name="Tea"))
    private val product=Product(RecordInfo(brandId=DEMO_BRAND_ID,name="Original Melati"),category.info.id,6000)
    private fun receipt(at:String,id:String=newId(),method:PaymentMethod=PaymentMethod.CASH,total:Long=6000):Receipt {
        val menu=Catalog(categories=listOf(category),products=listOf(product.copy(basePriceIdr=total)))
        return Receipt(id,newId(),DEMO_BRAND_ID,"outlet","device","cashier",Instant.parse(at).toEpochMilli(),
            listOf(CheckoutRules.line(menu,product.info.id,emptyList(),1)),total,
            CheckoutRules.payment(total,if(method==PaymentMethod.CASH)PaymentInput(method,total)else PaymentInput(method,manuallyVerified=true)))
    }
    private val today=LocalDate.of(2027,1,1)
    @Test fun jakartaMidnightAndYearBoundaryIgnoreClockDeviceZone() {
        val before=receipt("2026-12-31T16:59:59.999Z");val midnight=receipt("2026-12-31T17:00:00Z");val next=receipt("2027-01-01T17:00:00Z")
        val state=HistoryState(receipts=listOf(next,midnight,before),today=today)
        assertEquals(listOf(midnight),historyRows(state,HistoryFilters(day=HistoryDay.TODAY)))
        assertEquals(listOf(before),historyRows(state,HistoryFilters(day=HistoryDay.YESTERDAY)))
        assertEquals(today,historyToday(Clock.fixed(Instant.parse("2026-12-31T17:00:00Z"),ZoneOffset.ofHours(-8))))
    }
    @Test fun tickTargetsMidnightAndCapsLongWaitForClockChanges() {
        assertEquals(1L,historyTickDelay(Clock.fixed(Instant.parse("2026-12-31T16:59:59.999Z"),ZoneOffset.UTC)))
        assertEquals(60000L,historyTickDelay(Clock.fixed(Instant.parse("2026-12-31T17:00:00Z"),ZoneOffset.UTC)))
    }
    @Test fun uuidAndSnapshotNameSearchAreTrimmedCaseInsensitiveAndIntersectStatusAndDate() {
        val r=receipt("2026-12-31T17:01:00Z")
        val state=HistoryState(receipts=listOf(r),deliveries=mapOf(r.id to ReceiptDelivery(ReceiptDeliveryState.REVIEW,true)),today=today)
        assertEquals(listOf(r),historyRows(state,HistoryFilters(query="  ORIGINAL MELATI  ",day=HistoryDay.TODAY,status=ReceiptDeliveryState.REVIEW)))
        assertEquals(listOf(r),historyRows(state,HistoryFilters(query=r.id.uppercase())))
        assertTrue(historyRows(state,HistoryFilters(query="melati",status=ReceiptDeliveryState.SYNCED)).isEmpty())
        assertTrue(historyRows(state,HistoryFilters(query="melati",day=HistoryDay.YESTERDAY)).isEmpty())
    }
    @Test fun absentProofIsUnknownRegardlessOfOtherReceiptProof() {
        val a=receipt("2026-12-31T17:01:00Z");val b=receipt("2026-12-31T18:01:00Z")
        val state=HistoryState(receipts=listOf(b,a),deliveries=mapOf(b.id to ReceiptDelivery(ReceiptDeliveryState.SYNCED)),today=today)
        assertEquals(listOf(a),historyRows(state,HistoryFilters(status=ReceiptDeliveryState.UNKNOWN)))
        assertEquals(listOf(b),historyRows(state,HistoryFilters(status=ReceiptDeliveryState.SYNCED)))
    }
    @Test fun selectorsKeepTieOrderAndDoNotRoundZeroOrLongAmounts() {
        val zero=receipt("2026-12-31T17:01:00Z",total=0);val large=receipt("2026-12-31T17:01:00Z",total=Long.MAX_VALUE)
        val state=HistoryState(receipts=listOf(large,zero),today=today)
        assertEquals(listOf(large,zero),historyRows(state,HistoryFilters()));assertEquals(Long.MAX_VALUE,large.totalIdr);assertEquals(0L,zero.totalIdr)
    }
    @Test fun filterEvictionClearsOnlySelectionAndClosePreservesInputsScrollAndSnapshots() {
        val r=receipt("2026-12-31T17:01:00Z");val snapshot=HistoryState(receipts=listOf(r),today=today)
        val session=HistorySession();session.search("melati");session.select(r.id);session.scroll(8,30);session.close()
        assertEquals("melati",session.state.value.query);assertEquals(8,session.state.value.scrollIndex);assertNull(session.state.value.selectedId)
        session.select(r.id);session.status(ReceiptDeliveryState.REVIEW);session.reconcile(snapshot)
        assertNull(session.state.value.selectedId);assertTrue(session.state.value.selectionEvicted);assertEquals(listOf(r),snapshot.receipts)
        session.clearFilters();assertEquals(0,session.state.value.scrollIndex)
    }
    @Test fun boundedSnapshotEvictionCannotSilentlySelectAnotherReceipt() {
        val a=receipt("2026-12-31T17:01:00Z");val b=receipt("2026-12-31T18:01:00Z")
        val session=HistorySession();session.select(a.id);session.reconcile(HistoryState(receipts=listOf(b)))
        assertNull(session.state.value.selectedId);assertTrue(session.state.value.selectionEvicted)
        session.clear();assertEquals(HistoryFilters(),session.state.value)
    }

    @Test fun lateOldScopeUiCallbacksCannotRestoreQuerySelectionOrScroll() {
        val session=HistorySession();val old=session.generation
        session.search("Old");session.clear()
        session.guard(old){session.search("Must not restore");session.select("old-id");session.scroll(50,100)}
        assertEquals(HistoryFilters(),session.state.value)
        session.guard(session.generation){session.search("Current")};assertEquals("Current",session.state.value.query)
    }
}
