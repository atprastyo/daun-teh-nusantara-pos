package com.dauntehnusantara.pos

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.core.designsystem.components.*
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w390dp-h844dp")
class FormFieldSizingTest {
    private val compose=createComposeRule()
    @get:Rule val rules:TestRule=RuleChain.outerRule(object:ExternalResource(){override fun before(){
        val app=RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app,ComponentActivity::class.java))
    }}).around(compose)
    private fun height(tag:String)=compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.height
    private fun fields() {
        compose.setContent {DaunTehTheme {Column {
            OutlinedTextField("name",{},Modifier.testTag("login.reference"),label={Text("Email")},singleLine=true,shape=MaterialTheme.shapes.medium)
            ProvideTextStyle(MaterialTheme.typography.headlineLarge) {
                AppTextField("name",{},"Name",Modifier.testTag("shared"))
                MoneyInputField("5000",{},"Amount",Modifier.testTag("money"))
                AppTextField("query",{},"Search",Modifier.testTag("search"),leadingIcon={Icon(Icons.Default.Search,null)})
            }
        }}}
    }
    @Test fun formMoneyAndSearchMatchLoginDespiteSurroundingHeadingStyle() {
        fields()
        for(tag in listOf("shared","money","search"))assertEquals(tag,height("login.reference"),height(tag),1f)
    }
    @Test fun multilineAndValidationCanGrowBeyondSingleLine() {
        compose.setContent {DaunTehTheme {Column {
            AppTextField("value",{},"Name",Modifier.testTag("single"))
            AppTextField("First line\nSecond line\nThird line",{},"Reason",Modifier.testTag("reason"),
                singleLine=false,isError=true,supportingText="Please check this reason")
        }}}
        assertTrue(height("reason")>height("single"))
        compose.onNodeWithText("Please check this reason").assertExists()
        compose.onNodeWithTag("reason").assertTextContains("First line\nSecond line\nThird line")
    }
    @Test fun largeFontGrowsRatherThanBeingClippedToFixedHeight() {
        compose.setContent {DaunTehTheme {
            val density=LocalDensity.current
            Column {
                AppTextField("value",{},"Name",Modifier.testTag("normal"))
                CompositionLocalProvider(LocalDensity provides Density(density.density,2f)) {
                    AppTextField("value",{},"Name",Modifier.testTag("large"))
                }
            }
        }}
        assertTrue(height("large")>height("normal"))
    }
}
