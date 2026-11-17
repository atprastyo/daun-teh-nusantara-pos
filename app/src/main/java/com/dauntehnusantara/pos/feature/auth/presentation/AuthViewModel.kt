package com.dauntehnusantara.pos.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.auth.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import com.dauntehnusantara.pos.core.common.NetworkAvailability

private const val ACCESS_REFRESH_INTERVAL_SECONDS = 60 * 60

@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: AuthUseCases, network: NetworkAvailability, private val syncScheduler: com.dauntehnusantara.pos.feature.sync.domain.SyncScheduler) : ViewModel() {
    val state = repository.state
    private var action: Job? = null
    private var networkOnline = false
    init {
        launch { repository.restore() }
        viewModelScope.launch {
            network.validated.catch { emit(false) }.collect { online ->
                networkOnline = online
                if (online) syncScheduler.enqueue()
                val current = state.value
                if (online && !current.busy && (current.stage == AuthStage.LOCKED || current.stage == AuthStage.READY && current.offline)) repository.refresh()
            }
        }
        viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1000); repository.enforceLease(); seconds++
                val current = state.value
                if (seconds % 15 == 0 && networkOnline && current.stage == AuthStage.LOCKED &&
                    current.message == AuthMessage.DEVICE_PENDING && !current.busy) repository.refresh()
                else if (seconds % ACCESS_REFRESH_INTERVAL_SECONDS == 0 && state.value.stage == AuthStage.READY && (!state.value.offline || networkOnline) && !state.value.busy) repository.refresh()
            }
        }
    }
    private fun launch(block: suspend () -> Unit) {
        if (action?.isActive == true) return
        action = viewModelScope.launch { block() }
    }
    fun signIn(email: String, password: String) = launch { repository.signIn(email, password) }
    fun select(outlet: String) = launch { repository.selectOutlet(outlet) }
    fun refresh() = launch { repository.refresh() }
    fun switchOutlet() = launch { repository.chooseOutlets() }
    fun offline() = launch { repository.unlockOffline() }
    fun changeInitialPassword(current:String,password:String) = launch { repository.changeInitialPassword(current,password) }
    fun signOut() = launch { repository.signOut() }
    fun devices() = launch { repository.loadDeviceRequests() }
    fun approve(id: String) = launch { repository.approveDevice(id) }
    fun foreground() {
        syncScheduler.enqueue()
        if (state.value.stage == AuthStage.READY && (!state.value.offline || networkOnline)) refresh()
    }
}
