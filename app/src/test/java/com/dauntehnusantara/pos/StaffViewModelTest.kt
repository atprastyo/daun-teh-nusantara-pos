package com.dauntehnusantara.pos

import android.app.Application
import androidx.lifecycle.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.staff.presentation.*
import com.dauntehnusantara.pos.feature.staff.domain.*
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
class StaffViewModelTest {
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
    private val cashier=StaffMember("cashier","Rani","rani@example.invalid","CASHIER",true,true,1)
    private var row=cashier
    private var creations=mutableListOf<StaffCreation>()
    private var creationFailure:String?=null
    private var createPause:CompletableDeferred<Unit>?=null
    private var loadCalls=0
    private var writes=0
    private var hold:CompletableDeferred<Unit>?=null
    private var loadFailure=false
    private var writeFailure:String?=null
    private var failReadback=false
    private var pauseWrite=false
    private var cancelWrite=false
    private var desired:Boolean?=null
    private val api=object:StaffGateway {
        override suspend fun create(scope:PullScope,command:StaffCreation) {
            creations+=command;createPause?.await()
            creationFailure?.let{throw ManagementFailure(it)}
            if(failReadback)loadFailure=true
        }
        override suspend fun roster(scope:PullScope):StaffRoster {
            loadCalls++;val pause=hold;val failed=loadFailure
            if(pause!=null&&!pauseWrite)withContext(NonCancellable){pause.await()}
            if(failed)throw ManagementFailure("UNAVAILABLE")
            return StaffRoster(scope,listOf(row),emptyList())
        }
        override suspend fun memberAccess(scope:PullScope,id:String,active:Boolean,version:Long,reason:String) {
            writes++;desired=active
            if(cancelWrite)throw CancellationException("fixture timeout")
            if(pauseWrite)withContext(NonCancellable){hold?.await()}
            writeFailure?.let{throw ManagementFailure(it)}
            row=row.copy(active=active,version=version+1)
            if(failReadback)loadFailure=true
        }
        override suspend fun deviceAccess(scope:PullScope,id:String,revoked:Boolean,version:Long,reason:String){error("unused")}
    }
    private fun model()=ViewModelProvider(store,object:ViewModelProvider.Factory {
        override fun<T:ViewModel>create(modelClass:Class<T>):T {
            @Suppress("UNCHECKED_CAST")
            return StaffViewModel(auth,object:ReportingGateway {
                override suspend fun reportingScope(online:Boolean)=source
                override suspend fun report(query:ReportQuery):SalesReport=error("unused")
            },ManageStaff(api)) as T
        }
    })[StaffViewModel::class.java]
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){store.clear();Dispatchers.resetMain()}
    private fun scenario(block:suspend TestScope.()->Unit)=runTest(dispatcher){try{block()}finally{hold?.complete(Unit);store.clear();runCurrent()}}
    private fun creation(role:String="CASHIER")=StaffCreation(name="New staff",email="new@example.invalid",role=role,password="temporary-1234")
    @Test fun creationUsesSingleFrozenCommandForUncertainRetry()=scenario {
        val vm=model();runCurrent();vm.openCreation();creationFailure="UNAVAILABLE"
        val original=creation();vm.create(original);vm.create(creation());runCurrent()
        assertEquals(1,creations.size);assertSame(original,vm.pendingCreation)
        vm.closeCreation();vm.openCreation();creationFailure=null;vm.create(creation());runCurrent()
        assertEquals(2,creations.size);assertSame(original,creations.last());assertNull(vm.pendingCreation)
        assertFalse(vm.state.value.creating);assertEquals(StaffOutcome.SAVED,vm.state.value.outcome)
    }
    @Test fun rejectedPasswordCorrectionRetainsReservedOperationAndPublicDetails()=scenario {
        val vm=model();runCurrent();vm.openCreation();creationFailure="INVALID"
        val original=creation();vm.create(original);runCurrent();assertSame(original,vm.pendingCreation)
        creationFailure=null
        vm.create(StaffCreation(name="Changed name",email="other@example.invalid",role="MANAGER",password="corrected-5678"));runCurrent()
        val sent=creations.last();assertEquals(original.operationId,sent.operationId);assertEquals(original.email,sent.email)
        assertEquals(original.role,sent.role);assertEquals("corrected-5678",sent.password)
    }
    @Test fun creationAckReadbackFailureDoesNotOfferAnotherCreate()=scenario {
        val vm=model();runCurrent();vm.openCreation();failReadback=true;vm.create(creation());runCurrent()
        assertEquals(StaffOutcome.ACK_REFRESH_FAILED,vm.state.value.outcome);assertFalse(vm.state.value.creating)
        assertNull(vm.pendingCreation);vm.openCreation();assertFalse(vm.state.value.creating)
    }
    @Test fun managerCannotCreateManagerAndOfflineDropsSecretCommand()=scenario {
        source=source!!.copy(role="MANAGER");auth.state.value=auth.state.value.copy(membership=member.copy(role=Role.MANAGER))
        val vm=model();runCurrent();vm.openCreation();vm.create(creation("MANAGER"));runCurrent()
        assertTrue(creations.isEmpty());assertEquals("INVALID",vm.state.value.createError)
        creationFailure="UNAVAILABLE";vm.create(creation());runCurrent();assertNotNull(vm.pendingCreation)
        auth.state.value=auth.state.value.copy(offline=true);runCurrent()
        assertNull(vm.pendingCreation);assertFalse(vm.state.value.creating)
    }
    @Test fun creationContextLossDiscardsLateResult()=scenario {
        val vm=model();runCurrent();vm.openCreation();createPause=CompletableDeferred();vm.create(creation());runCurrent()
        auth.state.value=AuthState(AuthStage.LOGIN,true);runCurrent();createPause!!.complete(Unit);runCurrent()
        assertNull(vm.pendingCreation);assertNull(vm.state.value.roster);assertFalse(vm.state.value.saved)
    }
    @Test fun successUsesCapturedDesiredValueAndReadsBack()=scenario {
        val vm=model();runCurrent();vm.member(cashier,"Reviewed shift");vm.member(cashier,"Duplicate tap");runCurrent()
        assertEquals(1,writes);assertEquals(false,desired);assertTrue(vm.state.value.saved);assertEquals(StaffOutcome.SAVED,vm.state.value.outcome)
        assertFalse(vm.state.value.roster!!.members.single().active);assertEquals(3,loadCalls)
    }
    @Test fun acknowledgedWriteWithReadFailureIsNotLostOrRetried()=scenario {
        val vm=model();runCurrent();failReadback=true;vm.member(cashier,"Reviewed shift");runCurrent()
        assertEquals(StaffOutcome.ACK_REFRESH_FAILED,vm.state.value.outcome);assertTrue(vm.state.value.blocked);assertFalse(vm.state.value.saved)
        vm.member(cashier,"Do not resend");runCurrent();assertEquals(1,writes)
        loadFailure=false;vm.reload();runCurrent();assertEquals(StaffOutcome.NONE,vm.state.value.outcome);assertFalse(vm.state.value.roster!!.members.single().active)
    }
    @Test fun uncertainResponseRequiresRefreshAndNeverInverseToggle()=scenario {
        val vm=model();runCurrent();writeFailure="UNAVAILABLE";vm.member(cashier,"Reviewed");runCurrent()
        assertEquals(StaffOutcome.UNCERTAIN,vm.state.value.outcome);vm.member(cashier,"Do not replay");runCurrent();assertEquals(1,writes)
        row=row.copy(active=false,version=2);vm.reload();runCurrent();assertFalse(vm.state.value.saved);assertEquals(StaffOutcome.NONE,vm.state.value.outcome)
    }
    @Test fun staleTargetVersionIsRejectedBeforeDispatch()=scenario {
        val vm=model();runCurrent();row=row.copy(version=2);vm.member(cashier,"Reviewed");runCurrent()
        assertEquals(0,writes);assertEquals(StaffOutcome.CONFLICT,vm.state.value.outcome);assertTrue(vm.state.value.blocked)
    }
    @Test fun serverConflictDoesNotRetryOrClaimSuccess()=scenario {
        val vm=model();runCurrent();writeFailure="CONFLICT";vm.member(cashier,"Reviewed");runCurrent()
        assertEquals(1,writes);assertEquals(StaffOutcome.CONFLICT,vm.state.value.outcome);assertFalse(vm.state.value.saved)
    }
    @Test fun offlineClearsRosterDraftAndCannotQueueMutation()=scenario {
        val vm=model();runCurrent();vm.session.open(StaffTarget.Member(source!!,cashier));vm.session.reason("Sensitive reason")
        auth.state.value=auth.state.value.copy(offline=true);runCurrent()
        assertNull(vm.state.value.roster);assertNull(vm.session.state.value.target);assertEquals("",vm.session.state.value.reason)
        vm.member(cashier,"No queue");vm.reload();runCurrent();assertEquals(0,writes);assertEquals(1,loadCalls)
    }
    @Test fun lateOldReadAndFinallyCannotChangeNewScope()=scenario {
        val vm=model();runCurrent();hold=CompletableDeferred();vm.reload();runCurrent();val old=hold!!
        auth.state.value=auth.state.value.copy(stage=AuthStage.LOGIN,membership=null);runCurrent();assertNull(vm.state.value.roster)
        old.complete(Unit);runCurrent();assertNull(vm.state.value.scope);assertFalse(vm.state.value.busy)
    }
    @Test fun lateOldErrorCannotReplaceNewOutletRowsOrFinally()=scenario {
        val vm=model();runCurrent();hold=CompletableDeferred();loadFailure=true;vm.reload();runCurrent();val old=hold!!
        hold=null;loadFailure=false;source=source!!.copy(sale=source!!.sale.copy(outletId="second"))
        auth.state.value=auth.state.value.copy(membership=member.copy(outletId="second",outletName="Second"));runCurrent()
        assertEquals("second",vm.state.value.scope!!.sale.outletId);val fresh=vm.state.value.roster
        old.complete(Unit);runCurrent();assertEquals(fresh,vm.state.value.roster);assertNull(vm.state.value.error);assertFalse(vm.state.value.busy)
    }
    @Test fun oldCommittedWriteCannotPublishSuccessAfterAccountChange()=scenario {
        val vm=model();runCurrent();pauseWrite=true;hold=CompletableDeferred();vm.member(cashier,"Reviewed");runCurrent();assertEquals(1,writes)
        auth.state.value=auth.state.value.copy(stage=AuthStage.LOGIN,membership=null);runCurrent();hold!!.complete(Unit);runCurrent()
        assertNull(vm.state.value.roster);assertFalse(vm.state.value.saved);assertEquals(StaffOutcome.NONE,vm.state.value.outcome)
    }
    @Test fun backendMismatchClearsBeforeWrite()=scenario {
        val vm=model();runCurrent();source=source!!.copy(project="https://different.invalid");vm.member(cashier,"Reviewed");runCurrent()
        assertEquals(0,writes);assertNull(vm.state.value.roster);assertEquals("AUTH_REQUIRED",vm.state.value.error)
    }
    @Test fun failedRefreshKeepsLabeledSameScopeRowsButBlocksActions()=scenario {
        val vm=model();runCurrent();val snapshot=vm.state.value.roster;loadFailure=true;vm.reload();runCurrent()
        assertEquals(snapshot,vm.state.value.roster);assertTrue(vm.state.value.blocked);assertEquals("UNAVAILABLE",vm.state.value.error)
    }
    @Test fun cancellationAfterDispatchRequiresReadBeforeRetry()=scenario {
        val vm=model();runCurrent();cancelWrite=true;vm.member(cashier,"Reviewed");runCurrent()
        assertEquals(StaffOutcome.UNCERTAIN,vm.state.value.outcome);assertTrue(vm.state.value.blocked);assertFalse(vm.state.value.busy)
        vm.member(cashier,"Do not resend");runCurrent();assertEquals(1,writes)
    }
    @Test fun approvalBusyOrInvalidReasonCannotDispatchStaffWrite()=scenario {
        val vm=model();runCurrent();vm.member(cashier,"ab");runCurrent();assertEquals(0,writes)
        auth.state.value=auth.state.value.copy(busy=true);vm.member(cashier,"Reviewed");runCurrent();assertEquals(0,writes)
    }
}
