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

@HiltViewModel
class AuthViewModel @Inject constructor(private val repository: AuthUseCases, network: NetworkAvailability) : ViewModel() {
    val state = repository.state
    private var action: Job? = null
    private var networkOnline = false
    init {
        launch { repository.restore() }
        viewModelScope.launch {
            network.validated.catch { emit(false) }.collect { online ->
                networkOnline = online
                val current = state.value
                if (online && !current.busy && (current.stage == AuthStage.LOCKED || current.stage == AuthStage.READY && current.offline)) repository.refresh()
            }
        }
        viewModelScope.launch {
            var seconds = 0
            while (isActive) {
                delay(1000); repository.enforceLease(); seconds++
                if (seconds % 60 == 0 && state.value.stage == AuthStage.READY && (!state.value.offline || networkOnline) && !state.value.busy) repository.refresh()
            }
        }
    }
    private fun launch(block: suspend () -> Unit) {
        if (action?.isActive == true) return
        action = viewModelScope.launch { block() }
    }
    fun signIn(email: String, password: String) = launch { repository.signIn(email, password) }
    fun activate(code: String, password: String) = launch { repository.activate(code, password) }
    fun select(outlet: String) = launch { repository.selectOutlet(outlet) }
    fun refresh() = launch { repository.refresh() }
    fun switchOutlet() = launch { repository.chooseOutlets() }
    fun offline() = launch { repository.unlockOffline() }
    fun signOut() = launch { repository.signOut() }
    fun devices() = launch { repository.loadDeviceRequests() }
    fun approve(id: String) = launch { repository.approveDevice(id) }
    fun invite(email: String, role: Role) = launch { repository.invite(email, role) }
    fun foreground() {
        if (state.value.stage == AuthStage.READY && (!state.value.offline || networkOnline)) refresh()
    }
}
