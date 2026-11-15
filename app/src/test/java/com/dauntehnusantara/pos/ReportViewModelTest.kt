package com.dauntehnusantara.pos

import android.app.Application
import androidx.lifecycle.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.reports.presentation.*
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
class ReportViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    private val member=Membership("brand","Brand","outlet","Outlet",Role.OWNER)
    private val auth=object:AuthRepository {
        override val state=MutableStateFlow(AuthState(AuthStage.READY,true,membership=member,email="owner@example.invalid"))
        override suspend fun restore(){};override suspend fun signIn(email:String,password:String){};override suspend fun selectOutlet(outletId:String){}
        override suspend fun chooseOutlets(){};override suspend fun refresh(){};override suspend fun unlockOffline(){};override suspend fun enforceLease(){}
        override suspend fun loadDeviceRequests(){};override suspend fun approveDevice(id:String){};override suspend fun signOut(){}
    }
    private var source:PullScope?=PullScope(SaleScope("brand","outlet","device","owner@example.invalid"),"OWNER","https://example.invalid")
    private var saved:SalesReport?=null
    private var failure:String?=null
    private var hold:CompletableDeferred<Unit>?=null
    private var refreshes=0
    private var exports=0
    private var captured:SalesReport?=null
    private val repo=object:ReportRepository {
        override suspend fun cached(query:ReportQuery)=saved?.takeIf{it.query==query}
        override suspend fun refresh(query:ReportQuery):SalesReport {
            refreshes++;val pause=hold;val error=failure
            if(pause!=null)withContext(NonCancellable){pause.await()}
            error?.let{throw ManagementFailure(it)}
            return snapshot(query)
        }
        override suspend fun export(report:SalesReport):String {exports++;captured=report;return ReportCsv.render(report)}
    }
    private fun snapshot(q:ReportQuery)=SalesReport(q,123,ReportSummary(0,0,0,0,0,0),emptyList(),emptyList(),emptyList(),listOf(OutletChoice("outlet","Outlet",true)))
    private fun model()=ViewModelProvider(store,object:ViewModelProvider.Factory {
        override fun<T:ViewModel>create(modelClass:Class<T>):T {
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(auth,object:ReportingGateway {
                override suspend fun reportingScope(online:Boolean)=source
                override suspend fun report(query:ReportQuery)=snapshot(query)
            },Reports(repo),Clock.fixed(Instant.parse("2026-11-15T01:00:00Z"),ZoneOffset.UTC)) as T
        }
    })[ReportViewModel::class.java]
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){store.clear();Dispatchers.resetMain()}
    private fun scenario(block:suspend TestScope.()->Unit)=runTest(dispatcher){try{block()}finally{hold?.complete(Unit);store.clear();runCurrent()}}
    @Test fun sameQueryFailureRetainsSnapshotButChangedQueryDoesNot()=scenario {
        val vm=model();runCurrent();val original=vm.state.value.report;failure="UNAVAILABLE"
        vm.retry();runCurrent();assertEquals(original,vm.state.value.report);assertTrue(vm.state.value.cached)
        vm.load("2026-11-01","2026-11-01",null);assertNull(vm.state.value.report);runCurrent()
        assertNull(vm.state.value.report);assertEquals("UNAVAILABLE",vm.state.value.error)
    }
    @Test fun offlineExactCacheIsVisibleAndNoCacheIsNotZero()=scenario {
        val vm=model();runCurrent();saved=vm.state.value.report;auth.state.value=auth.state.value.copy(offline=true)
        vm.retry();runCurrent();assertEquals(1,refreshes);assertEquals(saved,vm.state.value.report);assertEquals("OFFLINE",vm.state.value.error)
        vm.load("2026-11-01","2026-11-01",null);runCurrent();assertNull(vm.state.value.report)
    }
    @Test fun oldRequestFailureAndFinallyCannotOverwriteNewRequest()=scenario {
        val vm=model();runCurrent();hold=CompletableDeferred();failure="TOO_LARGE";vm.retry();runCurrent()
        val old=hold!!;hold=null;failure=null;vm.load("2026-11-01","2026-11-01",null);runCurrent()
        val current=vm.state.value.report;old.complete(Unit);runCurrent()
        assertEquals(current,vm.state.value.report);assertNull(vm.state.value.error);assertFalse(vm.state.value.busy)
    }
    @Test fun scopeChangeClearsBeforeOldNonCancellableReadReturns()=scenario {
        val vm=model();runCurrent();hold=CompletableDeferred();vm.retry();runCurrent()
        auth.state.value=auth.state.value.copy(stage=AuthStage.LOGIN,membership=null);runCurrent()
        assertNull(vm.state.value.report);assertNull(vm.state.value.scope)
        hold!!.complete(Unit);runCurrent();assertNull(vm.state.value.report)
    }
    @Test fun pickerCancelAndObsoleteTicketDoNotWrite()=scenario {
        val vm=model();runCurrent();val ticket=requireNotNull(vm.prepareExport());vm.cancelExport(ticket.id)
        vm.export(ticket.id){error("must not write")};runCurrent();assertEquals(0,exports)
        assertEquals(ReportExportPhase.NONE,vm.state.value.exportPhase);assertFalse(vm.state.value.exportDone)
    }
    @Test fun dirtyDraftDisablesExportAndInvalidApplyRetainsSnapshot()=scenario {
        val vm=model();runCurrent();val original=vm.state.value.report;vm.session.open(requireNotNull(vm.state.value.query));vm.session.from("2026-02-30")
        assertNull(vm.prepareExport());vm.applyDraft();assertEquals(original,vm.state.value.report);assertEquals(1,refreshes)
    }
    @Test fun deviceMismatchClearsPreparedBeforeProviderWrite()=scenario {
        val vm=model();runCurrent();val ticket=requireNotNull(vm.prepareExport());source=source!!.copy(sale=source!!.sale.copy(deviceId="other"))
        vm.export(ticket.id){error("must not write")};runCurrent();assertEquals(0,exports);assertNull(vm.state.value.scope);assertFalse(vm.state.value.exportDone)
    }
    @Test fun pickerCapturesOriginalEvenIfAppliedQueryChanges()=scenario {
        val vm=model();runCurrent();val original=vm.state.value.report;val ticket=requireNotNull(vm.prepareExport())
        vm.load("2026-11-01","2026-11-01",null);runCurrent()
        val written=CompletableDeferred<String>();vm.export(ticket.id){written.complete(it)}
        runCurrent();written.await();vm.state.first{it.exportPhase==ReportExportPhase.NONE}
        assertTrue(vm.state.value.exportDone)
        assertEquals(original,captured);assertEquals(1,exports)
    }
    @Test fun scopeChangeDuringWriteNeverClaimsSuccess()=scenario {
        val vm=model();runCurrent();val ticket=requireNotNull(vm.prepareExport())
        val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        vm.export(ticket.id){started.complete(Unit);withContext(NonCancellable){finish.await()}}
        runCurrent();withContext(Dispatchers.IO){started.await()}
        auth.state.value=auth.state.value.copy(stage=AuthStage.LOGIN,membership=null);runCurrent()
        assertNull(vm.state.value.report);assertFalse(vm.state.value.exportDone)
        finish.complete(Unit);runCurrent();assertFalse(vm.state.value.exportDone)
    }
    @Test fun launchFailureClearsTicketAndExplainsFailure()=scenario {
        val vm=model();runCurrent();val ticket=requireNotNull(vm.prepareExport());vm.failExportLaunch(ticket.id)
        assertEquals("EXPORT_FAILED",vm.state.value.error);assertEquals(ReportExportPhase.NONE,vm.state.value.exportPhase)
        vm.export(ticket.id){error("must not write")};runCurrent();assertEquals(0,exports)
    }
    @Test fun providerFailureDoesNotClaimSuccessAndAllowsNewAttempt()=scenario {
        val vm=model();runCurrent();val ticket=requireNotNull(vm.prepareExport());val attempted=CompletableDeferred<Unit>()
        vm.export(ticket.id){attempted.complete(Unit);throw java.io.IOException("fixture")};runCurrent()
        attempted.await();vm.state.first{it.exportPhase==ReportExportPhase.NONE}
        assertEquals("EXPORT_FAILED",vm.state.value.error);assertFalse(vm.state.value.exportDone);assertNotNull(vm.prepareExport())
    }
}
