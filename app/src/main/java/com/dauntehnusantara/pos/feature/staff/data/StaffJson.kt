package com.dauntehnusantara.pos.feature.staff.data

import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import com.dauntehnusantara.pos.feature.reports.data.boundScope
import org.json.JSONObject

internal object StaffJson {
    fun read(j:JSONObject,s:PullScope):StaffRoster {
        j.boundScope(s)
        val m=j.getJSONArray("members");val d=j.getJSONArray("devices");val a=j.getJSONArray("audit")
        return StaffRoster(s,(0 until m.length()).map { m.getJSONObject(it).let { StaffMember(it.getString("user_id"),it.getString("name"),it.getString("email"),it.getString("role"),it.getBoolean("active"),it.getBoolean("profile_active"),it.getLong("version")) } },
            (0 until d.length()).map { d.getJSONObject(it).let { StaffDevice(it.getString("id"),it.getString("user_id"),if(it.isNull("role"))null else it.getString("role"),it.getBoolean("revoked"),it.getBoolean("approved"),it.getLong("version")) } }, (0 until a.length()).map { a.getJSONObject(it).let { StaffAudit(it.getString("id"),it.getString("actor_id"),if(it.isNull("target_id"))null else it.getString("target_id"),it.getString("action"),it.getLong("created_at_ms"),if(it.isNull("reason"))null else it.getString("reason")) } }).also {
                require(it.audit.size<=50 && (s.role=="OWNER" || it.audit.isEmpty()))
                require(it.members.all { m->m.version>0 && m.role in listOf("CASHIER","MANAGER") } && it.devices.all { d->d.version>0 })
                require(it.members.map { m->m.id }.distinct().size==it.members.size && it.devices.map { d->d.id }.distinct().size==it.devices.size)
            }
    }
}
