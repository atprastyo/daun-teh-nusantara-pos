package com.dauntehnusantara.pos.feature.foundation.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.dauntehnusantara.pos.feature.foundation.domain.FoundationRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class StorageState { Loading, Ready, Error }

@HiltViewModel
class FoundationViewModel @Inject constructor(private val repository: FoundationRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(StorageState.Loading)
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    init { retry() }

    fun retry() {
        observation?.cancel()
        observation = viewModelScope.launch {
            mutableState.value = StorageState.Loading
            try {
                repository.observeStorageReady().collect {
                    mutableState.value = if (it) StorageState.Ready else StorageState.Error
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = StorageState.Error
            }
        }
    }
}
