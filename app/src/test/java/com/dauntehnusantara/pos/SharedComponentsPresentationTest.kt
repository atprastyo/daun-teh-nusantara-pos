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

import com.dauntehnusantara.pos.core.designsystem.components.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.input.key.Key
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.time.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w390dp-h844dp")
class SharedComponentsPresentationTest {
    val compose=createComposeRule()
    @get:Rule val rules:TestRule=RuleChain.outerRule(object:ExternalResource(){
        override fun before(){val app=RuntimeEnvironment.getApplication();Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app,ComponentActivity::class.java))}
    }).around(compose)
    @Test fun moneyPasteClearAndMiddleEditingKeepRawDigitsAndCursorStable() {
        var raw by mutableStateOf("12345")
        compose.setContent{DaunTehTheme{MoneyInputField(raw,{raw=it},"Amount",Modifier.testTag("money"))}}
        val field=compose.onNodeWithTag("money")
        field.assertTextContains("12.345")
        field.performTextInputSelection(TextRange(2));field.performTextInput("9")
        compose.runOnIdle{assertEquals("129345",raw)}
        field.performKeyInput{pressKey(Key.Backspace)};compose.runOnIdle{assertEquals("12345",raw)}
        field.performTextClearance();compose.runOnIdle{assertEquals("",raw)}
        field.performTextInput("Rp 10.000");compose.runOnIdle{assertEquals("10000",raw);assertEquals(10000L,moneyInputLongOrNull(raw))}
        field.assertTextContains("10.000")
    }
    @Test fun malformedMoneyIsNotSilentlyChangedIntoAnotherAmount() {
        var raw by mutableStateOf("")
        compose.setContent{DaunTehTheme{MoneyInputField(raw,{raw=it},"Amount",Modifier.testTag("money"),isError=raw.isNotEmpty()&&moneyInputLongOrNull(raw)==null)}}
        compose.onNodeWithTag("money").performTextInput("1,50")
        compose.runOnIdle{assertEquals("1,50",raw);assertNull(moneyInputLongOrNull(raw))}
        compose.onNodeWithTag("money").assertTextContains("1,50")
    }
    @Test fun confirmationBusyBlocksSubmitAndAllowsActionAfterCompletion() {
        var busy by mutableStateOf(true);var submissions=0
        compose.setContent{DaunTehTheme{AppConfirmation("Review",onDismiss={},busy=busy,actions={
            AppPrimaryButton("Confirm",{submissions++},Modifier.testTag("confirm"),busy=busy)
        }){Text("Original effect stays feature-owned")}}}
        compose.onNodeWithTag("confirm").assertIsDisplayed().assertIsNotEnabled()
        compose.runOnIdle{assertEquals(0,submissions);busy=false}
        compose.onNodeWithTag("confirm").assertIsEnabled().performClick();compose.runOnIdle{assertEquals(1,submissions)}
    }
}
