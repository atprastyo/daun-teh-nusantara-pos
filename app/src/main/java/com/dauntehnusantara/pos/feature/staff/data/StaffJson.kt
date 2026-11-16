package com.dauntehnusantara.pos.feature.staff.data

import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.sync.domain.PullScope
import com.dauntehnusantara.pos.feature.reports.data.boundScope
import org.json.JSONObject

internal object StaffJson {
    fun read(j:JSONObject,s:PullScope):StaffRoster {
        j.boundScope(s)
        val m=j.getJSONArray("members");val d=j.getJSONArray("devices");val a=j.getJSONArray("audit")
        val capacity=if(!j.has("capacity")||j.isNull("capacity"))null else j.getJSONObject("capacity").let { c ->
            fun count(key:String):Long {
                val raw=c.get(key).toString();require(raw.matches(Regex("[0-9]+")))
                return raw.toLong()
            }
            StaffCapacity(count("manager_used"),count("cashier_used"),if(c.isNull("manager_limit"))null else count("manager_limit"),if(c.isNull("cashier_limit"))null else count("cashier_limit"))
        }
        return StaffRoster(s,(0 until m.length()).map { m.getJSONObject(it).let { StaffMember(it.getString("user_id"),it.getString("name"),it.getString("email"),it.getString("role"),it.getBoolean("active"),it.getBoolean("profile_active"),it.getLong("version")) } },
            (0 until d.length()).map { d.getJSONObject(it).let { StaffDevice(it.getString("id"),it.getString("user_id"),if(it.isNull("role"))null else it.getString("role"),it.getBoolean("revoked"),it.getBoolean("approved"),it.getLong("version")) } }, (0 until a.length()).map { a.getJSONObject(it).let { StaffAudit(it.getString("id"),it.getString("actor_id"),if(it.isNull("target_id"))null else it.getString("target_id"),it.getString("action"),it.getLong("created_at_ms"),if(it.isNull("reason"))null else it.getString("reason")) } },capacity).also {
                require(it.audit.size<=50 && (s.role=="OWNER" || it.audit.isEmpty()))
                require(it.members.all { m->m.version>0 && m.role in listOf("CASHIER","MANAGER") } && it.devices.all { d->d.version>0 })
                require(it.members.map { m->m.id }.distinct().size==it.members.size && it.devices.map { d->d.id }.distinct().size==it.devices.size)
            }
    }
}
