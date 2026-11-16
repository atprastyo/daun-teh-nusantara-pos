package com.dauntehnusantara.pos

import android.app.Application
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.staff.data.StaffJson
import com.dauntehnusantara.pos.feature.sync.domain.*
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class StaffCapacityTest {
    @Test fun separateRoleCapsZeroMissingAndOverLimitStayFailClosed() {
        val quota=StaffCapacity(2,3,2,5)
        assertFalse(quota.canCreate("MANAGER"));assertTrue(quota.canCreate("CASHIER"));assertTrue(quota.anyAvailable("MANAGER"))
        assertFalse(quota.canCreate("OWNER"));assertFalse(quota.anyAvailable("CASHIER"))
        assertFalse(StaffCapacity(0,0,0,0).anyAvailable("OWNER"))
        assertFalse(StaffCapacity(5,8,2,4).anyAvailable("OWNER"))
        assertFalse(StaffCapacity(1,2,null,null).configured)
        assertTrue(runCatching{StaffCapacity(-1,0,2,4)}.isFailure)
        assertTrue(runCatching{StaffCapacity(0,0,2,null)}.isFailure)
    }
    private val scope=PullScope(SaleScope("brand","outlet","device","actor"),"OWNER","https://example.invalid")
    private fun roster()=JSONObject().put("schema_version",1).put("brand_id","brand").put("outlet_id","outlet").put("device_id","device").put("user_id","actor").put("role","OWNER")
        .put("members",org.json.JSONArray()).put("devices",org.json.JSONArray()).put("audit",org.json.JSONArray())
    @Test fun legacyMissingCountsAndExactLongCapacityParsing() {
        assertNull(StaffJson.read(roster(),scope).capacity)
        val cap=JSONObject().put("manager_used",2).put("cashier_used",3).put("manager_limit",2).put("cashier_limit",5)
        assertEquals(StaffCapacity(2,3,2,5),StaffJson.read(roster().put("capacity",cap),scope).capacity)
        for(invalid in listOf(-1,1.5,"9223372036854775808")) {
            assertTrue(runCatching{StaffJson.read(roster().put("capacity",JSONObject(cap.toString()).put("cashier_used",invalid)),scope)}.isFailure)
        }
    }
}
