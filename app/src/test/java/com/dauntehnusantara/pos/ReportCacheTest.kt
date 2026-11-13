package com.dauntehnusantara.pos

import android.app.Application
import androidx.room.Room
import com.dauntehnusantara.pos.core.database.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.reports.data.RoomReportRepository
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class ReportCacheTest {
    private lateinit var db:PosDatabase
    private val context get()=RuntimeEnvironment.getApplication()
    private val scope=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private val day=LocalDate.parse("2026-11-13");private val query=ReportQuery(scope,day,day,null)
    private val snapshot=SalesReport(query,1800000000000,ReportSummary(2,9000,0,9000,6000,3000),listOf(ReportDay(day,2,9000)),
        listOf(ReportOutlet("outlet","A",2,9000)),listOf(ReportProduct("product","Tea",2,9000)),listOf(OutletChoice("outlet","A",true)))
    private val gateway=object:ReportingGateway {
        var current:PullScope?=scope;var failure=false;var value=snapshot
        override suspend fun reportingScope(online:Boolean)=current
        override suspend fun report(query:ReportQuery):SalesReport {if(failure)throw ManagementFailure("UNAVAILABLE");return value}
    }
    private fun open(){db=Room.databaseBuilder(context,PosDatabase::class.java,"report-cache-test.db").addCallback(CatalogConstraints).build()}
    @Before fun before(){context.deleteDatabase("report-cache-test.db");open()}
    @After fun after(){db.close();context.deleteDatabase("report-cache-test.db")}
    @Test fun lastServerSnapshotSurvivesReopenAndRefreshFailureWithoutBecomingNewTotals():Unit=runBlocking {
        var repo=RoomReportRepository(db,gateway);assertNull(repo.cached(query));repo.refresh(query)
        db.close();open();repo=RoomReportRepository(db,gateway);assertEquals(snapshot,repo.cached(query))
        gateway.failure=true;try{repo.refresh(query);fail("Expected failure")}catch(_:ManagementFailure){}
        assertEquals(snapshot,repo.cached(query));assertTrue(repo.export(snapshot).contains("9000"))
    }
    @Test fun cacheAndExportCannotCrossAccountRoleOrBackend():Unit=runBlocking {
        val repo=RoomReportRepository(db,gateway);repo.refresh(query)
        gateway.current=scope.copy(sale=scope.sale.copy(userId="other"))
        try{repo.cached(query);fail("Original cache must be hidden")}catch(_:ManagementFailure){}
        val other=ReportQuery(requireNotNull(gateway.current),day,day,null);assertNull(repo.cached(other))
        try{repo.export(snapshot);fail("Original export must be denied")}catch(_:ManagementFailure){}
        gateway.current=scope.copy(project="https://other.invalid");assertNull(repo.cached(ReportQuery(requireNotNull(gateway.current),day,day,null)))
    }
    @Test fun invalidRemoteFinancialResponseCannotOverwriteLastGoodSnapshot():Unit=runBlocking {
        val repo=RoomReportRepository(db,gateway);repo.refresh(query)
        gateway.value=snapshot.copy(summary=snapshot.summary.copy(total=1))
        try{repo.refresh(query);fail("Invalid totals must fail")}catch(_:IllegalArgumentException){}
        assertEquals(snapshot,repo.cached(query))
    }
}
