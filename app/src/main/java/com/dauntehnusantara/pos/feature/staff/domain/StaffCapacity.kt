package com.dauntehnusantara.pos.feature.staff.domain

/** Counts include disabled memberships and in-flight reservations, once per user/role/outlet. */
data class StaffCapacity(val managerUsed:Long,val cashierUsed:Long,val managerLimit:Long?,val cashierLimit:Long?) {
    init {
        require(managerUsed>=0&&cashierUsed>=0)
        require(managerLimit==null||managerLimit>=0);require(cashierLimit==null||cashierLimit>=0)
        require((managerLimit==null)==(cashierLimit==null))
    }
    val configured get()=managerLimit!=null&&cashierLimit!=null
    fun canCreate(role:String)=when(role){"MANAGER"->managerLimit?.let{managerUsed<it}==true;"CASHIER"->cashierLimit?.let{cashierUsed<it}==true;else->false}
    fun anyAvailable(actorRole:String)=StaffCreationRules.roles(actorRole).any(::canCreate)
}
