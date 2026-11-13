package com.dauntehnusantara.pos.feature.auth.data

import com.dauntehnusantara.pos.core.network.AuthTransport
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.core.common.*
import java.time.Clock
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

data class TokenSession(val accessToken: String, val refreshToken: String, val userId: String, val email: String, val expiresAtMs: Long) { override fun toString() = "TokenSession(redacted)" }
data class EnrolledDevice(val id: String, val secret: String, val userId: String, val outletId: String) { override fun toString() = "EnrolledDevice(redacted)" }
class AuthApi @Inject constructor(private val http: AuthTransport, private val clock: Clock) {
    private fun parseSession(raw: String): TokenSession {
        val json = JSONObject(raw); val user = json.getJSONObject("user")
        return TokenSession(json.getString("access_token"), json.getString("refresh_token"), user.getString("id"), user.getString("email"),
            if (json.has("expires_at")) Math.multiplyExact(json.getLong("expires_at"), 1000) else Math.addExact(clock.millis(), Math.multiplyExact(json.getLong("expires_in"), 1000)))
    }
    suspend fun signIn(email: String, password: String) = parseSession(http.request("/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password).toString()))
    suspend fun refresh(session: TokenSession) = parseSession(http.request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", session.refreshToken).toString()))
    suspend fun activate(hash: String) = parseSession(http.request("/auth/v1/verify", JSONObject().put("token_hash", hash).put("type", "invite").toString()))
    suspend fun password(session: TokenSession, password: String) { http.request("/auth/v1/user", JSONObject().put("password", password).toString(), session.accessToken, "PUT") }
    suspend fun choices(session: TokenSession): List<Membership> {
        http.request("/rest/v1/rpc/accept_staff_invitations", "{}", session.accessToken)
        val rows = JSONArray(http.request("/rest/v1/rpc/get_access_context", "{}", session.accessToken))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> Membership(row.getString("brand_id"), row.getString("brand_name"), row.getString("outlet_id"), row.getString("outlet_name"), Role.valueOf(row.getString("role"))) } }
    }
    suspend fun enroll(session: TokenSession, deviceId: String, outletId: String): EnrolledDevice {
        val json = JSONObject(http.request("/rest/v1/rpc/enroll_device", JSONObject().put("p_device_id", deviceId).put("p_outlet_id", outletId).toString(), session.accessToken))
        require(json.getString("device_id") == deviceId)
        return EnrolledDevice(deviceId, json.getString("device_secret"), session.userId, outletId)
    }
    suspend fun lease(session: TokenSession, device: EnrolledDevice, membership: Membership, anchor: ClockReading): DeviceLease {
        val json = JSONObject(http.request("/rest/v1/rpc/issue_offline_lease", JSONObject().put("p_device_id", device.id).put("p_device_secret", device.secret).toString(), session.accessToken))
        val result = DeviceLease(json.getString("user_id"), json.getString("brand_id"), json.getString("outlet_id"), json.getString("device_id"), Role.valueOf(json.getString("role")),
            json.getLong("issued_at_ms"), json.getLong("expires_at_ms"), anchor.wallMs, anchor.elapsedMs, anchor.bootCount)
        require(result.userId == session.userId && result.deviceId == device.id && result.outletId == membership.outletId && result.brandId == membership.brandId && result.role == membership.role)
        require(result.expiresAtMs >= result.issuedAtMs && result.expiresAtMs - result.issuedAtMs <= OfflineLeaseRules.MAX_DURATION_MS)
        return result
    }
    suspend fun invite(session: TokenSession, membership: Membership, email: String, role: Role) {
        http.request("/functions/v1/invite-staff", JSONObject().put("email", email).put("outlet_id", membership.outletId).put("role", role.name).toString(), session.accessToken)
    }
    suspend fun deviceRequests(session: TokenSession, outlet: String): List<DeviceRequest> {
        val rows = JSONArray(http.request("/rest/v1/rpc/list_device_requests", JSONObject().put("p_outlet_id", outlet).toString(), session.accessToken))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> DeviceRequest(row.getString("device_id"), row.getString("user_id"), row.getString("full_name"), row.getString("email"), Role.valueOf(row.getString("role"))) } }
    }
    suspend fun approveDevice(session: TokenSession, id: String) {
        http.request("/rest/v1/rpc/approve_device", JSONObject().put("p_device_id", id).toString(), session.accessToken)
    }
    suspend fun logout(session: TokenSession) { http.request("/auth/v1/logout?scope=local", "{}", session.accessToken) }
}
