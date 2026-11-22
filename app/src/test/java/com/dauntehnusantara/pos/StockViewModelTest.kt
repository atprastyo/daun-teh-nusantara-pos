package com.dauntehnusantara.pos

import android.app.Application
import androidx.lifecycle.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.stock.domain.*
import com.dauntehnusantara.pos.feature.stock.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.*
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
class StockViewModelTest {
    private val dispatcher=StandardTestDispatcher();private val store=ViewModelStore()
    private val member=Membership("brand","Brand","outlet","Outlet A",Role.OWNER)
    private val source=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private val id="00000000-0000-4000-8000-000000000001"
    private val snapshot=StockSnapshot(id,1800000000000,listOf(StockProduct(id,"Tea",true,null,0)),false,emptyList())
    private var pending:StockCommand?=null;private var failure:String?=null;private var writes=0
    private var hold:CompletableDeferred<Unit>?=null
    private val auth=object:AuthRepository {
        override val state=MutableStateFlow(AuthState(AuthStage.READY,true,membership=member,email="owner@example.invalid"))
        override suspend fun restore(){};override suspend fun signIn(email:String,password:String){};override suspend fun selectOutlet(outletId:String){}
        override suspend fun chooseOutlets(){};override suspend fun refresh(){};override suspend fun unlockOffline(){};override suspend fun enforceLease(){}
        override suspend fun loadDeviceRequests(){};override suspend fun approveDevice(id:String){};override suspend fun signOut(){}
    }
    private val access=object:ReportingGateway {
        override suspend fun reportingScope(online:Boolean)=source.takeIf{auth.state.value.stage==AuthStage.READY&&(!online||!auth.state.value.offline)}
        override suspend fun report(query:ReportQuery):SalesReport=error("unused")
    }
    private val repo=object:StockRepository {
        override suspend fun cached(scope:PullScope,query:StockQuery)=snapshot
        override suspend fun refresh(scope:PullScope,query:StockQuery):StockSnapshot {val pause=hold;if(pause!=null)withContext(NonCancellable){pause.await()};failure?.let{throw ManagementFailure(it)};return snapshot}
        override suspend fun pending(scope:PullScope)=pending
        override suspend fun prepare(scope:PullScope,command:StockCommand){pending=command}
        override suspend fun submit(scope:PullScope){writes++;failure?.let{throw ManagementFailure(it)};pending=null}
    }
    private fun model()=ViewModelProvider(store,object:ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST") override fun<T:ViewModel>create(modelClass:Class<T>):T=StockViewModel(auth,access,repo) as T
    })[StockViewModel::class.java]
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){hold?.complete(Unit);store.clear();Dispatchers.resetMain()}
    @Test fun unknownWriteRetainsFrozenCommandAndBlocksReplacementUntilRetry()=runTest(dispatcher){
        val vm=model();runCurrent();val command=StockCommand(id,id,id,12,0,"Opening count");failure="UNAVAILABLE"
        vm.save(command);runCurrent();assertEquals(command,vm.state.value.pending);assertTrue(vm.state.value.stale)
        vm.save(command.copy(quantity=14));runCurrent();assertEquals(1,writes)
        failure=null;vm.retryPending();runCurrent();assertNull(vm.state.value.pending);assertTrue(vm.state.value.saved)
    }
    @Test fun logoutDropsSnapshotAndLateReadCannotRestorePriorAccount()=runTest(dispatcher){
        val vm=model();runCurrent();hold=CompletableDeferred();vm.refresh();runCurrent()
        auth.state.value=AuthState(AuthStage.LOGIN,true);runCurrent();hold!!.complete(Unit);runCurrent()
        assertNull(vm.state.value.scope);assertNull(vm.state.value.snapshot)
    }
    @Test fun offlineCacheIsStaleAndCannotSubmit()=runTest(dispatcher){
        auth.state.value=auth.state.value.copy(offline=true);val vm=model();runCurrent()
        assertEquals(snapshot,vm.state.value.snapshot);assertTrue(vm.state.value.stale)
        vm.save(StockCommand(id,id,id,12,0,"Opening count"));runCurrent();assertEquals(0,writes)
    }
}
