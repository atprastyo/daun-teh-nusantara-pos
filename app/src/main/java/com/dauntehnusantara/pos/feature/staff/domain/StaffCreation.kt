package com.dauntehnusantara.pos.feature.staff.domain

import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import java.util.UUID

/** Credentials live only in memory, never in saved state, Room, audit or toString. */
class StaffCreation(val operationId:String=UUID.randomUUID().toString(),val name:String,val email:String,val role:String,val password:String) {
    override fun toString()="StaffCreation(redacted)"
    fun validate(scope:PullScope) {
        UUID.fromString(operationId)
        require(role in StaffCreationRules.roles(scope.role))
        require(name.trim().length in 1..120)
        require(email.trim().length<=254&&email.trim().matches(Regex("[a-zA-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)+")))
        require(StaffCreationRules.password(password))
    }
}
object StaffCreationRules {
    fun roles(role:String)=when(role){"OWNER"->listOf("MANAGER","CASHIER");"MANAGER"->listOf("CASHIER");else->emptyList()}
    fun password(value:String)=value.length in 12..72&&value.toByteArray(Charsets.UTF_8).size<=72
}
