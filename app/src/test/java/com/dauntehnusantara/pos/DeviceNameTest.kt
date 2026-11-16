package com.dauntehnusantara.pos

import android.app.Application
import com.dauntehnusantara.pos.core.network.AuthTransport
import com.dauntehnusantara.pos.feature.auth.data.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class DeviceNameTest {
    @Test fun deviceLabelSanitizesControlsAndBoundsDisplayOnly() {
        assertEquals("Samsung SM-M146B",DeviceName.clean(" Samsung SM-M146B "))
        assertEquals("name",DeviceName.clean("\u202ename\n"))
        assertNull(DeviceName.clean(" \n "));assertEquals(160,DeviceName.clean("x".repeat(200))!!.length)
        assertEquals(80,DeviceName.part("x".repeat(200)).length)
    }
    @Test fun approvalRequestJoinsOnlyMatchingLabelAndRetainsStableId():Unit=runBlocking {
        val api=AuthApi(object:AuthTransport {
            override suspend fun request(path:String,body:String?,token:String?,method:String)=when {
                path.endsWith("list_device_requests")->"""[{"device_id":"device","user_id":"user","full_name":"Staff","email":"staff@example.invalid","role":"CASHIER"}]"""
                else->"""[{"device_id":"device","device_name":"Samsung SM-M146B"},{"device_id":"foreign","device_name":"Other"}]"""
            }
        },Clock.systemUTC())
        val request=api.deviceRequests(TokenSession("fake","fake","user","staff@example.invalid",0),"outlet").single()
        assertEquals("device",request.deviceId);assertEquals("Samsung SM-M146B",request.deviceName)
    }
}
