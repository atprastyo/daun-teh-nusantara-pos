package com.dauntehnusantara.pos

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.reports.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class ReportScreenTest {
    @get:Rule val compose=createComposeRule()
    private val source=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private val q=requireNotNull(reportQuery(source,"2026-11-15","2026-11-15",null))
    private val report=SalesReport(q,123,ReportSummary(0,0,0,0,0,0),emptyList(),emptyList(),emptyList(),listOf(OutletChoice("old","Old",false)))
    private fun ready()=ReportState(source,report,from=q.from.toString(),to=q.to.toString())
    private fun scroll(text:String)=compose.onNode(hasScrollAction()).performScrollToNode(hasText(text,substring=true))
    @Test fun draftCancelDoesNotLoadAndSnapshotRemainsApplied() {
        var loads=0;val session=ReportSession().also{it.applied(q)}
        compose.setContent{DaunTehTheme{ReportScreen(ready(),load={_,_,_->loads++},retry={},export={},session=session)}}
        compose.onNodeWithText("Ubah filter").performClick()
        compose.onNodeWithText("Dari (YYYY-MM-DD)",substring=true).performTextClearance()
        compose.onNodeWithText("Batal").performClick()
        compose.runOnIdle{assertEquals(0,loads);assertFalse(session.dirty(q))}
    }
    @Test fun absentSnapshotDoesNotShowFabricatedZeroOrExport() {
        compose.setContent{DaunTehTheme{ReportScreen(ready().copy(report=null,error="OFFLINE"),load={_,_,_->},retry={},export={})}}
        scroll("Belum ada snapshot");compose.onNodeWithText("Belum ada snapshot",substring=true).assertIsDisplayed()
        compose.onNodeWithText("Ekspor laporan CSV").assertDoesNotExist()
    }
    @Test fun overviewOpensActualReportDestinationWithoutExportAction() {
        var opened=0
        compose.setContent{DaunTehTheme{ReportScreen(ready(),load={_,_,_->},retry={},export={},overview=true,onOpenReport={opened++})}}
        scroll("Lihat laporan");compose.onNodeWithText("Lihat laporan").performClick();compose.runOnIdle{assertEquals(1,opened)}
        compose.onNodeWithText("Ekspor laporan CSV").assertDoesNotExist()
    }
    @Test fun pickerOutstandingDisablesNewExport() {
        compose.setContent{DaunTehTheme{ReportScreen(ready(),load={_,_,_->},retry={},export={},pickerOutstanding=true)}}
        scroll("Ekspor laporan CSV");compose.onNodeWithText("Ekspor laporan CSV").assertIsNotEnabled()
    }
    @Test fun managerHasFixedOutlet() {
        val mq=requireNotNull(reportQuery(source.copy(role="MANAGER"),"2026-11-15","2026-11-15","outlet"))
        val session=ReportSession().also{it.applied(mq)}
        compose.setContent{DaunTehTheme{ReportScreen(ready().copy(scope=mq.source,report=report.copy(query=mq),outlet="outlet",outletName="Outlet tetap"),load={_,_,_->},retry={},export={},session=session)}}
        compose.onNodeWithText("Ubah filter").performClick()
        compose.onNodeWithText("Semua outlet merek").assertDoesNotExist()
        compose.onNodeWithText("Outlet tetap").assertExists()
    }
    @Test fun largeTextKeepsFilterAndEmptyStateReachable() {
        compose.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){DaunTehTheme{ReportScreen(ready(),expanded=true,load={_,_,_->},retry={},export={})}}}
        compose.onNodeWithText("Ubah filter").performScrollTo().assertIsDisplayed()
        scroll("Belum ada penjualan diterima server");compose.onNodeWithText("Belum ada penjualan diterima server",substring=true).assertIsDisplayed()
    }
}
