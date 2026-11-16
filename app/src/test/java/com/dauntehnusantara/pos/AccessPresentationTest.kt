package com.dauntehnusantara.pos

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.auth.presentation.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28], application = android.app.Application::class, qualifiers = "w390dp-h844dp")
class AccessPresentationTest {
    val compose = createComposeRule()
    // Release resource APKs omit debug-only Compose activities. Register the host
    // activity in Robolectric before ActivityScenario starts; never in the app manifest.
    @get:Rule val rules: org.junit.rules.TestRule = org.junit.rules.RuleChain.outerRule(
        object : org.junit.rules.ExternalResource() {
            override fun before() {
                val app = org.robolectric.RuntimeEnvironment.getApplication()
                val component = android.content.ComponentName(app, androidx.activity.ComponentActivity::class.java)
                org.robolectric.Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(component)
            }
        }
    ).around(compose)
    private val membership = Membership("brand", "Daun Teh", "outlet", "Outlet A", Role.MANAGER)

    @Test fun loginMasksPasswordSubmitsUnmodifiedThenClearsIt() {
        var submitted: Pair<String,String>? = null
        compose.setContent { DaunTehTheme {
            AccessScreen(AuthState(AuthStage.LOGIN, configured = true), { e,p -> submitted = e to p }, {}, {}, {})
        } }
        compose.onNodeWithText("Email").performTextInput(" staff@example.invalid ")
        compose.onNodeWithText("Kata sandi").performTextInput(" secret with spaces ")
        compose.onNodeWithContentDescription("Tampilkan kata sandi").performClick()
        compose.onNodeWithText("Kata sandi").assertTextContains(" secret with spaces ")
        compose.onNodeWithContentDescription("Sembunyikan kata sandi").performClick()
        compose.onNode(hasText("Masuk") and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("staff@example.invalid" to " secret with spaces ", submitted) }
        compose.onNodeWithText("Kata sandi").assertTextContains("")
        compose.onNodeWithContentDescription("Tampilkan kata sandi").assertExists()
        compose.onNode(hasText("Masuk") and hasClickAction()).assertIsNotEnabled()
    }

