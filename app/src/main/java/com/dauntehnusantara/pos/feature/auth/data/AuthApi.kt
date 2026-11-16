package com.dauntehnusantara.pos.feature.auth.data

import com.dauntehnusantara.pos.core.network.AuthTransport
import com.dauntehnusantara.pos.core.network.JsonSafety
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.core.common.*
import java.time.Clock
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

data class TokenSession(val accessToken: String, val refreshToken: String, val userId: String, val email: String, val expiresAtMs: Long) { override fun toString() = "TokenSession(redacted)" }
data class EnrolledDevice(val id: String, val secret: String, val userId: String, val outletId: String) { override fun toString() = "EnrolledDevice(redacted)" }
class AuthApi @Inject constructor(private val http: AuthTransport, private val clock: Clock, private val photos: com.dauntehnusantara.pos.feature.catalog.photos.data.PhotoTransport = com.dauntehnusantara.pos.feature.catalog.photos.data.UnconfiguredPhotoTransport) {
    private fun parseSession(raw: String): TokenSession {
        val json = JsonSafety.obj(raw); val user = json.getJSONObject("user")
        return TokenSession(json.getString("access_token"), json.getString("refresh_token"), user.getString("id"), user.getString("email"),
            if (json.has("expires_at")) Math.multiplyExact(json.getLong("expires_at"), 1000) else Math.addExact(clock.millis(), Math.multiplyExact(json.getLong("expires_in"), 1000)))
    }
    suspend fun signIn(email: String, password: String) = parseSession(http.request("/auth/v1/token?grant_type=password", JSONObject().put("email", email).put("password", password).toString()))
    suspend fun refresh(session: TokenSession) = parseSession(http.request("/auth/v1/token?grant_type=refresh_token", JSONObject().put("refresh_token", session.refreshToken).toString()))
    suspend fun passwordChangeRequired(session:TokenSession):Boolean = try {
        val raw=http.request("/rest/v1/rpc/staff_password_change_required","{}",session.accessToken).trim()
        require(raw in listOf("true","false"));raw=="true"
    } catch(e:com.dauntehnusantara.pos.core.network.AuthHttpFailure) {
        if(e.status==404)false else throw e // Legacy backend has no provisioned temporary accounts.
    }
    suspend fun staffAdmin(session:TokenSession,args:JSONObject):JSONObject =
        JsonSafety.obj(http.request("/functions/v1/staff-admin",args.toString(),session.accessToken))
    suspend fun choices(session: TokenSession): List<Membership> {
        val rows = JsonSafety.array(http.request("/rest/v1/rpc/get_access_context", "{}", session.accessToken))
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> Membership(row.getString("brand_id"), row.getString("brand_name"), row.getString("outlet_id"), row.getString("outlet_name"), Role.valueOf(row.getString("role"))) } }
    }
    suspend fun enroll(session: TokenSession, deviceId: String, outletId: String): EnrolledDevice {
        val json = JsonSafety.obj(http.request("/rest/v1/rpc/enroll_device", JSONObject().put("p_device_id", deviceId).put("p_outlet_id", outletId).toString(), session.accessToken))
        require(json.getString("device_id") == deviceId)
        return EnrolledDevice(deviceId, json.getString("device_secret"), session.userId, outletId)
    }
    suspend fun lease(session: TokenSession, device: EnrolledDevice, membership: Membership, anchor: ClockReading): DeviceLease {
        val json = JsonSafety.obj(http.request("/rest/v1/rpc/issue_offline_lease", JSONObject().put("p_device_id", device.id).put("p_device_secret", device.secret).toString(), session.accessToken))
        val result = DeviceLease(json.getString("user_id"), json.getString("brand_id"), json.getString("outlet_id"), json.getString("device_id"), Role.valueOf(json.getString("role")),
            json.getLong("issued_at_ms"), json.getLong("expires_at_ms"), anchor.wallMs, anchor.elapsedMs, anchor.bootCount)
        require(result.userId == session.userId && result.deviceId == device.id && result.outletId == membership.outletId && result.brandId == membership.brandId && result.role == membership.role)
        require(result.expiresAtMs >= result.issuedAtMs && result.expiresAtMs - result.issuedAtMs <= OfflineLeaseRules.MAX_DURATION_MS)
        return result
    }
    suspend fun reportDeviceName(session:TokenSession,device:EnrolledDevice) {
        http.request("/rest/v1/rpc/set_device_label",JSONObject().put("p_device_id",device.id).put("p_device_secret",device.secret)
            .put("p_manufacturer",DeviceName.part(android.os.Build.MANUFACTURER)).put("p_model",DeviceName.part(android.os.Build.MODEL)).toString(),session.accessToken)
    }
    suspend fun deviceNames(session:TokenSession,outlet:String):Map<String,String> = try {
        val rows=JsonSafety.array(http.request("/rest/v1/rpc/get_device_labels",JSONObject().put("p_outlet_id",outlet).toString(),session.accessToken))
        buildMap {
            for(i in 0 until rows.length()) {
                val row=rows.getJSONObject(i)
                DeviceName.clean(if(row.isNull("device_name"))null else row.getString("device_name"))?.let{put(row.getString("device_id"),it)}
            }
        }
    }catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){emptyMap()} // Optional display data never grants or blocks access.
    suspend fun deviceRequests(session: TokenSession, outlet: String): List<DeviceRequest> {
        val rows = JsonSafety.array(http.request("/rest/v1/rpc/list_device_requests", JSONObject().put("p_outlet_id", outlet).toString(), session.accessToken))
        val names=deviceNames(session,outlet)
        return (0 until rows.length()).map { rows.getJSONObject(it).let { row -> DeviceRequest(row.getString("device_id"), row.getString("user_id"), row.getString("full_name"), row.getString("email"), Role.valueOf(row.getString("role")),names[row.getString("device_id")]) } }
    }
    suspend fun approveDevice(session: TokenSession, id: String) {
        http.request("/rest/v1/rpc/approve_device", JSONObject().put("p_device_id", id).toString(), session.accessToken)
    }
    suspend fun pull(session: TokenSession, device: EnrolledDevice, after: Long, role: String, epoch: String?): JSONObject =
        JsonSafety.obj(http.request("/rest/v1/rpc/pull_changes", JSONObject().put("p_device_id", device.id).put("p_device_secret", device.secret)
            .put("p_after_seq", after).put("p_epoch", epoch?:JSONObject.NULL).put("p_role", role).put("p_limit", 100).toString(), session.accessToken))
    suspend fun submit(session: TokenSession, device: EnrolledDevice, payload: String): JSONObject =
        JsonSafety.obj(http.request("/rest/v1/rpc/submit_sale", JSONObject().put("p_payload", payload)
            .put("p_device_secret", device.secret).toString(), session.accessToken))
    suspend fun management(session: TokenSession, device: EnrolledDevice, name: String, args: JSONObject): String {
        require(name in listOf("get_sales_report","list_staff","set_staff_access","set_device_access","save_catalog_record","set_product_photo"))
        return http.request("/rest/v1/rpc/$name", args.put("p_device_id",device.id).put("p_device_secret",device.secret).toString(),session.accessToken)
    }
    suspend fun logout(session: TokenSession) { http.request("/auth/v1/logout?scope=local", "{}", session.accessToken) }
    suspend fun transferPhoto(token:String,path:String,bytes:ByteArray?=null) = photos.transfer(token,path,bytes)

}
