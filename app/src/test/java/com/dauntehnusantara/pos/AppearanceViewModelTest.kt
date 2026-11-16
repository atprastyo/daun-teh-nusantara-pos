package com.dauntehnusantara.pos

import androidx.lifecycle.ViewModelStore
import com.dauntehnusantara.pos.feature.settings.domain.*
import com.dauntehnusantara.pos.feature.settings.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private val dispatcher=StandardTestDispatcher()
    private val store=ViewModelStore()
    private val selected=MutableStateFlow(ThemeMode.LIGHT)
    private var readFailure=false;private var writeFailure=false
    private var pause:CompletableDeferred<Unit>?=null
    private var writes=0
    private val repo=object:AppearanceRepository {
        override val theme=flow{if(readFailure)throw IOException();emitAll(selected)}
        override suspend fun setTheme(mode:ThemeMode){writes++;pause?.await();if(writeFailure)throw IOException();selected.value=mode}
    }
    private fun model()=AppearanceViewModel(repo).also{store.put("appearance",it)}
    @Before fun before(){Dispatchers.setMain(dispatcher)}
    @After fun after(){pause?.complete(Unit);store.clear();Dispatchers.resetMain()}
    @Test fun loadedChoiceWritesOnceAndBlocksConcurrentChanges()=runTest(dispatcher){
        val vm=model();assertNull(vm.state.value.mode);runCurrent();pause=CompletableDeferred()
        vm.change(ThemeMode.DARK);vm.change(ThemeMode.DARK);runCurrent()
        assertTrue(vm.state.value.saving);assertEquals(1,writes);assertEquals(ThemeMode.LIGHT,vm.state.value.mode)
        pause!!.complete(Unit);runCurrent();assertEquals(ThemeMode.DARK,vm.state.value.mode);assertFalse(vm.state.value.saving)
        vm.change(ThemeMode.DARK);runCurrent();assertEquals(1,writes);store.clear();runCurrent()
    }
    @Test fun failedWriteKeepsPreviousAppearanceAndCanRetry()=runTest(dispatcher){
        val vm=model();runCurrent();writeFailure=true;vm.change(ThemeMode.DARK);runCurrent()
        assertEquals(ThemeMode.LIGHT,vm.state.value.mode);assertEquals(AppearanceError.WRITE,vm.state.value.error)
        writeFailure=false;vm.change(ThemeMode.DARK);runCurrent();assertEquals(ThemeMode.DARK,vm.state.value.mode);assertNull(vm.state.value.error)
        store.clear();runCurrent()
    }
    @Test fun readFailureUsesLightAndReloadCanRecoverSavedDark()=runTest(dispatcher){
        readFailure=true;val vm=model();runCurrent();assertEquals(ThemeMode.LIGHT,vm.state.value.mode);assertEquals(AppearanceError.READ,vm.state.value.error)
        readFailure=false;selected.value=ThemeMode.DARK;vm.retry();runCurrent();assertEquals(ThemeMode.DARK,vm.state.value.mode);assertNull(vm.state.value.error)
        store.clear();runCurrent()
    }
}
