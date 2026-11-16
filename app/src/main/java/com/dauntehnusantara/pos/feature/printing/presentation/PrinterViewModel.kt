package com.dauntehnusantara.pos.feature.printing.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.printing.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class PrinterState(val scope:PrinterScope?=null,val selected:PrinterDevice?=null,val devices:List<PrinterDevice> = emptyList(),
    val loaded:Boolean=false,val busy:Boolean=false,val problem:PrinterProblem?=null,val result:PrinterResult?=null)
@HiltViewModel
class PrinterViewModel @Inject constructor(private val repository:PrinterRepository,private val sales:SaleGateway):ViewModel() {
    private val mutable=MutableStateFlow(PrinterState());val state=mutable.asStateFlow()
    private var generation=0L
    private var source:PullScope?=null;private var action:Job?=null
    fun bind(scope:PullScope?) {
        if(source==scope)return
        generation++;source=scope;action?.cancel()
        val key=scope?.let{PrinterScope(it.project,it.sale.userId,it.sale.outletId)}
        mutable.value=PrinterState(scope=key)
        if(key!=null)launch { val selected=repository.selected(key);state.value.copy(selected=selected) }
    }
    fun leave(){generation++;source=null;action?.cancel();mutable.value=PrinterState()}
    private fun launch(block:suspend ()->PrinterState) {
        if(state.value.busy)return
        val captured=source;val token=generation
        mutable.value=state.value.copy(busy=true,problem=null,result=null)
        action=viewModelScope.launch {
            try{val updated=block();if(generation==token)mutable.value=updated}
            catch(e:CancellationException){throw e}
            catch(e:Exception){if(source==captured&&generation==token)mutable.value=state.value.copy(problem=(e as? PrinterFailure)?.problem?:PrinterProblem.READ)}
            finally{if(source==captured&&generation==token)mutable.value=state.value.copy(busy=false)}
        }
    }
    fun refresh(){if(source!=null)launch {
        val devices=repository.paired();state.value.copy(devices=devices,loaded=true)
    }}
    fun select(device:PrinterDevice?){val key=state.value.scope?:return;launch {
        repository.select(key,device);state.value.copy(selected=device)
    }}
    fun test(){val expected=source?:return;val device=state.value.selected?:return
        launch {
            if(sales.checkoutScope()!=expected.sale)throw PrinterFailure(PrinterProblem.FAILED)
            val result=repository.test(device)
            state.value.copy(result=result)
        }
    }
}
