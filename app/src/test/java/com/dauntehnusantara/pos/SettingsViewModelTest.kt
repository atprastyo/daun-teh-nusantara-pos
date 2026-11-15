package com.dauntehnusantara.pos

import android.app.Application
import androidx.lifecycle.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.settings.presentation.*
import com.dauntehnusantara.pos.feature.recovery.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class SettingsViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    private val member=Membership("brand","Brand","outlet","Outlet",Role.OWNER)
    private val auth=object:AuthRepository {
        override val state=MutableStateFlow(AuthState(AuthStage.READY,true,membership=member,email="owner@example.invalid"))
        override suspend fun restore(){};override suspend fun signIn(email:String,password:String){};override suspend fun selectOutlet(outletId:String){}
        override suspend fun chooseOutlets(){};override suspend fun refresh(){refreshes++};override suspend fun unlockOffline(){};override suspend fun enforceLease(){}
        override suspend fun loadDeviceRequests(){};override suspend fun approveDevice(id:String){};override suspend fun signOut(){signouts++;state.value=state.value.copy(stage=AuthStage.LOGIN,membership=null,email="")}
    }
    private var source:PullScope?=PullScope(SaleScope("brand","outlet","device","owner@example.invalid"),"OWNER","https://example.invalid")
    private var refreshes=0
    private var signouts=0
    private var captures=0
    private var captureFailure:String?=null
    private var raw="original immutable archive"
    private var captureHold:CompletableDeferred<Unit>?=null
    private val captureStarted=CompletableDeferred<Unit>()
    private var saleScope:SaleScope?=source!!.sale
    private val sale=object:SaleGateway {
        override suspend fun backgroundScope()=saleScope
        override suspend fun checkoutScope()=saleScope
        override suspend fun submit(sale:PendingSale):PushResult=error("unused")
    }
    private val gateway=object:PullGateway {
        override suspend fun pullScope()=source
        override suspend fun fetch(scope:PullScope,after:Long,epoch:String?):PullPage=error("unused")
    }
    private val repo=object:RecoveryRepository {
        override suspend fun capture(scope:PullScope)=snapshot(scope).raw
        override suspend fun snapshot(scope:PullScope):RecoverySnapshot {
            captures++;captureFailure?.let{throw ManagementFailure(it)};val frozen=raw;captureStarted.complete(Unit)
            withContext(NonCancellable){captureHold?.await()}
            return RecoverySnapshot(frozen,1234)
        }
    }
    private fun model()=ViewModelProvider(store,object:ViewModelProvider.Factory {
        override fun<T:ViewModel>create(modelClass:Class<T>):T {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(auth,sale,gateway,ExportRecovery(repo)) as T
        }
    })[SettingsViewModel::class.java]
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){store.clear();Dispatchers.resetMain()}
    private fun scenario(block:suspend TestScope.()->Unit)=runTest(dispatcher){try{block()}finally{captureHold?.complete(Unit);store.clear();runCurrent()}}
    private suspend fun TestScope.prepared(vm:SettingsViewModel):RecoveryTicket {
        vm.openExport();vm.prepare();runCurrent();return requireNotNull(vm.state.first{it.phase!=RecoveryPhase.CAPTURE}.ticket)
    }
    @Test fun exportCancelBeforeCaptureDoesNoIO()=scenario {
        val vm=model();runCurrent();vm.openExport();vm.cancelDialog();vm.prepare();runCurrent();assertEquals(0,captures);assertNull(vm.state.value.ticket)
    }
    @Test fun archiveIsCapturedBeforePickerAndWriteUsesOriginalBytes()=scenario {
        val vm=model();runCurrent();val ticket=prepared(vm);assertEquals(1,captures);raw="changed later"
        var written="";vm.write(ticket.id){written=it};vm.state.first{it.phase==RecoveryPhase.NONE}
        assertEquals("original immutable archive",written);assertTrue(vm.state.value.exportDone);assertEquals(1234L,ticket.capturedAtMs)
    }
    @Test fun pickerCancelConsumesAuthorityAndObsoleteCallbackCannotWrite()=scenario {
        val vm=model();runCurrent();val ticket=prepared(vm);vm.cancelPicker(ticket.id);vm.write(ticket.id){error("must not write")};runCurrent()
        assertEquals(RecoveryPhase.NONE,vm.state.value.phase);assertFalse(vm.state.value.exportDone)
    }
    @Test fun launchFailureConsumesTicketAndReportsError()=scenario {
        val vm=model();runCurrent();val ticket=prepared(vm);vm.launchFailed(ticket.id)
        assertNull(vm.state.value.ticket);assertEquals("EXPORT_FAILED",vm.state.value.error);vm.write(ticket.id){error("must not write")}
    }
    @Test fun backendChangeBeforeProviderPreventsOpeningIt()=scenario {
        val vm=model();runCurrent();val ticket=prepared(vm);source=source!!.copy(project="https://other.invalid")
        vm.write(ticket.id){error("must not write")};runCurrent();assertNull(vm.state.value.scope);assertFalse(vm.state.value.exportDone)
    }
    @Test fun providerFailureDoesNotClaimSuccessAndAllowsNewCapture()=scenario {
        val vm=model();runCurrent();val ticket=prepared(vm);vm.write(ticket.id){throw java.io.IOException("fixture")}
        vm.state.first{it.phase==RecoveryPhase.NONE};assertEquals("EXPORT_FAILED",vm.state.value.error);assertFalse(vm.state.value.exportDone)
        vm.openExport();assertTrue(vm.state.value.exportConfirm)
    }
    @Test fun offlineValidScopeCanExportButCannotValidateOnline()=scenario {
        auth.state.value=auth.state.value.copy(offline=true);val vm=model();runCurrent();vm.validateAccess();runCurrent();assertEquals(0,refreshes)
        val ticket=prepared(vm);assertEquals(RecoveryPhase.PICKER,vm.state.value.phase);vm.cancelPicker(ticket.id)
    }
    @Test fun signoutWarningCanBeCancelledAndExplicitExitKeepsRecoveryRepositoryUntouched()=scenario {
        val vm=model();runCurrent();vm.openSignOut();vm.cancelDialog();vm.signOut();runCurrent();assertEquals(0,signouts)
        vm.openSignOut();vm.signOut();runCurrent();assertEquals(1,signouts);assertEquals(0,captures);assertNull(vm.state.value.scope)
    }
    @Test fun signoutBlockedWhilePickerOutstanding()=scenario {
        val vm=model();runCurrent();prepared(vm);vm.openSignOut();vm.signOut();runCurrent();assertEquals(0,signouts);assertFalse(vm.state.value.signOutConfirm)
    }
    @Test fun lateCaptureAndErrorCannotRepopulateNewAccount()=scenario {
        val vm=model();runCurrent();captureHold=CompletableDeferred();vm.openExport();vm.prepare();runCurrent();captureStarted.await()
        auth.state.value=auth.state.value.copy(stage=AuthStage.LOGIN,membership=null);runCurrent();captureHold!!.complete(Unit);runCurrent()
        assertNull(vm.state.value.scope);assertNull(vm.state.value.ticket);assertFalse(vm.state.value.exportDone)
    }
    @Test fun authorityLossDuringWriteNeverClaimsSuccess()=scenario {
        val vm=model();runCurrent();val ticket=prepared(vm);val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        vm.write(ticket.id){entered.complete(Unit);withContext(NonCancellable){finish.await()}}
        runCurrent();entered.await();auth.state.value=auth.state.value.copy(stage=AuthStage.LOGIN,membership=null);runCurrent();finish.complete(Unit);runCurrent()
        assertFalse(vm.state.value.exportDone);assertNull(vm.state.value.scope)
    }
    @Test fun navigationClearsCapturedIntentAndAllowsFreshCapture()=scenario {
        val vm=model();runCurrent();val ticket=prepared(vm);vm.leave();vm.write(ticket.id){error("must not write")};runCurrent()
        assertEquals(RecoveryPhase.NONE,vm.state.value.phase);assertNull(vm.state.value.ticket);vm.openExport();assertTrue(vm.state.value.exportConfirm)
    }
    @Test fun confirmedCaptureDenialClearsDisplayedAuthority()=scenario {
        val vm=model();runCurrent();captureFailure="AUTH_REQUIRED";vm.openExport();vm.prepare();runCurrent()
        vm.state.first{it.phase!=RecoveryPhase.CAPTURE};assertNull(vm.state.value.scope);assertNull(vm.state.value.ticket);assertEquals("AUTH_REQUIRED",vm.state.value.error)
    }
    @Test fun interruptedProviderReportsPartialFileFailure()=scenario {
        val vm=model();runCurrent();val ticket=prepared(vm);vm.write(ticket.id){throw CancellationException("fixture interrupted write")}
        vm.state.first{it.phase==RecoveryPhase.NONE};assertFalse(vm.state.value.exportDone);assertEquals("EXPORT_FAILED",vm.state.value.error)
    }
    @Test fun oversizedArchiveNeverReachesPicker()=scenario {
        val vm=model();runCurrent();raw="x".repeat(16*1024*1024+1);vm.openExport();vm.prepare();runCurrent()
        vm.state.first{it.phase!=RecoveryPhase.CAPTURE};assertNull(vm.state.value.ticket);assertEquals("TOO_LARGE",vm.state.value.error)
    }
}
