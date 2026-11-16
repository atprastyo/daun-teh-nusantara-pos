package com.dauntehnusantara.pos

import androidx.lifecycle.ViewModelStore
import com.dauntehnusantara.pos.core.common.NetworkAvailability
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.auth.presentation.AuthViewModel
import com.dauntehnusantara.pos.feature.sync.domain.SyncScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    private val online = MutableStateFlow(true)
    private val current = MutableStateFlow(AuthState(AuthStage.LOCKED, true, message = AuthMessage.DEVICE_PENDING))
    private var refreshes = 0
    private var approved = false
    private val repository = object : AuthRepository {
        override val state = current.asStateFlow()
        override suspend fun restore() {}
        override suspend fun signIn(email:String,password:String) {}
        override suspend fun selectOutlet(outletId:String) {}
        override suspend fun chooseOutlets() {}
        override suspend fun refresh() { refreshes++; if(approved)current.value = AuthState(AuthStage.READY,true) }
        override suspend fun unlockOffline() {}
        override suspend fun enforceLease() {}
        override suspend fun loadDeviceRequests() {}
        override suspend fun approveDevice(id:String) {}
        override suspend fun signOut() {}
    }
    @Before fun before() { Dispatchers.setMain(dispatcher) }
    @After fun after() { store.clear(); Dispatchers.resetMain() }
    private fun model() = AuthViewModel(AuthUseCases(repository),object:NetworkAvailability {
        override val validated = online
    },object:SyncScheduler {
        override fun enqueue(force:Boolean) {}
        override fun ensurePeriodic() {}
    }).also { store.put("auth",it) }

    @Test fun onlinePendingApprovalIsRecheckedAndStopsWhenReady() = runTest(dispatcher) {
        model(); runCurrent(); val initial = refreshes
        advanceTimeBy(15000); runCurrent(); assertEquals(initial+1,refreshes)
        approved = true
        advanceTimeBy(15000); runCurrent(); assertEquals(AuthStage.READY,current.value.stage)
        val ready = refreshes
        advanceTimeBy(15000); runCurrent(); assertEquals(ready,refreshes)
        store.clear(); runCurrent()
    }
    @Test fun offlineBusyAndOtherLockedStatesDoNotPollApproval() = runTest(dispatcher) {
        online.value=false; model(); runCurrent()
        advanceTimeBy(15000); runCurrent(); assertEquals(0,refreshes)
        current.value=current.value.copy(busy=true); online.value=true; runCurrent()
        advanceTimeBy(15000); runCurrent(); assertEquals(0,refreshes)
        current.value=current.value.copy(busy=false,message=AuthMessage.UNAVAILABLE)
        advanceTimeBy(15000); runCurrent(); assertEquals(0,refreshes)
        store.clear(); runCurrent()
    }
}
