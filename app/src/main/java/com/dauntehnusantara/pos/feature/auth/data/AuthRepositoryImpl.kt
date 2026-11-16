package com.dauntehnusantara.pos.feature.auth.data

import com.dauntehnusantara.pos.core.network.*
import com.dauntehnusantara.pos.core.security.SecretStore
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.core.common.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import org.json.JSONObject
import com.dauntehnusantara.pos.feature.reports.domain.*
import com.dauntehnusantara.pos.feature.reports.data.ReportJson
import com.dauntehnusantara.pos.feature.staff.domain.*
import com.dauntehnusantara.pos.feature.staff.data.StaffJson
import com.dauntehnusantara.pos.feature.catalog.domain.*
import com.dauntehnusantara.pos.feature.catalog.data.CatalogCommandJson
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

import com.dauntehnusantara.pos.feature.catalog.photos.domain.*

private class AuthInputFailure : IllegalArgumentException()

@Singleton
class AuthRepositoryImpl @Inject constructor(private val api: AuthApi, private val config: BackendConfig,
    private val store: SecretStore, private val clock: AccessClock, private val enrollmentLookup: DeviceEnrollmentLookup) : AuthRepository, SaleGateway, PullGateway, ReportingGateway, StaffGateway, CatalogGateway, ProductPhotoGateway {
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
            error is AuthHttpFailure && error.status == 423 -> {
                cache = cache.copy(lease = null); runCatching { save() }
                mutable.value = mutable.value.copy(stage = AuthStage.LOCKED, membership = null, deviceRequests = emptyList(), deviceRequestsLoaded = false, offline = false, canOffline = false, pendingDeviceId = cache.device?.id, lockedContext = cache.membership, message = AuthMessage.DEVICE_PENDING)
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
                else mutable.value.copy(stage = AuthStage.LOCKED, membership = null, deviceRequests = emptyList(), deviceRequestsLoaded = false, offline = false, busy = false, canOffline = valid, lockedContext = if (valid) cache.membership else null, leaseExpiresAtMs = if (valid) cache.lease?.expiresAtMs else null, message = AuthMessage.UNAVAILABLE)
            }
            error is AuthInputFailure -> mutable.value = mutable.value.copy(busy = false, message = AuthMessage.INVALID_INPUT)
            else -> {
                // Malformed/corrupt authorization data is not an offline fallback signal.
                cache = cache.copy(lease = null); runCatching { save() }
                mutable.value = mutable.value.copy(stage = AuthStage.LOCKED, membership = null, deviceRequests = emptyList(), deviceRequestsLoaded = false, offline = false, busy = false, canOffline = false, lockedContext = null, message = AuthMessage.DENIED)
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
    private suspend fun refreshTokenSession(session: TokenSession): TokenSession = try { api.refresh(session) }
    catch (e: AuthHttpFailure) { if (e.status == 400) throw AuthHttpFailure(401) else throw e }
    private suspend fun <T> authorized(block: suspend (TokenSession) -> T): T {
        var session = requireNotNull(cache.session)
        if (session.expiresAtMs <= clock.read().wallMs + 60000) {
            session = refreshTokenSession(session); cache = cache.copy(session = session); save()
        }
        return try { block(session) } catch (e: AuthHttpFailure) {
            if (e.status != 401) throw e
            session = refreshTokenSession(session); cache = cache.copy(session = session); save()
            block(session)
        }
    }
    private suspend fun loadChoices(keepSelection: Boolean) {
        if(authorized { api.passwordChangeRequired(it) }) {
            cache=cache.copy(membership=null,device=null,lease=null);save()
            mutable.value=AuthState(AuthStage.PASSWORD_CHANGE,true,email=cache.session?.email.orEmpty())
            return
        }
        val choices = authorized { api.choices(it) }
        if (choices.isEmpty()) {
            cache = cache.copy(membership = null, device = null, lease = null); save()
            mutable.value = AuthState(AuthStage.LOCKED, true, email = cache.session?.email.orEmpty(), message = AuthMessage.NO_ACCESS)
            return
        }
        val previous = if (keepSelection) cache.membership?.let { saved -> choices.find { it.outletId == saved.outletId } } else null
        val current=mutable.value
        val keepReady=previous!=null && current.stage==AuthStage.READY &&
            current.membership?.let { it.brandId==previous.brandId && it.outletId==previous.outletId && it.role==previous.role }==true &&
            current.email==cache.session?.email && cache.device?.userId==cache.session?.userId
        // Revalidating an unchanged context must not remove the shell or a pending
        // document callback. Busy still blocks commands; actual changes fail closed.
        mutable.value = if(keepReady) current.copy(choices=choices,busy=true)
            else AuthState(AuthStage.OUTLETS, true, choices = choices, email = cache.session?.email.orEmpty(), busy = true)
        if (previous != null) select(previous)
    }
    private suspend fun select(membership: Membership) {
        // Preserve only the same confirmed context; neither an outage nor a retry extends its old lease.
        val previous = cache.lease?.takeIf { it.outletId == membership.outletId && it.brandId == membership.brandId && it.role == membership.role }
        cache = cache.copy(membership = membership, lease = previous); save()
        var device = cache.device
        if (device == null || device.userId != cache.session?.userId || device.outletId != membership.outletId) {
            val retainedId = enrollmentLookup.retainedDevice(requireNotNull(cache.session).userId, membership.outletId)
            device = authorized { api.enroll(it, retainedId ?: UUID.randomUUID().toString(), membership.outletId) }
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
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { clear(); null }
        if (stored != null && stored.projectUrl == config.url) cache = stored
        else { clear(); cache = AuthCache(config.url) }
        if (cache.session == null) { mutable.value = AuthState(AuthStage.LOGIN, true); return@action }
        loadChoices(true)
    }
    override suspend fun signIn(email: String, password: String) = action {
        if (!(email.isNotBlank() && email.length <= 254 && '@' in email && password.isNotEmpty())) throw AuthInputFailure()
        clear()
        val session = api.signIn(email.trim(), password)
        cache = AuthCache(config.url, session = session); save()
        loadChoices(false)
    }
    override suspend fun selectOutlet(outletId: String) = action {
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
            mutable.value = mutable.value.copy(stage = AuthStage.LOCKED, membership = null, deviceRequests = emptyList(), deviceRequestsLoaded = false, offline = false, canOffline = false, lockedContext = null, message = AuthMessage.OFFLINE_EXPIRED)
        } else mutable.value = AuthState(AuthStage.READY, true, membership = cache.membership, email = cache.session?.email.orEmpty(),
            offline = true, leaseExpiresAtMs = cache.lease?.expiresAtMs, canOffline = true)
    }
    override suspend fun enforceLease() = mutex.withLock {
        if ((mutable.value.offline || mutable.value.stage == AuthStage.LOCKED && mutable.value.canOffline) && !offlineValid()) {
            cache = cache.copy(lease = null); runCatching { save() }
            mutable.value = mutable.value.copy(stage = AuthStage.LOCKED, membership = null, deviceRequests = emptyList(), deviceRequestsLoaded = false, offline = false, canOffline = false, lockedContext = null, message = AuthMessage.OFFLINE_EXPIRED)
        }
    }
    override suspend fun loadDeviceRequests() = action {
        val membership = requireNotNull(mutable.value.membership)
        if (mutable.value.offline || membership.role == Role.CASHIER) throw AuthInputFailure()
        val requests = authorized { api.deviceRequests(it, membership.outletId) }
        mutable.value = mutable.value.copy(deviceRequests = requests, deviceRequestsLoaded = true)
    }
    override suspend fun approveDevice(id: String) = action {
        val membership = requireNotNull(mutable.value.membership)
        if (mutable.value.offline || membership.role == Role.CASHIER) throw AuthInputFailure()
        authorized { api.approveDevice(it, id) }
        val requests = authorized { api.deviceRequests(it, membership.outletId) }
        mutable.value = mutable.value.copy(deviceRequests = requests, deviceRequestsLoaded = true)
    }
    private suspend fun storedScope(): SaleScope? {
        if (!config.configured) return null
        if (cache.session == null) {
            val stored = withContext(Dispatchers.IO) { store.read()?.let(AuthCache::decode) }
            if (stored?.projectUrl == config.url) cache = stored
        }
        val user = cache.session?.userId ?: return null
        val membership = cache.membership ?: return null
        val device = cache.device ?: return null
        if (device.userId != user || device.outletId != membership.outletId) return null
        return SaleScope(membership.brandId, membership.outletId, device.id, user)
    }
    private suspend fun storedPullScope(): PullScope? = storedScope()?.let { PullScope(it, requireNotNull(cache.membership).role.name, config.url) }
    override suspend fun pullScope(): PullScope? = mutex.withLock { storedPullScope() }
    override suspend fun fetch(scope: PullScope, after: Long, epoch: String?): PullPage = mutex.withLock {
        try {
            if (storedPullScope() != scope) throw PullFailure("SCOPE_CHANGED")
            val page = authorized { api.pull(it, requireNotNull(cache.device), after, scope.role, epoch) }
            com.dauntehnusantara.pos.feature.sync.data.PullJson.page(page, scope, after)
        } catch (e: CancellationException) { throw e }
        catch (e: AuthHttpFailure) {
            if (e.status in listOf(401, 403, 423)) failure(e, mutable.value.offline)
            if (e.status == 409) {
                cache = cache.copy(lease = null); save()
                mutable.value = mutable.value.copy(stage = AuthStage.LOCKED, membership = null, offline = false, canOffline = false, lockedContext = null, message = AuthMessage.DENIED)
            }
            throw PullFailure(if (e.status in listOf(401, 403, 423)) "AUTH_REQUIRED" else if (e.status == 409) "SCOPE_CHANGED" else if(e.status == 410) "REBOOTSTRAP_REQUIRED" else "UNAVAILABLE")
        } catch (_: IOException) { throw PullFailure("UNAVAILABLE") }
        catch (e: PullFailure) { throw e }
        catch (_: Exception) { throw PullFailure("INVALID_PAGE") }
    }
    override suspend fun backgroundScope(): SaleScope? = mutex.withLock { storedScope() }
    override suspend fun checkoutScope(): SaleScope? = mutex.withLock {
        if (mutable.value.stage != AuthStage.READY || mutable.value.busy || mutable.value.offline && !offlineValid()) null else storedScope()
    }
    override suspend fun submit(sale: PendingSale): PushResult = mutex.withLock {
        try {
            if (storedScope() != sale.scope) return@withLock PushResult.AuthRequired
            val payload = JSONObject(sale.payload)
            require(payload.getBoolean("demo") == false && payload.getInt("schema_version") == 1 &&
                payload.getString("operation_id") == sale.operationId && payload.getString("sale_id") == sale.saleId &&
                payload.getString("brand_id") == sale.scope.brandId && payload.getString("outlet_id") == sale.scope.outletId &&
                payload.getString("device_id") == sale.scope.deviceId && payload.getString("cashier_user_id") == sale.scope.userId)
            val ack = authorized { api.submit(it, requireNotNull(cache.device), sale.payload) }
            val payloadHash = java.security.MessageDigest.getInstance("SHA-256").digest(sale.payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
            require(ack.getString("payload_hash") == payloadHash)
            require(ack.getString("operation_id") == sale.operationId && ack.getString("sale_id") == sale.saleId &&
                ack.getString("request_hash").matches(Regex("[0-9a-f]{64}")))
            when (ack.getString("status")) {
                "ACCEPTED" -> PushResult.Accepted
                "REVIEW" -> {
                    val code = ack.getString("code")
                    require(code in listOf("CATALOG_CHANGED", "INVALID_SALE", "SALE_CONFLICT", "OPERATION_CONFLICT"))
                    PushResult.Review(code)
                }
                else -> PushResult.Review("INVALID_ACK")
            }
        } catch (e: CancellationException) { throw e }
        catch (_: IOException) { PushResult.Retry }
        catch (e: AuthHttpFailure) {
            when {
                e.status == 429 || e.status >= 500 -> PushResult.Retry
                e.status in listOf(401, 403, 423) -> { failure(e, mutable.value.offline); PushResult.AuthRequired }
                else -> PushResult.Review("HTTP_REJECTED")
            }
        } catch (_: Exception) { PushResult.Review("INVALID_ACK_OR_PAYLOAD") }
    }
    private suspend fun currentManagementScope(online:Boolean):PullScope? {
        if(mutable.value.stage!=AuthStage.READY || cache.membership?.role !in listOf(Role.OWNER,Role.MANAGER)) return null
        if(mutable.value.offline && (online || !offlineValid())) return null
        return storedPullScope()
    }
    override suspend fun reportingScope(online:Boolean):PullScope? = mutex.withLock { currentManagementScope(online) }
    private suspend fun <T> management(scope:PullScope,block:suspend (TokenSession,EnrolledDevice)->T):T = mutex.withLock {
        if(currentManagementScope(false)!=scope)throw ManagementFailure("AUTH_REQUIRED")
        if(mutable.value.offline)throw ManagementFailure("OFFLINE")
        try { authorized { block(it,requireNotNull(cache.device)) } }
        catch(e:CancellationException){throw e}
        catch(e:AuthHttpFailure){
            if(e.status in listOf(401,403,423))failure(e,false)
            throw ManagementFailure(e.code?.takeIf{it in setOf("STAFF_LIMIT_REACHED","STAFF_LIMITS_UNCONFIGURED")} ?: when(e.status){404->"NOT_CONFIGURED";429->"RATE_LIMIT";409->"CONFLICT";401,403,423->"AUTH_REQUIRED";413->"TOO_LARGE";410->"REBOOTSTRAP_REQUIRED";422->"INVALID";else->"UNAVAILABLE"})
        } catch(_:IOException){throw ManagementFailure("UNAVAILABLE")}
        catch(e:ManagementFailure){throw e}
        catch(_:Exception){throw ManagementFailure("INVALID_RESPONSE")}
    }
    private suspend fun currentPhotoScope():PullScope? =
        if(mutable.value.stage!=AuthStage.READY || mutable.value.busy || mutable.value.offline && !offlineValid())null else storedPullScope()
    override suspend fun photoScope():PullScope? = mutex.withLock { currentPhotoScope() }
    private suspend fun photoToken(scope:PullScope,writing:Boolean):String = mutex.withLock {
        if(currentPhotoScope()!=scope || writing && currentManagementScope(true)!=scope)throw ManagementFailure("AUTH_REQUIRED")
        if(mutable.value.offline)throw ManagementFailure("OFFLINE")
        authorized { it.accessToken }
    }
    override suspend fun uploadPhoto(scope:PullScope,path:String,bytes:ByteArray) {
        PhotoRules.path(path,scope.sale.brandId);require(bytes.size in 1..PhotoRules.MAX_BYTES)
        // Binary IO deliberately runs outside the auth mutex: slow photos never gate checkout.
        val token=photoToken(scope,true)
        try { api.transferPhoto(token,"/storage/v1/object/${PhotoRules.BUCKET}/$path",bytes) }
        catch(e:AuthHttpFailure){if(e.status!=409)throw e} // Immutable UUID path, same frozen bytes on retry.
        if(photoScope()!=scope)throw ManagementFailure("AUTH_REQUIRED")
    }
    override suspend fun downloadPhoto(scope:PullScope,path:String):ByteArray {
        PhotoRules.path(path,scope.sale.brandId)
        val bytes=api.transferPhoto(photoToken(scope,false),"/storage/v1/object/authenticated/${PhotoRules.BUCKET}/$path")
        if(photoScope()!=scope)throw ManagementFailure("AUTH_REQUIRED")
        return bytes
    }
    override suspend fun setPhoto(scope:PullScope,epoch:String,command:PhotoCommand):PhotoAck = management(scope) { session,device ->
        command.validate(scope.sale.brandId)
        val j=JsonSafety.obj(api.management(session,device,"set_product_photo",JSONObject().put("p_epoch",epoch)
            .put("p_product_id",command.productId).put("p_expected_version",command.expectedVersion)
            .put("p_path",command.path?:JSONObject.NULL).put("p_operation_id",command.operationId)))
        require(j.getString("operation_id")==command.operationId && j.getString("product_id")==command.productId && j.getString("feed_epoch")==epoch)
        PhotoAck(j.getLong("version"),j.getLong("through_seq")).also{require(it.version==command.expectedVersion+1 && it.throughSeq>0)}
    }
    override suspend fun saveCatalog(scope:PullScope,epoch:String,change:CatalogMutation):CatalogAck = management(scope) { session,device ->
        change.validate()
        CatalogCommandJson.ack(JsonSafety.obj(api.management(session,device,"save_catalog_record",JSONObject().put("p_epoch",epoch)
            .put("p_change",CatalogCommandJson.write(change)))),change,epoch)
    }
    override suspend fun report(query:ReportQuery):SalesReport = management(query.source) { session,device ->
        ReportJson.read(JsonSafety.obj(api.management(session,device,"get_sales_report",JSONObject().put("p_from_date",query.from.toString())
            .put("p_to_date",query.to.toString()).put("p_outlet_id",query.outletId?:JSONObject.NULL))),query)
    }
    override suspend fun roster(scope:PullScope):StaffRoster = management(scope) { session,device ->
        StaffJson.read(JsonSafety.obj(api.management(session,device,"list_staff",JSONObject())),scope)
    }
    override suspend fun memberAccess(scope:PullScope,id:String,active:Boolean,version:Long,reason:String) {
        StaffRules.reason(reason)
        management(scope) { session,device -> api.management(session,device,"set_staff_access",JSONObject().put("p_user_id",id)
            .put("p_active",active).put("p_expected_version",version).put("p_reason",reason.trim())) }
    }
    override suspend fun deviceAccess(scope:PullScope,id:String,revoked:Boolean,version:Long,reason:String) {
        StaffRules.reason(reason)
        management(scope) { session,device -> api.management(session,device,"set_device_access",JSONObject().put("p_target_id",id)
            .put("p_revoked",revoked).put("p_expected_version",version).put("p_reason",reason.trim())) }
    }
    override suspend fun create(scope:PullScope,command:StaffCreation) {
        command.validate(scope)
        management(scope) { session,device ->
            val ack=api.staffAdmin(session,JSONObject().put("action","create").put("operation_id",command.operationId)
                .put("device_id",device.id).put("device_secret",device.secret).put("email",command.email.trim())
                .put("full_name",command.name.trim()).put("role",command.role).put("password",command.password))
            require(ack.getString("operation_id")==command.operationId&&ack.getString("outlet_id")==scope.sale.outletId&&
                ack.getString("role")==command.role&&ack.getString("status")=="CREATED")
            UUID.fromString(ack.getString("user_id"))
        }
    }
    override suspend fun changeInitialPassword(current:String,password:String) = action {
        if(mutable.value.stage!=AuthStage.PASSWORD_CHANGE||!StaffCreationRules.password(password)||current.isEmpty()||current.length>128||current==password)throw AuthInputFailure()
        try {
            val ack=authorized { api.staffAdmin(it,JSONObject().put("action","change_password").put("current_password",current).put("password",password)) }
            require(ack.getString("code")=="PASSWORD_CHANGED")
            // Require fresh sign-in; no temporary-password session/lease survives this action.
            val previous=cache.session;clear();mutable.value=AuthState(AuthStage.LOGIN,true)
            if(previous!=null)runCatching{api.logout(previous)}
        }catch(e:AuthHttpFailure) {
            if(e.status==422)mutable.value=mutable.value.copy(message=AuthMessage.INVALID_INPUT)
            else if(e.status>=500||e.status==409)mutable.value=mutable.value.copy(message=AuthMessage.UNAVAILABLE)
            else throw e
        } catch(_:IOException) { mutable.value=mutable.value.copy(message=AuthMessage.UNAVAILABLE) }
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
