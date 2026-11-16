package com.dauntehnusantara.pos.feature.settings.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dauntehnusantara.pos.feature.settings.domain.*
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class AppearanceError { READ, WRITE }
data class AppearanceState(val mode:ThemeMode?=null,val saving:Boolean=false,val error:AppearanceError?=null)
@HiltViewModel
class AppearanceViewModel @Inject constructor(private val repository:AppearanceRepository):ViewModel() {
    private val mutable=MutableStateFlow(AppearanceState());val state=mutable.asStateFlow()
    private var observation:Job?=null
    init{retry()}
    fun retry(){
        if(state.value.saving)return
        observation?.cancel()
        observation=viewModelScope.launch {
            try{repository.theme.collect{mode->mutable.update{it.copy(mode=mode,error=null)}}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){mutable.update{it.copy(mode=it.mode?:ThemeMode.LIGHT,error=AppearanceError.READ)}}
        }
    }
    fun change(mode:ThemeMode){
        if(state.value.saving||state.value.mode==null||state.value.mode==mode)return
        mutable.update{it.copy(saving=true,error=null)}
        viewModelScope.launch {
            try{repository.setTheme(mode);mutable.update{it.copy(mode=mode)}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){mutable.update{it.copy(error=AppearanceError.WRITE)}}
            finally{mutable.update{it.copy(saving=false)}}
        }
    }
}
