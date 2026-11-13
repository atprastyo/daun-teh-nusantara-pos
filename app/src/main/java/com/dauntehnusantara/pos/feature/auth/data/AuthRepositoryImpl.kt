package com.dauntehnusantara.pos.feature.auth.data

import com.dauntehnusantara.pos.core.network.*
import com.dauntehnusantara.pos.core.security.SecretStore
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.core.common.*
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private class AuthInputFailure : IllegalArgumentException()

@Singleton
class AuthRepositoryImpl @Inject constructor(private val api: AuthApi, private val config: BackendConfig,
    private val store: SecretStore, private val clock: AccessClock) : AuthRepository {
    private val mutable = MutableStateFlow(AuthState(configured = config.configured))
    override val state = mutable.asStateFlow()
    private val mutex = Mutex()
    private var cache = AuthCache(config.url)
    private suspend fun save() = withContext(Dispatchers.IO) { store.write(cache.encode()) }
    private fun offlineValid() = cache.device?.userId == cache.session?.userId && cache.device?.outletId == cache.membership?.outletId &&
        OfflineLeaseRules.valid(cache.lease, cache.session?.userId, cache.membership, cache.device?.id, clock.read())
    private suspend fun clear() {
        cache = AuthCache(config.url)
        withContext(Dispatchers.IO) { store.clear() }
    }
    private suspend fun failure(error: Exception, wasOffline: Boolean) {
        when {
            cache.activationPending && (error is IOException || error is AuthHttpFailure && error.status >= 500) -> {
                mutable.value = AuthState(AuthStage.ACTIVATE, true, activationPending = true, email = cache.session?.email.orEmpty(), message = AuthMessage.UNAVAILABLE)
            }
            error is AuthHttpFailure && error.status == 423 -> {
                cache = cache.copy(lease = null); runCatching { save() }
                mutable.value = mutable.value.copy(stage = AuthStage.LOCKED, offline = false, canOffline = false, pendingDeviceId = cache.device?.id, message = AuthMessage.DEVICE_PENDING)
            }
            error is AuthHttpFailure && error.status in listOf(400, 401, 403) -> {
                runCatching { clear() }
                mutable.value = AuthState(AuthStage.LOGIN, config.configured, message = AuthMessage.DENIED)
            }
            error is IOException || error is AuthHttpFailure && error.status >= 500 -> {
                val valid = offlineValid()
                mutable.value = if (wasOffline && valid) AuthState(AuthStage.READY, true, membership = cache.membership,
                    email = cache.session?.email.orEmpty(), offline = true, leaseExpiresAtMs = cache.lease?.expiresAtMs,
                    canOffline = true, message = AuthMessage.UNAVAILABLE)
                else mutable.value.copy(stage = AuthStage.LOCKED, offline = false, busy = false, canOffline = valid, message = AuthMessage.UNAVAILABLE)
            }
            error is AuthInputFailure -> mutable.value = mutable.value.copy(busy = false, message = AuthMessage.INVALID_INPUT)
            else -> {
                // Malformed/corrupt authorization data is not an offline fallback signal.
                cache = cache.copy(lease = null); runCatching { save() }
                mutable.value = mutable.value.copy(stage = AuthStage.LOCKED, offline = false, busy = false, canOffline = false, message = AuthMessage.DENIED)
            }
        }
    }
    private suspend fun action(block: suspend () -> Unit) = mutex.withLock {
        if (!config.configured) { mutable.value = AuthState(AuthStage.LOGIN, message = AuthMessage.CONFIGURATION); return@withLock }
        val wasOffline = mutable.value.offline
        mutable.value = mutable.value.copy(busy = true, message = null)
        try { block() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure(e, wasOffline) }
        finally { mutable.value = mutable.value.copy(busy = false) }
    }
    private suspend fun <T> authorized(block: suspend (TokenSession) -> T): T {
        var session = requireNotNull(cache.session)
        if (session.expiresAtMs <= clock.read().wallMs + 60000) {
            session = api.refresh(session); cache = cache.copy(session = session); save()
        }
        return try { block(session) } catch (e: AuthHttpFailure) {
            if (e.status != 401) throw e
            session = api.refresh(session); cache = cache.copy(session = session); save()
            block(session)
        }
    }
    private suspend fun loadChoices(keepSelection: Boolean) {
        if (cache.activationPending) { mutable.value = AuthState(AuthStage.ACTIVATE, true, activationPending = true); return }
        val choices = authorized { api.choices(it) }
        if (choices.isEmpty()) {
            cache = cache.copy(membership = null, device = null, lease = null); save()
            mutable.value = AuthState(AuthStage.LOCKED, true, email = cache.session?.email.orEmpty(), message = AuthMessage.NO_ACCESS)
            return
        }
        val previous = if (keepSelection) cache.membership?.let { saved -> choices.find { it.outletId == saved.outletId } } else null
        mutable.value = AuthState(AuthStage.OUTLETS, true, choices = choices, email = cache.session?.email.orEmpty(), busy = true)
        if (previous != null) select(previous)
    }
    private suspend fun select(membership: Membership) {
        // Preserve only the same confirmed context; neither an outage nor a retry extends its old lease.
        val previous = cache.lease?.takeIf { it.outletId == membership.outletId && it.brandId == membership.brandId && it.role == membership.role }
        cache = cache.copy(membership = membership, lease = previous); save()
        var device = cache.device
        if (device == null || device.userId != cache.session?.userId || device.outletId != membership.outletId) {
            device = authorized { api.enroll(it, UUID.randomUUID().toString(), membership.outletId) }
            cache = cache.copy(device = device); save()
        }
        val anchor = clock.read() // Anchor before network round trip: latency cannot extend the lease.
        val selectedDevice = requireNotNull(device)
        val lease = authorized { api.lease(it, selectedDevice, membership, anchor) }
        cache = cache.copy(lease = lease); save()
        mutable.value = AuthState(AuthStage.READY, true, choices = mutable.value.choices, membership = membership,
            email = cache.session?.email.orEmpty(), leaseExpiresAtMs = lease.expiresAtMs, canOffline = offlineValid())
    }
    override suspend fun restore() = action {
        val stored = try { withContext(Dispatchers.IO) { store.read()?.let(AuthCache::decode) } }
        catch (_: Exception) { clear(); null }
        if (stored != null && stored.projectUrl == config.url) cache = stored
        else { clear(); cache = AuthCache(config.url) }
        if (cache.session == null) { mutable.value = AuthState(AuthStage.LOGIN, true); return@action }
        if (cache.activationPending) {
            mutable.value = AuthState(AuthStage.ACTIVATE, true, activationPending = true, email = cache.session?.email.orEmpty()); return@action
        }
        loadChoices(true)
    }
    override suspend fun signIn(email: String, password: String) = action {
        if (!(email.isNotBlank() && email.length <= 254 && '@' in email && password.isNotEmpty())) throw AuthInputFailure()
        clear()
        val session = api.signIn(email.trim(), password)
        cache = AuthCache(config.url, session = session); save()
        loadChoices(false)
    }
    override suspend fun activate(tokenHash: String, password: String) = action {
        if (password.length !in 12..128) throw AuthInputFailure()
        if (!cache.activationPending) {
            if (!(tokenHash.length in 20..512 && tokenHash.none { it.isWhitespace() })) throw AuthInputFailure()
            clear()
            cache = AuthCache(config.url, session = api.activate(tokenHash), activationPending = true); save()
        }
        authorized { api.password(it, password) }
        cache = cache.copy(activationPending = false); save()
        loadChoices(false)
    }
    override suspend fun selectOutlet(outletId: String) = action {
        if (cache.activationPending) { mutable.value = AuthState(AuthStage.ACTIVATE, true, activationPending = true); return@action }
        // Re-query memberships; a stale UI choice cannot enroll a now-forbidden outlet.
        val choices = authorized { api.choices(it) }
        val membership = choices.find { it.outletId == outletId } ?: throw AuthHttpFailure(403)
        mutable.value = mutable.value.copy(choices = choices)
        select(membership)
    }
    override suspend fun chooseOutlets() = action { loadChoices(false) }
    override suspend fun refresh() = action { if (cache.session == null) mutable.value = AuthState(AuthStage.LOGIN, true) else loadChoices(true) }
    override suspend fun unlockOffline() = mutex.withLock {
        if (!offlineValid()) {
            cache = cache.copy(lease = null); runCatching { save() }
            mutable.value = mutable.value.copy(stage = AuthStage.LOCKED, offline = false, canOffline = false, message = AuthMessage.OFFLINE_EXPIRED)
        } else mutable.value = AuthState(AuthStage.READY, true, membership = cache.membership, email = cache.session?.email.orEmpty(),
            offline = true, leaseExpiresAtMs = cache.lease?.expiresAtMs, canOffline = true)
    }
    override suspend fun enforceLease() = mutex.withLock {
        if (mutable.value.offline && !offlineValid()) {
            cache = cache.copy(lease = null); runCatching { save() }
            mutable.value = mutable.value.copy(stage = AuthStage.LOCKED, offline = false, canOffline = false, message = AuthMessage.OFFLINE_EXPIRED)
        }
    }
    override suspend fun loadDeviceRequests() = action {
        val membership = requireNotNull(mutable.value.membership)
        if (mutable.value.offline || membership.role == Role.CASHIER) throw AuthInputFailure()
        val requests = authorized { api.deviceRequests(it, membership.outletId) }
        mutable.value = mutable.value.copy(deviceRequests = requests)
    }
    override suspend fun approveDevice(id: String) = action {
        val membership = requireNotNull(mutable.value.membership)
        if (mutable.value.offline || membership.role == Role.CASHIER) throw AuthInputFailure()
        authorized { api.approveDevice(it, id) }
        val requests = authorized { api.deviceRequests(it, membership.outletId) }
        mutable.value = mutable.value.copy(deviceRequests = requests)
    }
    override suspend fun invite(email: String, role: Role) = action {
        val membership = requireNotNull(mutable.value.membership)
        if (!(!mutable.value.offline && RoleNavigation.mayInvite(membership.role, role) && '@' in email && email.length <= 254)) throw AuthInputFailure()
        try {
            authorized { api.invite(it, membership, email.trim(), role) }
            mutable.value = mutable.value.copy(message = AuthMessage.INVITE_SENT)
        } catch (e: AuthHttpFailure) {
            when {
                e.status == 429 -> mutable.value = mutable.value.copy(message = AuthMessage.RATE_LIMIT)
                e.status == 409 -> mutable.value = mutable.value.copy(message = AuthMessage.INVITE_CONFLICT)
                e.status >= 500 -> mutable.value = mutable.value.copy(message = AuthMessage.INVITE_UNCERTAIN)
                else -> throw e
            }
        }
    }
    override suspend fun signOut() = mutex.withLock {
        val session = cache.session
        // Revoke local offline authority immediately, even if remote logout cannot be reached.
        runCatching { clear() }
        mutable.value = AuthState(AuthStage.LOGIN, config.configured, busy = true)
        if (session != null) runCatching { api.logout(session) }
        mutable.value = mutable.value.copy(busy = false)
    }
}
