package com.dauntehnusantara.pos.feature.staff.presentation

import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import kotlinx.coroutines.flow.*

enum class StaffTab { MEMBERS, DEVICES, AUDIT }
enum class StaffOutcome { NONE, SAVED, ACK_REFRESH_FAILED, UNCERTAIN, CONFLICT }
sealed interface StaffTarget {
    val scope:PullScope
    data class Member(override val scope:PullScope,val value:StaffMember):StaffTarget
    data class Device(override val scope:PullScope,val value:StaffDevice):StaffTarget
}
data class StaffDraft(val tab:StaffTab=StaffTab.MEMBERS,val target:StaffTarget?=null,val reason:String="")
/** Memory only: never restore an access mutation or reason after process/scope loss. */
class StaffSession {
    private val mutable=MutableStateFlow(StaffDraft());val state=mutable.asStateFlow()
    var generation=0L;private set
    fun clear(){generation++;mutable.value=StaffDraft()}
    fun guard(token:Long,block:()->Unit){if(generation==token)block()}
    fun tab(tab:StaffTab){mutable.update{it.copy(tab=tab)}}
    fun open(target:StaffTarget){mutable.update{it.copy(target=target,reason="")}}
    fun reason(value:String){mutable.update{if(it.target!=null)it.copy(reason=value)else it}}
    fun cancel(){mutable.update{it.copy(target=null,reason="")}}
}
fun staffCanChange(scope:PullScope,m:StaffMember)=StaffRules.mayChange(scope,m)&&(m.active||m.profileActive)
fun staffCanChange(scope:PullScope,d:StaffDevice)=if(d.revoked)StaffRules.mayRestore(scope,d)&&d.role!=null else StaffRules.mayRevoke(scope,d)
fun staffRole(role:String?)=when(role){"OWNER"->"Pemilik";"MANAGER"->"Pengelola";"CASHIER"->"Kasir";else->"Akses anggota perlu ditinjau"}
fun staffAction(target:StaffTarget)=when(target){is StaffTarget.Member->if(target.value.active)"Nonaktifkan akses outlet" else "Aktifkan akses outlet"
    is StaffTarget.Device->if(target.value.revoked)"Pulihkan ke menunggu persetujuan" else "Cabut perangkat"}
