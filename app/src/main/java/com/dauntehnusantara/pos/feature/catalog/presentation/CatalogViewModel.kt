package com.dauntehnusantara.pos.feature.catalog.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import com.dauntehnusantara.pos.feature.catalog.domain.*
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogUiState(
    val catalog: Catalog? = null, val loading: Boolean = true, val failed: Boolean = false,
    val seeding: Boolean = false, val seedFailed: Boolean = false,
)

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val observe: ObserveCatalog, private val loadDemo: LoadDemoCatalog,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CatalogUiState())
    val state = mutableState.asStateFlow()
    private var observation: Job? = null
    private var seedJob: Job? = null
    init { retry() }
    fun retry() {
        observation?.cancel()
        observation = viewModelScope.launch {
            mutableState.update { it.copy(loading = true, failed = false) }
            try {
                observe(DEMO_BRAND_ID).collect { catalog ->
                    mutableState.update { it.copy(catalog = catalog, loading = false, failed = false) }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, failed = true) } }
        }
    }
    fun seedDemo() {
        if (seedJob?.isActive == true) return
        seedJob = viewModelScope.launch {
            mutableState.update { it.copy(seeding = true, seedFailed = false) }
            try { loadDemo() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(seedFailed = true) } }
            finally { mutableState.update { it.copy(seeding = false) } }
        }
    }
}
