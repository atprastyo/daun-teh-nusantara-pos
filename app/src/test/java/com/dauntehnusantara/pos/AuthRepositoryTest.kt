package com.dauntehnusantara.pos

import android.app.Application
import com.dauntehnusantara.pos.core.network.*
import com.dauntehnusantara.pos.core.security.SecretStore
import com.dauntehnusantara.pos.feature.auth.data.*
import com.dauntehnusantara.pos.feature.auth.domain.*
import com.dauntehnusantara.pos.core.common.*
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlinx.coroutines.runBlocking
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
        var unavailable=false; var denied=false; var noAccess=false; var pending=false
        var passwordFailure=false; var protocolFailure=false; var role=Role.CASHIER; var issuedDevice=""; var invitationStatus=200
        val calls=mutableListOf<String>(); var activations=0; var refreshes=0
        override suspend fun request(path:String,body:String?,token:String?,method:String):String {
            calls+=path
            if(unavailable)throw IOException("simulated connection failure")
            if(denied)throw AuthHttpFailure(403)
            if(protocolFailure)throw IllegalStateException("simulated invalid authorization response")
            return when {
                path.startsWith("/auth/v1/token") || path=="/auth/v1/verify" -> {
                    if(path.contains("refresh_token"))refreshes++
                    if(path.endsWith("verify"))activations++
                    JSONObject().put("access_token","test-access").put("refresh_token","test-refresh-$refreshes").put("expires_at",base/1000+3600)
                        .put("user",JSONObject().put("id","user").put("email","user@example.invalid")).toString()
                }
                path=="/auth/v1/user" -> {
                    if(passwordFailure){passwordFailure=false;throw IOException("simulated password update interruption")};"{}"
                }
                path.endsWith("accept_staff_invitations") -> "0"
                path.endsWith("get_access_context") -> if(noAccess)"[]" else """[{"brand_id":"brand","brand_name":"Daun Teh","outlet_id":"outlet","outlet_name":"A","role":"${role.name}"}]"""
                path.endsWith("enroll_device") -> {
                    issuedDevice=JSONObject(requireNotNull(body)).getString("p_device_id")
                    JSONObject().put("device_id",issuedDevice).put("device_secret","test-device-secret").toString()
                }
                path.endsWith("issue_offline_lease") -> {
                    if(pending)throw AuthHttpFailure(423)
                    JSONObject().put("user_id","user").put("brand_id","brand").put("outlet_id","outlet").put("device_id",issuedDevice)
                        .put("role",role.name).put("issued_at_ms",base).put("expires_at_ms",base+30L*24*60*60*1000).toString()
                }
                path.endsWith("invite-staff") -> {if(invitationStatus!=200)throw AuthHttpFailure(invitationStatus);"{}"}
                path.endsWith("list_device_requests") -> "[]"
                else -> "{}"
            }
        }
    }
    private fun repo(http:FakeHttp,store:MemoryStore,clock:FakeClock,configuration:BackendConfig=config)=
        AuthRepositoryImpl(AuthApi(http,Clock.fixed(Instant.ofEpochMilli(base),ZoneOffset.UTC)),configuration,store,clock)
    private suspend fun signedIn(repository:AuthRepositoryImpl){repository.signIn("user@example.invalid","test-password-value");repository.selectOutlet("outlet")}
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
        assertTrue(restored.state.value.canOffline);restored.unlockOffline();assertTrue(restored.state.value.offline)
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
        assertEquals(AuthStage.LOGIN,repo.state.value.stage);assertFalse(repo.state.value.canOffline);assertNull(store.value)
        repo.unlockOffline();assertFalse(repo.state.value.offline)
    }
    @Test fun pendingApprovalCannotBecomeReadyOrOfflineUntilServerApproves():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        http.pending=true;signedIn(repo)
        assertEquals(AuthMessage.DEVICE_PENDING,repo.state.value.message);assertFalse(repo.state.value.canOffline)
        http.pending=false;repo.refresh();assertEquals(AuthStage.READY,repo.state.value.stage)
    }
    @Test fun activationCanRetryPasswordAfterOneTimeCodeWasConsumed():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val first=repo(http,store,clock)
        http.passwordFailure=true;first.activate("test-invitation-code-hash-value","test-password-value")
        assertEquals(AuthStage.ACTIVATE,first.state.value.stage);assertTrue(first.state.value.activationPending)
        val restored=repo(http,store,clock);restored.restore();assertTrue(restored.state.value.activationPending)
        restored.activate("","test-password-value");assertEquals(1,http.activations);assertEquals(AuthStage.OUTLETS,restored.state.value.stage)
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
    @Test fun invitationPermissionsAndRateErrorsDoNotTrustUiRoles():Unit=runBlocking {
        val http=FakeHttp(base);val store=MemoryStore();val clock=FakeClock(base);val repo=repo(http,store,clock)
        signedIn(repo);repo.invite("new@example.invalid",Role.MANAGER)
        assertFalse(http.calls.any{it.endsWith("invite-staff")})
        http.role=Role.MANAGER;repo.refresh();http.invitationStatus=429
        repo.invite("new@example.invalid",Role.CASHIER)
        assertEquals(AuthStage.READY,repo.state.value.stage);assertEquals(AuthMessage.RATE_LIMIT,repo.state.value.message)
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
    @Test fun backendConfigRejectsSecretKeysAndInsecureUrls() {
        fun jwt(role:String)="a."+Base64.getUrlEncoder().withoutPadding().encodeToString("{\"role\":\"$role\"}".toByteArray())+".b"
        assertTrue(BackendConfig.valid(config.url,"sb_publishable_test_fixture"))
        assertTrue(BackendConfig.valid(config.url,jwt("anon")))
        assertFalse(BackendConfig.valid(config.url,jwt("service_role")))
        assertFalse(BackendConfig.valid(config.url,"sb_secret_test_fixture"))
        assertFalse(BackendConfig.valid("http://example.invalid","sb_publishable_test_fixture"))
        assertFalse(BackendConfig.valid("https://user:password@example.invalid","sb_publishable_test_fixture"))
    }
}
