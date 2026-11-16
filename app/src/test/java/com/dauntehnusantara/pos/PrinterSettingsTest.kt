package com.dauntehnusantara.pos

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.dauntehnusantara.pos.core.designsystem.DaunTehTheme
import com.dauntehnusantara.pos.feature.printing.domain.*
import com.dauntehnusantara.pos.feature.printing.presentation.*
import org.junit.Assert.*
import org.junit.*
import org.junit.rules.*
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class,qualifiers="w390dp-h844dp")
class PrinterSettingsTest {
    private val compose=createComposeRule()
    @get:Rule val rules:TestRule=RuleChain.outerRule(object:ExternalResource(){override fun before(){
        val app=RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app,ComponentActivity::class.java))
    }}).around(compose)
    private val scope=PrinterScope("project","user","outlet")
    private fun content(state:PrinterState,onTest:()->Unit={}) {
        compose.setContent {DaunTehTheme {Column(Modifier.verticalScroll(rememberScrollState())) {
            PrinterSettings(state,true,{}, {},onTest,{}, {})
        }}}
    }
    @Test fun selectedPrinterNeedsExplicitConfirmationBeforeFictionalTest() {
        var tests=0
        content(PrinterState(scope,PrinterDevice("00:11:22:33:44:55","Generic printer"))){tests++}
        compose.onNodeWithText("Cetak tes").performScrollTo().performClick()
        assertEquals(0,tests)
        compose.onNodeWithText("Kirim teks tes").performClick()
        assertEquals(1,tests)
    }
    @Test fun missingPrinterAndBusyStateDisableTest() {
        content(PrinterState(scope,busy=true,problem=PrinterProblem.OFF))
        compose.onNodeWithText("Cetak tes").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Muat printer berpasangan").assertIsNotEnabled()
        compose.onNodeWithText("Bluetooth mati. Aktifkan melalui pengaturan Bluetooth.").assertExists()
    }
    @Test @Config(qualifiers="w1100dp-h800dp") fun tabletUnknownOutcomeNeverClaimsPaperSuccess() {
        content(PrinterState(scope,result=PrinterResult.UNKNOWN))
        compose.onNodeWithText("Hasil cetak belum dapat dipastikan. Periksa printer sebelum mengirim tes lagi.").assertExists()
        compose.onNodeWithText("Cetak tes").performScrollTo().assertIsNotEnabled()
    }
}
