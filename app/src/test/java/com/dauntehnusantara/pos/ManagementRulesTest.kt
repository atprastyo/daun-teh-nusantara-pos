package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.LocalDate
import java.time.Instant
import org.junit.Test
import org.junit.Assert.*

class ManagementRulesTest {
    private val source=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private val date=LocalDate.parse("2026-11-13")
    private fun report(total:Long=10000)=SalesReport(ReportQuery(source,date,date,null),1,ReportSummary(1,total,0,total,total,0),
        listOf(ReportDay(date,1,total)),listOf(ReportOutlet("outlet","=SUM(A1:A2)",1,total)),
        listOf(ReportProduct("product","Tea, \"Large\"\nFresh",1,total)),listOf(OutletChoice("outlet","Outlet",true)))
    private fun invalid(block:()->Unit){try{block();fail("Must reject inconsistent financial data")}catch(_:IllegalArgumentException){}}
    @Test fun reportDateBoundariesUseJakartaAndRangeIsBounded() {
        val q=ReportQuery(source,date,date,null)
        assertEquals(Instant.parse("2026-11-12T17:00:00Z").toEpochMilli(),q.startUtcMs)
        assertEquals(Instant.parse("2026-11-13T17:00:00Z").toEpochMilli(),q.endUtcMs)
        invalid{ReportQuery(source,date,date.minusDays(1),null)}
        invalid{ReportQuery(source,date,date.plusDays(366),null)}
        invalid{ReportQuery(source.copy(role="CASHIER"),date,date,"outlet")}
        invalid{ReportQuery(source.copy(role="MANAGER"),date,date,null)}
    }
    @Test fun allFinancialPartitionsMustReconcile() {
        val r=report();ReportRules.validate(r)
        invalid{ReportRules.validate(r.copy(summary=r.summary.copy(cash=9999)))}
        invalid{ReportRules.validate(r.copy(summary=r.summary.copy(discount=1)))}
        invalid{ReportRules.validate(r.copy(days=listOf(ReportDay(date,2,10000))))}
        invalid{ReportRules.validate(r.copy(outlets=listOf(ReportOutlet("outlet","A",1,9999))))}
        invalid{ReportRules.validate(r.copy(products=listOf(ReportProduct("p","Bad",1,10001))))}
    }
    @Test fun duplicateDaysAndForeignManagerOutletsAreRejected() {
        val r=report()
        invalid{ReportRules.validate(r.copy(days=r.days+r.days))}
        val manager=r.copy(query=ReportQuery(source.copy(role="MANAGER"),date,date,"outlet"))
        ReportRules.validate(manager)
        invalid{ReportRules.validate(manager.copy(outlets=listOf(ReportOutlet("foreign","Foreign",1,10000))))}
    }
    @Test fun integerIdrExportPreservesLongPrecisionAndDetectsOverflow() {
        val r=report(Long.MAX_VALUE);ReportRules.validate(r)
        assertTrue(ReportCsv.render(r).contains(Long.MAX_VALUE.toString()))
        try{ReportRules.validate(r.copy(summary=r.summary.copy(qris=1)));fail("Overflow must be detected")}catch(_:ArithmeticException){}
    }
    @Test fun csvQuotesAndFormulaPrefixesAreSafeWithoutChangingStoredSnapshots() {
        val r=report();val csv=ReportCsv.render(r)
        assertTrue(csv.contains("\"'=SUM(A1:A2)\""))
        assertTrue(csv.contains("\"Tea, \"\"Large\"\"\nFresh\""))
        assertTrue(ReportCsv.cell("  +1").startsWith("\"'"));assertTrue(ReportCsv.cell("\t=1").startsWith("\"'"))
        assertEquals("=SUM(A1:A2)",r.outlets.single().name)
    }
    @Test fun staffPermissionsSeparateAccountProvisioningFromOutletAndDeviceActions() {
        val manager=source.copy(role="MANAGER")
        val cashier=StaffMember("cashier","Cashier","cashier@example.invalid","CASHIER",true,true,1)
        val peerManager=cashier.copy(id="manager",role="MANAGER")
        assertTrue(StaffRules.mayChange(manager,cashier));assertFalse(StaffRules.mayChange(manager,peerManager))
        assertFalse(StaffRules.mayChange(source,cashier.copy(id="owner")))
        assertTrue(StaffRules.mayChange(source,peerManager))
        val device=StaffDevice("other","cashier","CASHIER",false,true,1)
        assertTrue(StaffRules.mayRevoke(manager,device));assertFalse(StaffRules.mayRestore(manager,device.copy(revoked=true)))
        assertTrue(StaffRules.mayRestore(source,device.copy(revoked=true)))
        assertFalse(StaffRules.mayRevoke(source,device.copy(id="device")))
        invalid{StaffRules.reason("x")}
    }
}
