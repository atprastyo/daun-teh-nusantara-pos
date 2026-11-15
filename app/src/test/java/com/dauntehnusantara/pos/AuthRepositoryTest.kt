package com.dauntehnusantara.pos

import android.app.Application
import com.dauntehnusantara.pos.core.network.*
import com.dauntehnusantara.pos.core.security.SecretStore
import com.dauntehnusantara.pos.feature.auth.data.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.core.common.*
import com.dauntehnusantara.pos.feature.sync.domain.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class AuthRepositoryTest {
    private val base = 1800000000000L
    private val config=BackendConfig("https://example.invalid","sb_publishable_test_fixture")
    private class MemoryStore : SecretStore {
        var value:String?=null
        override fun read()=value
        override fun write(value:String){this.value=value}
        override fun clear(){value=null}
    }
    private class FakeClock(private val base:Long) : AccessClock {
        var value=ClockReading(base,10000,4,true)
        override fun read()=value
    }
    private class FakeHttp(private val base:Long) : AuthTransport {
        var leaseGate:CompletableDeferred<Unit>?=null
        var leaseEntered:CompletableDeferred<Unit>?=null
        var brand="brand"
        var unavailable=false; var denied=false; var noAccess=false; var pending=false
        var protocolFailure=false; var role=Role.CASHIER; var issuedDevice=""
        var managementStatus=200
        var pullStatus=200;var pullWrongScope=false
        var invalidRefresh=false
        var wrongAck=false;var pushStatus="ACCEPTED";var pushCode="CATALOG_CHANGED"; var pushCalls=0
        val calls=mutableListOf<String>(); var refreshes=0
        override suspend fun request(path:String,body:String?,token:String?,method:String):String {
            calls+=path
            if(unavailable)throw IOException("simulated connection failure")
            if(denied)throw AuthHttpFailure(403)
            if(protocolFailure)throw IllegalStateException("simulated invalid authorization response")
            return when {
                path.startsWith("/auth/v1/token") -> {
                    if(path.contains("refresh_token")){if(invalidRefresh)throw AuthHttpFailure(400);refreshes++}
                    JSONObject().put("access_token","test-access").put("refresh_token","test-refresh-$refreshes").put("expires_at",base/1000+3600)
                        .put("user",JSONObject().put("id","user").put("email","user@example.invalid")).toString()
                }
                path.endsWith("save_catalog_record") -> {
                    if(managementStatus!=200)throw AuthHttpFailure(managementStatus)
                    val args=JSONObject(requireNotNull(body));require(args.getString("p_device_id")==issuedDevice && args.getString("p_device_secret")=="test-device-secret")
                    val c=args.getJSONObject("p_change")
                    JSONObject().put("operation_id",c.getString("operation_id")).put("id",c.getString("id")).put("kind",c.getString("kind"))
                        .put("version",c.getLong("expected_version")+1).put("through_seq",42).put("feed_epoch",args.getString("p_epoch")).toString()
                }
                path.endsWith("list_staff") || path.endsWith("set_staff_access") || path.endsWith("set_device_access") -> {
                    if(managementStatus!=200)throw AuthHttpFailure(managementStatus)
                    val args=JSONObject(requireNotNull(body));require(args.getString("p_device_id")==issuedDevice && args.getString("p_device_secret")=="test-device-secret")
                    if(!path.endsWith("list_staff"))"" else JSONObject().put("schema_version",1).put("brand_id",brand).put("outlet_id","outlet").put("user_id","user")
                        .put("device_id",issuedDevice).put("role",role.name).put("members",org.json.JSONArray()).put("devices",org.json.JSONArray()).put("audit",org.json.JSONArray()).toString()
                }
                path.endsWith("pull_changes") -> {
                    if(pullStatus!=200)throw AuthHttpFailure(pullStatus)
                    val input=JSONObject(requireNotNull(body));require(input.getString("p_device_id")==issuedDevice)
                    require(input.getString("p_device_secret")=="test-device-secret")
                    JSONObject().put("schema_version",1).put("brand_id",brand).put("outlet_id",if(pullWrongScope)"foreign" else "outlet")
                        .put("device_id",issuedDevice).put("user_id","user").put("role",input.getString("p_role"))
                        .put("after_seq",input.getLong("p_after_seq")).put("next_seq",input.getLong("p_after_seq"))
                        .put("has_more",false).put("feed_epoch","11111111-1111-4111-8111-111111111111").put("changes",org.json.JSONArray()).toString()
                }
                path.endsWith("submit_sale") -> {
                    pushCalls++
                    val envelope=JSONObject(requireNotNull(body));require(envelope.getString("p_device_secret")=="test-device-secret")
                    val raw=envelope.getString("p_payload");val payload=JSONObject(raw)
                    val hash=java.security.MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                    JSONObject().put("operation_id",payload.getString("operation_id")).put("sale_id",payload.getString("sale_id"))
                        .put("request_hash","a".repeat(64)).put("payload_hash",if(wrongAck) "b".repeat(64) else hash)
                        .put("status",pushStatus).put("code",pushCode).toString()
                }
                path.endsWith("get_access_context") -> if(noAccess)"[]" else """[{"brand_id":"${brand}","brand_name":"Daun Teh","outlet_id":"outlet","outlet_name":"A","role":"${role.name}"}]"""
                path.endsWith("enroll_device") -> {
                    issuedDevice=JSONObject(requireNotNull(body)).getString("p_device_id")
                    JSONObject().put("device_id",issuedDevice).put("device_secret","test-device-secret").toString()
                }
                path.endsWith("issue_offline_lease") -> {
                    leaseEntered?.complete(Unit);leaseGate?.await()
                    if(pending)throw AuthHttpFailure(423)
                    JSONObject().put("user_id","user").put("brand_id",brand).put("outlet_id","outlet").put("device_id",issuedDevice)
                        .put("role",role.name).put("issued_at_ms",base).put("expires_at_ms",base+30L*24*60*60*1000).toString()
                }
                path.endsWith("list_device_requests") -> "[]"
                path.endsWith("approve_device") || path.startsWith("/auth/v1/logout") -> "{}"
                else -> error("Unexpected endpoint")
            }
        }
    }
    private fun repo(http:FakeHttp,store:MemoryStore,clock:FakeClock,configuration:BackendConfig=config,photos:com.dauntehnusantara.pos.feature.catalog.photos.data.PhotoTransport=com.dauntehnusantara.pos.feature.catalog.photos.data.UnconfiguredPhotoTransport)=
        AuthRepositoryImpl(AuthApi(http,Clock.fixed(Instant.ofEpochMilli(base),ZoneOffset.UTC),photos),configuration,store,clock,DeviceEnrollmentLookup { _, _ -> null })
    private suspend fun signedIn(repository:AuthRepositoryImpl){repository.signIn("user@example.invalid","test-password-value");repository.selectOutlet("outlet")}
    @Test fun deviceRequestEmptySuccessIsDistinctFromUnloadedAndNetworkFailure():Unit=runBlocking {
        val http=FakeHttp(base).apply { role = Role.MANAGER };val repository=repo(http,MemoryStore(),FakeClock(base))
        signedIn(repository)
        assertFalse(repository.state.value.deviceRequestsLoaded)
        repository.loadDeviceRequests()
        assertTrue(repository.state.value.deviceRequestsLoaded)
        assertTrue(repository.state.value.deviceRequests.isEmpty())
        http.unavailable=true
        repository.loadDeviceRequests()
        assertFalse(repository.state.value.deviceRequestsLoaded)
        assertEquals(AuthStage.LOCKED,repository.state.value.stage)
        assertNull(repository.state.value.membership)
    }
    @Test fun sameContextRefreshKeepsReadyShellWhileLeaseIsValidated():Unit=runBlocking {
        val http=FakeHttp(base);val repository=repo(http,MemoryStore(),FakeClock(base));signedIn(repository)
        val original=repository.state.value.membership;http.leaseEntered=CompletableDeferred();http.leaseGate=CompletableDeferred()
        val job=async{repository.refresh()}
        try{http.leaseEntered!!.await();assertEquals(AuthStage.READY,repository.state.value.stage);assertEquals(original,repository.state.value.membership);assertTrue(repository.state.value.busy)}
        finally{http.leaseGate!!.complete(Unit);job.await()}
        assertEquals(AuthStage.READY,repository.state.value.stage);assertFalse(repository.state.value.busy)
    }
    @Test fun changedRoleStillRemovesOldReadyContextBeforeLeaseValidation():Unit=runBlocking {
        val http=FakeHttp(base);val repository=repo(http,MemoryStore(),FakeClock(base));signedIn(repository)
        http.role=Role.MANAGER;http.leaseEntered=CompletableDeferred();http.leaseGate=CompletableDeferred()
        val job=async{repository.refresh()}
        try{http.leaseEntered!!.await();assertEquals(AuthStage.OUTLETS,repository.state.value.stage);assertNull(repository.state.value.membership)}
        finally{http.leaseGate!!.complete(Unit);job.await()}
        assertEquals(Role.MANAGER,repository.state.value.membership?.role)
    }
    @Test fun sameContextRefreshStillHonorsConfirmedDeviceDenial():Unit=runBlocking {
        val http=FakeHttp(base);val repository=repo(http,MemoryStore(),FakeClock(base));signedIn(repository)
        http.pending=true;repository.refresh();assertEquals(AuthStage.LOCKED,repository.state.value.stage);assertNull(repository.state.value.membership);assertFalse(repository.state.value.canOffline)
    }
    @Test fun firstLoginRequiresOnlineAndServerMembership():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        repo.restore();assertEquals(AuthStage.LOGIN,repo.state.value.stage)
        http.unavailable=true;repo.signIn("user@example.invalid","test-password-value")
        assertFalse(repo.state.value.canOffline)
        http.unavailable=false;http.noAccess=true;repo.signIn("user@example.invalid","test-password-value")
        assertEquals(AuthMessage.NO_ACCESS,repo.state.value.message);assertFalse(repo.state.value.canOffline)
    }
    @Test fun deviceLeaseRestoresOfflineWithoutRenewingItAndExpires():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val first=repo(http,store,clock)
        signedIn(first);assertEquals(AuthStage.READY,first.state.value.stage)
        assertFalse(requireNotNull(store.value).contains("test-password-value"))
        val leaseCalls=http.calls.count{it.endsWith("issue_offline_lease")}
        http.unavailable=true
        val restored=repo(http,store,clock);restored.restore()
        assertTrue(restored.state.value.canOffline)
        assertEquals("outlet", restored.state.value.lockedContext?.outletId)
        assertNotNull(restored.state.value.leaseExpiresAtMs)
        assertNull(restored.state.value.membership)
        restored.unlockOffline();assertTrue(restored.state.value.offline)
        assertEquals(leaseCalls,http.calls.count{it.endsWith("issue_offline_lease")})
        clock.value=clock.value.copy(elapsedMs=clock.value.elapsedMs+30L*24*60*60*1000)
        restored.enforceLease();assertEquals(AuthStage.LOCKED,restored.state.value.stage);assertFalse(restored.state.value.canOffline);assertNull(restored.state.value.membership)
    }
    @Test fun rebootAndClockRollbackCannotResetLease():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);http.unavailable=true;repo.refresh()
        clock.value=clock.value.copy(bootCount=5);repo.unlockOffline();assertFalse(repo.state.value.offline)
        assertFalse(repo.state.value.canOffline)
    }
    @Test fun onlineDenialInvalidatesStoredOfflineAuthority():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);http.denied=true;repo.refresh()
        assertEquals(AuthStage.LOGIN,repo.state.value.stage);assertFalse(repo.state.value.canOffline);assertNull(store.value);assertNull(repo.state.value.lockedContext)
        repo.unlockOffline();assertFalse(repo.state.value.offline)
    }
    @Test fun pendingApprovalCannotBecomeReadyOrOfflineUntilServerApproves():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        http.pending=true;signedIn(repo)
        assertEquals(AuthMessage.DEVICE_PENDING,repo.state.value.message);assertFalse(repo.state.value.canOffline)
        assertEquals("outlet", repo.state.value.lockedContext?.outletId)
        assertNull(repo.state.value.membership)
        http.pending=false;repo.refresh();assertEquals(AuthStage.READY,repo.state.value.stage)
    }
    @Test fun manuallyProvisionedStaffSignInWithoutAcceptanceOrAccountMutation():Unit=runBlocking {
        for (role in Role.entries) {
            val http=FakeHttp(base);http.role=role
            val repo=repo(http,MemoryStore(),FakeClock(base))
            repo.signIn("user@example.invalid","test-password-value")
            assertEquals(AuthStage.OUTLETS,repo.state.value.stage)
            assertEquals(listOf("/auth/v1/token?grant_type=password","/rest/v1/rpc/get_access_context"),http.calls)
            repo.selectOutlet("outlet");assertEquals(role,repo.state.value.membership?.role)
            assertEquals(AuthStage.READY,repo.state.value.stage)
        }
    }
    @Test fun oldCompletedSessionPreservesEnrollmentWithoutActivationState():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);signedIn(repo(http,store,clock))
        val oldDevice=http.issuedDevice;val enrollCalls=http.calls.count{it.endsWith("enroll_device")}
        store.value=JSONObject(requireNotNull(store.value)).put("activation_pending",false).toString()
        val restored=repo(http,store,clock);restored.restore()
        assertEquals(AuthStage.READY,restored.state.value.stage);assertEquals(oldDevice,restored.backgroundScope()?.deviceId)
        assertEquals(enrollCalls,http.calls.count{it.endsWith("enroll_device")})
        assertFalse(JSONObject(requireNotNull(store.value)).has("activation_pending"))
    }
    @Test fun retiredPartialActivationCacheRequiresFreshSignIn():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);signedIn(repo(http,store,clock))
        store.value=JSONObject(requireNotNull(store.value)).put("activation_pending",true).toString()
        val before=http.calls.size;val restored=repo(http,store,clock);restored.restore()
        assertEquals(AuthStage.LOGIN,restored.state.value.stage);assertFalse(restored.state.value.canOffline)
        assertNull(store.value);assertEquals(before,http.calls.size)
    }
    @Test fun refreshRotationIsPersistedAndLogoutClearsLocalLeaseWhenNetworkIsDown():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);clock.value=clock.value.copy(wallMs=base+3600001);repo.refresh()
        assertTrue(http.refreshes>0);assertTrue(requireNotNull(store.value).contains("test-refresh-${http.refreshes}"))
        http.unavailable=true;repo.signOut();assertNull(store.value);assertEquals(AuthStage.LOGIN,repo.state.value.stage)
    }
    @Test fun projectChangesAndCorruptCacheCannotRestoreTokens():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);signedIn(repo(http,store,clock))
        val before=http.calls.size
        val changed=repo(http,store,clock,BackendConfig("https://other.invalid","sb_publishable_test_fixture"));changed.restore()
        assertEquals(before,http.calls.size);assertEquals(AuthStage.LOGIN,changed.state.value.stage)
        store.value="not-valid-json";val corrupted=repo(http,store,clock);corrupted.restore()
        assertEquals(AuthStage.LOGIN,corrupted.state.value.stage);assertNull(store.value)
    }
    @Test fun failedReconnectionNeverExtendsOrDropsAlreadyUnlockedValidLease():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);http.unavailable=true;repo.refresh();repo.unlockOffline()
        val deadline=repo.state.value.leaseExpiresAtMs
        repo.refresh()
        assertEquals(AuthStage.READY,repo.state.value.stage);assertTrue(repo.state.value.offline)
        assertEquals(deadline,repo.state.value.leaseExpiresAtMs)
        http.unavailable=false;http.denied=true;repo.refresh()
        assertFalse(repo.state.value.offline);assertFalse(repo.state.value.canOffline);assertNull(store.value)
    }
    @Test fun malformedAuthorizationResponseCannotPreserveOfflineAuthority():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);http.unavailable=true;repo.refresh();repo.unlockOffline()
        http.unavailable=false;http.protocolFailure=true;repo.refresh()
        assertEquals(AuthStage.LOCKED,repo.state.value.stage);assertFalse(repo.state.value.canOffline)
        assertNull(repo.state.value.membership)
    }
    private fun pending(scope:SaleScope)=PendingSale("op","sale",scope,JSONObject().put("demo",false).put("schema_version",1)
        .put("operation_id","op").put("sale_id","sale").put("brand_id",scope.brandId).put("outlet_id",scope.outletId)
        .put("device_id",scope.deviceId).put("cashier_user_id",scope.userId).toString(),0)
    @Test fun pushRefreshesTokensAndOnlyAcknowledgesMatchingPayloadHash():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);val sale=pending(requireNotNull(repo.checkoutScope()))
        clock.value=clock.value.copy(wallMs=base+3600001)
        assertEquals(PushResult.Accepted,repo.submit(sale));assertTrue(http.refreshes>0)
        http.wrongAck=true;assertTrue(repo.submit(sale) is PushResult.Review)
        http.wrongAck=false;http.pushStatus="REVIEW";assertEquals(PushResult.Review("CATALOG_CHANGED"),repo.submit(sale))
    }
    @Test fun backgroundPushRestoresEncryptedSessionButNeverCrossesAccountOrDemoScope():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val first=repo(http,store,clock)
        signedIn(first);val scope=requireNotNull(first.checkoutScope())
        val restored=repo(http,store,clock)
        assertEquals(scope,restored.backgroundScope());assertNull(restored.checkoutScope())
        assertEquals(PushResult.Accepted,restored.submit(pending(scope)))
        val before=http.pushCalls
        assertEquals(PushResult.AuthRequired,restored.submit(pending(scope.copy(userId="different"))))
        assertTrue(restored.submit(pending(scope).copy(payload=pending(scope).payload.replace("false","true"))) is PushResult.Review)
        assertEquals(before,http.pushCalls)
        restored.signOut();assertNull(restored.backgroundScope())
    }
    @Test fun invalidRefreshTokenPausesPushForReauthentication():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);val sale=pending(requireNotNull(repo.checkoutScope()))
        http.invalidRefresh=true;clock.value=clock.value.copy(wallMs=base+3600001)
        assertEquals(PushResult.AuthRequired,repo.submit(sale));assertEquals(0,http.pushCalls)
        assertNull(store.value);assertEquals(AuthStage.LOGIN,repo.state.value.stage)
    }
    @Test fun pullValidatesScopeAndUsesPrivateDeviceProof():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);val scope=requireNotNull(repo.pullScope())
        assertEquals(7L,repo.fetch(scope,7).next)
        http.pullWrongScope=true
        try { repo.fetch(scope,7);fail("Foreign scope must not parse") } catch(e:PullFailure) { assertEquals("INVALID_PAGE",e.code) }
    }
    @Test fun pullRoleConflictClearsOfflineLeaseAndRevocationRequiresLogin():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);val scope=requireNotNull(repo.pullScope());http.pullStatus=409
        try { repo.fetch(scope,0);fail("Changed role must stop") } catch(e:PullFailure) { assertEquals("SCOPE_CHANGED",e.code) }
        assertEquals(AuthStage.LOCKED,repo.state.value.stage);assertFalse(repo.state.value.canOffline)
        http.pullStatus=403
        try { repo.fetch(scope,0);fail("Revocation must stop") } catch(e:PullFailure) { assertEquals("AUTH_REQUIRED",e.code) }
        assertNull(store.value);assertEquals(AuthStage.LOGIN,repo.state.value.stage)
    }
    @Test fun retainedQueueDeviceIsReenrolledAfterLogoutWithoutKeepingCredentials():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base)
        val repo=AuthRepositoryImpl(AuthApi(http,Clock.fixed(Instant.ofEpochMilli(base),ZoneOffset.UTC)),config,store,clock,
            DeviceEnrollmentLookup { user,outlet -> if(user=="user" && outlet=="outlet") "retained-device" else null })
        signedIn(repo);assertEquals("retained-device",requireNotNull(repo.checkoutScope()).deviceId)
        repo.signOut();assertNull(store.value);signedIn(repo)
        assertEquals("retained-device",requireNotNull(repo.checkoutScope()).deviceId)
    }
    @Test fun backendConfigRejectsSecretKeysAndInsecureUrls() {
        fun jwt(role:String)="a."+Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"$role\"}".toByteArray())+".b"
        assertTrue(BackendConfig.valid(config.url,"sb_publishable_test_fixture"))
        assertTrue(BackendConfig.valid(config.url,jwt("anon")))
        assertFalse(BackendConfig.valid(config.url,jwt("service_role")))
        assertFalse(BackendConfig.valid(config.url,"sb_secret_test_fixture"))
        assertFalse(BackendConfig.valid("http://example.invalid","sb_publishable_test_fixture"))
        assertFalse(BackendConfig.valid("https://user:password@example.invalid","sb_publishable_test_fixture"))
    }
    @Test fun managementConflictsPreserveSessionAndOfflineActionsNeverSendMutations():Unit=runBlocking {
        val http=FakeHttp(base);http.role=Role.MANAGER;val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);val scope=requireNotNull(repo.reportingScope(false));assertTrue(repo.roster(scope).members.isEmpty())
        http.managementStatus=409
        try{repo.memberAccess(scope,"cashier",false,1,"Reviewed change");fail("Stale version must fail")}catch(e:com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure){assertEquals("CONFLICT",e.code)}
        assertEquals(AuthStage.READY,repo.state.value.stage);assertNotNull(store.value)
        http.managementStatus=200;http.unavailable=true;repo.refresh();repo.unlockOffline()
        assertEquals(scope,repo.reportingScope(false));assertNull(repo.reportingScope(true));val before=http.calls.size
        try{repo.roster(scope);fail("Offline management must stop")}catch(e:com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure){assertEquals("OFFLINE",e.code)}
        assertEquals(before,http.calls.size)
    }
    @Test fun cashierCannotReadManagementAndLiveManagementDenialClearsAuthority():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);assertNull(repo.reportingScope(false));val cashierScope=requireNotNull(repo.pullScope());val before=http.calls.size
        try{repo.roster(cashierScope);fail("Cashier management must stop")}catch(_:com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure){}
        assertEquals(before,http.calls.size)
        http.role=Role.OWNER;repo.refresh();val ownerScope=requireNotNull(repo.reportingScope(true));http.managementStatus=403
        try{repo.roster(ownerScope);fail("Denied actor must stop")}catch(_:com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure){}
        assertNull(store.value);assertEquals(AuthStage.LOGIN,repo.state.value.stage);assertFalse(repo.state.value.canOffline)
    }

    @Test fun catalogRpcUsesLiveManagementScopeDeviceProofAndMapsVersionAndEpochConflicts():Unit=runBlocking {
        val http=FakeHttp(base);http.role=Role.OWNER
        val repository=repo(http,MemoryStore(),FakeClock(base));signedIn(repository)
        val scope=requireNotNull(repository.reportingScope(false))
        val command=com.dauntehnusantara.pos.feature.catalog.domain.CatalogMutation(kind=com.dauntehnusantara.pos.feature.catalog.domain.CatalogRecord.Category,name="Tea")
        val epoch="11111111-1111-4111-8111-111111111111"
        assertEquals(42L,repository.saveCatalog(scope,epoch,command).throughSeq)
        http.managementStatus=409
        assertEquals("CONFLICT",(runCatching{repository.saveCatalog(scope,epoch,command)}.exceptionOrNull() as com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure).code)
        assertEquals(AuthStage.READY,repository.state.value.stage)
        http.managementStatus=410
        assertEquals("REBOOTSTRAP_REQUIRED",(runCatching{repository.saveCatalog(scope,epoch,command)}.exceptionOrNull() as com.dauntehnusantara.pos.feature.reports.domain.ManagementFailure).code)
        assertEquals(AuthStage.READY,repository.state.value.stage)
    }

    @Test fun slowPhotoTransferDoesNotHoldAuthorityMutexOrDelayLocalCheckoutScope():Unit=runBlocking {
        val http=FakeHttp(base);http.role=Role.OWNER;http.brand="10000000-0000-4000-8000-000000000001"
        val started=kotlinx.coroutines.CompletableDeferred<Unit>();val finish=kotlinx.coroutines.CompletableDeferred<Unit>()
        val transport=object:com.dauntehnusantara.pos.feature.catalog.photos.data.PhotoTransport {
            override suspend fun transfer(token:String,path:String,body:ByteArray?):ByteArray{started.complete(Unit);finish.await();return byteArrayOf()}
        }
        val repository=repo(http,MemoryStore(),FakeClock(base),photos=transport);signedIn(repository)
        val scope=requireNotNull(repository.photoScope())
        val path=com.dauntehnusantara.pos.feature.catalog.photos.domain.PhotoRules.newPath(http.brand,"10000000-0000-4000-8000-000000000002")
        val upload=async { repository.uploadPhoto(scope,path,byteArrayOf(1)) }
        started.await()
        try { assertNotNull(kotlinx.coroutines.withTimeout(1000){repository.checkoutScope()}) }
        finally{finish.complete(Unit);upload.await()}
    }

}
