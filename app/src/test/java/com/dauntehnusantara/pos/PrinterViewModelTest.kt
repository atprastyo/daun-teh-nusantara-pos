package com.dauntehnusantara.pos

import androidx.lifecycle.ViewModelStore
import com.dauntehnusantara.pos.feature.printing.domain.*
import com.dauntehnusantara.pos.feature.printing.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    private val source=PullScope(SaleScope("brand","outlet","device","user"),"CASHIER","https://example.invalid")
    private val printer=PrinterDevice("00:11:22:33:44:55","Printer")
    private val saved=mutableMapOf<PrinterScope,PrinterDevice>()
    private var allowed:SaleScope?=source.sale
    private var gate:CompletableDeferred<Unit>?=null
    private var count=0
    private var result=PrinterResult.SENT
    private var failure:PrinterProblem?=null
    private val repo=object:PrinterRepository {
        override suspend fun selected(scope:PrinterScope)=saved[scope]
        override suspend fun select(scope:PrinterScope,device:PrinterDevice?){if(device==null)saved.remove(scope)else saved[scope]=device}
        override suspend fun paired():List<PrinterDevice>{failure?.let{throw PrinterFailure(it)};return listOf(printer)}
        override suspend fun test(device:PrinterDevice):PrinterResult{count++;gate?.await();return result}
    }
    private val sales=object:SaleGateway {
        override suspend fun checkoutScope()=allowed
        override suspend fun backgroundScope()=allowed
        override suspend fun submit(sale:PendingSale):PushResult=error("Printing must never submit a sale")
    }
    private fun model()=PrinterViewModel(repo,sales).also{store.put("printer",it);it.bind(source)}
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){gate?.complete(Unit);store.clear();Dispatchers.resetMain()}
    @Test fun duplicateTapSendsOnceAndUnknownRequiresAnotherExplicitAction()=runTest(dispatcher){
        val vm=model();runCurrent();vm.select(printer);runCurrent();gate=CompletableDeferred();result=PrinterResult.UNKNOWN
        vm.test();vm.test();runCurrent();assertEquals(1,count);assertTrue(vm.state.value.busy)
        gate!!.complete(Unit);runCurrent();assertEquals(PrinterResult.UNKNOWN,vm.state.value.result)
        advanceTimeBy(60000);runCurrent();assertEquals(1,count)
        vm.test();runCurrent();assertEquals(2,count)
    }
    @Test fun scopeChangeCancelsTestAndHidesOldSelectionAndOutcome()=runTest(dispatcher){
        val vm=model();runCurrent();vm.select(printer);runCurrent();gate=CompletableDeferred()
        vm.test();runCurrent();vm.bind(source.copy(sale=source.sale.copy(userId="other")));runCurrent()
        gate!!.complete(Unit);runCurrent();assertNull(vm.state.value.selected);assertNull(vm.state.value.result)
        assertEquals("other",vm.state.value.scope?.user)
    }
    @Test fun currentAccessIsCheckedBeforeTestAndLeavingClearsDisplay()=runTest(dispatcher){
        val vm=model();runCurrent();vm.select(printer);runCurrent();allowed=null
        vm.test();runCurrent();assertEquals(0,count);assertEquals(PrinterProblem.FAILED,vm.state.value.problem)
        vm.leave();assertNull(vm.state.value.scope);assertNull(vm.state.value.selected)
    }
    @Test fun permissionFailureCanBeRetriedWithoutLosingSavedPrinter()=runTest(dispatcher){
        val vm=model();runCurrent();vm.select(printer);runCurrent();failure=PrinterProblem.PERMISSION
        vm.refresh();runCurrent();assertEquals(PrinterProblem.PERMISSION,vm.state.value.problem);assertEquals(printer,vm.state.value.selected)
        failure=null;vm.refresh();runCurrent();assertTrue(vm.state.value.loaded);assertNull(vm.state.value.problem)
    }
}
