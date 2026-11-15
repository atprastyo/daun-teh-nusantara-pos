package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.reports.presentation.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.*
import org.junit.Test
import org.junit.Assert.*

class ReportSessionTest {
    private val scope=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private val q=requireNotNull(reportQuery(scope,"2026-11-01","2026-11-15",null))
    @Test fun strictCalendarAndInclusiveRange() {
        listOf("2026-02-29","2026-2-01","2026-04-31"," 2026-11-01").forEach{assertNull(reportQuery(scope,it,"2026-11-15",null))}
        assertNotNull(reportQuery(scope,"2024-02-29","2025-02-28",null))
        assertNull(reportQuery(scope,"2024-02-29","2025-03-01",null))
        assertNotNull(reportQuery(scope,"2099-01-01","2099-01-01",null))
        assertNull(reportQuery(scope.copy(role="MANAGER"),"2026-11-01","2026-11-15",null))
        assertNull(reportQuery(scope.copy(role="CASHIER"),"2026-11-01","2026-11-15","outlet"))
    }
    @Test fun cancelRestoresAppliedAndObsoleteCallbacksCannotEditNewScope() {
        val session=ReportSession();session.applied(q);session.open(q);session.from("2026-10-01")
        assertTrue(session.dirty(q));session.cancel(q);assertFalse(session.dirty(q));assertFalse(session.state.value.editing)
        session.open(q);val generation=session.generation;session.clear();session.guard(generation){session.from("2026-01-01")}
        assertEquals(ReportDraft(),session.state.value)
    }
    @Test fun presetsReadCurrentJakartaClockAndDoNotApply() {
        val session=ReportSession();session.applied(q);session.open(q)
        session.preset(Clock.fixed(Instant.parse("2026-12-31T17:00:00Z"),ZoneOffset.UTC),ReportPreset.MONTH)
        assertEquals("2027-01-01",session.state.value.from);assertEquals("2027-01-01",session.state.value.to)
        assertTrue(session.state.value.editing);assertEquals("2026-11-01",q.from.toString())
        assertEquals("2026-12-26" to "2027-01-01",reportPreset(Clock.fixed(Instant.parse("2026-12-31T17:00:00Z"),ZoneOffset.UTC),ReportPreset.WEEK))
    }
    @Test fun unknownOutletNeverMasqueradesAsAllBrand() {
        assertEquals("Outlet pilihan · missing",reportOutletLabel("missing",emptyList(),"outlet","Current"))
        assertEquals("Old · nonaktif",reportOutletLabel("old",listOf(OutletChoice("old","Old",false)),"outlet","Current"))
    }
}
