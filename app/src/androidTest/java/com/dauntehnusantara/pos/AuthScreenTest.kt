package com.dauntehnusantara.pos

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.auth.presentation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import com.dauntehnusantara.pos.core.common.NetworkAvailability
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun clear() { store.clear() }
    private fun model(repository: FakeAuth): AuthViewModel = ViewModelProvider(store, object : ViewModelProvider.Factory {
        override fun <T:ViewModel> create(modelClass:Class<T>):T {
            @Suppress("UNCHECKED_CAST") return AuthViewModel(AuthUseCases(repository), object : NetworkAvailability { override val validated = emptyFlow<Boolean>() }) as T
        }
    })[AuthViewModel::class.java]
    private fun ready(role:Role)=AuthState(AuthStage.READY,true,membership=Membership("brand","Daun Teh","outlet","Outlet A",role))
    @Test fun cashierCannotSeeReportsOrStaff() {
        val repository=FakeAuth(ready(Role.CASHIER));val model=model(repository)
        compose.setContent { DaunTehTheme { AuthRoot(model) {} } }
        compose.onNodeWithText("Laporan").assertDoesNotExist()
        compose.onNodeWithText("Staf").assertDoesNotExist()
        compose.onNodeWithText("Outlet A",substring=true).assertIsDisplayed()
    }
    @Test fun ownerCanRequestInviteButNoOwnerRoleIsOffered() {
        val repository=FakeAuth(ready(Role.OWNER));val model=model(repository)
        compose.setContent { DaunTehTheme { AuthRoot(model) {} } }
        compose.onNodeWithText("Staf").performClick()
        compose.onNodeWithText("Email").performTextInput("new@example.invalid")
        compose.onNodeWithText("MANAGER").performClick()
        compose.onNodeWithText("Kirim undangan").performClick()
        compose.runOnIdle { assertEquals(Role.MANAGER,repository.invited) }
        compose.onNodeWithText("Muat permintaan perangkat").assertExists()
    }
    @Test fun invalidOfflineLeaseCannotLaunchUnlock() {
        val repository=FakeAuth(AuthState(AuthStage.LOCKED,true,canOffline=false));val model=model(repository)
        compose.setContent { DaunTehTheme { AuthRoot(model) {} } }
        compose.onNodeWithText("Buka offline dengan kunci perangkat").assertIsNotEnabled()
    }
    private class FakeAuth(initial:AuthState):AuthRepository {
        override val state=MutableStateFlow(initial)
        var invited:Role?=null
        override suspend fun restore(){}
        override suspend fun signIn(email:String,password:String){}
        override suspend fun activate(tokenHash:String,password:String){}
        override suspend fun selectOutlet(outletId:String){}
        override suspend fun chooseOutlets(){}
        override suspend fun refresh(){}
        override suspend fun unlockOffline(){}
        override suspend fun enforceLease(){}
        override suspend fun loadDeviceRequests(){}
        override suspend fun approveDevice(id:String){}
        override suspend fun invite(email:String,role:Role){invited=role}
        override suspend fun signOut(){}
    }
}
