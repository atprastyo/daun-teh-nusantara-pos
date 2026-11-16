package com.dauntehnusantara.pos

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.staff.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w390dp-h844dp")
class StaffPresentationTest {
    val compose=createComposeRule()
    @get:Rule val rules:TestRule=RuleChain.outerRule(object:ExternalResource(){
        override fun before(){val app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app,ComponentActivity::class.java))}
    }).around(compose)
    private val source=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private val cashier=StaffMember("cashier","Rani","rani@example.invalid","CASHIER",true,true,1)
    private val peer=cashier.copy(id="manager",name="Bima",role="MANAGER")
    private val revoked=StaffDevice("revoked","cashier","CASHIER",true,true,1,deviceName="Samsung SM-M146B")
    private fun ready()=StaffState(source,StaffRoster(source,listOf(cashier),listOf(revoked)))
    private fun list()=compose.onNodeWithTag("staff.list")
    @Test fun reasonBoundsCancelAndEffectSpecificConfirmation() {
        var writes=0
        compose.setContent{DaunTehTheme{StaffScreen(ready(),{}, {_,_->writes++}, {_,_->writes++})}}
        compose.onNodeWithText("Nonaktifkan akses outlet").performClick()
        compose.onNodeWithTag("staff.confirm").assertIsNotEnabled()
        compose.onNodeWithTag("staff.reason").performTextInput("x".repeat(241));compose.onNodeWithTag("staff.confirm").assertIsNotEnabled()
        compose.onNodeWithTag("staff.reason").performTextClearance();compose.onNodeWithTag("staff.reason").performTextInput("Reviewed access")
        compose.onNodeWithTag("staff.confirm").assertIsEnabled();compose.onNodeWithText("Batal").performClick()
        compose.runOnIdle{assertEquals(0,writes)}
    }
    @Test fun managerPeerAndRestoreRemainDisabledAndAuditAbsent() {
        val scope=source.copy(role="MANAGER")
        compose.setContent{DaunTehTheme{StaffScreen(ready().copy(scope=scope,roster=StaffRoster(scope,listOf(peer),listOf(revoked))),{}, {_,_->}, {_,_->})}}
        compose.onNodeWithText("Nonaktifkan akses outlet").assertIsNotEnabled()
        compose.onNodeWithText("Aktivitas akses").assertDoesNotExist()
        compose.onNodeWithText("Perangkat",substring=false).performClick()
        list().performScrollToNode(hasText("Pulihkan ke menunggu persetujuan"))
        compose.onNodeWithText("Pulihkan ke menunggu persetujuan").assertIsNotEnabled()
    }
    @Test fun revokedWinsOverApprovedAndUnknownRoleCannotRestore() {
        compose.setContent{DaunTehTheme{StaffScreen(ready().copy(roster=StaffRoster(source,emptyList(),listOf(revoked.copy(role=null)))),{}, {_,_->}, {_,_->})}}
        compose.onNodeWithText("Perangkat",substring=false).performClick()
        compose.onNodeWithText("Samsung SM-M146B").assertExists()
        compose.onNodeWithText("Perangkat dicabut").assertExists();compose.onNodeWithText("Perangkat disetujui").assertDoesNotExist()
        compose.onNodeWithText("Pulihkan ke menunggu persetujuan").assertIsNotEnabled()
    }
    @Test fun offlineDoesNotRenderOldDirectoryOrApprovalPanel() {
        compose.setContent{DaunTehTheme{StaffScreen(ready().copy(offline=true),{}, {_,_->}, {_,_->},approval={androidx.compose.material3.Text("Approval fixture")})}}
        compose.onNodeWithText("Rani").assertDoesNotExist();compose.onNodeWithText("Approval fixture").assertDoesNotExist()
        compose.onNodeWithText("Pengelolaan staf memerlukan koneksi.",substring=true).assertExists()
    }
    @Test fun readbackFailureExplainsAckAndBlocksRepeatAction() {
        compose.setContent{DaunTehTheme{StaffScreen(ready().copy(blocked=true,outcome=StaffOutcome.ACK_REFRESH_FAILED),{}, {_,_->}, {_,_->})}}
        compose.onNodeWithText("Perubahan diterima; daftar belum diperbarui.",substring=true).assertExists()
        list().performScrollToNode(hasText("Nonaktifkan akses outlet"));compose.onNodeWithText("Nonaktifkan akses outlet").assertIsNotEnabled()
    }
    @Test fun independentApprovalIsOnlyInDeviceSectionAndReviewerBlocksStaff() {
        var loads=0
        compose.setContent{DaunTehTheme{StaffScreen(ready(),{loads++}, {_,_->}, {_,_->},approval={androidx.compose.material3.Text("Approval fixture")},reviewerBusy=true)}}
        compose.onNodeWithText("Nonaktifkan akses outlet").assertIsNotEnabled();compose.onNodeWithText("Muat ulang staf").assertIsNotEnabled()
        compose.onNodeWithText("Approval fixture").assertDoesNotExist();compose.onNodeWithText("Perangkat",substring=false).performClick()
        list().performScrollToNode(hasText("Approval fixture"));compose.onNodeWithText("Approval fixture").assertIsDisplayed();compose.runOnIdle{assertEquals(0,loads)}
    }
    @Test @Config(qualifiers="w1200dp-h900dp") fun tabletLargeTextKeepsReasonAndActionsReachable() {
        compose.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){DaunTehTheme{StaffScreen(ready(),{}, {_,_->}, {_,_->})}}}
        compose.onNodeWithText("Nonaktifkan akses outlet").performScrollTo().performClick()
        compose.onNodeWithTag("staff.reason").performScrollTo().performTextInput("Reviewed")
        compose.onNodeWithTag("staff.confirm").assertIsDisplayed().assertIsEnabled();compose.onNodeWithText("Batal").assertIsDisplayed()
    }
}
