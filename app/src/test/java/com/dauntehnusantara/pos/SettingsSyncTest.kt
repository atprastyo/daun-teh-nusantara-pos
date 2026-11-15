package com.dauntehnusantara.pos

import android.app.Application
import androidx.lifecycle.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import com.dauntehnusantara.pos.feature.sync.presentation.*
import com.dauntehnusantara.pos.feature.checkout.domain.Receipt
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
class SettingsSyncTest {
    private val dispatcher=StandardTestDispatcher();private val store=ViewModelStore()
    private val original=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private var source:PullScope?=original
    private val push=MutableStateFlow(SyncStatus(pending=2,review=1))
    private val download=MutableStateFlow(PullStatus(1,true,123))
    private var retries=0
    private var retryHold:CompletableDeferred<Unit>?=null
    private var readHold:CompletableDeferred<Unit>?=null
    private var readError=false
    private val repo=object:SyncRepository {
        override fun observe(scope:SaleScope):Flow<SyncStatus> = flow {
            val pause=readHold;val failed=readError
            if(pause!=null)withContext(NonCancellable){pause.await()}
            if(failed)error("fixture read failure")
            emitAll(push)
        }
        override suspend fun record(receipt:Receipt){error("never mutate sales")}
        override suspend fun drain():Boolean=error("never submit in UI test")
        override suspend fun retry(scope:SaleScope){retries++;withContext(NonCancellable){retryHold?.await()}}
    }
    private fun model()=ViewModelProvider(store,object:ViewModelProvider.Factory {
        override fun<T:ViewModel>create(modelClass:Class<T>):T {
            @Suppress("UNCHECKED_CAST")
            return SyncStatusViewModel(object:SaleGateway {
                override suspend fun backgroundScope()=source?.sale
                override suspend fun checkoutScope()=source?.sale
                override suspend fun submit(sale:PendingSale):PushResult=error("unused")
            },repo,object:PullGateway {
                override suspend fun pullScope()=source
                override suspend fun fetch(scope:PullScope,after:Long,epoch:String?):PullPage=error("unused")
            },object:PullRepository {
                override fun observe(scope:PullScope)=download
                override suspend fun sync():Boolean=error("UI only schedules")
            }) as T
        }
    })[SyncStatusViewModel::class.java]
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){store.clear();Dispatchers.resetMain()}
    private fun scenario(block:suspend TestScope.()->Unit)=runTest(dispatcher){try{block()}finally{readHold?.complete(Unit);retryHold?.complete(Unit);store.clear();runCurrent()}}
    @Test fun schedulingDoesNotChangeDeliveryCountsAndDuplicateTapIsBlocked()=scenario {
        val vm=model();vm.bind(source);runCurrent();val before=vm.state.value;retryHold=CompletableDeferred()
        vm.retry();vm.retry();runCurrent();assertEquals(1,retries);assertTrue(vm.retrying);assertFalse(vm.scheduled)
        retryHold!!.complete(Unit);runCurrent();assertTrue(vm.scheduled);assertEquals(before,vm.state.value)
    }
    @Test fun offlineAndRestoredBranchDoNotRequestRetry()=scenario {
        val vm=model();vm.bind(source);runCurrent();vm.retry(false);runCurrent();assertEquals(0,retries)
        download.value=download.value.copy(error="REBOOTSTRAP_REQUIRED");runCurrent();vm.retry();runCurrent();assertEquals(0,retries)
    }
    @Test fun missingScopeCannotEnqueueAnything()=scenario {
        val vm=model();vm.retry();runCurrent();assertEquals(0,retries);assertNull(vm.state.value)
    }
    @Test fun lateOldReadErrorCannotClearNewAccountStatus()=scenario {
        val vm=model();readHold=CompletableDeferred();readError=true;vm.bind(source);runCurrent();val old=readHold!!
        readHold=null;readError=false;source=original.copy(sale=original.sale.copy(userId="other"));vm.bind(source);runCurrent();val current=vm.state.value
        old.complete(Unit);runCurrent();assertEquals(current,vm.state.value);assertFalse(vm.failed);assertEquals(source,vm.boundScope)
    }
    @Test fun delayedRetryCannotClaimScheduledInNewScope()=scenario {
        val vm=model();vm.bind(source);runCurrent();retryHold=CompletableDeferred();vm.retry();runCurrent();val old=retryHold!!
        source=original.copy(project="https://other.invalid");vm.bind(source);runCurrent();old.complete(Unit);runCurrent();assertFalse(vm.scheduled);assertFalse(vm.retrying)
    }
    @Test fun backendMismatchDuringObservationClearsCounts()=scenario {
        val vm=model();vm.bind(source);runCurrent();source=original.copy(project="https://other.invalid");push.value=SyncStatus(pending=3);runCurrent()
        assertNull(vm.state.value);assertNull(vm.boundScope)
    }
    @Test fun failedReadCannotBecomeEmptyQueueAndCanRebind()=scenario {
        val vm=model();readError=true;vm.bind(source);runCurrent();assertTrue(vm.failed);assertNull(vm.state.value)
        readError=false;vm.bind(source);runCurrent();assertFalse(vm.failed);assertEquals(2,vm.state.value!!.pending)
    }
}
