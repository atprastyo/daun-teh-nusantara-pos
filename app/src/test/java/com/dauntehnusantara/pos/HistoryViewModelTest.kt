package com.dauntehnusantara.pos

import android.app.Application
import androidx.lifecycle.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.checkout.domain.*
import com.dauntehnusantara.pos.feature.history.presentation.*
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
class HistoryViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    private var instant=Instant.parse("2026-12-31T16:59:59Z")
    private val clock=object:Clock(){override fun getZone()=ZoneOffset.UTC;override fun withZone(zone:ZoneId)=this;override fun instant()=instant}
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){store.clear();Dispatchers.resetMain()}
    private val member=Membership(DEMO_BRAND_ID,"Brand","outlet-a","Outlet A",Role.CASHIER)
    private val auth=object:AuthRepository {
        override val state=MutableStateFlow(AuthState(AuthStage.READY,true,membership=member,email="one@example.invalid"))
        override suspend fun restore(){};override suspend fun signIn(email:String,password:String){};override suspend fun selectOutlet(outletId:String){}
        override suspend fun chooseOutlets(){};override suspend fun refresh(){};override suspend fun unlockOffline(){};override suspend fun enforceLease(){}
        override suspend fun loadDeviceRequests(){};override suspend fun approveDevice(id:String){};override suspend fun signOut(){}
    }
    private val category=Category(RecordInfo(brandId=DEMO_BRAND_ID,name="Tea"))
    private val product=Product(RecordInfo(brandId=DEMO_BRAND_ID,name="Original"),category.info.id,6000)
    private val line=CheckoutRules.line(Catalog(categories=listOf(category),products=listOf(product)),product.info.id,emptyList(),1)
    private val original=Receipt(newId(),newId(),member.brandId,member.outletId,"device","one@example.invalid",instant.toEpochMilli(),listOf(line),6000,CheckoutRules.payment(6000,PaymentInput(PaymentMethod.CASH,10000)))
    private val retained=CheckoutState(Draft(newId(),newId(),1,listOf(line)),original,1,mapOf(original.id to ReceiptDelivery(ReceiptDeliveryState.PENDING)))
    private val pullStatus=MutableStateFlow(PullStatus(1,true,100))
    private var device="device"
    private var project="https://example.invalid"
    private var pause:CompletableDeferred<Unit>?=null
    private var historyPause:CompletableDeferred<Unit>?=null
    private var ignoreCancellation=false
    private var mode="SUCCESS"
    private var unreadable=false
    private var syncCalls=0
    private var mutationCalls=0
    private var factoryCalls=0
    private var scheduleCalls=0
    private fun scope()=auth.state.value.membership?.let{PullScope(SaleScope(it.brandId,it.outletId,device,auth.state.value.email),it.role.name,project)}
    private val repo=object:CheckoutRepository {
        val cart=MutableStateFlow(retained)
        override fun observe():Flow<CheckoutState> = flow {if(unreadable)error("read failed");emitAll(cart)}
        override suspend fun receipts():List<Receipt>{val hold=historyPause;if(hold!=null)withContext(NonCancellable){hold.await()};if(unreadable)error("read failed");return listOf(original)}
        override suspend fun add(draft:Draft,line:CartLine){mutationCalls++}
        override suspend fun quantity(draft:Draft,lineId:String,quantity:Int){mutationCalls++}
        override suspend fun remove(draft:Draft,lineId:String){mutationCalls++}
        override suspend fun complete(command:CheckoutCommand):Receipt{mutationCalls++;return original}
        override suspend fun dismissReceipt(saleId:String){mutationCalls++}
    }
    private fun model():HistoryViewModel=ViewModelProvider(store,object:ViewModelProvider.Factory {
        override fun<T:ViewModel>create(modelClass:Class<T>):T {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(auth,object:PullGateway {
                override suspend fun pullScope()=scope()
                override suspend fun fetch(scope:PullScope,after:Long,epoch:String?):PullPage=error("unused")
            },object:AuthenticatedCheckoutFactory{override fun open(scope:PullScope):CheckoutRepository{
                factoryCalls++
                return if(scope.sale.outletId==member.outletId)repo else object:CheckoutRepository by repo {
                    override suspend fun receipts()=emptyList<Receipt>()
                }
            }},
                object:PullRepository {
                    override fun observe(scope:PullScope)=pullStatus
                    override suspend fun sync():Boolean {
                        syncCalls++;if(ignoreCancellation)withContext(NonCancellable){pause?.await()}else pause?.await()
                        if(mode=="FAIL"){pullStatus.value=pullStatus.value.copy(error="UNAVAILABLE");throw PullFailure("UNAVAILABLE")}
                        if(mode=="MORE"){pullStatus.value=pullStatus.value.copy(seq=2);return true}
                        if(mode=="NO_SUCCESS"){pullStatus.value=PullStatus();return false}
                        pullStatus.value=pullStatus.value.copy(initialized=true,error=null,lastSuccessMs=100)
                        return false
                    }
                },object:SyncScheduler{override fun enqueue(force:Boolean){scheduleCalls++};override fun ensurePeriodic(){}},clock) as T
        }
    })[HistoryViewModel::class.java]
    private fun historyTest(block:suspend TestScope.()->Unit)=runTest(dispatcher) {
        try { block() }
        finally { pause?.complete(Unit);historyPause?.complete(Unit);store.clear();runCurrent() }
    }
    @Test fun refreshAwaitsActualPullEvenWhenSuccessTimestampDoesNotChange()=historyTest {
        val vm=model();runCurrent();vm.session.search("original");vm.session.select(original.id);pause=CompletableDeferred()
        vm.refresh();vm.refresh();runCurrent();assertTrue(vm.state.value.refreshing);assertEquals(1,syncCalls);assertEquals(HistoryRefresh.NONE,vm.state.value.refresh)
        pause!!.complete(Unit);runCurrent();assertFalse(vm.state.value.refreshing);assertEquals(HistoryRefresh.PULL,vm.state.value.refresh)
        assertEquals("original",vm.session.state.value.query);assertEquals(original.id,vm.session.state.value.selectedId)
        assertEquals(retained,repo.cart.value);assertEquals(0,mutationCalls)
    }
    @Test fun localOnlyOfflineReadNeverClaimsCloudAcceptance()=historyTest {
        auth.state.value=auth.state.value.copy(offline=true);val vm=model();runCurrent();vm.refresh();runCurrent()
        assertEquals(0,syncCalls);assertEquals(HistoryRefresh.LOCAL,vm.state.value.refresh);assertEquals(ReceiptDeliveryState.PENDING,vm.state.value.deliveries[original.id]?.state)
    }
    @Test fun recoveryKeepsReceiptAndDoesNotAttemptAutomaticRebootstrap()=historyTest {
        pullStatus.value=PullStatus(initialized=false,error="REBOOTSTRAP_REQUIRED");val vm=model();runCurrent();vm.session.select(original.id);vm.refresh();runCurrent()
        assertEquals(listOf(original),vm.state.value.receipts);assertEquals(original.id,vm.session.state.value.selectedId);assertEquals(0,syncCalls);assertEquals(0,mutationCalls)
    }
    @Test fun failedRefreshAndReadKeepSameScopeSnapshotsWithoutSuccess()=historyTest {
        val vm=model();runCurrent();mode="FAIL";vm.refresh();runCurrent();assertEquals(HistoryRefresh.FAILED,vm.state.value.refresh);assertEquals(listOf(original),vm.state.value.receipts)
        unreadable=true;vm.refresh();runCurrent();assertTrue(vm.state.value.loadFailed);assertFalse(vm.state.value.refreshing);assertEquals(listOf(original),vm.state.value.receipts)
        unreadable=false;mode="SUCCESS";vm.refresh();runCurrent();assertFalse(vm.state.value.loadFailed);assertEquals(HistoryRefresh.PULL,vm.state.value.refresh)
    }
    @Test fun boundedPartialPullIsNotCompleteAndRequestsContinuationOnly()=historyTest {
        val vm=model();runCurrent();mode="MORE";vm.refresh();runCurrent();assertEquals(HistoryRefresh.MORE,vm.state.value.refresh);assertEquals(1,scheduleCalls);assertEquals(0,mutationCalls)
    }
    @Test fun returnedPullWithoutSuccessfulStatusNeverClaimsSuccess()=historyTest {
        val vm=model();runCurrent();mode="NO_SUCCESS";vm.refresh();runCurrent();assertEquals(HistoryRefresh.FAILED,vm.state.value.refresh)
    }
    @Test fun slowOldScopeRefreshCannotRestoreOldResultsOrFilterState()=historyTest {
        val vm=model();runCurrent();vm.session.search("original");vm.session.select(original.id);pause=CompletableDeferred();ignoreCancellation=true;vm.refresh();runCurrent()
        auth.state.value=auth.state.value.copy(membership=member.copy(outletId="outlet-b"));runCurrent()
        val next=vm.state.value.scopeKey;assertTrue(next.contains("outlet-b"));assertEquals(HistoryFilters(),vm.session.state.value)
        pause!!.complete(Unit);runCurrent();assertEquals(next,vm.state.value.scopeKey);assertTrue(vm.state.value.receipts.isEmpty());assertEquals(HistoryRefresh.NONE,vm.state.value.refresh);assertFalse(vm.state.value.refreshing)
    }
    @Test fun slowCancelledReadCannotKeepOldDisplayAfterLock()=historyTest {
        val vm=model();runCurrent();historyPause=CompletableDeferred();pullStatus.value=pullStatus.value.copy(seq=2);runCurrent()
        auth.state.value=AuthState(AuthStage.LOCKED,true);runCurrent();assertTrue(vm.state.value.receipts.isEmpty());assertFalse(vm.state.value.allowed)
        historyPause!!.complete(Unit);runCurrent();assertTrue(vm.state.value.receipts.isEmpty());assertEquals(HistoryFilters(),vm.session.state.value)
    }
    @Test fun deviceOrBackendScopeMismatchFailsClosedDuringRead()=historyTest {
        val vm=model();runCurrent();vm.session.select(original.id);device="another-device";vm.refresh();runCurrent()
        assertTrue(vm.state.value.receipts.isEmpty());assertFalse(vm.state.value.allowed);assertNull(vm.session.state.value.selectedId)
        vm.refresh();runCurrent();assertTrue(vm.state.value.scopeKey.contains("another-device"))
        project="https://other.invalid";pullStatus.value=pullStatus.value.copy(seq=5);runCurrent();assertTrue(vm.state.value.receipts.isEmpty());assertFalse(vm.state.value.allowed)
    }
    @Test fun ownerAndManagerCanOpenSelectedOutletHistoryAndDemotionRestoresOwnScope()=historyTest {
        auth.state.value=auth.state.value.copy(membership=member.copy(role=Role.OWNER));val vm=model();runCurrent();assertTrue(vm.state.value.allowed);assertTrue(vm.state.value.outletWide);assertEquals(1,factoryCalls)
        assertEquals(listOf(original),vm.state.value.receipts)
        auth.state.value=auth.state.value.copy(membership=member.copy(role=Role.MANAGER));runCurrent();assertTrue(vm.state.value.allowed);assertEquals(2,factoryCalls)
        assertTrue(vm.state.value.outletWide);assertEquals(listOf(original),vm.state.value.receipts)
        auth.state.value=auth.state.value.copy(membership=member);runCurrent()
        assertTrue(vm.state.value.allowed);assertFalse(vm.state.value.outletWide);assertEquals(3,factoryCalls)
    }
    @Test fun dayRolloverReconcilesTodaySelectionAtJakartaMidnight()=historyTest {
        val vm=model();runCurrent();vm.session.day(HistoryDay.TODAY);vm.session.select(original.id)
        instant=Instant.parse("2026-12-31T17:00:00Z");advanceTimeBy(1000);runCurrent()
        assertEquals(LocalDate.of(2027,1,1),vm.state.value.today);assertNull(vm.session.state.value.selectedId);assertTrue(vm.session.state.value.selectionEvicted)
    }
}
