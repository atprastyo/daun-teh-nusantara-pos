package com.dauntehnusantara.pos

import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import com.dauntehnusantara.pos.core.navigation.DoubleBackExitCallback
import org.junit.Assert.*
import org.junit.Test

class DoubleBackExitCallbackTest {
    private var time = 0L
    private var hints = 0
    private var exits = 0
    private val callback = DoubleBackExitCallback({ time }, { hints++ }, { exits++ })

    @Test fun firstPressHintsAndSecondWithinTwoSecondsExits() {
        callback.handleOnBackPressed()
        assertEquals(1,hints); assertEquals(0,exits)
        time = 2_000
        callback.handleOnBackPressed()
        assertEquals(1,hints); assertEquals(1,exits)
    }

    @Test fun expiredPressStartsNewWindow() {
        callback.handleOnBackPressed(); time = 2_001; callback.handleOnBackPressed()
        assertEquals(2,hints); assertEquals(0,exits)
        time++; callback.handleOnBackPressed(); assertEquals(1,exits)
    }

    @Test fun leavingAppResetsConfirmation() {
        callback.handleOnBackPressed(); callback.reset(); time++
        callback.handleOnBackPressed(); assertEquals(2,hints); assertEquals(0,exits)
    }

    @Test fun featureBackTakesPriorityWithoutArmingExit() {
        val dispatcher = OnBackPressedDispatcher()
        dispatcher.addCallback(callback)
        var featureBack = 0
        val feature = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { featureBack++; isEnabled = false }
        }
        dispatcher.addCallback(feature)
        dispatcher.onBackPressed()
        assertEquals(1,featureBack); assertEquals(0,hints); assertEquals(0,exits)
        dispatcher.onBackPressed()
        assertEquals(1,hints); assertEquals(0,exits)
        dispatcher.onBackPressed(); assertEquals(1,exits)
    }
}
