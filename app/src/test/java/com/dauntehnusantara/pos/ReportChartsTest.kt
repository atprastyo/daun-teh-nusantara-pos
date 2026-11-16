package com.dauntehnusantara.pos

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.core.designsystem.formatIdr
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.reports.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.LocalDate
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w390dp-h844dp")
class ReportChartsTest {
    private val compose=createComposeRule()
    @get:Rule val rules:TestRule=RuleChain.outerRule(object:ExternalResource(){override fun before(){
        val app=RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app,ComponentActivity::class.java))
    }}).around(compose)
    private val query=ReportQuery(PullScope(SaleScope("b","o","d","u"),"OWNER","https://example.invalid"),LocalDate.parse("2026-11-01"),LocalDate.parse("2026-11-03"),null)
    private val report=SalesReport(query,123,ReportSummary(3,30000,0,30000,10000,20000),
        listOf(ReportDay(query.from,1,10000),ReportDay(query.to,2,20000)),listOf(ReportOutlet("o","Outlet",3,30000)),
        listOf(ReportProduct("p","Tea",3,30000)),emptyList())
    @Test fun daySelectionExposesExactAmountsWithoutInventingMissingDay() {
        compose.setContent {DaunTehTheme {DailySalesChart(report)}}
        compose.onNodeWithContentDescription("2026-11-01 · 1 penjualan · ${formatIdr(10000)}").performClick()
        compose.onNodeWithTag("report.chart.selection").assertTextEquals("2026-11-01 · 1 penjualan · ${formatIdr(10000)}")
        compose.onNodeWithContentDescription("2026-11-02",substring=true).assertDoesNotExist()
        compose.onNodeWithText("Transaksi").performClick()
        compose.onNodeWithTag("report.chart.selection").assertTextContains("${formatIdr(10000)}",substring=true)
    }
    @Test fun fullReportExpandsExactTablesAndDispatchesQuickPeriod() {
        var selected:ReportPreset?=null
        compose.setContent {DaunTehTheme {ReportScreen(
            ReportState(query.source,report,from=query.from.toString(),to=query.to.toString()),
            load={_,_,_->},retry={},export={},onQuickPreset={selected=it})}}
        compose.onNodeWithText("7 hari terakhir").performClick()
        assertEquals(ReportPreset.WEEK,selected)
        compose.onNodeWithTag("report.page").performScrollToNode(hasText("Lihat tabel harian"))
        compose.onNodeWithText("Lihat tabel harian").performClick()
        compose.onNodeWithTag("report.page").performScrollToNode(hasText("2026-11-01"))
        compose.onNodeWithText("2026-11-01").assertIsDisplayed()
        compose.onNodeWithTag("report.page").performScrollToNode(hasText("Lihat semua produk tersedia"))
        compose.onNodeWithText("Lihat semua produk tersedia").performClick()
        compose.onNodeWithTag("report.page").performScrollToNode(hasText("Tea · p"))
        compose.onNodeWithText("Tea · p").assertIsDisplayed()
    }
    @Test fun emptyPaymentsHaveNoNaNOrInventedPercent() {
        compose.setContent {DaunTehTheme {PaymentSalesChart(ReportSummary(0,0,0,0,0,0))}}
        compose.onNodeWithText("QRIS manual · ${formatIdr(0)} · —").assertExists()
    }
    @Test @Config(qualifiers="w1100dp-h800dp") fun productModeKeepsExactValuesAndScope() {
        compose.setContent {DaunTehTheme {Column(Modifier.verticalScroll(rememberScrollState())){ProductSalesChart(report)}}}
        compose.onNodeWithText("Unit").performClick()
        compose.onNodeWithText("3 unit · ${formatIdr(30000)}").assertExists()
        assertEquals(null,report.query.outletId)
    }
}