    @Test fun invalidEmailStaysLocalAndDoesNotSubmit() {
        var calls = 0
        compose.setContent { DaunTehTheme {
            AccessScreen(AuthState(AuthStage.LOGIN, true), { _,_ -> calls++ }, {}, {}, {})
        } }
        compose.onNodeWithText("Email").performTextInput("invalid")
        compose.onNodeWithText("Kata sandi").performTextInput("secret")
        compose.onNode(hasText("Masuk") and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithText("Masukkan alamat email yang memuat @.").assertExists()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w1280dp-h800dp")
    fun tabletShowsBrandPanelWithBoundedForm() {
        compose.setContent { DaunTehTheme {
            AccessScreen(AuthState(AuthStage.LOGIN, true), { _,_ -> }, {}, {}, {})
        } }
        compose.onNodeWithText("Kelola outlet dengan lebih tenang.").assertIsDisplayed()
        compose.onNodeWithText("Email").assertWidthIsEqualTo(480.dp)
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w1280dp-h800dp")
    fun largeTextUsesSingleScrollingColumn() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                DaunTehTheme { AccessScreen(AuthState(AuthStage.LOGIN, true), { _,_ -> }, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("Kelola outlet dengan lebih tenang.").assertDoesNotExist()
        compose.onNode(hasText("Masuk") and hasClickAction()).performScrollTo().assertIsDisplayed()
    }

    @Test fun missingConfigurationNeverSubmits() {
        var calls = 0
        compose.setContent { DaunTehTheme {
            AccessScreen(AuthState(AuthStage.LOGIN), { _,_ -> calls++ }, {}, {}, {})
        } }
        compose.onNodeWithText("Email").performTextInput("staff@example.invalid")
        compose.onNodeWithText("Kata sandi").performTextInput("secret")
        compose.onNode(hasText("Masuk") and hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun pendingDeviceHasMatchingIdentityAndNoOfflineBypass() {
        var refreshes = 0
        compose.setContent { DaunTehTheme {
            AccessScreen(AuthState(AuthStage.LOCKED, true, message = AuthMessage.DEVICE_PENDING,
                pendingDeviceId = "public-device-id", lockedContext = membership), { _,_ -> }, {}, { refreshes++ }, {})
        } }
        compose.onNodeWithText("Outlet A · Pengelola").assertExists()
        compose.onNodeWithText("public-device-id").assertExists()
        compose.onNodeWithText("Minta pemilik meninjau ID perangkat ini.").assertExists()
        compose.onNodeWithText("Konfirmasi kunci layar").assertDoesNotExist()
        compose.onNodeWithText("Setujui perangkat ini").assertDoesNotExist()
        compose.onNodeWithText("Cek persetujuan").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun busyOutletSelectionCannotSubmitAnotherChoice() {
        var selections = 0
        compose.setContent { DaunTehTheme {
            AccessScreen(AuthState(AuthStage.OUTLETS, true, choices = listOf(membership), busy = true),
                { _,_ -> }, { selections++ }, {}, {})
        } }
        compose.onNode(hasText("Outlet A") and hasClickAction()).assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0, selections) }
    }

    @Test fun reviewerRequiresConfirmationAndDisablesRepeatSubmission() {
        val request = DeviceRequest("device-id", "user", "Nama Staf", "staff@example.invalid", Role.CASHIER,deviceName="Samsung SM-M146B")
        var state by mutableStateOf(AuthState(AuthStage.READY, true, membership = membership,
            deviceRequests = listOf(request), deviceRequestsLoaded = true))
        var approvals = 0
        compose.setContent { DaunTehTheme {
            DeviceRequestsPanel(state, {}, { approvals++; state = state.copy(busy = true) })
        } }
        compose.onNodeWithTag("device.request.name.device-id").assertTextEquals("Samsung SM-M146B")
        compose.onNodeWithText("Tinjau").performClick()
        compose.onNodeWithTag("device.approval.name").assertTextEquals("Samsung SM-M146B")
        compose.runOnIdle { assertEquals(0, approvals) }
        compose.onNodeWithText("Setujui perangkat ini").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, approvals) }
        compose.onNodeWithText("Memproses persetujuan…").assertIsNotEnabled()
        compose.onNodeWithText("Batal").assertIsNotEnabled()
        compose.runOnIdle { state = state.copy(busy = false, deviceRequests = emptyList()) }
        compose.onNodeWithText("Tidak ada permintaan perangkat.").assertExists()
        compose.onNodeWithText("Setujui perangkat?").assertDoesNotExist()
    }

    @Test
    @org.robolectric.annotation.Config(qualifiers = "w1280dp-h800dp")
    fun tabletApprovalCanBeCancelledWithoutCallingServer() {
        val request = DeviceRequest("device-id", "user", "Nama Staf", "staff@example.invalid", Role.CASHIER)
        var approvals = 0
        compose.setContent { DaunTehTheme {
            DeviceRequestsPanel(AuthState(AuthStage.READY, true, membership = membership,
                deviceRequests = listOf(request), deviceRequestsLoaded = true), {}, { approvals++ })
        } }
        compose.onNodeWithText("Tinjau").performClick()
        compose.onNodeWithText("Setujui perangkat?").assertIsDisplayed()
        compose.onNodeWithTag("device.approval.name").assertTextEquals("Nama perangkat belum tersedia")
        compose.onNodeWithText("Batal").performScrollTo().performClick()
        compose.onNodeWithText("Setujui perangkat?").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, approvals) }
    }

    @Test fun unloadedRequestsDoNotClaimEmptySuccess() {
        compose.setContent { DaunTehTheme {
            DeviceRequestsPanel(AuthState(AuthStage.READY, true, membership = membership), {}, {})
        } }
        compose.onNodeWithText("Tidak ada permintaan perangkat.").assertDoesNotExist()
        compose.onNodeWithText("Muat permintaan untuk melihat perangkat yang perlu ditinjau.").assertExists()
    }
}
