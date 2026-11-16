package com.dauntehnusantara.pos

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.auth.presentation.AuthenticatedShell
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, qualifiers = "w390dp-h844dp")
class AuthenticatedShellTest {
    val compose = createComposeRule()
    @get:Rule val rules: TestRule = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            val app = RuntimeEnvironment.getApplication()
            Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app, ComponentActivity::class.java))
        }
    }).around(compose)

    private val store = androidx.lifecycle.ViewModelStore()
    @org.junit.After fun clearModels() { store.clear() }

    private fun state(role: Role = Role.CASHIER) = AuthState(AuthStage.READY, true,
        membership = Membership("brand", "Daun Teh", "outlet-a", "Outlet A", role), email = "staff@example.invalid")
    private fun headerText(value:String)=compose.onNode(hasText(value) and hasAnyAncestor(hasTestTag("shell.appbar")))
    private fun nav(label: String) = compose.onNode(hasText(label) and hasClickAction())
    private fun show(state: AuthState, switch: () -> Unit = {}) {
        compose.setContent { DaunTehTheme {
            AuthenticatedShell(state, switch) { destination, expanded -> Text("feature.${destination.name}.$expanded") }
        } }
    }

    @Test fun compactCashierHasOnlyPermittedDestinationsAndStartsAtPos() {
        show(state())
        compose.onNodeWithTag("shell.bottom").assertIsDisplayed()
        compose.onNodeWithTag("shell.rail").assertDoesNotExist()
        nav("Kasir").assertIsSelected()
        compose.onNodeWithText("feature.POS.false").assertIsDisplayed()
        nav("Riwayat").performClick()
        compose.onNodeWithText("feature.HISTORY.false").assertIsDisplayed()
        nav("Riwayat").assertIsSelected()
        listOf("Ringkasan", "Katalog", "Laporan", "Staf").forEach { nav(it).assertDoesNotExist() }
        nav("Setelan").assertExists()
    }

    private fun openMenu()=compose.onNodeWithContentDescription("Buka menu navigasi").performClick()
    @Test fun compactOwnerUsesReadableMenuAndCanEnterCashierHistoryAndManagement() {
        show(state(Role.OWNER))
        compose.onNodeWithTag("shell.bottom").assertDoesNotExist()
        compose.onNodeWithText("feature.OVERVIEW.false").assertIsDisplayed()
        headerText("Pemilik").assertIsDisplayed()
        headerText("Outlet A").assertIsDisplayed()
        openMenu()
        listOf("Ringkasan","Kasir","Riwayat","Katalog","Laporan","Staf","Setelan").forEach { nav(it).performScrollTo().assertIsDisplayed() }
        nav("Kasir").performScrollTo().performClick()
        compose.onNodeWithText("feature.POS.false").assertIsDisplayed()
        openMenu();nav("Riwayat").performScrollTo().performClick()
        compose.onNodeWithText("feature.HISTORY.false").assertIsDisplayed()
        openMenu();nav("Staf").performScrollTo().performClick()
        compose.onNodeWithText("feature.STAFF.false").assertIsDisplayed()
    }

    @Test fun drawerShowsBrandThenScopedUserAndDoesNotInventProfileName() {
        show(state(Role.OWNER));openMenu()
        compose.onNodeWithTag("shell.brand.logo").assertIsDisplayed()
        compose.onNodeWithText("Daun Teh").assertIsDisplayed()
        compose.onNodeWithTag("shell.user.email").assertTextEquals("staff@example.invalid").assertIsDisplayed()
        val brand=compose.onNodeWithText("Daun Teh").fetchSemanticsNode().boundsInRoot
        val user=compose.onNodeWithTag("shell.user.email").fetchSemanticsNode().boundsInRoot
        assertTrue(brand.bottom<=user.top)
    }
    @Test fun darkDrawerWrapsLongIdentityAndKeepsDestinationsReachable() {
        val current=state(Role.OWNER).let{it.copy(email="long.staff.identity@example.invalid",membership=it.membership!!.copy(brandName="Daun Teh Nusantara"))}
        compose.setContent{DaunTehTheme(darkTheme=true){AuthenticatedShell(current,{}){d,e->Text("feature.${d.name}.$e")}}}
        openMenu()
        compose.onNodeWithTag("shell.brand.logo").assertIsDisplayed()
        compose.onNodeWithText("Daun Teh Nusantara").assertIsDisplayed()
        compose.onNodeWithTag("shell.user.email").assertTextEquals(current.email).assertIsDisplayed()
        nav("Setelan").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("feature.SETTINGS.false").assertIsDisplayed()
    }
    @Test fun accessIconExplainsAuthorizationSeparatelyFromSync() {
        show(state())
        compose.onNodeWithContentDescription("Akses tervalidasi").performClick()
        compose.onNodeWithText("Status ini menunjukkan validasi akses akun, bukan status sinkronisasi penjualan.").assertIsDisplayed()
        compose.onNodeWithText("Tutup informasi akses").performClick()
        compose.onNodeWithText("feature.POS.false").assertIsDisplayed()
    }
    @Test fun outletSwitchIsBesideNameAndBeforeStatus() {
        show(state(Role.OWNER))
        val name=compose.onNodeWithTag("shell.outlet.name",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val button=compose.onNodeWithTag("shell.outlet.switch").fetchSemanticsNode().boundsInRoot
        val status=compose.onNodeWithTag("shell.access").fetchSemanticsNode().boundsInRoot
        assertTrue(button.left>=name.right);assertTrue(button.left-name.right<40f)
        assertTrue(button.right<=status.left)
    }
    @Test fun themeRecompositionPreservesDestinationAndInProgressField() {
        var dark by mutableStateOf(false)
        compose.setContent{DaunTehTheme(darkTheme=dark){AuthenticatedShell(state(),{}){d,_->
            var draft by androidx.compose.runtime.saveable.rememberSaveable{mutableStateOf("")}
            androidx.compose.foundation.layout.Column {
                Text("destination.${d.name}")
                androidx.compose.material3.OutlinedTextField(draft,{draft=it},label={Text("Draf tema")})
            }
        }}}
        nav("Riwayat").performClick();compose.onNodeWithText("Draf tema").performTextInput("retained")
        compose.runOnIdle{dark=true}
        compose.onNodeWithText("destination.HISTORY").assertIsDisplayed()
        compose.onNodeWithText("Draf tema").assertTextContains("retained")
    }
    @Test fun headerSwitchInvokesExistingActionAndDoesNotLocallyChangeOutlet() {
        var switches = 0
        show(state(Role.MANAGER), { switches++ })
        headerText("Outlet A").assertIsDisplayed()
        headerText("Pengelola").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pilih outlet lain").performClick()
        compose.runOnIdle { assertEquals(1, switches) }
        headerText("Outlet A").assertIsDisplayed()
        compose.onNodeWithText("feature.OVERVIEW.false").assertExists()
    }

    @Test fun offlineAndBusyCannotSwitchAndAccessLabelDoesNotClaimSync() {
        var current by mutableStateOf(state().copy(offline = true))
        var switches = 0
        compose.setContent { DaunTehTheme { AuthenticatedShell(current, { switches++ }) { _,_ -> Text("content") } } }
        compose.onNodeWithText("Akses offline").assertDoesNotExist()
        compose.onNodeWithContentDescription("Akses offline").assertIsDisplayed().performClick()
        compose.onNodeWithText("Akses offline").assertIsDisplayed()
        compose.onNodeWithText("Hubungkan kembali untuk memilih outlet lain.").assertExists()
        compose.onNodeWithContentDescription("Pilih outlet lain").assertIsNotEnabled()
        compose.onNodeWithText("Tersinkron", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Tutup informasi akses").performClick()
        compose.runOnIdle { current = current.copy(offline = false, busy = true) }
        compose.onNodeWithContentDescription("Pilih outlet lain").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Memvalidasi akses").assertIsDisplayed()
        compose.runOnIdle { current = current.copy(busy=false) }
        compose.onNodeWithContentDescription("Akses tervalidasi").assertIsDisplayed()
        compose.onNodeWithText("Akses tervalidasi").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, switches) }
    }

    @Test fun roleOutletAndAccountChangesResetSelection() {
        var current by mutableStateOf(state(Role.OWNER))
        compose.setContent { DaunTehTheme { AuthenticatedShell(current, {}) { d,e -> Text("feature.${d.name}.$e") } } }
        openMenu();nav("Staf").performScrollTo().performClick()
        compose.runOnIdle { current = current.copy(membership = current.membership!!.copy(outletId = "outlet-b", outletName = "Outlet B")) }
        headerText("Outlet B").assertIsDisplayed()
        compose.onNodeWithText("feature.OVERVIEW.false").assertExists()
        openMenu();nav("Laporan").performScrollTo().performClick()
        compose.runOnIdle { current = current.copy(email = "other@example.invalid") }
        compose.onNodeWithText("feature.OVERVIEW.false").assertExists()
        openMenu();nav("Staf").performScrollTo().performClick()
        compose.runOnIdle { current = current.copy(membership = current.membership!!.copy(role = Role.CASHIER)) }
        compose.onNodeWithText("feature.POS.false").assertExists()
        compose.onNodeWithText("feature.STAFF.false").assertDoesNotExist()
        nav("Laporan").assertDoesNotExist()
    }

    @Test fun allowedDestinationRestoresAfterRecreation() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { DaunTehTheme { AuthenticatedShell(state(), {}) { d,e -> Text("feature.${d.name}.$e") } } }
        nav("Riwayat").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("feature.HISTORY.false").assertIsDisplayed()
        nav("Riwayat").assertIsSelected()
    }

    @Test fun savedManagementDestinationCannotRestoreIntoCashierContext() {
        val restoration = StateRestorationTester(compose)
        var role = Role.OWNER
        restoration.setContent { DaunTehTheme { AuthenticatedShell(state(role), {}) { d,e -> Text("feature.${d.name}.$e") } } }
        openMenu();nav("Staf").performScrollTo().performClick()
        compose.runOnIdle { role = Role.CASHIER }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("feature.POS.false").assertIsDisplayed()
        compose.onNodeWithText("feature.STAFF.false").assertDoesNotExist()
        nav("Staf").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w600dp-h900dp")
    fun mediumUsesRailAndKeepsFeatureCompact() {
        show(state(Role.MANAGER))
        compose.onNodeWithTag("shell.rail").assertIsDisplayed()
        compose.onNodeWithTag("shell.bottom").assertDoesNotExist()
        compose.onNodeWithText("feature.OVERVIEW.false").assertExists()
        nav("Katalog").performClick()
        compose.onNodeWithText("feature.CATALOG.false").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w840dp-h900dp")
    fun expandedPassesExistingExpandedFlagToFeatures() {
        show(state(Role.OWNER))
        compose.onNodeWithTag("shell.rail").assertIsDisplayed()
        compose.onNodeWithText("feature.OVERVIEW.true").assertIsDisplayed()
    }

    @Test fun largeTextCanReachEveryOwnerDestinationViaDrawer() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                DaunTehTheme { AuthenticatedShell(state(Role.OWNER), {}) { d,e -> Text("feature.${d.name}.$e") } }
            }
        }
        compose.onNodeWithTag("shell.bottom").assertDoesNotExist()
        compose.onNodeWithTag("shell.rail").assertDoesNotExist()
        compose.onNodeWithContentDescription("Buka menu navigasi").performClick()
        nav("Setelan").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("feature.SETTINGS.false").assertExists()
        compose.onNodeWithContentDescription("Buka menu navigasi").performClick()
        listOf("Ringkasan", "Kasir", "Riwayat", "Katalog", "Laporan", "Staf").forEach { nav(it).performScrollTo().assertIsDisplayed() }
    }

    @Test
    @Config(qualifiers = "w700dp-h360dp")
    fun shortWindowUsesScrollingMenuAndKeepsOutletActionReachable() {
        show(state(Role.OWNER))
        compose.onNodeWithTag("shell.rail").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pilih outlet lain").assertIsDisplayed()
        compose.onNodeWithContentDescription("Buka menu navigasi").performClick()
        nav("Setelan").performScrollTo().performClick()
        compose.onNodeWithText("feature.SETTINGS.false").assertExists()
    }

    @Test fun navigationLayoutChangesKeepInProgressFeatureInput() {
        var fontScale by mutableStateOf(1f)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                DaunTehTheme { AuthenticatedShell(state(), {}) { _,_ ->
                    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
                    androidx.compose.material3.OutlinedTextField(query, { query = it }, label = { Text("Cari di fitur") })
                } }
            }
        }
        compose.onNodeWithText("Cari di fitur").performTextInput("teh melati")
        compose.runOnIdle { fontScale = 2f }
        compose.onNodeWithContentDescription("Buka menu navigasi").assertIsDisplayed()
        compose.onNodeWithText("Cari di fitur").assertTextContains("teh melati")
        compose.runOnIdle { fontScale = 1f }
        compose.onNodeWithTag("shell.bottom").assertIsDisplayed()
        compose.onNodeWithText("Cari di fitur").assertTextContains("teh melati")
    }

    @Test fun authRootHeaderReusesRepositoryOutletSelectionFlow() {
        val initial = state(Role.OWNER)
        val repository = object : AuthRepository {
            override val state = kotlinx.coroutines.flow.MutableStateFlow(initial)
            var switches = 0
            override suspend fun restore() {}
            override suspend fun signIn(email: String, password: String) {}
            override suspend fun selectOutlet(outletId: String) {}
            override suspend fun chooseOutlets() {
                switches++
                state.value = AuthState(AuthStage.OUTLETS, true, choices = listOf(requireNotNull(initial.membership)))
            }
            override suspend fun refresh() {}
            override suspend fun unlockOffline() {}
            override suspend fun enforceLease() {}
            override suspend fun loadDeviceRequests() {}
            override suspend fun approveDevice(id: String) {}
            override suspend fun signOut() {}
        }
        val model = androidx.lifecycle.ViewModelProvider(store, object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return com.dauntehnusantara.pos.feature.auth.presentation.AuthViewModel(AuthUseCases(repository),
                    object : com.dauntehnusantara.pos.core.common.NetworkAvailability {
                        override val validated = kotlinx.coroutines.flow.emptyFlow<Boolean>()
                    }, object : com.dauntehnusantara.pos.feature.sync.domain.SyncScheduler {
                        override fun enqueue(force: Boolean) {}
                        override fun ensurePeriodic() {}
                    }) as T
            }
        })[com.dauntehnusantara.pos.feature.auth.presentation.AuthViewModel::class.java]
        compose.setContent { DaunTehTheme {
            com.dauntehnusantara.pos.feature.auth.presentation.AuthRoot(model,
                authenticated = { d,e -> Text("feature.${d.name}.$e") }, demo = {})
        } }
        compose.onNodeWithText("feature.OVERVIEW.false").assertExists()
        compose.onNodeWithContentDescription("Pilih outlet lain").performClick()
        compose.onNodeWithText("Pilih outlet").assertIsDisplayed()
        compose.onNodeWithTag("shell.bottom").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, repository.switches) }
    }

    @Test fun ownerCashierSelectionSurvivesSameContextRefreshAndRestoration() {
        val restoration=StateRestorationTester(compose)
        var current by mutableStateOf(state(Role.OWNER))
        restoration.setContent{DaunTehTheme{AuthenticatedShell(current,{}){d,e->Text("feature.${d.name}.$e")}}}
        openMenu();nav("Kasir").performScrollTo().performClick()
        compose.runOnIdle{current=current.copy(busy=true)}
        compose.onNodeWithText("feature.POS.false").assertIsDisplayed()
        compose.runOnIdle{current=current.copy(busy=false)}
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("feature.POS.false").assertIsDisplayed()
    }
    @Test fun lockedContextNeverRendersShellContent() {
        var renders = 0
        compose.setContent { DaunTehTheme {
            AuthenticatedShell(state().copy(stage = AuthStage.LOCKED, membership = null, lockedContext = state().membership), {}) { _,_ -> renders++ }
        } }
        compose.runOnIdle { assertEquals(0, renders) }
        compose.onNodeWithTag("shell.bottom").assertDoesNotExist()
        compose.onNodeWithContentDescription("Pilih outlet lain").assertDoesNotExist()
    }
}
