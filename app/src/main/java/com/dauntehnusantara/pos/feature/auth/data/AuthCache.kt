package com.dauntehnusantara.pos.feature.auth.data

import com.dauntehnusantara.pos.feature.auth.domain.*
import org.json.JSONObject

internal data class AuthCache(val projectUrl: String, val session: TokenSession? = null, val membership: Membership? = null,
    val device: EnrolledDevice? = null, val lease: DeviceLease? = null, val activationPending: Boolean = false) {
    fun encode(): String = JSONObject().put("project", projectUrl).put("activation_pending", activationPending).apply {
        session?.let { put("session", JSONObject().put("access", it.accessToken).put("refresh", it.refreshToken).put("user", it.userId).put("email", it.email).put("expires", it.expiresAtMs)) }
        membership?.let { put("membership", JSONObject().put("brand", it.brandId).put("brand_name", it.brandName).put("outlet", it.outletId).put("outlet_name", it.outletName).put("role", it.role.name)) }
        device?.let { put("device", JSONObject().put("id", it.id).put("secret", it.secret).put("user", it.userId).put("outlet", it.outletId)) }
        lease?.let { put("lease", JSONObject().put("user", it.userId).put("brand", it.brandId).put("outlet", it.outletId).put("device", it.deviceId)
            .put("role", it.role.name).put("issued", it.issuedAtMs).put("expires", it.expiresAtMs).put("wall", it.anchorWallMs).put("elapsed", it.anchorElapsedMs).put("boot", it.bootCount)) }
    }.toString()
    companion object {
        fun decode(value: String): AuthCache {
            val json = JSONObject(value)
            return AuthCache(json.getString("project"), json.optJSONObject("session")?.let {
                TokenSession(it.getString("access"), it.getString("refresh"), it.getString("user"), it.getString("email"), it.getLong("expires"))
            }, json.optJSONObject("membership")?.let {
                Membership(it.getString("brand"), it.getString("brand_name"), it.getString("outlet"), it.getString("outlet_name"), Role.valueOf(it.getString("role")))
            }, json.optJSONObject("device")?.let {
                EnrolledDevice(it.getString("id"), it.getString("secret"), it.getString("user"), it.getString("outlet"))
            }, json.optJSONObject("lease")?.let {
                DeviceLease(it.getString("user"), it.getString("brand"), it.getString("outlet"), it.getString("device"), Role.valueOf(it.getString("role")),
                    it.getLong("issued"), it.getLong("expires"), it.getLong("wall"), it.getLong("elapsed"), it.getInt("boot"))
            }, json.optBoolean("activation_pending", false))
        }
    }
}
