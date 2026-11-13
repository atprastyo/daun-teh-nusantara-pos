package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.core.designsystem.WindowLayout
import com.dauntehnusantara.pos.core.designsystem.windowLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowLayoutTest {
    @Test fun compactIncludesWidthsBelow600() {
        assertEquals(WindowLayout.Compact, windowLayout(393))
        assertEquals(WindowLayout.Compact, windowLayout(599))
    }
    @Test fun mediumStartsAt600AndEndsBefore840() {
        assertEquals(WindowLayout.Medium, windowLayout(600))
        assertEquals(WindowLayout.Medium, windowLayout(839))
    }
    @Test fun expandedStartsAt840() {
        assertEquals(WindowLayout.Expanded, windowLayout(840))
        assertEquals(WindowLayout.Expanded, windowLayout(1280))
    }
}
