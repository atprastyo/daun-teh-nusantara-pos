package com.dauntehnusantara.pos

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.staff.presentation.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.feature.auth.presentation.InitialPasswordScreen
import com.dauntehnusantara.pos.feature.sync.domain.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w390dp-h844dp")
class StaffCreationPresentationTest {
    val compose=createComposeRule()
    @get:Rule val rules:TestRule=RuleChain.outerRule(object:ExternalResource(){
        override fun before(){val app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app,ComponentActivity::class.java))}
    }).around(compose)
    private val scope=PullScope(SaleScope("brand","outlet","device","actor"),"OWNER","https://example.invalid")
    private fun enter(tag:String,value:String)=compose.onNodeWithTag(tag).performScrollTo().performTextInput(value)
    @Test fun managerOnlyCashierAndValidFormCapturesOutletWithoutAutoSubmit() {
        var sent:StaffCreation?=null
        compose.setContent{DaunTehTheme{StaffCreateForm(scope.copy(role="MANAGER"),"Outlet A",false,null,null,{sent=it},{})}}
        compose.onNodeWithTag("staff.create.role.MANAGER").assertDoesNotExist()
        compose.onNodeWithTag("staff.create.submit").assertIsNotEnabled()
        enter("staff.create.name","Rani");enter("staff.create.email","rani@example.invalid")
        enter("staff.create.password","temporary-1234");enter("staff.create.confirm","temporary-1234")
        compose.runOnIdle{assertNull(sent)}
        compose.onNodeWithTag("staff.create.submit").assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle{assertEquals("CASHIER",sent?.role);assertEquals("Rani",sent?.name);assertFalse(sent.toString().contains("temporary"))}
    }
    @Test @Config(qualifiers="w1200dp-h900dp") fun ownerLargeTextFormActionsReachableAndBusyBlocksCreation() {
        var busy by mutableStateOf(false)
        compose.setContent{val density=LocalDensity.current;CompositionLocalProvider(LocalDensity provides Density(density.density,2f)){
            DaunTehTheme{StaffCreateForm(scope,"Outlet A",busy,null,null,{},{})}
        }}
        enter("staff.create.name","Bima");enter("staff.create.email","bima@example.invalid")
        compose.onNodeWithTag("staff.create.role.MANAGER").performScrollTo().performClick()
        enter("staff.create.password","temporary-1234");enter("staff.create.confirm","temporary-1234")
        compose.onNodeWithTag("staff.create.submit").assertIsDisplayed().assertIsEnabled()
        compose.runOnIdle{busy=true}
        compose.onNodeWithTag("staff.create.submit").assertIsNotEnabled()
        compose.onNodeWithTag("staff.create.email").performScrollTo().assertIsNotEnabled()
    }
    @Test fun firstPasswordRequiresDifferentMatchingPassword() {
        var submissions=0
        compose.setContent{DaunTehTheme{InitialPasswordScreen(AuthState(AuthStage.PASSWORD_CHANGE,true),{_,_->submissions++},{})}}
        enter("password.current","temporary-1234");enter("password.new","temporary-1234");enter("password.confirm","temporary-1234")
        compose.onNodeWithTag("password.save").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("password.new").performScrollTo().performTextClearance();enter("password.new","personal-5678")
        compose.onNodeWithTag("password.confirm").performScrollTo().performTextClearance();enter("password.confirm","personal-5678")
        compose.onNodeWithTag("password.save").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle{assertEquals(1,submissions)}
    }
}
