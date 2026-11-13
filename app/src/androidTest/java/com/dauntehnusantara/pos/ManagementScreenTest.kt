package com.dauntehnusantara.pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.reports.presentation.*
import com.dauntehnusantara.pos.feature.staff.presentation.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class ManagementScreenTest {
    @get:Rule val compose=createComposeRule()
    private val source=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    @Test fun reportLabelsAcceptedServerBasisAndEmptyCachedState() {
        val day=LocalDate.parse("2026-11-13")
        val report=SalesReport(ReportQuery(source,day,day,null),0,ReportSummary(0,0,0,0,0,0),emptyList(),emptyList(),emptyList(),listOf(OutletChoice("outlet","A",true)))
        compose.setContent { DaunTehTheme { ReportScreen(ReportState(source,report,from=day.toString(),to=day.toString()),load={_,_,_->},retry={},export={}) } }
        compose.onNodeWithText("Hanya penjualan yang diterima server.",substring=true).assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Snapshot tersimpan",substring=true))
        compose.onNodeWithText("Snapshot tersimpan",substring=true).assertIsDisplayed()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Belum ada penjualan diterima server",substring=true))
        compose.onNodeWithText("Belum ada penjualan diterima server",substring=true).assertIsDisplayed()
    }
    @Test fun managerCannotRestoreRevokedDeviceOrModifyPeerManager() {
        val scope=source.copy(role="MANAGER")
        val peer=StaffMember("peer","Peer","peer@example.invalid","MANAGER",true,true,1)
        val device=StaffDevice("revoked","cashier","CASHIER",true,false,1)
        compose.setContent { DaunTehTheme { StaffScreen(StaffState(scope,StaffRoster(scope,listOf(peer),listOf(device))),{}, {_,_->}, {_,_->}) } }
        compose.onNodeWithText("Nonaktifkan akses outlet").assertIsNotEnabled()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Pulihkan ke menunggu persetujuan"))
        compose.onNodeWithText("Pulihkan ke menunggu persetujuan").assertIsNotEnabled()
    }
    @Test fun accessChangeRequiresConfirmationAndReason() {
        val cashier=StaffMember("cashier","Cashier","cashier@example.invalid","CASHIER",true,true,1)
        compose.setContent { DaunTehTheme { StaffScreen(StaffState(source,StaffRoster(source,listOf(cashier),emptyList())),{}, {_,_->}, {_,_->}) } }
        compose.onNodeWithText("Nonaktifkan akses outlet").performClick()
        compose.onNodeWithText("Simpan perubahan akses").assertIsNotEnabled()
        compose.onNodeWithText("Alasan perubahan (3–240 karakter)").performTextInput("Reviewed access")
        compose.onNodeWithText("Simpan perubahan akses").assertIsEnabled()
    }
}
