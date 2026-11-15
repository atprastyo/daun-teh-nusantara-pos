package com.dauntehnusantara.pos

import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import org.junit.Test
import org.junit.Assert.*

class StaffCreationRulesTest {
    private val scope=PullScope(SaleScope("brand","outlet","device","actor"),"OWNER","https://example.invalid")
    private fun command(role:String="MANAGER",email:String="staff@example.invalid",name:String="Staff",password:String="temporary-1234")=StaffCreation(name=name,email=email,role=role,password=password)
    @Test fun rolesMatchCreationHierarchyAndDoNotAllowOwner() {
        command().validate(scope);command("CASHIER").validate(scope.copy(role="MANAGER"))
        for((actor,target) in listOf("MANAGER" to "MANAGER","OWNER" to "OWNER","CASHIER" to "CASHIER"))
            assertTrue(runCatching{command(target).validate(scope.copy(role=actor))}.isFailure)
    }
    @Test fun namesEmailsAndPasswordUtf8BoundsAreValidatedWithoutLeakingCredentials() {
        for(value in listOf("x","x@host","a b@host.invalid"))assertTrue(runCatching{command(email=value).validate(scope)}.isFailure)
        for(value in listOf(""," ","x".repeat(121)))assertTrue(runCatching{command(name=value).validate(scope)}.isFailure)
        assertFalse(StaffCreationRules.password("x".repeat(11)));assertTrue(StaffCreationRules.password("x".repeat(72)))
        assertFalse(StaffCreationRules.password("x".repeat(73)));assertFalse(StaffCreationRules.password("😀".repeat(19)))
        val c=command();assertFalse(c.toString().contains(c.password));assertFalse(c.toString().contains(c.email))
    }
}
