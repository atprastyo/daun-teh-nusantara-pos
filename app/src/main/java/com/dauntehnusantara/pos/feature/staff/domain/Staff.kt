package com.dauntehnusantara.pos.feature.staff.domain

import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import javax.inject.Inject

data class StaffMember(val id:String,val name:String,val email:String,val role:String,val active:Boolean,val profileActive:Boolean,val version:Long)
data class StaffDevice(val id:String,val userId:String,val role:String?,val revoked:Boolean,val approved:Boolean,val version:Long)
data class StaffAudit(val id:String,val actorId:String,val targetId:String?,val action:String,val createdAtMs:Long,val reason:String?)
data class StaffRoster(val scope:PullScope,val members:List<StaffMember>,val devices:List<StaffDevice>,val audit:List<StaffAudit> = emptyList())
object StaffRules {
    fun mayChange(scope:PullScope,m:StaffMember)=m.id!=scope.sale.userId && m.role in listOf("MANAGER","CASHIER") && (scope.role=="OWNER" || scope.role=="MANAGER" && m.role=="CASHIER")
    fun mayRevoke(scope:PullScope,d:StaffDevice)=d.id!=scope.sale.deviceId && (scope.role=="OWNER" || scope.role=="MANAGER" && d.role=="CASHIER")
    fun mayRestore(scope:PullScope,d:StaffDevice)=d.id!=scope.sale.deviceId && scope.role=="OWNER" && d.revoked
    fun reason(value:String) { require(value.trim().length in 3..240) }
}
interface StaffGateway {
    suspend fun roster(scope:PullScope):StaffRoster
    suspend fun memberAccess(scope:PullScope,id:String,active:Boolean,version:Long,reason:String)
    suspend fun deviceAccess(scope:PullScope,id:String,revoked:Boolean,version:Long,reason:String)
}
class ManageStaff @Inject constructor(private val gateway:StaffGateway) {
    suspend fun load(scope:PullScope)=gateway.roster(scope)
    suspend fun member(scope:PullScope,m:StaffMember,reason:String) { require(StaffRules.mayChange(scope,m));StaffRules.reason(reason);gateway.memberAccess(scope,m.id,!m.active,m.version,reason.trim()) }
    suspend fun device(scope:PullScope,d:StaffDevice,reason:String) { require(if(d.revoked)StaffRules.mayRestore(scope,d) else StaffRules.mayRevoke(scope,d));StaffRules.reason(reason);gateway.deviceAccess(scope,d.id,!d.revoked,d.version,reason.trim()) }
}
