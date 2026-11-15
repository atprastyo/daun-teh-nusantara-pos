package com.dauntehnusantara.pos

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme

import com.dauntehnusantara.pos.feature.settings.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith

@RunWith(androidx.test.ext.junit.runners.AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose=createComposeRule()
    private val source=PullScope(SaleScope("brand","outlet","device","cashier"),"CASHIER","https://example.invalid")
    private fun ready()=SettingsState(scope=source,email="cashier@example.invalid",outletName="Outlet",loading=false)
    @Test fun signoutUnknownQueueWarnsAndCancelNeverSignsOut() {
        var exits=0;var cancelled=0
        compose.setContent{DaunTehTheme{SettingsScreen(ready().copy(signOutConfirm=true),onCancel={cancelled++},onConfirmSignOut={exits++})}}
        compose.onNodeWithText("Keadaan antrean belum dapat dipastikan.").assertExists();compose.onNodeWithText("Batal").performClick()
        compose.runOnIdle{assertEquals(0,exits);assertEquals(1,cancelled)}
    }
    @Test fun exportExplanationCancelDoesNotCapture() {
        var captures=0
        compose.setContent{DaunTehTheme{SettingsScreen(ready().copy(exportConfirm=true),onPrepare={captures++})}}
        compose.onNodeWithText("Maksimal 16 MiB",substring=true).assertExists();compose.onNodeWithText("Batal").performClick()
        compose.runOnIdle{assertEquals(0,captures)}
    }
    @Test fun restoredBranchKeepsExportAndBlocksDeliveryRetry() {
        compose.setContent{DaunTehTheme{SettingsScreen(ready(),SyncStatus(pending=2),PullStatus(error="REBOOTSTRAP_REQUIRED"))}}
        compose.onNodeWithText("Jangan hapus data aplikasi",substring=true).assertExists()
        compose.onNodeWithText("Coba pengiriman ulang").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Ekspor data perangkat").performScrollTo().assertIsEnabled()
        compose.onNodeWithText("Reset data").assertDoesNotExist()
    }
    @Test fun largeCountsNeverOverflowAndScheduledIsNotDelivered() {
        compose.setContent{DaunTehTheme{SettingsScreen(ready(),SyncStatus(pending=Int.MAX_VALUE,retrying=Int.MAX_VALUE),PullStatus(lastSuccessMs=123),scheduled=true)}}
        compose.onNodeWithText("4294967294").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Bukan waktu unggah atau cadangan.").assertExists()
        compose.onNodeWithText("Pengiriman dijadwalkan",substring=true).assertExists()
        compose.onNodeWithText("Arsip selesai",substring=true).assertDoesNotExist()
    }
    @Test fun offlineDisablesNetworkButPermitsAuthorizedLocalExport() {
        compose.setContent{DaunTehTheme{SettingsScreen(ready().copy(offline=true),SyncStatus(),PullStatus())}}
        compose.onNodeWithText("Validasi akses online").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Coba pengiriman ulang").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Ekspor data perangkat").performScrollTo().assertIsEnabled()
    }
    @Test fun pickerBlocksNewExportAndSignoutAndReadFailureNeverShowsZero() {
        compose.setContent{DaunTehTheme{SettingsScreen(ready(),readFailed=true,outstandingPicker=true)}}
        compose.onNodeWithText("Jumlah belum tersedia.",substring=true).assertExists()
        compose.onNodeWithText("Ekspor data perangkat").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Keluar dari akun").performScrollTo().assertIsNotEnabled()
        compose.onAllNodesWithText("0",substring=false).assertCountEquals(0)
    }
    @Test fun largeTextConfirmationKeepsActionsReachable() {
        compose.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){DaunTehTheme{SettingsScreen(ready().copy(exportConfirm=true))}}}
        compose.onNodeWithTag("settings.confirm").assertIsDisplayed().assertIsEnabled();compose.onNodeWithText("Batal").assertIsDisplayed()
    }
}
