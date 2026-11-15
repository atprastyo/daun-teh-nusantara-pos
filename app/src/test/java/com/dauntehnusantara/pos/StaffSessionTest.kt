package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.staff.presentation.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import org.junit.Test
import org.junit.Assert.*

class StaffSessionTest {
    private val source=PullScope(SaleScope("brand","outlet","device","owner"),"OWNER","https://example.invalid")
    private val member=StaffMember("cashier","Rani","rani@example.invalid","CASHIER",true,true,1)
    @Test fun reasonHasTrimmedBoundsWithoutTruncatingDraft() {
        val session=StaffSession();session.open(StaffTarget.Member(source,member));val long="x".repeat(241);session.reason(long);assertEquals(long,session.state.value.reason)
        listOf("  ","ab",long).forEach{assertTrue(runCatching{StaffRules.reason(it)}.isFailure)}
        listOf("  abc  ","x".repeat(240)).forEach{StaffRules.reason(it)}
    }
    @Test fun cancelledAndOldScopeCallbacksCannotRestoreDestructiveIntent() {
        val session=StaffSession();session.tab(StaffTab.DEVICES);session.open(StaffTarget.Member(source,member));session.reason("reviewed");session.cancel()
        assertNull(session.state.value.target);assertEquals(StaffTab.DEVICES,session.state.value.tab)
        val token=session.generation;session.clear();session.guard(token){session.open(StaffTarget.Member(source,member))};assertEquals(StaffDraft(),session.state.value)
    }
    @Test fun inactiveProfilesAndCurrentOrUnknownRoleDevicesKeepSeparateRules() {
        assertFalse(staffCanChange(source,member.copy(active=false,profileActive=false)))
        assertTrue(staffCanChange(source,member.copy(profileActive=false)))
        assertFalse(staffCanChange(source,StaffDevice("device","owner","OWNER",false,true,1)))
        assertTrue(staffCanChange(source,StaffDevice("other","owner",null,false,true,1)))
        assertFalse(staffCanChange(source,StaffDevice("other","owner",null,true,true,1)))
        assertFalse(staffCanChange(source.copy(role="MANAGER"),StaffDevice("other","cashier","CASHIER",true,false,1)))
    }
}
