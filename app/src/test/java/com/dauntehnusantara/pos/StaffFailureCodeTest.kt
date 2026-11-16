package com.dauntehnusantara.pos

import android.app.Application
import com.dauntehnusantara.pos.core.network.readStaffFailureCode
import java.io.ByteArrayInputStream
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class StaffFailureCodeTest {
    @Test fun onlyKnownCodesSurviveAndUntrustedDetailsAreDiscarded() {
        for(code in listOf("STAFF_LIMIT_REACHED","STAFF_LIMITS_UNCONFIGURED"))
            assertEquals(code,readStaffFailureCode(ByteArrayInputStream("{\"code\":\"$code\",\"message\":\"private synthetic details\"}".toByteArray())))
        for(body in listOf("bad json","{}","{\"code\":\"unexpected-sensitive-value\"}"))
            assertNull(readStaffFailureCode(ByteArrayInputStream(body.toByteArray())))
        assertNull(readStaffFailureCode(null))
    }
    @Test fun oversizedBodyCannotReturnCodeAndStreamIsClosed() {
        var closed=false
        val input=object:ByteArrayInputStream(("{\"code\":\"STAFF_LIMIT_REACHED\",\"message\":\""+"x".repeat(10000)+"\"}").toByteArray()) {
            override fun close(){closed=true;super.close()}
        }
        assertNull(readStaffFailureCode(input));assertTrue(closed);assertTrue(input.available()>0)
    }
}
