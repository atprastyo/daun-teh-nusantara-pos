package com.dauntehnusantara.pos.feature.auth.domain

import kotlinx.coroutines.flow.StateFlow
import com.dauntehnusantara.pos.core.common.ClockReading

enum class Role { OWNER, MANAGER, CASHIER }
data class Membership(val brandId: String, val brandName: String, val outletId: String, val outletName: String, val role: Role)
data class DeviceLease(val userId: String, val brandId: String, val outletId: String, val deviceId: String, val role: Role,
    val issuedAtMs: Long, val expiresAtMs: Long, val anchorWallMs: Long, val anchorElapsedMs: Long, val bootCount: Int)
object OfflineLeaseRules {
    const val MAX_DURATION_MS = 31L * 24 * 60 * 60 * 1000
    fun valid(lease: DeviceLease?, userId: String?, membership: Membership?, deviceId: String?, now: ClockReading): Boolean {
        if (lease == null || membership == null || userId == null || deviceId == null) return false
        if (!now.deviceSecure || now.bootCount < 0 || now.bootCount != lease.bootCount) return false
        if (lease.userId != userId || lease.deviceId != deviceId || lease.brandId != membership.brandId ||
            lease.outletId != membership.outletId || lease.role != membership.role) return false
        if (lease.issuedAtMs < 0 || lease.expiresAtMs <= lease.issuedAtMs || lease.anchorWallMs < 0 || lease.anchorElapsedMs < 0) return false
        val duration = lease.expiresAtMs - lease.issuedAtMs
        if (duration > MAX_DURATION_MS) return false
        if (now.wallMs < lease.anchorWallMs || now.elapsedMs < lease.anchorElapsedMs) return false
        return now.wallMs - lease.anchorWallMs < duration && now.elapsedMs - lease.anchorElapsedMs < duration
    }
}
enum class AuthStage { LOADING, LOGIN, OUTLETS, READY, LOCKED, PASSWORD_CHANGE }
enum class AuthMessage { RATE_LIMIT, DEVICE_PENDING, CONFIGURATION, INVALID_INPUT, SIGN_IN_FAILED, UNAVAILABLE, NO_ACCESS, DENIED, OFFLINE_EXPIRED }
data class DeviceRequest(val deviceId: String, val userId: String, val fullName: String, val email: String, val role: Role,val deviceName:String?=null)
data class AuthState(val stage: AuthStage = AuthStage.LOADING, val configured: Boolean = false,
    val choices: List<Membership> = emptyList(), val membership: Membership? = null, val email: String = "",
    val offline: Boolean = false, val leaseExpiresAtMs: Long? = null, val canOffline: Boolean = false,
    val busy: Boolean = false, val pendingDeviceId: String? = null, val deviceRequests: List<DeviceRequest> = emptyList(), val message: AuthMessage? = null,
    val deviceRequestsLoaded: Boolean = false,
    // Display context only: LOCKED context never grants access to the authenticated shell.
    val lockedContext: Membership? = null)
interface AuthRepository {
    val state: StateFlow<AuthState>
    suspend fun restore()
    suspend fun signIn(email: String, password: String)
    suspend fun selectOutlet(outletId: String)
    suspend fun chooseOutlets()
    suspend fun refresh()
    suspend fun unlockOffline()
    suspend fun enforceLease()
    suspend fun loadDeviceRequests()
    suspend fun approveDevice(id: String)
    suspend fun changeInitialPassword(current:String,password:String) { throw UnsupportedOperationException() }
    suspend fun signOut()
}
enum class AccessDestination { OVERVIEW, POS, HISTORY, CATALOG, STOCK, REPORTS, STAFF, SETTINGS }
object RoleNavigation {
    fun destinations(role: Role): List<AccessDestination> = when (role) {
        Role.CASHIER -> listOf(AccessDestination.POS, AccessDestination.HISTORY, AccessDestination.SETTINGS)
        Role.OWNER -> listOf(AccessDestination.OVERVIEW, AccessDestination.POS, AccessDestination.HISTORY, AccessDestination.CATALOG, AccessDestination.STOCK, AccessDestination.REPORTS, AccessDestination.STAFF, AccessDestination.SETTINGS)
        Role.MANAGER -> listOf(AccessDestination.OVERVIEW, AccessDestination.POS, AccessDestination.HISTORY, AccessDestination.CATALOG, AccessDestination.STOCK, AccessDestination.REPORTS, AccessDestination.STAFF, AccessDestination.SETTINGS)
    }
}

class AuthUseCases @javax.inject.Inject constructor(private val repository: AuthRepository) {
    val state get() = repository.state
    suspend fun restore() = repository.restore()
    suspend fun signIn(email: String, password: String) = repository.signIn(email, password)
    suspend fun selectOutlet(outlet: String) = repository.selectOutlet(outlet)
    suspend fun chooseOutlets() = repository.chooseOutlets()
    suspend fun refresh() = repository.refresh()
    suspend fun unlockOffline() = repository.unlockOffline()
    suspend fun enforceLease() = repository.enforceLease()
    suspend fun loadDeviceRequests() = repository.loadDeviceRequests()
    suspend fun approveDevice(id: String) = repository.approveDevice(id)
    suspend fun changeInitialPassword(current:String,password:String) = repository.changeInitialPassword(current,password)
    suspend fun signOut() = repository.signOut()
}

/** Recover only the public device ID for this account's retained queue; never credentials. */
fun interface DeviceEnrollmentLookup { suspend fun retainedDevice(userId: String, outletId: String): String? }
